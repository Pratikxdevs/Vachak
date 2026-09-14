package com.vachak.ui.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.vachak.engine.Worksheet
import java.io.File
import java.io.FileOutputStream

/**
 * Offline worksheet PDF ("mock PDF" wired real): renders a generated
 * [Worksheet] (template-based, precomputed — never AI-generated) to a
 * single-page A4 PDF under filesDir/worksheets via android.graphics.pdf.
 * No network, no print service needed; opens in any installed viewer through
 * FileProvider, or use [share] for a send chooser.
 */
object WorksheetPdf {
    const val DIR = "worksheets"
    private const val PAGE_W = 595 // A4 @72dpi
    private const val PAGE_H = 842
    private const val MARGIN = 48
    private const val LINE = 22f

    data class SavedPdf(val file: File, val pages: Int)

    fun dir(context: Context): File = File(context.filesDir, DIR).also { it.mkdirs() }

    fun list(context: Context): List<File> =
        dir(context).listFiles { f -> f.extension.lowercase() == "pdf" }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun delete(file: File): Boolean = try { file.delete() } catch (_: Exception) { false }

    /** Pure content model (unit-tested in WorksheetContentTest; no Android). */
    fun contentLines(title: String, lessonTitle: String, grade: Int, items: List<Pair<String, String>>): List<String> =
        WorksheetContent.lines(title, lessonTitle, grade, items)

    fun generate(context: Context, worksheet: Worksheet, lessonTitle: String, grade: Int): SavedPdf {
        val items = worksheet.items.map { it.prompt to it.answerKey }
        val lines = contentLines(worksheet.template.ifBlank { "Worksheet" }, lessonTitle, grade, items)
        val doc = PdfDocument()
        val titlePaint = Paint().apply { textSize = 20f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val headPaint = Paint().apply { textSize = 12f; color = 0xFF6B5CA5.toInt() }
        val bodyPaint = Paint().apply { textSize = 12f }
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        var y = MARGIN.toFloat()
        var pages = 1
        fun newPage() {
            doc.finishPage(page)
            pages++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pages).create())
            y = MARGIN.toFloat()
        }
        lines.forEachIndexed { idx, line ->
            val paint = when (idx) { 0 -> titlePaint; 1, 2 -> headPaint; else -> bodyPaint }
            if (y > PAGE_H - MARGIN) newPage()
            // Naive wrap at ~80 chars (ASCII-safe; Indic scripts render unshaped but legible).
            val chunks = line.chunked(80).ifEmpty { listOf("") }
            chunks.forEach { c ->
                if (y > PAGE_H - MARGIN) newPage()
                page.canvas.drawText(c, MARGIN.toFloat(), y, paint)
                y += LINE
            }
        }
        doc.finishPage(page)
        val file = File(dir(context), "${worksheet.id.ifBlank { "worksheet" }}-${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return SavedPdf(file, pages)
    }

    fun viewIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun shareIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        return Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Share worksheet"
        )
    }
}
