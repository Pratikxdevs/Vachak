package com.vachak.ui.pdf

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pure worksheet content model (frozen spec §9): HEADER (Vachak, grade,
 * domain/lesson, learning outcome) + QUESTIONS (bilingual, typed, answered)
 * + FOOTER (teacher/date/score). No Android dependencies — unit-tested.
 */
object WorksheetContent {
    fun lines(
        title: String,
        lessonTitle: String,
        grade: Int,
        items: List<Pair<String, String>>,
        learningOutcome: String = "",
        domain: String = ""
    ): List<String> {
        val lines = ArrayList<String>()
        lines += "Vachak • $title"
        lines += "$lessonTitle • Grade $grade" + (if (domain.isNotBlank()) " • $domain" else "")
        if (learningOutcome.isNotBlank()) lines += "Learning Outcome: $learningOutcome"
        lines += "Date: ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())}"
        lines += ""
        items.forEachIndexed { i, (prompt, key) ->
            lines += "${i + 1}. $prompt"
            lines += "   Answer: $key"
            lines += ""
        }
        lines += "Teacher: ____________     Date: ____________     Score: ____"
        return lines
    }
}
