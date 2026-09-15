package com.vachak.ui.pdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vachak.ui.theme.VachakColors
import java.io.File

/**
 * Phase 12: in-app PDF pager for pool-backed worksheet slots. Offline,
 * framework-only (no new dependency, no network).
 *
 * Memory discipline (2 GB devices): ONE live page bitmap (RGB_565, capped at
 * display width), recycled on page change and on dispose; renderer + fd
 * closed in DisposableEffect. Files over [MAX_PDF_BYTES] or with more than
 * [MAX_PDF_PAGES] pages are refused into the Failed state — never rendered.
 */
const val MAX_PDF_BYTES: Long = 30L * 1024 * 1024
const val MAX_PDF_PAGES: Int = 200
private const val RENDER_WIDTH_PX: Int = 1080

/** Pure bounds clamp for page index (JVM-testable). */
internal fun clampPage(index: Int, pageCount: Int): Int {
    if (pageCount <= 0) return 0
    return index.coerceIn(0, pageCount - 1)
}

/** Pure cap check (JVM-testable). Null = renderable; non-null = refuse reason. */
internal fun rejectFile(sizeBytes: Long, pageCount: Int): String? = when {
    sizeBytes > MAX_PDF_BYTES -> "Worksheet file is too large to open on this device."
    pageCount > MAX_PDF_PAGES -> "Worksheet has too many pages to open on this device."
    pageCount <= 0 -> "Worksheet file has no readable pages."
    else -> null
}

private sealed interface PdfUi {
    data object Loading : PdfUi
    data class Ready(val pageCount: Int) : PdfUi
    data class Failed(val reason: String) : PdfUi
}

@Composable
fun PdfViewer(
    pdfFile: File,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    var ui by remember(pdfFile) { mutableStateOf<PdfUi>(PdfUi.Loading) }
    var pageIndex by remember(pdfFile) { mutableStateOf(0) }
    var bitmap by remember(pdfFile) { mutableStateOf<Bitmap?>(null) }

    var renderer by remember(pdfFile) { mutableStateOf<PdfRenderer?>(null) }
    DisposableEffect(pdfFile) {
        var fd: ParcelFileDescriptor? = null
        try {
            if (!pdfFile.isFile || pdfFile.length() > MAX_PDF_BYTES) {
                ui = PdfUi.Failed("Worksheet file is too large to open on this device.")
            } else {
                fd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val r = PdfRenderer(fd!!)
                fd = null // owned by the renderer now
                val reject = rejectFile(pdfFile.length(), r.pageCount)
                if (reject != null) {
                    runCatching { r.close() }
                    ui = PdfUi.Failed(reject)
                } else {
                    renderer = r
                    pageIndex = clampPage(0, r.pageCount)
                    ui = PdfUi.Ready(r.pageCount)
                }
            }
        } catch (t: Throwable) {
            runCatching { fd?.close() }
            ui = PdfUi.Failed(t.message ?: "Couldn't open this worksheet.")
        }
        onDispose {
            bitmap?.let { old -> runCatching { if (!old.isRecycled) old.recycle() } }
            bitmap = null
            runCatching { renderer?.close() }
            renderer = null
        }
    }

    LaunchedEffect(pdfFile, pageIndex, ui) {
        val r = renderer ?: return@LaunchedEffect
        if (ui !is PdfUi.Ready) return@LaunchedEffect
        try {
            // Page implements AutoCloseable (not Closeable) — no .use(); close manually.
            val page = r.openPage(pageIndex.coerceIn(0, r.pageCount - 1))
            try {
                val scale = (RENDER_WIDTH_PX.toFloat() / page.width).coerceAtMost(1f)
                val w = (page.width * scale).toInt().coerceAtLeast(1)
                val h = (page.height * scale).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap?.let { old -> runCatching { if (!old.isRecycled) old.recycle() } }
                bitmap = bmp
            } finally {
                runCatching { page.close() }
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (_: Exception) {
            // Keep the previous page (if any); a single bad page never kills the viewer.
        }
    }

    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (val state = ui) {
            is PdfUi.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = VachakColors.Lavender600)
                    Text("Opening worksheet…", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                }
            }
            is PdfUi.Failed -> Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Couldn't open worksheet", style = MaterialTheme.typography.titleSmall, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(state.reason, style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                    Button(
                        onClick = {
                            runCatching {
                                ctx.startActivity(WorksheetPdf.viewIntent(ctx, pdfFile))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VachakColors.PrimaryDark, contentColor = Color.White),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("Open externally")
                    }
                }
            }
            is PdfUi.Ready -> {
                val bmp = bitmap
                if (bmp != null && !bmp.isRecycled) {
                    Image(
                        bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 560.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, Color.Black.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                } else {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f, fill = false), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = VachakColors.Lavender600)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { pageIndex = clampPage(pageIndex - 1, state.pageCount) },
                        enabled = pageIndex > 0,
                        shape = RoundedCornerShape(50)
                    ) { Text("Previous") }
                    Text(
                        "${pageIndex + 1} / ${state.pageCount}",
                        style = MaterialTheme.typography.labelMedium,
                        color = VachakColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Button(
                        onClick = { pageIndex = clampPage(pageIndex + 1, state.pageCount) },
                        enabled = pageIndex < state.pageCount - 1,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = VachakColors.PrimaryDark, contentColor = Color.White)
                    ) { Text("Next") }
                }
            }
        }
    }
}
