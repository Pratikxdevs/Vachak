package com.vachak.engine

/**
 * Common result wrapper. All engines return a [Result] so callers handle
 * success/failure uniformly without per-engine branching.
 */
sealed interface EngineResult<out T> {
    data class Ok<out T>(val value: T) : EngineResult<T>
    data class Err(val code: EngineError, val message: String) : EngineResult<Nothing>
}

enum class EngineError {
    UNSUPPORTED_LANGUAGE,
    MODEL_NOT_LOADED,
    MODEL_LOAD_FAILED,
    MODEL_DECODE_FAILED,
    TIMEOUT,
    IO_ERROR,
    INVALID_INPUT,
    OFFLINE_REQUIRED
}

data class LanguagePair(val source: String, val target: String)

/** Fold helper so callers handle Ok/Err uniformly (mirrors kotlin.Result.fold). */
inline fun <T, R> EngineResult<T>.fold(
    onOk: (T) -> R,
    onErr: (EngineError, String) -> R
): R = when (this) {
    is EngineResult.Ok -> onOk(value)
    is EngineResult.Err -> onErr(code, message)
}

/** Latency budget (ms) per the SIH acceptance test: total < 3000ms. */
object LatencyBudget {
    const val ASR_MS = 1000L
    const val MT_MS = 500L
    const val TTS_MS = 1000L
    const val TOTAL_MS = 3000L
}

/**
 * TranslationEngine — Hindi -> target (Mundari/Ol Chiki) machine translation.
 * Interface only. Swap the implementation (mock / IndicTrans2 / ONNX) without
 * touching UI code (see EngineProvider).
 */
interface TranslationEngine {
    fun supports(pair: LanguagePair): Boolean
    fun translate(text: String, pair: LanguagePair): EngineResult<String>
    fun loadModel(packId: String): EngineResult<Unit>
}

/**
 * ASREngine — speech -> text. Push-to-talk capture is handled by the caller
 * (AudioPipeline); this engine consumes a 16-bit PCM buffer.
 */
interface ASREngine {
    fun supports(language: String): Boolean
    fun loadModel(packId: String): EngineResult<Unit>
    fun transcribe(pcm16: ShortArray, sampleRateHz: Int): EngineResult<String>
}

/**
 * TTSEngine — text -> speech. Returns 16-bit PCM to be played by AudioTrack.
 */
interface TTSEngine {
    fun supports(language: String): Boolean
    fun loadModel(packId: String): EngineResult<Unit>
    fun synthesize(text: String, language: String): EngineResult<ShortArray>
}

/**
 * CurriculumEngine — serves FLN lessons / outcomes from the local Room DB.
 * Curriculum translations are PRECOMPUTED, never generated on device.
 */
interface CurriculumEngine {
    fun listLessons(grade: Int): EngineResult<List<LessonRef>>
    fun getLesson(id: String): EngineResult<Lesson>
    fun getOutcomes(lessonId: String): EngineResult<List<Outcome>>
    /** Activities for a lesson (frozen spec §6). Empty until authored — never invented. */
    fun getActivities(lessonId: String): EngineResult<List<Activity>> =
        EngineResult.Ok(emptyList())
    /** Assessment prompts for a lesson (frozen spec §6). Empty until authored. */
    fun getAssessments(lessonId: String): EngineResult<List<AssessmentPrompt>> =
        EngineResult.Ok(emptyList())
}

data class LessonRef(val id: String, val title: String, val grade: Int, val domain: String = "")
data class Lesson(
    val id: String,
    val title: String,
    val grade: Int,
    val sourceTextHi: String,
    val translatedText: String,
    val precomputed: Boolean = true,
    val domain: String = ""
)
data class Outcome(val id: String, val description: String, val nipunMapped: Boolean)

/** Frozen spec §6: teacher-led activity (Hindi + Santhali instructions). */
data class Activity(
    val id: String,
    val lessonId: String,
    val titleHi: String,
    val instructionHi: String,
    val instructionSat: String,
    val materials: String = ""
)

/** Frozen spec §6: assessment prompt (Hindi + Santhali + expected response). */
data class AssessmentPrompt(
    val id: String,
    val lessonId: String,
    val promptHi: String,
    val promptSat: String,
    val expectedResponse: String = ""
)

/**
 * WorksheetEngine — TEMPLATE-BASED worksheets. Not AI-generated.
 */
interface WorksheetEngine {
    fun generate(lessonId: String, template: String): EngineResult<Worksheet>
}

data class Worksheet(val id: String, val lessonId: String, val template: String, val items: List<WorksheetItem>)
data class WorksheetItem(val prompt: String, val answerKey: String)

/**
 * FlashcardEngine — PREBUILT flashcard assets. Not generated.
 */
interface FlashcardEngine {
    fun listDeck(lessonId: String): EngineResult<FlashcardDeck>
}

data class FlashcardDeck(val lessonId: String, val cards: List<Flashcard>)
data class Flashcard(val front: String, val back: String, val imageAsset: String)

/**
 * LanguagePackManager — installs/removes/validates offline language packs.
 * See offline/model_registry for the manifest schema and storage logic.
 */
interface LanguagePackManager {
    fun install(packPath: String): EngineResult<PackInfo>
    fun uninstall(packId: String): EngineResult<Unit>
    fun installed(): EngineResult<List<PackInfo>>
    fun validate(packId: String): EngineResult<Boolean>
    fun rollback(packId: String): EngineResult<PackInfo>
    fun storageUsedBytes(): EngineResult<Long>
}

data class PackInfo(
    val id: String,
    val language: String,
    val version: String,
    val minAndroid: Int,
    val sizeBytes: Long
)

/**
 * SyncManager — offline PACKAGE installer. NEVER a network client.
 * Syncs from a local/side-loaded package, not the internet.
 */
interface SyncManager {
    fun isNetworkAllowed(): Boolean = false
    fun installPackage(path: String): EngineResult<SyncReport>
    fun lastSync(): EngineResult<SyncReport?>
}

data class SyncReport(val packId: String, val version: String, val timestampMs: Long, val applied: Boolean)

/**
 * BenchmarkRunner — measures latency/memory of the sequential pipeline under
 * the <3s budget. Runs locally; no network.
 */
interface BenchmarkRunner {
    fun run(pair: LanguagePair, sample: String): EngineResult<BenchmarkReport>
}

data class BenchmarkReport(
    val asrMs: Long,
    val mtMs: Long,
    val ttsMs: Long,
    val totalMs: Long,
    val withinBudget: Boolean
)
