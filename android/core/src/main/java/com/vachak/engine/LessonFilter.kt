package com.vachak.engine

/**
 * Centralized lesson category filter — replaces duplicate title.contains heuristics
 * previously copy-pasted in HomeScreen and CurriculumScreen.
 *
 * Frozen SIH spec (docs/curriculum.md): exactly two domains — Foundational
 * **Literacy** and Foundational **Numeracy**. Lesson sub-skills stay
 * oral | reading | writing (all Literacy). Numeracy is matched by topic
 * heuristic until numeracy lessons are seeded with a dedicated sub-skill.
 * Legacy categories (Language/Mathematics/EVS/Stories/Life Skills) still match
 * so old callers never silently empty out.
 * Stable: no randomness, no allocation outside call.
 */
object LessonFilter {
    fun matches(lesson: Lesson, category: String): Boolean {
        if (category == "All") return true
        val domain = lesson.domain.lowercase().trim()
        val id = lesson.id.lowercase()
        val title = lesson.title.lowercase()
        val numeracy = isNumeracy(id, title)
        // Blank domain (legacy callers that map LessonRef without a domain)
        // falls back to the numeracy heuristic so Literacy/Numeracy filters
        // never silently empty out: non-numeracy ⇒ Literacy.
        val literacyDomain = domain.isEmpty() || domain in setOf("oral", "reading", "writing")
        return when (category) {
            "Literacy" -> literacyDomain && !numeracy
            "Numeracy" -> numeracy
            "Language" -> matches(lesson, "Literacy")
            "Mathematics" -> matches(lesson, "Numeracy")
            "EVS" -> id.contains("evs") ||
                title.contains("evs") || title.contains("around") ||
                title.contains("plant") || title.contains("पेड़") ||
                title.contains("परिवार") || title.contains("प्रकृति")
            "Stories" -> title.contains("story") || title.contains("कहानी") || domain == "reading" && id.contains("read")
            "Life Skills" -> title.contains("life") || title.contains("skill")
            else -> true
        }
    }

    private fun isNumeracy(id: String, title: String): Boolean =
        id.contains("math") || id.contains("count") || id.contains("num") ||
            title.contains("number") || title.contains("math") ||
            title.contains("count") || title.contains("shape") ||
            title.contains("गिनती") || title.contains("आकार") ||
            title.contains("संख्या") || title.contains("जोड़") || title.contains("माप")

    /** Display domain per frozen spec: Literacy | Numeracy. */
    fun domainLabel(lesson: Lesson): String =
        if (matches(lesson, "Numeracy")) "Numeracy" else "Literacy"
    fun stableDeckCount(key: String): String {
        val n = (kotlin.math.abs(key.hashCode()) % 13) + 8
        return "$n cards"
    }
}
