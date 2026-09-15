package com.vachak.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vachak.ui.content.PackContentReader
import com.vachak.ui.theme.VachakColors
import com.vachak.ui.theme.cardShadow
import com.vachak.ui.theme.tabletHPad

/**
 * Shared renderers for installed-pack worksheets and decks.
 * Used by chapter sub-pages (Learn tree) and Tools studio panes alike —
 * one renderer, no duplicated logic, real pack data only.
 */

/** Pack worksheet questions. */
@Composable
fun PackWorksheetView(
    questions: List<PackQuestionAlias>,
    grade: Int = -1,
    slug: String = ""
) {
    val ctx = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = tabletHPad(), vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Index-suffixed keys (same duplicate-prompt hazard as the chapter
        // Read list — pack ids are not guaranteed unique).
        items(questions.withIndex().toList(), key = { (idx, q) -> "${q.id}-$idx" }) { (_, q) ->
            Surface(shape = RoundedCornerShape(16.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), shadowElevation = 0.dp, tonalElevation = 0.dp, modifier = Modifier.fillMaxWidth().cardShadow(RoundedCornerShape(16.dp))) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(50), color = VachakColors.SoftLavender) {
                            Text(q.type, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = VachakColors.DeepLavender, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    // Phase 15: full-visibility preview — entire image fits
                    // border-to-border (Fit, full width, tall cap).
                    if (grade >= 1 && slug.isNotBlank() && !q.imageRef.isNullOrBlank()) {
                        var thumb by remember(q.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
                        LaunchedEffect(q.id) {
                            try {
                                val bmp = PackContentReader.decodeQuestionImage(ctx, grade, slug, q.imageRef)
                                thumb?.let { old -> runCatching { if (!old.isRecycled) old.recycle() } }
                                thumb = bmp
                            } catch (ce: kotlinx.coroutines.CancellationException) {
                                throw ce
                            } catch (_: Exception) {
                                thumb = null
                            }
                        }
                        DisposableEffect(q.id) {
                            onDispose {
                                thumb?.let { old -> runCatching { if (!old.isRecycled) old.recycle() } }
                            }
                        }
                        thumb?.let { bmp ->
                            if (!bmp.isRecycled) {
                                Image(
                                    bmp.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .border(1.dp, Color.Black.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                )
                            }
                        }
                    }
                    Text(q.prompt.ifBlank { q.id }, style = MaterialTheme.typography.bodyMedium, color = VachakColors.TextPrimary)
                    if (!q.promptHi.isNullOrBlank()) {
                        Text(q.promptHi, style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                    }
                    if (q.renderCount > 0) {
                        Text("Count objects: ${q.renderCount}", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                    }
                    // Phase 14: practice sheet shows questions only — answer
                    // keys stay in pack data, never on screen.
                }
            }
        }
    }
}

typealias PackQuestionAlias = PackContentReader.PackQuestion
typealias PackCardAlias = PackContentReader.PackCard

/** Pack flashcard deck: tap to flip, prev/next, real art (APK prebuilt or installed pack). */
@Composable
fun PackDeckView(grade: Int, cards: List<PackCardAlias>) {
    val ctx = LocalContext.current
    var idx by remember(cards) { mutableIntStateOf(0) }
    var flipped by remember(cards) { mutableStateOf(false) }
    val card = cards.getOrNull(idx.coerceIn(cards.indices))
    if (card == null) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("No cards in this deck.", style = MaterialTheme.typography.bodyMedium, color = VachakColors.TextSecondary)
        }
        return
    }
    var art by remember(card.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(card.id) {
        try {
            val bmp = PackContentReader.decodeDeckImage(ctx, grade, card.imageRef)
            art?.let { old -> runCatching { if (!old.isRecycled) old.recycle() } }
            art = bmp
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (_: Exception) {
            art = null
        }
        flipped = false
    }
    DisposableEffect(card.id) {
        onDispose {
            art?.let { old -> runCatching { if (!old.isRecycled) old.recycle() } }
        }
    }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(tabletHPad()), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp, max = 520.dp).cardShadow(RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border),
                shadowElevation = 0.dp,
                tonalElevation = 0.dp,
                onClick = { flipped = !flipped }
            ) {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (flipped) "Ol Chiki" else "Santali (Deva)",
                    style = MaterialTheme.typography.labelMedium, color = VachakColors.TextSecondary
                )
                // Phase 14: face scrolls internally so long prompts always fit
                // the card on small tablets; tap-to-flip still works on tap.
                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val face = if (flipped) card.target else card.frontDeva
                    if (face.isNullOrBlank()) {
                        Text("—", style = MaterialTheme.typography.bodyMedium, color = VachakColors.TextSecondary)
                    } else {
                        Text(face, style = MaterialTheme.typography.headlineSmall, color = VachakColors.TextPrimary, fontWeight = FontWeight.Bold)
                    }
                    // Large art on top: full-bleed illustration, options/text below.
                    art?.let { bmp ->
                        Image(
                            bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 280.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, Color.Black.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                    }
                }
                Text(
                    if (flipped) "Tap to see Santali" else "Tap to flip",
                    style = MaterialTheme.typography.bodySmall, color = VachakColors.DeepLavender
                )
            }
        }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { if (idx > 0) { idx--; flipped = false } }, enabled = idx > 0, shape = RoundedCornerShape(50)) { Text("Previous") }
                Text("${idx + 1} / ${cards.size}", style = MaterialTheme.typography.labelMedium, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            Button(onClick = { if (idx < cards.lastIndex) { idx++; flipped = false } }, enabled = idx < cards.lastIndex, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = VachakColors.PrimaryDark, contentColor = Color.White)) { Text("Next") }
        }
    }
}
