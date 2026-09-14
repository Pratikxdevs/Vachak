package com.vachak.content

import android.content.Context
import com.vachak.content.db.AppDatabase
import com.vachak.content.db.FlashcardEntity
import com.vachak.content.db.LessonEntity
import com.vachak.content.db.OutcomeEntity
import com.vachak.content.db.WorksheetEntity
import com.vachak.content.db.getLessonsSuspend
import com.vachak.content.db.prepopulateFromAssets
import com.vachak.engine.CurriculumEngine
import com.vachak.engine.EngineError
import com.vachak.engine.EngineResult
import com.vachak.engine.Flashcard
import com.vachak.engine.FlashcardDeck
import com.vachak.engine.FlashcardEngine
import com.vachak.engine.Lesson
import com.vachak.engine.LessonRef
import com.vachak.engine.Outcome
import com.vachak.engine.Worksheet
import com.vachak.engine.WorksheetEngine
import com.vachak.engine.WorksheetItem
import kotlinx.coroutines.runBlocking

/**
 * Real content engine for Phase 4: Room-backed FLN lessons + NIPUN outcomes +
 * template worksheets + prebuilt flashcards.
 *
 * Replaces MockCurriculumEngine. All translations are PRECOMPUTED (textSatOlChiki stored in DB),
 * never computed via on-device MT at runtime. Worksheets are template PDFs, flashcards are
 * prebuilt PNGs — both opened offline via assetPath (no generation, no network).
 *
 * Threading: Room DAOs are suspend; this engine blocks via runBlocking for the synchronous
 * EngineContracts interface (acceptable for small offline queries on 2GB device). For Compose,
 * callers may move to Dispatchers.IO.
 */
class ContentEngine(private val context: Context) : CurriculumEngine, WorksheetEngine, FlashcardEngine {

    private val db: AppDatabase by lazy { AppDatabase.getInstance(context) }

    private fun ensurePrepopulated() {
        // Best-effort blocking prepopulate if DB still empty (e.g., fresh install)
        runBlocking {
            try {
                if (db.lessonDao().count() == 0) {
                    prepopulateFromAssets(context, db)
                }
            } catch (_: Exception) { /* logged in callback */ }
        }
    }

    // --- CurriculumEngine ---

    override fun listLessons(grade: Int): EngineResult<List<LessonRef>> {
        ensurePrepopulated()
        return runBlocking {
            try {
                val entities = if (grade <= 0) db.lessonDao().getAll() else db.lessonDao().getByGrade(grade)
                if (entities.isEmpty() && grade <= 0) return@runBlocking EngineResult.Err(EngineError.IO_ERROR, "no lessons seeded")
                EngineResult.Ok(entities.map { it.toRef() })
            } catch (e: Exception) {
                EngineResult.Err(EngineError.IO_ERROR, e.message ?: "listLessons failed")
            }
        }
    }

    // Prod suspend path — no runBlocking, no main-thread query
    suspend fun getLessonsSuspend(): EngineResult<List<Lesson>> = try {
        val entities = db.getLessonsSuspend()
        EngineResult.Ok(entities.map { it.toLesson() })
    } catch (e: Exception) {
        EngineResult.Err(EngineError.IO_ERROR, e.message ?: "getLessonsSuspend failed")
    }

    /** Full lesson list regardless of grade — used by UI. */
    fun getLessons(): EngineResult<List<Lesson>> = runBlocking {
        ensurePrepopulated()
        try {
            val entities = db.lessonDao().getAll()
            EngineResult.Ok(entities.map { it.toLesson() })
        } catch (e: Exception) {
            EngineResult.Err(EngineError.IO_ERROR, e.message ?: "getLessons failed")
        }
    }

    override fun getLesson(id: String): EngineResult<Lesson> {
        ensurePrepopulated()
        return runBlocking {
            try {
                val e = db.lessonDao().getById(id)
                    ?: return@runBlocking EngineResult.Err(EngineError.INVALID_INPUT, "lesson $id not found")
                EngineResult.Ok(e.toLesson())
            } catch (e: Exception) {
                EngineResult.Err(EngineError.IO_ERROR, e.message ?: "getLesson failed")
            }
        }
    }

    override fun getOutcomes(lessonId: String): EngineResult<List<Outcome>> {
        ensurePrepopulated()
        return runBlocking {
            try {
                val lesson = db.lessonDao().getById(lessonId)
                    ?: return@runBlocking EngineResult.Err(EngineError.INVALID_INPUT, "lesson $lessonId not found")
                val outcome = db.outcomeDao().getById(lesson.outcomeId)
                if (outcome != null) {
                    EngineResult.Ok(listOf(outcome.toOutcome()))
                } else {
                    EngineResult.Ok(emptyList())
                }
            } catch (e: Exception) {
                EngineResult.Err(EngineError.IO_ERROR, e.message ?: "getOutcomes failed")
            }
        }
    }

    /** Extended: outcome for a lesson (single, deterministic). */
    fun getOutcome(lessonId: String): EngineResult<Outcome?> = runBlocking {
        ensurePrepopulated()
        try {
            val lesson = db.lessonDao().getById(lessonId)
                ?: return@runBlocking EngineResult.Err(EngineError.INVALID_INPUT, "lesson $lessonId not found")
            val outcome = db.outcomeDao().getById(lesson.outcomeId)
            EngineResult.Ok(outcome?.toOutcome())
        } catch (e: Exception) {
            EngineResult.Err(EngineError.IO_ERROR, e.message ?: "getOutcome failed")
        }
    }

    /** NIPUN-mapped outcome entity (richer than EngineContracts.Outcome). */
    fun getOutcomeEntity(lessonId: String): OutcomeEntity? = runBlocking {
        val lesson = db.lessonDao().getById(lessonId) ?: return@runBlocking null
        db.outcomeDao().getById(lesson.outcomeId)
    }

    override fun getActivities(lessonId: String): EngineResult<List<com.vachak.engine.Activity>> {
        ensurePrepopulated()
        return runBlocking {
            try {
                EngineResult.Ok(
                    db.activityDao().getForLesson(lessonId).map {
                        com.vachak.engine.Activity(
                            id = it.id, lessonId = it.lessonId, titleHi = it.titleHi,
                            instructionHi = it.instructionHi, instructionSat = it.instructionSatOlChiki,
                            materials = it.materials
                        )
                    }
                )
            } catch (e: Exception) {
                EngineResult.Err(EngineError.IO_ERROR, e.message ?: "getActivities failed")
            }
        }
    }

    override fun getAssessments(lessonId: String): EngineResult<List<com.vachak.engine.AssessmentPrompt>> {
        ensurePrepopulated()
        return runBlocking {
            try {
                EngineResult.Ok(
                    db.assessmentPromptDao().getForLesson(lessonId).map {
                        com.vachak.engine.AssessmentPrompt(
                            id = it.id, lessonId = it.lessonId, promptHi = it.promptHi,
                            promptSat = it.promptSatOlChiki, expectedResponse = it.expectedResponse
                        )
                    }
                )
            } catch (e: Exception) {
                EngineResult.Err(EngineError.IO_ERROR, e.message ?: "getAssessments failed")
            }
        }
    }

    // --- WorksheetEngine ---

    override fun generate(lessonId: String, template: String): EngineResult<Worksheet> {
        ensurePrepopulated()
        return runBlocking {
            try {
                val list = db.worksheetDao().getForLesson(lessonId)
                val chosen = if (template.isBlank()) list.firstOrNull()
                else list.firstOrNull { it.templateType == template } ?: list.firstOrNull()
                if (chosen == null) return@runBlocking EngineResult.Err(EngineError.INVALID_INPUT, "no worksheet for $lessonId")
                EngineResult.Ok(
                    Worksheet(
                        id = chosen.id,
                        lessonId = lessonId,
                        template = chosen.templateType,
                        items = listOf(WorksheetItem(prompt = chosen.titleHi, answerKey = chosen.assetPath))
                    )
                )
            } catch (e: Exception) {
                EngineResult.Err(EngineError.IO_ERROR, e.message ?: "generate worksheet failed")
            }
        }
    }

    /** All template worksheets for a lesson (offline PDFs via assetPath). */
    fun getWorksheets(lessonId: String): EngineResult<List<WorksheetEntity>> = runBlocking {
        ensurePrepopulated()
        try {
            val list = db.worksheetDao().getForLesson(lessonId)
            if (list.isEmpty()) return@runBlocking EngineResult.Err(EngineError.INVALID_INPUT, "no worksheets for $lessonId")
            EngineResult.Ok(list)
        } catch (e: Exception) {
            EngineResult.Err(EngineError.IO_ERROR, e.message ?: "getWorksheets failed")
        }
    }

    // --- FlashcardEngine ---

    override fun listDeck(lessonId: String): EngineResult<FlashcardDeck> {
        ensurePrepopulated()
        return runBlocking {
            try {
                val cards = db.flashcardDao().getForLesson(lessonId)
                if (cards.isEmpty()) return@runBlocking EngineResult.Err(EngineError.INVALID_INPUT, "no flashcards for $lessonId")
                EngineResult.Ok(
                    FlashcardDeck(
                        lessonId = lessonId,
                        cards = cards.map { it.toFlashcard() }
                    )
                )
            } catch (e: Exception) {
                EngineResult.Err(EngineError.IO_ERROR, e.message ?: "listDeck failed")
            }
        }
    }

    /** Raw flashcard entities (with imagePath, sequence). */
    fun getFlashcards(lessonId: String): EngineResult<List<FlashcardEntity>> = runBlocking {
        ensurePrepopulated()
        try {
            val cards = db.flashcardDao().getForLesson(lessonId)
            if (cards.isEmpty()) return@runBlocking EngineResult.Err(EngineError.INVALID_INPUT, "no flashcards for $lessonId")
            EngineResult.Ok(cards)
        } catch (e: Exception) {
            EngineResult.Err(EngineError.IO_ERROR, e.message ?: "getFlashcards failed")
        }
    }

    // --- Mappers ---
    private fun LessonEntity.toRef() = LessonRef(id = id, title = titleHi, grade = grade, domain = domain)
    private fun LessonEntity.toLesson() = Lesson(
        id = id,
        title = titleHi,
        grade = grade,
        sourceTextHi = textHi,
        translatedText = textSatOlChiki,
        precomputed = precomputed,
        domain = domain
    )
    private fun OutcomeEntity.toOutcome() = Outcome(
        id = outcomeId,
        description = descriptor,
        nipunMapped = true
    )
    private fun FlashcardEntity.toFlashcard() = Flashcard(
        front = conceptHi,
        back = conceptSatOlChiki,
        imageAsset = imagePath
    )

    companion object {
        /** Validate Ol Chiki codepoints — public for testing. */
        fun isOlChikiValid(text: String): Boolean {
            if (text.isBlank()) return false
            return text.any { c -> c in '\u1C50'..'\u1C7F' }
        }
    }
}
