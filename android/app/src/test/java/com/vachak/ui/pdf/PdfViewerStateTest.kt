package com.vachak.ui.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

/** Phase 12: PdfViewer pure helpers (JVM-safe, no Android calls). */
class PdfViewerStateTest {

    @Test
    fun `clampPage bounds`() {
        assertEquals(0, clampPage(-3, 9))
        assertEquals(0, clampPage(0, 9))
        assertEquals(4, clampPage(4, 9))
        assertEquals(8, clampPage(99, 9))
    }

    @Test
    fun `clampPage zero pages`() {
        assertEquals(0, clampPage(5, 0))
        assertEquals(0, clampPage(-1, 0))
    }

    @Test
    fun `rejectFile allows normal`() {
        assertNull(rejectFile(3_200_000L, 9))
    }

    @Test
    fun `rejectFile refuses oversize`() {
        assertNotNull(rejectFile(MAX_PDF_BYTES + 1, 9))
    }

    @Test
    fun `rejectFile refuses too many pages`() {
        assertNotNull(rejectFile(1_000_000L, MAX_PDF_PAGES + 1))
    }

    @Test
    fun `rejectFile refuses empty`() {
        assertNotNull(rejectFile(100L, 0))
    }
}
