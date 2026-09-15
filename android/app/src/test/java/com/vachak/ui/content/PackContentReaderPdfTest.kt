package com.vachak.ui.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase 12: pool-PDF ref parsing/resolution. Pure-string helpers only —
 * JSONObject-backed parsePdfRef is covered by the Python converter tests +
 * device manual check (android.jar JSONObject stubs throw on JVM).
 */
class PackContentReaderPdfTest {

    private fun ref(path: String, sha: String? = null) =
        PackContentReader.PdfRef(path, sha)

    @Test
    fun `resolvePdfRef accepts pool-relative pdf`() {
        assertEquals(
            "pdf_pool/a.pdf",
            PackContentReader.resolvePdfRef(ref("pdf_pool/a.pdf"))
        )
    }

    @Test
    fun `resolvePdfRef strips subdirs to basename under pool`() {
        assertEquals(
            "pdf_pool/a.pdf",
            PackContentReader.resolvePdfRef(ref("worksheets/a.pdf"))
        )
    }

    @Test
    fun `resolvePdfRef rejects traversal`() {
        assertNull(PackContentReader.resolvePdfRef(ref("../evil.pdf")))
        assertNull(PackContentReader.resolvePdfRef(ref("pdf_pool/../../evil.pdf")))
        assertNull(PackContentReader.resolvePdfRef(ref("..")))
    }

    @Test
    fun `resolvePdfRef rejects absolute and blank`() {
        assertNull(PackContentReader.resolvePdfRef(ref("/abs/a.pdf")))
        assertNull(PackContentReader.resolvePdfRef(ref("")))
        assertNull(PackContentReader.resolvePdfRef(ref("   ")))
    }

    @Test
    fun `resolvePdfRef rejects non-pdf`() {
        assertNull(PackContentReader.resolvePdfRef(ref("pdf_pool/a.zip")))
        assertNull(PackContentReader.resolvePdfRef(ref("pdf_pool/")))
    }

    @Test
    fun `resolvePdfRef backslash normalized`() {
        assertEquals(
            "pdf_pool/a.pdf",
            PackContentReader.resolvePdfRef(ref("pdf_pool\\a.pdf"))
        )
    }

    @Test
    fun `PdfRef carries sha`() {
        val r = ref("pdf_pool/a.pdf", "ABCDEF")
        assertEquals("pdf_pool/a.pdf", r.relativePath)
        assertEquals("ABCDEF", r.sha256)
    }

    @Test
    fun `ChapterBundle worksheetPdf defaults null`() {
        val b = PackContentReader.ChapterBundle(
            grade = 1, slug = "s", source = PackContentReader.BundleSource.SUMMARY_ONLY,
            assignments = emptyList(), questions = emptyList(), cards = emptyList(),
            imageFiles = emptyList(), titleSatDeva = null, titleHi = null,
            notices = emptyList()
        )
        assertNull(b.worksheetPdf)
    }
}
