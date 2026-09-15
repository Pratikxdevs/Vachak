package com.vachak.ui.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Phase 12: worksheet question-image key guard (pure, JVM-safe). */
class PackWorksheetImageTest {

    @Test
    fun `questionImageKey builds grade-slug-ref key`() {
        assertEquals(
            "1/so-many-toys/pages/p01_top.webp",
            PackContentReader.questionImageKey(1, "so-many-toys", "pages/p01_top.webp")
        )
    }

    @Test
    fun `questionImageKey rejects blank null absolute`() {
        assertNull(PackContentReader.questionImageKey(1, "s", null))
        assertNull(PackContentReader.questionImageKey(1, "s", ""))
        assertNull(PackContentReader.questionImageKey(1, "s", "   "))
        assertNull(PackContentReader.questionImageKey(1, "s", "/abs/x.webp"))
    }

    @Test
    fun `questionImageKey rejects traversal`() {
        assertNull(PackContentReader.questionImageKey(1, "s", "../x.webp"))
        assertNull(PackContentReader.questionImageKey(1, "s", "pages/../../x.webp"))
    }

    @Test
    fun `questionImageKey normalizes backslash`() {
        assertEquals(
            "1/s/pages/a.webp",
            PackContentReader.questionImageKey(1, "s", "pages\\a.webp")
        )
    }
}
