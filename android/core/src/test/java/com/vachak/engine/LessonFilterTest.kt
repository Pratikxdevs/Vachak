package com.vachak.engine

import org.junit.Assert.*
import org.junit.Test

/**
 * Filter regression lock: Literacy/Numeracy must work even for lessons mapped
 * without a domain (legacy LessonRef path), falling back to the numeracy
 * heuristic so filters never silently empty out.
 */
class LessonFilterTest {

    private fun lesson(id: String, title: String, domain: String = "") =
        Lesson(id = id, title = title, grade = 1, sourceTextHi = "", translatedText = "", domain = domain)

    @Test
    fun `blank domain non-numeracy is Literacy`() {
        val l = lesson("L-SAT-G1-ORAL-01", "अभिवादन और अपना नाम बताना")
        assertTrue(LessonFilter.matches(l, "All"))
        assertTrue(LessonFilter.matches(l, "Literacy"))
        assertFalse(LessonFilter.matches(l, "Numeracy"))
        assertEquals("Literacy", LessonFilter.domainLabel(l))
    }

    @Test
    fun `blank domain numeracy title is Numeracy`() {
        val l = lesson("L-SAT-G1-MATH-01", "1 से 10 तक गिनती — उंगलियों से")
        assertFalse(LessonFilter.matches(l, "Literacy"))
        assertTrue(LessonFilter.matches(l, "Numeracy"))
        assertEquals("Numeracy", LessonFilter.domainLabel(l))
    }

    @Test
    fun `explicit oral domain literacy still works`() {
        val l = lesson("L-SAT-G1-READ-01", "ओल चिकी अक्षरों को पहचानना", domain = "reading")
        assertTrue(LessonFilter.matches(l, "Literacy"))
        assertFalse(LessonFilter.matches(l, "Numeracy"))
    }

    @Test
    fun `explicit domain numeracy-shaped title excluded from Literacy`() {
        val l = lesson("L-SAT-G1-MATH-02", "आकार और रंग पहचानना", domain = "reading")
        assertFalse(LessonFilter.matches(l, "Literacy"))
        assertTrue(LessonFilter.matches(l, "Numeracy"))
    }
}
