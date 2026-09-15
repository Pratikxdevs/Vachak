package com.vachak.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Style
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
import com.vachak.engine.EngineProvider
import com.vachak.ui.components.BreadcrumbTrail
import com.vachak.ui.components.HomeSectionHeader
import com.vachak.ui.components.ScriptChoice
import com.vachak.ui.components.ScriptToggle
import com.vachak.ui.content.PackContentReader
import com.vachak.ui.navigation.loadPackSummary
import com.vachak.ui.theme.VachakColors
import com.vachak.ui.theme.cardShadow
import com.vachak.ui.theme.tabletHPad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface ChapterUi {
    data object Loading : ChapterUi
    data class Ready(val bundle: PackContentReader.ChapterBundle, val art: List<android.graphics.Bitmap>) : ChapterUi
    data object Missing : ChapterUi
    data class Failed(val reason: String) : ChapterUi
}

/**
 * Chapter page — rewritten around a single guarded [PackContentReader.loadChapterBundle]
 * load. Four states only: Loading / Ready / Missing / Failed. Every bitmap
 * decode, romanization and JSON parse is guarded — this screen cannot crash
 * on bad pack data, it explains instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterScreen(
    engine: EngineProvider,
    grade: Int,
    slug: String,
    onOpenWorksheets: () -> Unit,
    onOpenFlashcards: () -> Unit,
    onOpenPacks: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    var ui by remember(grade, slug) { mutableStateOf<ChapterUi>(ChapterUi.Loading) }
    var script by remember(grade, slug) { mutableStateOf(ScriptChoice.HI) }
    var showNotices by remember(grade, slug) { mutableStateOf(false) }
    var metaTitle by remember(grade, slug) { mutableStateOf(slug) }
    var metaSubject by remember(grade, slug) { mutableStateOf("") }
    val prefs = remember(ctx) { com.vachak.ui.prefs.VachakPrefs(ctx) }
    var done by remember(grade, slug) { mutableStateOf(prefs.isChapterComplete(grade, slug)) }

    LaunchedEffect(grade, slug) {
        ui = ChapterUi.Loading
        try {
            val meta = withContext(Dispatchers.IO) {
                runCatching {
                    loadPackSummary(ctx)
                        ?.firstOrNull { it.grade == grade }
                        ?.chapterTitles?.firstOrNull { it.slug == slug }
                }.getOrNull()
            }
            if (meta != null) {
                metaTitle = meta.title
                metaSubject = meta.subject
            }
            val bundle = withContext(Dispatchers.IO) {
                runCatching { PackContentReader.loadChapterBundle(ctx, grade, slug, meta) }.getOrNull()
            }
            if (bundle == null) {
                ui = if (meta == null) ChapterUi.Missing
                else ChapterUi.Failed("Couldn't read this chapter's files. Try again.")
            } else {
                val art = runCatching {
                    PackContentReader.decodeBundleImages(bundle.imageFiles)
                }.getOrDefault(emptyList())
                ui = ChapterUi.Ready(bundle, art)
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (t: Throwable) {
            ui = ChapterUi.Failed(t.message ?: "Something went wrong.")
        }
    }

    // Recycle gallery bitmaps when leaving.
    DisposableEffect(grade, slug) {
        onDispose {
            (ui as? ChapterUi.Ready)?.art?.forEach {
                runCatching { if (!it.isRecycled) it.recycle() }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().background(VachakColors.Background)) {
        Surface(color = Color.White, shadowElevation = 0.dp, tonalElevation = 0.dp) {
            Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = VachakColors.TextPrimary) }
                Column(modifier = Modifier.weight(1f)) {
                    BreadcrumbTrail(listOf("Learn", "Grade $grade"))
                    Text(metaTitle, style = MaterialTheme.typography.titleLarge, color = VachakColors.TextPrimary, fontWeight = FontWeight.Bold, maxLines = 2)
                    Text(
                        if (metaSubject.isNotBlank()) "Grade $grade • $metaSubject" else "Grade $grade",
                        style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary, maxLines = 1
                    )
                }
                if (done) {
                    Surface(shape = RoundedCornerShape(50), color = VachakColors.SuccessLight) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Outlined.CheckCircle, null, tint = VachakColors.Success, modifier = Modifier.size(14.dp))
                            Text("Done", style = MaterialTheme.typography.labelSmall, color = VachakColors.Success, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        when (val state = ui) {
            is ChapterUi.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = VachakColors.Lavender600, modifier = Modifier.size(32.dp))
                    Text("Opening chapter…", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                }
            }
            is ChapterUi.Missing -> Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Chapter not found", style = MaterialTheme.typography.titleSmall, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text("It may belong to a pack that isn't installed yet.", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                    Button(onClick = onOpenPacks, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = VachakColors.PrimaryDark, contentColor = Color.White)) {
                        Text("Open Packs")
                    }
                }
            }
            is ChapterUi.Failed -> Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Couldn't open this chapter", style = MaterialTheme.typography.titleSmall, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(state.reason, style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                    Button(onClick = onBack, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = VachakColors.PrimaryDark, contentColor = Color.White)) {
                        Text("Go Back")
                    }
                }
            }
            is ChapterUi.Ready -> ChapterContent(
                bundle = state.bundle,
                art = state.art,
                script = script,
                onScript = { script = it },
                showNotices = showNotices,
                onToggleNotices = { showNotices = !showNotices },
                onOpenWorksheets = onOpenWorksheets,
                onOpenFlashcards = onOpenFlashcards,
                done = done,
                onMarkComplete = { prefs.markChapterComplete(grade, slug); done = true }
            )
        }
    }
}

@Composable
private fun ChapterContent(
    bundle: PackContentReader.ChapterBundle,
    art: List<android.graphics.Bitmap>,
    script: ScriptChoice,
    onScript: (ScriptChoice) -> Unit,
    showNotices: Boolean,
    onToggleNotices: () -> Unit,
    onOpenWorksheets: () -> Unit,
    onOpenFlashcards: () -> Unit,
    done: Boolean,
    onMarkComplete: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = tabletHPad(), vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth()) {
                Text(
                    "${bundle.assignments.size} activities • ${bundle.questions.size} worksheets • ${bundle.cards.size} flashcards",
                    style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        if (art.isNotEmpty()) {
            item { HomeSectionHeader(title = "Pictures (${art.size})", actionLabel = null, onAction = null) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 8.dp)) {
                    items(art.size, key = { idx -> "${bundle.slug}-pic-$idx" }) { idx ->
                        val bmp = art.getOrNull(idx)
                        if (bmp != null && !bmp.isRecycled) {
                            Image(
                                runCatching { bmp.asImageBitmap() }.getOrNull()
                                    ?: return@items,
                                contentDescription = null,
                                modifier = Modifier.size(width = 200.dp, height = 140.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(1.dp, Color.Black.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }

        if (bundle.assignments.isNotEmpty()) {
            item { HomeSectionHeader(title = "Read (${bundle.assignments.size})", actionLabel = null, onAction = null) }
            item { ScriptToggle(selected = script, onSelect = onScript) }
            val shown = bundle.assignments.take(20)
            // Index-suffixed keys: pack data legitimately repeats identical
            // prompts (fill-in-the-blank pages), so content-derived keys
            // collide and crash LazyColumn. Index makes them unique by construction.
            items(shown.withIndex().toList(), key = { (idx, a) -> "${a.page}-${a.type}-${a.text.hashCode()}-$idx" }) { (_, a) ->
                Surface(shape = RoundedCornerShape(16.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(50), color = VachakColors.SoftLavender) {
                                Text(a.type, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = VachakColors.DeepLavender, fontWeight = FontWeight.SemiBold)
                            }
                            Text("Page ${a.page}", style = MaterialTheme.typography.labelSmall, color = VachakColors.TextSecondary)
                        }
                        val body = runCatching {
                            when (script) {
                                ScriptChoice.HI -> a.textHi.ifBlank { a.text }
                                ScriptChoice.EN -> com.vachak.ui.text.Romanize.auto(a.text).ifBlank { a.text }
                                ScriptChoice.SAT -> a.text
                            }
                        }.getOrDefault(a.text)
                        if (body.isNotBlank()) Text(body, style = MaterialTheme.typography.bodySmall, color = VachakColors.TextPrimary)
                    }
                }
            }
            if (bundle.assignments.size > shown.size) {
                item {
                    Text(
                        "+${bundle.assignments.size - shown.size} more in the pack",
                        style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary
                    )
                }
            }
        }

        item { HomeSectionHeader(title = "Practice", actionLabel = null, onAction = null) }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PracticeCard(
                    title = "Worksheets",
                    subtitle = if (bundle.questions.isNotEmpty()) "${bundle.questions.size} items" else "None yet",
                    icon = Icons.Outlined.Description,
                    onClick = onOpenWorksheets,
                    modifier = Modifier.weight(1f)
                )
                PracticeCard(
                    title = "Flashcards",
                    subtitle = if (bundle.cards.isNotEmpty()) "${bundle.cards.size} cards" else "None yet",
                    icon = Icons.Outlined.Style,
                    onClick = onOpenFlashcards,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Button(
                onClick = onMarkComplete,
                enabled = !done,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = VachakColors.PrimaryDark, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Outlined.CheckCircle, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (done) "Completed ✓" else "Mark Chapter Complete")
            }
        }

        if (bundle.notices.isNotEmpty()) {
            item {
                Surface(shape = RoundedCornerShape(16.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth(), onClick = onToggleNotices) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.Info, contentDescription = null, tint = VachakColors.TextSecondary, modifier = Modifier.size(16.dp))
                            Text(
                                if (showNotices) "Hide details" else "Why is something missing?",
                                style = MaterialTheme.typography.labelMedium, color = VachakColors.TextSecondary, fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (showNotices) {
                            bundle.notices.forEach { n ->
                                Text("• $n", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PracticeCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = modifier.cardShadow(RoundedCornerShape(20.dp)),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = null, tint = VachakColors.DeepLavender, modifier = Modifier.size(24.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
        }
    }
}
