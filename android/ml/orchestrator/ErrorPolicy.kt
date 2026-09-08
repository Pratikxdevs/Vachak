package com.vachak.engine.orchestrator

import com.vachak.engine.*

/**
 * Per-stage graceful degradation (PHASE 9C). Mirrors shared/orchestrator/errors.py.
 *
 *  ASR fail      -> retry (bounded) then clear error
 *  Translate fail-> show SOURCE (Hindi) text
 *  TTS fail      -> show TRANSLATED text
 *  Model missing -> PROMPT_LANGUAGE_PACK action
 */
enum class Degradation { RETRY, SHOW_SOURCE, SHOW_TRANSLATION, PROMPT_LANGUAGE_PACK, HARD_ERROR }

data class StagePolicy(val retries: Int = 0, val degradation: Degradation, val userMessage: String)

class ErrorPolicy(
    val asrRetries: Int = 2,
    val asrMessage: String = "आवाज़ साफ़ नहीं सुनी गई। कृपया दोबारा बोलें।",
    val translateMessage: String = "अनुवाद उपलब्ध नहीं; हिंदी पाठ दिखाया जा रहा है।",
    val ttsMessage: String = "आवाज़ नहीं बनी; अनुवादित पाठ दिखाया जा रहा है।",
    val modelMessage: String = "भाषा पैक अभी इंस्टॉल नहीं है। कृपया भाषा पैक इंस्टॉल करें।",
) {
    fun forStage(stage: String): StagePolicy = when (stage) {
        "asr" -> StagePolicy(asrRetries, Degradation.RETRY, asrMessage)
        "translate" -> StagePolicy(0, Degradation.SHOW_SOURCE, translateMessage)
        "tts" -> StagePolicy(0, Degradation.SHOW_TRANSLATION, ttsMessage)
        else -> StagePolicy(0, Degradation.HARD_ERROR, "")
    }

    fun decide(stage: String, attempt: Int, modelUnavailable: Boolean): Pair<Degradation, String> {
        if (modelUnavailable) return Degradation.PROMPT_LANGUAGE_PACK to modelMessage
        val p = forStage(stage)
        if (p.degradation == Degradation.RETRY && attempt < p.retries) {
            return Degradation.RETRY to p.userMessage
        }
        return p.degradation to p.userMessage
    }
}
