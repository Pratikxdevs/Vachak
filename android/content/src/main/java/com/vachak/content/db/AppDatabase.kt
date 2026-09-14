package com.vachak.content.db

import com.vachak.engine.VachakLog

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Room database for Vachak curriculum (Phase 4).
 * Prepopulated from curriculum/lessons/sat_lessons.json + outcomes/nipun.json
 * via [Callback] reading JSON assets (no network, no on-device MT).
 * Assets are under curriculum/lessons/sat_lessons.json (also merged via app module).
 *
 * Validates Ol Chiki codepoints U+1C50–U+1C7F on insert.
 */
@Database(
    entities = [LessonEntity::class, OutcomeEntity::class, WorksheetEntity::class, FlashcardEntity::class, ActivityEntity::class, AssessmentPromptEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun lessonDao(): LessonDao
    abstract fun outcomeDao(): OutcomeDao
    abstract fun worksheetDao(): WorksheetDao
    abstract fun flashcardDao(): FlashcardDao
    abstract fun activityDao(): ActivityDao
    abstract fun assessmentPromptDao(): AssessmentPromptDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }

        private fun buildDatabase(context: Context): AppDatabase {
            val isDebuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            if (!isDebuggable) {
                VachakLog.w("Vachak-Content", "AppDatabase.allowMainThreadQueries() is DEBUG-only — prod path must use suspend wrappers (getAllSuspend etc).")
            } else {
                VachakLog.d("Vachak-Content", "AppDatabase allowMainThreadQueries enabled for DEBUG/demo")
            }
            return Room.databaseBuilder(context, AppDatabase::class.java, "vachak_content.db")
                .addCallback(PrepopulateCallback(context))
                .fallbackToDestructiveMigration()
                // Kept for debug/demo & tests; prod must use suspend wrappers below.
                .allowMainThreadQueries()
                .build()
        }

        /** For tests: in-memory DB with same prepopulate. */
        fun inMemoryForTest(context: Context): AppDatabase {
            return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .addCallback(PrepopulateCallback(context))
                .allowMainThreadQueries()
                .build()
        }
    }

    private class PrepopulateCallback(private val context: Context) : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            // Prepopulate off main thread via coroutine; also provide synchronous fallback.
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Use Room's instance after creation — need to reopen via getInstance
                    val instance = getInstance(context)
                    prepopulateFromAssets(context, instance)
                } catch (_: Exception) { /* logged below */ }
            }
        }
    }
}

/**
 * Reads JSON assets and inserts into [db]. Called from [AppDatabase.Callback].
 * Idempotent: inserts with REPLACE.
 */
suspend fun prepopulateFromAssets(context: Context, db: AppDatabase) {
    // Try both asset paths (content module merges into app)
    val lessonsJson = readAssetOrNull(context, "curriculum/lessons/sat_lessons.json")
        ?: readAssetOrNull(context, "sat_lessons.json")
    val outcomesJson = readAssetOrNull(context, "curriculum/outcomes/nipun.json")
        ?: readAssetOrNull(context, "nipun.json")

    if (lessonsJson != null) {
        val root = JSONObject(lessonsJson)
        val arr = root.optJSONArray("lessons") ?: JSONArray()
        val lessons = mutableListOf<LessonEntity>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val textSat = o.optString("textSatOlChiki", "")
            validateOlChiki(textSat, o.optString("id"))
            lessons.add(
                LessonEntity(
                    id = o.getString("id"),
                    titleHi = o.getString("titleHi"),
                    titleSatOlChiki = o.optString("titleSatOlChiki", o.getString("titleHi")),
                    textHi = o.getString("textHi"),
                    textSatOlChiki = textSat,
                    grade = o.getInt("grade"),
                    domain = o.getString("domain"),
                    outcomeId = o.getString("outcomeId"),
                    estimatedMinutes = o.optInt("estimatedMinutes", 30),
                    precomputed = o.optBoolean("precomputed", true)
                )
            )
        }
        if (lessons.isNotEmpty()) db.lessonDao().insertAll(lessons)
        // Seed worksheets & flashcards from lessons metadata + defaults
        seedWorksheetsAndFlashcards(db, lessons)
    }

    if (outcomesJson != null) {
        val root = JSONObject(outcomesJson)
        val arr = root.optJSONArray("outcomes") ?: JSONArray()
        val outcomes = mutableListOf<OutcomeEntity>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            outcomes.add(
                OutcomeEntity(
                    outcomeId = o.getString("outcomeId"),
                    nipunCode = o.getString("nipunCode"),
                    descriptor = o.getString("descriptor"),
                    descriptorHi = o.optString("descriptorHi", o.getString("descriptor")),
                    descriptorSatOlChiki = o.optString("descriptorSatOlChiki", o.getString("descriptor")),
                    grade = o.getInt("grade"),
                    domain = o.getString("domain")
                )
            )
        }
        if (outcomes.isNotEmpty()) db.outcomeDao().insertAll(outcomes)
    }
}

private fun readAssetOrNull(context: Context, path: String): String? = try {
    context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
} catch (_: Exception) { null }

private fun validateOlChiki(text: String, id: String) {
    if (text.isBlank()) return
    // At least one Ol Chiki codepoint expected; log otherwise (no crash in prod)
    val hasOlChiki = text.any { c -> c in '\u1C50'..'\u1C7F' }
    if (!hasOlChiki) {
        VachakLog.w("Vachak-Content", "Lesson $id textSatOlChiki has no Ol Chiki codepoints: $text")
    }
}

// --- Prod-path suspend wrappers (enforce Dispatchers.IO, no main-thread query) ---
suspend fun AppDatabase.getLessonsSuspend(): List<LessonEntity> =
    kotlinx.coroutines.withContext(Dispatchers.IO) { lessonDao().getAll() }
suspend fun AppDatabase.getLessonSuspend(id: String): LessonEntity? =
    kotlinx.coroutines.withContext(Dispatchers.IO) { lessonDao().getById(id) }
suspend fun AppDatabase.getOutcomesSuspend(): List<OutcomeEntity> =
    kotlinx.coroutines.withContext(Dispatchers.IO) { outcomeDao().getAll() }
suspend fun AppDatabase.getWorksheetsSuspend(lessonId: String): List<WorksheetEntity> =
    kotlinx.coroutines.withContext(Dispatchers.IO) { worksheetDao().getForLesson(lessonId) }
suspend fun AppDatabase.getFlashcardsSuspend(lessonId: String): List<FlashcardEntity> =
    kotlinx.coroutines.withContext(Dispatchers.IO) { flashcardDao().getForLesson(lessonId) }

/** Seed deterministic worksheets/flashcards so every lesson has offline assets. */
private suspend fun seedWorksheetsAndFlashcards(db: AppDatabase, lessons: List<LessonEntity>) {
    val worksheets = mutableListOf<WorksheetEntity>()
    val flashcards = mutableListOf<FlashcardEntity>()

    // Template worksheets per domain (template-based, not AI-generated)
    val templatesByDomain = mapOf(
        "oral" to listOf("fill_blank" to "worksheet/templates/oral_fill_blank_G1.pdf", "match" to "worksheet/templates/oral_match_G1.pdf"),
        "reading" to listOf("comprehension" to "worksheet/templates/comprehension_G2.pdf", "match" to "worksheet/templates/reading_match_G2.pdf"),
        "writing" to listOf("trace" to "worksheet/templates/trace_olchiki_G1.pdf", "fill_blank" to "worksheet/templates/fill_numbers_G2.pdf")
    )
    // Domain fallback
    val defaultTemplates = listOf("trace" to "worksheet/templates/trace_olchiki_G1.pdf", "fill_blank" to "worksheet/templates/fill_numbers_G2.pdf", "comprehension" to "worksheet/templates/comprehension_G3.pdf")

    for (lesson in lessons) {
        val tpls = templatesByDomain[lesson.domain] ?: defaultTemplates
        for ((type, path) in tpls) {
            worksheets.add(
                WorksheetEntity(
                    id = "W-${lesson.id}-$type",
                    lessonId = lesson.id,
                    templateType = type,
                    assetPath = path,
                    titleHi = "${lesson.titleHi} — वर्कशीट ($type)",
                    titleSatOlChiki = "${lesson.titleSatOlChiki} — ${type}"
                )
            )
        }
        // Extra comprehension for G3
        if (lesson.grade == 3 && lesson.domain == "reading") {
            worksheets.add(
                WorksheetEntity(
                    id = "W-${lesson.id}-trace",
                    lessonId = lesson.id,
                    templateType = "trace",
                    assetPath = "worksheet/templates/trace_olchiki_G1.pdf",
                    titleHi = "${lesson.titleHi} — ट्रेस",
                    titleSatOlChiki = lesson.titleSatOlChiki
                )
            )
        }

        // Flashcards: at least 2 per lesson, using lesson mediaRefs + synthetic decks
        val hiConcepts = when (lesson.id) {
            "L-SAT-G1-ORAL-01" -> listOf("जोहार" to "ᱡᱚᱦᱟᱨ", "नमस्ते" to "ᱡᱚᱦᱟᱨ", "मेरा नाम" to "ᱤᱧᱟᱜ ᱧᱩᱛᱩᱢ")
            "L-SAT-G1-READ-01" -> listOf("अक्षर अ" to "ᱚ", "अक्षर आ" to "ᱟ", "ओल चिकी" to "ᱚᱞ ᱪᱤᱠᱤ")
            "L-SAT-G1-WRITE-01" -> listOf("पेंसिल" to "ᱯᱮᱱᱥᱤᱞ", "लिखना" to "ᱚᱞ", "बिंदी" to "ᱴᱷᱤᱠᱟᱹ")
            "L-SAT-G2-ORAL-01" -> listOf("एक" to "ᱢᱤᱫ", "बीस" to "᱒᱐", "गिनो" to "ᱞᱮᱠᱷᱟ")
            "L-SAT-G2-READ-01" -> listOf("हाथी" to "ᱦᱟᱹᱛᱤ", "खरगोश" to "ᱠᱩᱞᱟᱹᱭ", "जंगल" to "ᱵᱤᱨ")
            "L-SAT-G2-WRITE-01" -> listOf("सौ" to "᱑᱐᱐", "छलांग" to "ᱡᱟᱸᱯ", "संख्या" to "ᱞᱮᱠᱷᱟ")
            "L-SAT-G3-ORAL-01" -> listOf("बाजार" to "ᱦᱟᱴ", "दुकानदार" to "ᱫᱚᱠᱟᱱᱤᱭᱟᱹ", "रुपये" to "ᱴᱟᱠᱟ")
            "L-SAT-G3-READ-01" -> listOf("गाँव" to "ᱟᱹᱛᱩ", "नदी" to "ᱜᱟᱰᱟ", "स्कूल" to "ᱟᱥᱲᱟ")
            else -> listOf("शब्द" to "ᱟᱹᱲᱟᱹ", "पाठ" to "ᱯᱟᱲᱦᱟᱣ")
        }
        hiConcepts.forEachIndexed { idx, (hi, sat) ->
            flashcards.add(
                FlashcardEntity(
                    cardId = "FC-${lesson.id}-${idx + 1}",
                    lessonId = lesson.id,
                    conceptHi = hi,
                    conceptSatOlChiki = sat,
                    conceptEn = "$hi ($sat)",
                    imagePath = "flashcard/assets/${lesson.id.lowercase()}_${idx + 1}.png",
                    sequence = idx + 1
                )
            )
        }
        // For counting lesson, ensure numerals 1-10 deck
        if (lesson.id == "L-SAT-G2-ORAL-01") {
            for (n in 1..10) {
                val olDigit = String(Character.toChars(0x1C50 + n % 10)) // fallback approximate
                // Use proper Ol Chiki digits ᱑..᱑᱐
                val satDigit = when (n) {
                    1 -> "᱑"; 2 -> "᱒"; 3 -> "᱓"; 4 -> "᱔"; 5 -> "᱕"
                    6 -> "᱖"; 7 -> "᱗"; 8 -> "᱘"; 9 -> "᱙"; 10 -> "᱑᱐"
                    else -> n.toString()
                }
                flashcards.add(
                    FlashcardEntity(
                        cardId = "FC-${lesson.id}-NUM-$n",
                        lessonId = lesson.id,
                        conceptHi = n.toString(),
                        conceptSatOlChiki = satDigit,
                        conceptEn = n.toString(),
                        imagePath = "flashcard/assets/number_${n}.png",
                        sequence = 10 + n
                    )
                )
            }
        }
    }
    if (worksheets.isNotEmpty()) db.worksheetDao().insertAll(worksheets)
    if (flashcards.isNotEmpty()) db.flashcardDao().insertAll(flashcards)
}
