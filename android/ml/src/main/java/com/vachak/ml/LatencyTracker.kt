package com.vachak.ml

import com.vachak.engine.VachakLog

import android.os.SystemClock
import java.util.concurrent.TimeUnit

/**
 * 5D — Latency instrumentation (Kotlin). Mirrors ml/pipeline/latency.py.
 *
 * Marks (monotonic, not wall-clock):
 *   T0 speech begins | T1 ASR | T2 translate | T3 TTS begins | T4 audio begins
 *   end_to_end = T4 - T0
 *
 * IMPORTANT: latency MUST be MEASURED on the real device (2GB RAM Android 9). With mock/
 * fixture backends these numbers are NOT representative of production. Do NOT claim <=3s
 * from fixtures — the <3s target is a device-measurement goal, not a code guarantee.
 */
data class LatencySample(
    val runId: String,
    var t0SpeechBegin: Long? = null, // nanoseconds
    var t1Asr: Long? = null,
    var t2Translate: Long? = null,
    var t3TtsBegin: Long? = null,
    var t4AudioBegin: Long? = null,
    /** Stop-tap nanos (benchmark T0 for stop→translate). */
    var tStopTap: Long? = null,
    /** Translated-text-visible nanos (benchmark T1: ASR+MT headline). */
    var tTranslateShown: Long? = null,
    var asrText: String = "",
    var targetText: String = "",
    var backend: String = "",
    var isFixture: Boolean = false
) {
    fun endToEndMs(): Float? {
        if (t0SpeechBegin == null || t4AudioBegin == null) return null
        return (t4AudioBegin!! - t0SpeechBegin!!) / 1_000_000f
    }

    /** Headline benchmark: stop-tap → translated text on screen (ASR+MT only,
     * voice excluded by product definition). The <3s budget applies HERE. */
    fun stopToTranslateMs(): Float? {
        if (tStopTap == null || tTranslateShown == null) return null
        return (tTranslateShown!! - tStopTap!!) / 1_000_000f
    }

    fun stageMs(): Map<String, Float?> = mapOf(
        "asr" to span(t0SpeechBegin, t1Asr),
        "translate" to span(t1Asr, t2Translate),
        "tts" to span(t2Translate, t3TtsBegin),
        "render" to span(t3TtsBegin, t4AudioBegin)
    )

    /**
     * Phase 4 honesty fix: the REAL TTS synthesis cost. Callers run
     * markTtsBegin() -> tts.synthesize() (blocking generate) ->
     * markAudioBegin(), so generate() lands in the "render" span while the
     * "tts" span is just the MT-done -> synth-start queue gap (~0ms).
     * Diagnostics showed TTS as ~0ms because it read stages["tts"].
     * Every UI/budget consumer must use THIS for the TTS number.
     */
    fun ttsSynthMs(): Float? = span(t3TtsBegin, t4AudioBegin)

    private fun span(a: Long?, b: Long?): Float? =
        if (a == null || b == null) null else (b - a) / 1_000_000f
}

class LatencyTracker(private val sample: LatencySample) {
    fun markSpeechBegin(text: String = "") { if (sample.t0SpeechBegin == null) sample.t0SpeechBegin = now(); sample.asrText = text }
    fun markAsr(text: String = "") { sample.t1Asr = now(); if (text.isNotEmpty()) sample.asrText = text }
    fun markTranslate(targetText: String = "") { sample.t2Translate = now(); if (targetText.isNotEmpty()) sample.targetText = targetText }
    fun markTtsBegin() { sample.t3TtsBegin = now() }
    fun markAudioBegin() { sample.t4AudioBegin = now() }
    fun markStopTap(ns: Long) { sample.tStopTap = ns }
    fun markTranslateShown() { sample.tTranslateShown = now() }
    fun result(): LatencySample = sample
    private fun now(): Long = SystemClock.elapsedRealtimeNanos()
}

/**
 * Last completed voice/typed pipeline run, published for DiagnosticsScreen
 * and the debug dialog. Replaces the old mock benchmark card: timings shown
 * anywhere in the UI are MEASURED here, never canned. Null until the first
 * run completes (UI must render "not measured yet", not zeros).
 */
object LastPipelineRun {
    @Volatile var sample: LatencySample? = null
        private set

    fun publish(tracker: LatencyTracker) {
        sample = tracker.result()
        val s = sample!!
        VachakLog.d(
            "Vachak-Latency",
            "RUN ${s.runId} stages=${s.stageMs()} totalMs=${s.endToEndMs()} " +
                "stopToTranslateMs=${s.stopToTranslateMs()} " +
                "withinBudget=${(s.stopToTranslateMs() ?: Float.MAX_VALUE) < 3000}"
        )
    }
}
