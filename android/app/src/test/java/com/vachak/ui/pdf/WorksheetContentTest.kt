package com.vachak.ui.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorksheetContentTest {
    @Test
    fun `header questions footer structure`() {
        val lines = WorksheetContent.lines(
            title = "Trace Ol Chiki",
            lessonTitle = "Letters & Sounds",
            grade = 1,
            items = listOf("Trace ᱐" to "᱐", "Trace ᱑" to "᱑"),
            learningOutcome = "Identify Ol Chiki vowels",
            domain = "Literacy"
        )
        assertTrue(lines[0].startsWith("Vachak"))
        assertTrue(lines.any { it.contains("Grade 1") && it.contains("Literacy") })
        assertTrue(lines.any { it.contains("Learning Outcome:") })
        assertTrue(lines.any { it.startsWith("1. Trace") })
        assertTrue(lines.any { it.contains("Answer: ᱑") })
        assertTrue(lines.last().contains("Teacher:"))
        assertTrue(lines.last().contains("Score:"))
    }

    @Test
    fun `empty items still well-formed`() {
        val lines = WorksheetContent.lines("T", "L", 2, emptyList())
        assertTrue(lines.isNotEmpty())
        assertTrue(lines.last().contains("Score:"))
        assertEquals(0, lines.count { it.matches(Regex("\\d+\\. .*")) })
    }

    @Test
    fun `question numbering sequential`() {
        val items = (1..15).map { "Q$it" to "A$it" }
        val lines = WorksheetContent.lines("T", "L", 3, items)
        (1..15).forEach { n ->
            assertTrue("missing Q$n", lines.any { it.startsWith("$n. Q$n") })
        }
    }
}
