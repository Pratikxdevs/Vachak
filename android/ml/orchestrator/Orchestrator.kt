package com.vachak.engine.orchestrator

import com.vachak.engine.*

/**
 * Orchestrator — request lifecycle (PHASE 9A). Kotlin wiring that binds the
 * shared/orchestrator state machine to the existing EngineContracts.
 *
 * Sequential only (RAM budget): ASR -> MT -> TTS -> play. Never parallelize.
 * Uses EngineResult<T> uniformly; EngineError.MODEL_NOT_LOADED is the signal for
 * PROMPT_LANGUAGE_PACK (see ErrorPolicy).
 *
 * Components come from the EngineProvider (mock in dev, sherpa-onnx /
 * IndicTrans2 in production). This file only sequences them and applies the
 * graceful-degradation policy; it contains no model code.
 */
enum class PipelineStage { CAPTURE, VAD, ASR, CONTEXT, TRANSLATE, VALIDATE, SYNTHESIZE, SPEAK, DONE }

enum class RequestStatus { OK, DEGRADED, FAILED }

data class StageResult(val stage: String, val ok: Boolean, val ms: Long, val error: String? = null, val action: String? = null)

data class PipelineResult(
    val status: RequestStatus,
    val transcript: String? = null,
    val translation: String? = null,
    val presentText: String? = null,
    val presentAudio: ShortArray? = null,
    val promptLanguagePack: Boolean = false,
    val error: String? = null,
    val stages: List<StageResult> = emptyList(),
    val totalMs: Long = 0L,
)

/**
 * Local contract for terminology validation (not in EngineContracts). Swap with
 * a glossary-backed implementation; on failure the orchestrator shows the source.
 */
interface TerminologyValidator {
    fun validate(text: String, context: ClassroomState): ValidationOutcome
    data class ValidationOutcome(val ok: Boolean, val normalized: String, val issues: List<String>)
}

/**
 * Local contract for audio playback (EngineContracts.TTSEngine returns PCM;
 * AudioTrack owns playback on device).
 */
interface Speaker {
    fun play(pcm16: ShortArray)
}

class Orchestrator(
    private val asr: ASREngine,
    private val nmt: TranslationEngine,
    private val tts: TTSEngine,
    private val validator: TerminologyValidator? = null,
    private val speaker: Speaker? = null,
    private val packManager: LanguagePackManager? = null,
    private val state: ClassroomState = ClassroomState(),
    private val policy: ErrorPolicy = ErrorPolicy(),
    private val budgetMs: Long = LatencyBudget.TOTAL_MS,
) {
    fun processUtterance(pcm16: ShortArray, sampleRateHz: Int): PipelineResult {
        val t0 = System.nanoTime()
        val stages = mutableListOf<StageResult>()

        // 1. ASR with retry
        var transcript: String? = null
        var asrOk = false
        var lastErr: String? = null
        for (attempt in 0..policy.asrRetries) {
            when (val r = asr.transcribe(pcm16, sampleRateHz)) {
                is EngineResult.Ok -> { transcript = r.value; asrOk = true
                    stages += StageResult(PipelineStage.ASR.name, true, 0); break }
                is EngineResult.Err -> {
                    lastErr = r.message
                    stages += StageResult(PipelineStage.ASR.name, false, 0, r.message)
                    if (r.code == EngineError.MODEL_NOT_LOADED)
                        return finish(RequestStatus.FAILED, stages, t0, promptPack = true, error = policy.modelMessage)
                    val (deg, msg) = policy.decide("asr", attempt, false)
                    if (deg != Degradation.RETRY) return finish(RequestStatus.FAILED, stages, t0, error = msg)
                }
            }
        }
        if (!asrOk) return finish(RequestStatus.FAILED, stages, t0, error = lastErr)
        state.transcript = transcript

        // 2. Translate (fallback: show source)
        val pair = LanguagePair(state.sourceLanguage, state.targetLanguage)
        val translation = when (val r = nmt.translate(transcript!!, pair)) {
            is EngineResult.Ok -> r.value
            is EngineResult.Err -> {
                if (r.code == EngineError.MODEL_NOT_LOADED)
                    return finish(RequestStatus.FAILED, stages, t0, promptPack = true, error = policy.modelMessage)
                return finish(RequestStatus.DEGRADED, stages, t0, presentText = transcript,
                    error = policy.translateMessage)
            }
        }
        state.translation = translation
        stages += StageResult(PipelineStage.TRANSLATE.name, true, 0)

        // 3. Validate
        if (validator != null) {
            val v = validator.validate(translation, state)
            if (!v.ok)
                return finish(RequestStatus.DEGRADED, stages, t0, presentText = transcript,
                    error = "terminology validation failed")
            state.translation = v.normalized
        }

        // 4. Synthesize (fallback: show translation)
        val audio = when (val r = tts.synthesize(translation, state.targetLanguage)) {
            is EngineResult.Ok -> r.value
            is EngineResult.Err -> {
                if (r.code == EngineError.MODEL_NOT_LOADED)
                    return finish(RequestStatus.FAILED, stages, t0, promptPack = true, error = policy.modelMessage)
                return finish(RequestStatus.DEGRADED, stages, t0, presentText = translation,
                    error = policy.ttsMessage)
            }
        }
        stages += StageResult(PipelineStage.SYNTHESIZE.name, true, 0)

        // 5. Speak
        speaker?.play(audio)
        return finish(RequestStatus.OK, stages, t0, presentAudio = audio, transcript = transcript, translation = translation)
    }

    private fun finish(
        status: RequestStatus, stages: List<StageResult>, t0: Long,
        presentText: String? = null, presentAudio: ShortArray? = null,
        transcript: String? = null, translation: String? = null,
        error: String? = null, promptPack: Boolean = false,
    ): PipelineResult {
        state.lastStage = PipelineStage.DONE.name
        state.lastStatus = status.name
        state.lastError = error
        state.lastAction = if (promptPack) "prompt_language_pack" else null
        return PipelineResult(
            status = status, transcript = transcript ?: state.transcript,
            translation = translation ?: state.translation,
            presentText = presentText, presentAudio = presentAudio,
            promptLanguagePack = promptPack, error = error, stages = stages,
            totalMs = (System.nanoTime() - t0) / 1_000_000,
        )
    }
}
