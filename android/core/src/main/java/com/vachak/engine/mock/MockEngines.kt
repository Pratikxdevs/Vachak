package com.vachak.engine.mock

import com.vachak.engine.*

/**
 * DEV FIXTURES — mock engine implementations used by unit tests and as the
 * default offline build stand-in until real models are wired. They produce
 * deterministic, clearly-fake output and MUST NOT be mistaken for real
 * inference. Swap these for sherpa-onnx / IndicTrans2 adapters in production
 * via EngineProvider without changing any UI code.
 */
object MockTranslationEngine : TranslationEngine {
    override fun supports(pair: LanguagePair) = pair.source == "hi" && pair.target == "mund"
    override fun loadModel(packId: String) = EngineResult.Ok(Unit)
    override fun translate(text: String, pair: LanguagePair): EngineResult<String> =
        if (supports(pair)) EngineResult.Ok("[DEV-FIXTURE-mund] $text")
        else EngineResult.Err(EngineError.UNSUPPORTED_LANGUAGE, "pair $pair unsupported by mock")
}

object MockAsrEngine : ASREngine {
    override fun supports(language: String) = language == "hi"
    override fun loadModel(packId: String) = EngineResult.Ok(Unit)
    override fun transcribe(pcm16: ShortArray, sampleRateHz: Int): EngineResult<String> {
        require(sampleRateHz > 0) { "sampleRateHz must be positive" }
        return if (pcm16.isEmpty()) EngineResult.Err(EngineError.INVALID_INPUT, "empty pcm")
        else EngineResult.Ok("[DEV-FIXTURE-asr] नमस्ते")
    }
}

object MockTtsEngine : TTSEngine {
    override fun supports(language: String) = language == "mund"
    override fun loadModel(packId: String) = EngineResult.Ok(Unit)
    override fun synthesize(text: String, language: String): EngineResult<ShortArray> {
        if (!supports(language)) return EngineResult.Err(EngineError.UNSUPPORTED_LANGUAGE, language)
        // deterministic silence-ish pcm, length scales with text (placeholder)
        return EngineResult.Ok(ShortArray(text.length) { (it % 2).toShort() })
    }
}

object MockCurriculumEngine : CurriculumEngine {
    override fun listLessons(grade: Int) =
        EngineResult.Ok(listOf(LessonRef("L1", "DEV-FIXTURE lesson $grade", grade)))
    override fun getLesson(id: String) = EngineResult.Ok(
        Lesson(id, "DEV-FIXTURE", 1, "हिन्दी पाठ", "[DEV-FIXTURE-mund] lesson", precomputed = true)
    )
    override fun getOutcomes(lessonId: String) =
        EngineResult.Ok(listOf(Outcome("O1", "DEV-FIXTURE outcome", nipunMapped = true)))
}

object MockWorksheetEngine : WorksheetEngine {
    override fun generate(lessonId: String, template: String) = EngineResult.Ok(
        Worksheet("W_$lessonId", lessonId, template,
            listOf(WorksheetItem("DEV-FIXTURE prompt", "DEV-FIXTURE key")))
    )
}

object MockFlashcardEngine : FlashcardEngine {
    override fun listDeck(lessonId: String) = EngineResult.Ok(
        FlashcardDeck(lessonId, listOf(Flashcard("हाथी", "[DEV-FIXTURE] elephant", "asset://elephant.png")))
    )
}
