package com.vachak.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vachak.engine.EngineProvider
import com.vachak.ui.content.PackContentReader
import com.vachak.ui.navigation.loadPackSummary
import com.vachak.ui.theme.VachakColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Chapter sub-pages (Learn tree — never delegates to Tools).
 * learn/grade/{g}/chapter/{slug}/worksheet | .../flashcards
 * Both render from one guarded [PackContentReader.loadChapterBundle] load:
 * Loading / Ready / Empty (with reason) / Failed. No state in this file can
 * crash on corrupt or missing pack data.
 */

private sealed interface BundleUi {
    data object Loading : BundleUi
    data class Ready(val bundle: PackContentReader.ChapterBundle) : BundleUi
    data class Failed(val reason: String) : BundleUi
}

@Composable
private fun loadBundle(grade: Int, slug: String): BundleUi {
    val ctx = LocalContext.current
    var ui by remember(grade, slug) { mutableStateOf<BundleUi>(BundleUi.Loading) }
    LaunchedEffect(grade, slug) {
        ui = BundleUi.Loading
        try {
            val meta = withContext(Dispatchers.IO) {
                runCatching {
                    loadPackSummary(ctx)
                        ?.firstOrNull { it.grade == grade }
                        ?.chapterTitles?.firstOrNull { it.slug == slug }
                }.getOrNull()
            }
            val bundle = withContext(Dispatchers.IO) {
                runCatching { PackContentReader.loadChapterBundle(ctx, grade, slug, meta) }.getOrNull()
            }
            ui = if (bundle != null) BundleUi.Ready(bundle)
            else BundleUi.Failed("Couldn't read this chapter's files. Try again.")
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (t: Throwable) {
            ui = BundleUi.Failed(t.message ?: "Something went wrong.")
        }
    }
    return ui
}

@Composable
private fun StudySubHeader(title: String, subtitle: String, count: String?, onBack: () -> Unit) {
    Surface(color = Color.White, shadowElevation = 0.dp, tonalElevation = 0.dp) {
        Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = VachakColors.TextPrimary) }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary, maxLines = 1)
            }
            if (count != null) {
                Surface(shape = RoundedCornerShape(50), color = VachakColors.SoftLavender) {
                    Text(count, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = VachakColors.DeepLavender, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun CenterNote(title: String, body: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
            if (actionLabel != null && onAction != null) {
                Button(onClick = onAction, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = VachakColors.PrimaryDark, contentColor = Color.White)) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun LoadingNote(label: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(color = VachakColors.Lavender600, modifier = Modifier.size(32.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterWorksheetScreen(
    engine: EngineProvider,
    grade: Int,
    slug: String,
    onOpenPacks: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ui = loadBundle(grade, slug)
    val bundle = (ui as? BundleUi.Ready)?.bundle
    val chapterTitle = bundle?.let { shortTitle(it) } ?: slug
    Column(modifier = modifier.fillMaxSize().background(VachakColors.Background)) {
        StudySubHeader(
            title = "Worksheet",
            subtitle = "Grade $grade • $chapterTitle",
            count = bundle?.questions?.takeIf { it.isNotEmpty() }?.let { "${it.size} items" },
            onBack = onBack
        )
        when (ui) {
            is BundleUi.Loading -> LoadingNote("Opening worksheet…")
            is BundleUi.Failed -> CenterNote("Couldn't open worksheet", (ui as BundleUi.Failed).reason)
            is BundleUi.Ready -> {
                val qs = bundle!!.questions
                if (qs.isEmpty()) {
                    CenterNote(
                        title = "No worksheet here yet",
                        body = (bundle.notices.firstOrNull { it.contains("worksheet", ignoreCase = true) }
                            ?: "This chapter has no worksheet items.") +
                                "\nInstall the latest content pack if you expected items here.",
                        actionLabel = "Open Packs",
                        onAction = onOpenPacks
                    )
                } else {
                    PackWorksheetView(qs)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterDeckScreen(
    engine: EngineProvider,
    grade: Int,
    slug: String,
    onOpenPacks: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ui = loadBundle(grade, slug)
    val bundle = (ui as? BundleUi.Ready)?.bundle
    val chapterTitle = bundle?.let { shortTitle(it) } ?: slug
    Column(modifier = modifier.fillMaxSize().background(VachakColors.Background)) {
        StudySubHeader(
            title = "Flashcards",
            subtitle = "Grade $grade • $chapterTitle",
            count = bundle?.cards?.takeIf { it.isNotEmpty() }?.let { "${it.size} cards" },
            onBack = onBack
        )
        when (ui) {
            is BundleUi.Loading -> LoadingNote("Opening flashcards…")
            is BundleUi.Failed -> CenterNote("Couldn't open flashcards", (ui as BundleUi.Failed).reason)
            is BundleUi.Ready -> {
                val cs = bundle!!.cards
                if (cs.isEmpty()) {
                    CenterNote(
                        title = "No flashcards here yet",
                        body = (bundle.notices.firstOrNull { it.contains("flashcard", ignoreCase = true) }
                            ?: "This chapter has no flashcard deck.") +
                                "\nInstall the latest content pack if you expected cards here.",
                        actionLabel = "Open Packs",
                        onAction = onOpenPacks
                    )
                } else {
                    PackDeckView(grade, cs)
                }
            }
        }
    }
}

private fun shortTitle(bundle: PackContentReader.ChapterBundle): String =
    bundle.titleSatDeva ?: bundle.slug
