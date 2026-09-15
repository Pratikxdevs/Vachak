package com.vachak.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vachak.engine.EngineProvider
import com.vachak.engine.Lesson
import com.vachak.ui.components.BreadcrumbTrail
import com.vachak.ui.components.HomeSectionHeader
import com.vachak.ui.content.PackContentReader
import com.vachak.ui.navigation.PackGrade
import com.vachak.ui.navigation.loadPackSummary
import com.vachak.ui.theme.VachakColors
import com.vachak.ui.theme.cardShadow
import com.vachak.ui.theme.tabletHPad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Grade page — one page per class (grades 1–5).
 * ONE unified chapter list. Each row carries a language toggle at the
 * extreme right that cycles English → Hindi → Santali for that page.
 * Titles come from real pack data only; a missing Hindi/Santali line falls
 * back to the pack title with a small note — never invented, never blank.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradeScreen(
    engine: EngineProvider,
    grade: Int,
    onOpenChapter: (slug: String) -> Unit,
    onOpenLesson: (Lesson) -> Unit,
    onOpenPacks: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val prefs = remember(ctx) { com.vachak.ui.prefs.VachakPrefs(ctx) }
    var pack by remember(grade) { mutableStateOf<PackGrade?>(null) }
    var packMissing by remember(grade) { mutableStateOf(false) }
    var lang by remember(grade) { mutableStateOf(prefs.chapterTitleLang) }
    // Done-chapter keys, refreshed on resume so returning from a chapter
    // updates chips immediately.
    var doneSlugs by remember(grade) { mutableStateOf(prefs.completedChapters()) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                doneSlugs = prefs.completedChapters()
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    // (Santali Deva line, Hindi line) per chapter slug — real source text.
    var titleLines by remember(grade) { mutableStateOf<Map<String, Pair<String?, String?>>>(emptyMap()) }

    LaunchedEffect(grade) {
        val found = withContext(Dispatchers.IO) {
            loadPackSummary(ctx)?.firstOrNull { it.grade == grade }
        }
        if (found != null) {
            pack = found
            // One background pass for every chapter's title lines.
            val lines = withContext(Dispatchers.IO) {
                runCatching {
                    PackContentReader.readGradeTitleLines(ctx, grade, found.chapterTitles.map { it.slug })
                }.getOrDefault(emptyMap())
            }
            titleLines = lines
        } else {
            packMissing = true
        }
    }

    fun cycleLang() {
        val next = (lang + 1) % 3
        lang = next
        prefs.chapterTitleLang = next
    }

    Column(modifier = modifier.fillMaxSize().background(VachakColors.Background)) {
        Surface(color = Color.White, shadowElevation = 0.dp, tonalElevation = 0.dp) {
            Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = VachakColors.TextPrimary) }
                Column(modifier = Modifier.weight(1f)) {
                    BreadcrumbTrail(listOf("Learn", "Grade $grade"))
                    Text("Grade $grade", style = MaterialTheme.typography.titleLarge, color = VachakColors.TextPrimary, fontWeight = FontWeight.Bold, maxLines = 1)
                    val n = pack?.chapterTitles.orEmpty().distinctBy { it.slug }.size
                    Text(
                        if (pack != null) "$n chapters" else "Loading…",
                        style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary, maxLines = 1
                    )
                }
            }
        }

        val chapters = pack?.chapterTitles.orEmpty().distinctBy { it.slug }
        when {
            chapters.isNotEmpty() -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = tabletHPad(), vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    HomeSectionHeader(title = "Chapters (${chapters.size})", actionLabel = null, onAction = null)
                }
                items(chapters.withIndex().toList(), key = { (_, ch) -> ch.slug }) { (idx, ch) ->
                    ChapterRow(
                        number = (idx + 1).toString().padStart(2, '0'),
                        packTitle = ch.title,
                        subject = ch.subject,
                        worksheets = ch.worksheets,
                        flashcards = ch.flashcards,
                        satLine = titleLines[ch.slug]?.first,
                        hiLine = titleLines[ch.slug]?.second,
                        lang = lang,
                        completed = doneSlugs.contains("$grade/${ch.slug}"),
                        onCycleLang = ::cycleLang,
                        onOpen = { onOpenChapter(ch.slug) }
                    )
                }
            }
            packMissing -> Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("No chapters for Grade $grade yet", style = MaterialTheme.typography.titleSmall, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text("Install the content pack to unlock chapters.", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                    Button(onClick = onOpenPacks, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = VachakColors.PrimaryDark, contentColor = Color.White)) {
                        Text("Open Packs")
                    }
                }
            }
            else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VachakColors.Lavender600, modifier = Modifier.size(32.dp))
            }
        }
    }
}

/** All grades on one page: full-width rows with real counts. Reached from
 *  Learn → Browse by Grade → View All. Self-loading so the route needs no
 *  params beyond callbacks. */
@Composable
fun GradeListScreen(
    engine: EngineProvider,
    onOpenGrade: (Int) -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    var lessons by remember { mutableStateOf<List<Lesson>>(emptyList()) }
    var doneIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var chapters by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val ce = engine.curriculum as? com.vachak.content.ContentEngine
            val res = ce?.getLessons()
            val packs = loadPackSummary(ctx)
            val prefs = com.vachak.ui.prefs.VachakPrefs(ctx)
            withContext(Dispatchers.Main) {
                lessons = (res as? com.vachak.engine.EngineResult.Ok)?.value.orEmpty()
                doneIds = prefs.completedLessons()
                chapters = packs.orEmpty().associate { it.grade to it.chapterTitles.size }
                loading = false
            }
        }
    }
    Column(modifier = modifier.fillMaxSize().background(VachakColors.Background)) {
        Surface(color = Color.White, shadowElevation = 0.dp, tonalElevation = 0.dp) {
            Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = VachakColors.TextPrimary) }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BreadcrumbTrail(listOf("Learn", "Grades"))
                    Text("All Grades", style = MaterialTheme.typography.titleLarge, color = VachakColors.TextPrimary, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("Every class in one list", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary, maxLines = 1)
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (loading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = VachakColors.Lavender600)
                            Text("Loading grades…", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                        }
                    }
                }
            }
            items((1..5).toList(), key = { "grade-row-$it" }) { g ->
                val total = lessons.count { it.grade == g }
                val done = lessons.count { it.grade == g && doneIds.contains(it.id) }
                val ch = chapters[g] ?: 0
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border),
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth().cardShadow(RoundedCornerShape(20.dp)),
                    onClick = { onOpenGrade(g) }
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Surface(shape = RoundedCornerShape(12.dp), color = VachakColors.Lavender100, modifier = Modifier.size(48.dp)) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text("$g", style = MaterialTheme.typography.titleLarge, color = VachakColors.DeepLavender, fontWeight = FontWeight.Bold)
                            }
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Grade $g", style = MaterialTheme.typography.titleSmall, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text(
                                "$total lessons • $ch chapters • $done done",
                                style = MaterialTheme.typography.bodySmall,
                                color = VachakColors.TextSecondary,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Surface(shape = CircleShape, color = VachakColors.Lavender100, modifier = Modifier.size(36.dp)) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(Icons.Outlined.ChevronRight, null, tint = VachakColors.DeepLavender, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One chapter row. lang: 0 = English, 1 = Hindi, 2 = Santali. */
@Composable
private fun ChapterRow(
    number: String,
    packTitle: String,
    subject: String,
    worksheets: Int,
    flashcards: Int,
    satLine: String?,
    hiLine: String?,
    lang: Int,
    completed: Boolean,
    onCycleLang: () -> Unit,
    onOpen: () -> Unit
) {
    val langTag = when (lang) { 1 -> "HI"; 2 -> "SAT"; else -> "EN" }
    val (title, fallbackNote) = when (lang) {
        1 -> if (!hiLine.isNullOrBlank()) hiLine to null else packTitle to "Hindi text pending review"
        2 -> if (!satLine.isNullOrBlank()) satLine to null else packTitle to "Santali text pending review"
        else -> packTitle to null
    }
    Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(number, style = MaterialTheme.typography.titleMedium, color = VachakColors.DeepLavender, fontWeight = FontWeight.Bold)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(
                    "$subject • $worksheets worksheets • $flashcards flashcards",
                    style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (fallbackNote != null) {
                    Text(fallbackNote, style = MaterialTheme.typography.labelSmall, color = VachakColors.Amber, maxLines = 1)
                }
                if (completed) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = VachakColors.Success, modifier = Modifier.size(12.dp))
                        Text("Completed", style = MaterialTheme.typography.labelSmall, color = VachakColors.Success, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            // Language toggle — extreme right, same row as number + title.
            TextButton(
                onClick = onCycleLang,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(Icons.Outlined.Translate, contentDescription = "Switch title language (now $langTag)", tint = VachakColors.DeepLavender, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                Text(langTag, style = MaterialTheme.typography.labelSmall, color = VachakColors.DeepLavender, fontWeight = FontWeight.Bold)
            }
        }
    }
}
