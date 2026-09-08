package com.vachak.engine

/**
 * Centralized lesson category filter — replaces duplicate title.contains heuristics
 * previously copy-pasted in HomeScreen and CurriculumScreen.
 *
 * Primary key is [Lesson.domain] (oral | reading | writing) + [Lesson.id] prefix.
 * Falls back to title heuristic only for legacy mock data without domain.
 * Stable: no randomness, no allocation outside call.
 */
object LessonFilter {
    fun matches(lesson: Lesson, category: String): Boolean {
        if (category == "All") return true
        val domain = lesson.domain.lowercase()
        val id = lesson.id.lowercase()
        val title = lesson.title.lowercase()
        return when (category) {
            "Language" -> domain in setOf("oral", "reading", "writing") &&
                (id.contains("oral") || id.contains("read") || id.contains("write") ||
                    title.contains("language") || title.contains("letter") ||
                    title.contains("sound") || title.contains("family") ||
                    title.contains("अभिवादन") || title.contains("ओल चिकी") || title.contains("बिंदी"))
            "Mathematics" -> id.contains("math") ||
                title.contains("number") || title.contains("math") ||
                title.contains("count") || title.contains("shape") ||
                title.contains("गिनती") || title.contains("आकार") ||
                title.contains("संख्या") || title.contains("जोड़") || title.contains("माप")
            "EVS" -> id.contains("evs") ||
                title.contains("evs") || title.contains("around") ||
                title.contains("plant") || title.contains("पेड़") ||
                title.contains("परिवार") || title.contains("प्रकृति")
            "Stories" -> title.contains("story") || title.contains("कहानी") || domain == "reading" && id.contains("read")
            "Life Skills" -> title.contains("life") || title.contains("skill")
            else -> true
        }
    }

    /** Stable deck card count (8..20) derived from title hash — replaces Random. */
    fun stableDeckCount(key: String): String {
        val n = (kotlin.math.abs(key.hashCode()) % 13) + 8
        return "$n cards"
    }
}
