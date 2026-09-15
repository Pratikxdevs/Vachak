package com.vachak.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vachak.engine.EngineProvider
import com.vachak.engine.EngineResult
import com.vachak.engine.Lesson
import com.vachak.engine.LessonFilter
import com.vachak.ui.components.ContinueLearningCard
import com.vachak.ui.components.FilterPill
import com.vachak.ui.components.FlashcardDeckCard
import com.vachak.ui.components.GradeCard
import com.vachak.ui.components.GradeFilterSheet
import com.vachak.ui.components.GuidedEmpty
import com.vachak.ui.components.HomeSectionHeader
import com.vachak.ui.components.RecentLessonRow
import com.vachak.ui.components.SectionCard
import com.vachak.ui.components.StatusRow
import com.vachak.ui.components.SteppedLoading
import com.vachak.ui.navigation.PackGrade
import com.vachak.ui.navigation.loadPackSummary
import com.vachak.ui.theme.VachakColors
import com.vachak.ui.theme.cardShadow
import com.vachak.ui.theme.tabletHPad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface CurriculumUiState {
    data object Loading : CurriculumUiState
    data class Ready(val lessons: List<Lesson>) : CurriculumUiState
    data object Empty : CurriculumUiState
    data class Error(val msg: String) : CurriculumUiState
}

/**
 * Unified Learn page — the single home for lessons, worksheets, decks and
 * saved items. The old Library tab is gone: its panes are sub-pages
 * (learn/worksheets, learn/flashcards, learn/saved) and its hub content
 * (recent PDFs, deck totals, saved counts) lives in the sections below.
 * Chapter-scoped study pages (learn/grade/…) are untouched.
 */
@Composable
fun CurriculumScreen(
    engine: EngineProvider,
    onOpenLesson: (Lesson) -> Unit,
    onOpenGrade: (Int) -> Unit,
    onOpenGrades: () -> Unit = {},
    onOpenWorksheets: () -> Unit = {},
    onOpenFlashcards: () -> Unit = {},
    onOpenSaved: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var uiState by remember { mutableStateOf<CurriculumUiState>(CurriculumUiState.Loading) }
    var query by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var gradeFilter by remember { mutableStateOf<Int?>(null) }
    var showGradeSheet by remember { mutableStateOf(false) }
    var expandedRecents by remember { mutableStateOf(false) }
    var expandedDecks by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var isSearchExpanded by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableStateOf(0) }
    var pdfNotice by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        withContext(Dispatchers.IO) {
            val ce = engine.curriculum as? com.vachak.content.ContentEngine
            val res = ce?.getLessons() ?: engine.curriculum.listLessons(0).let { r ->
                when (r) {
                    is EngineResult.Ok -> EngineResult.Ok(r.value.map { ref -> Lesson(ref.id, ref.title, ref.grade, "", "", true, ref.domain) })
                    is EngineResult.Err -> r as EngineResult<List<Lesson>>
                }
            }
            withContext(Dispatchers.Main) {
                uiState = when (res) {
                    is EngineResult.Ok -> {
                        if (res.value.isEmpty()) CurriculumUiState.Empty
                        else CurriculumUiState.Ready(res.value)
                    }
                    is EngineResult.Err -> CurriculumUiState.Error(res.message)
                }
            }
        }
    }

    LaunchedEffect(reloadTick) { load() }

    val lessons = when (val s = uiState) {
        is CurriculumUiState.Ready -> s.lessons
        else -> emptyList()
    }

    val filteredLessons by remember(lessons, query, selectedFilter, gradeFilter) {
        derivedStateOf {
            var lst = lessons
            if (selectedFilter != "All") {
                lst = lst.filter { l -> LessonFilter.matches(l, selectedFilter) }
            }
            gradeFilter?.let { g -> lst = lst.filter { it.grade == g } }
            if (query.isNotBlank()) lst = lst.filter { it.title.contains(query, true) || it.id.contains(query, true) }
            lst
        }
    }

    val gradeCounts by remember(lessons) {
        derivedStateOf { (1..5).associateWith { g -> lessons.count { it.grade == g } } }
    }

    // Grade content packs — null until asset loads.
    var packGrades by remember { mutableStateOf<List<PackGrade>?>(null) }
    // Generated worksheet PDFs on disk — the Worksheets section source.
    var pdfs by remember { mutableStateOf<List<java.io.File>>(emptyList()) }
    // Real recently-viewed order + completion set, refreshed on resume so the
    // section never shows out-of-date data after opening a lesson.
    val ctx = LocalContext.current
    var recentIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var doneIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                reloadTick++
                val prefs = com.vachak.ui.prefs.VachakPrefs(ctx)
                recentIds = prefs.recentlyViewed()
                doneIds = prefs.completedLessons()
                pdfs = com.vachak.ui.pdf.WorksheetPdf.list(ctx)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // Avoid nested BoxWithConstraints (double-measure cost at 90/120Hz)
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    // Seed once lessons arrive (remember(lessons) re-seeds on data load, while
    // ON_RESUME keeps it fresh afterwards).
    LaunchedEffect(lessons) {
        if (lessons.isNotEmpty() && recentIds.isEmpty()) {
            val prefs = com.vachak.ui.prefs.VachakPrefs(ctx)
            recentIds = prefs.recentlyViewed()
            doneIds = prefs.completedLessons()
        }
    }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            packGrades = loadPackSummary(ctx)
            pdfs = com.vachak.ui.pdf.WorksheetPdf.list(ctx)
        }
    }
    val isTablet = remember(configuration.screenWidthDp) { configuration.screenWidthDp >= 840 }
    val hPad = tabletHPad(24.dp)
    // Saved voice notes live in the process store (same source SavedPane reads).
    val voiceCount = LiveConversationStore.items.size
    Box(modifier = modifier.fillMaxSize().background(VachakColors.Background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = hPad, end = hPad, top = 6.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (isTablet) 20.dp else 14.dp)
        ) {
            // Header
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Learn", style = MaterialTheme.typography.headlineLarge, color = VachakColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Lessons, worksheets, decks and saved items — all offline.", style = MaterialTheme.typography.bodyMedium, color = VachakColors.TextSecondary, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.size(44.dp)) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                IconButton(onClick = { isSearchExpanded = !isSearchExpanded; if (!isSearchExpanded) query = "" }, modifier = Modifier.size(44.dp)) {
                                    Icon(if (isSearchExpanded) Icons.Outlined.Close else Icons.Outlined.Search, null, tint = VachakColors.TextPrimary, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                        Surface(shape = RoundedCornerShape(50), color = VachakColors.SuccessLight, modifier = Modifier.height(32.dp)) {
                            Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(VachakColors.Success))
                                Text("Offline", style = MaterialTheme.typography.labelSmall, color = VachakColors.Success, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                if (isSearchExpanded) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search lessons…", color = VachakColors.TextSecondary) },
                        leadingIcon = { Icon(Icons.Outlined.Search, null, tint = VachakColors.TextSecondary) },
                        trailingIcon = {
                            if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, null) }
                        },
                        modifier = Modifier.fillMaxWidth().cardShadow(RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VachakColors.Lavender300,
                            unfocusedBorderColor = VachakColors.Border,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        ),
                        singleLine = true
                    )
                    if (query.isNotBlank()) {
                        Text(
                            "${filteredLessons.size} of ${lessons.size} lessons",
                            style = MaterialTheme.typography.bodySmall,
                            color = VachakColors.TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // Category Filters
            item {
                val filters = remember { listOf("All", "Literacy", "Numeracy") }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 8.dp)) {
                    item(key = "filter-icon") {
                        FilterPill(
                            label = gradeFilter?.let { "Grade $it" } ?: "Grade",
                            selected = gradeFilter != null,
                            onClick = { showGradeSheet = true },
                            leadingIcon = Icons.Outlined.Tune
                        )
                    }
                    items(filters, key = { it }) { label ->
                        FilterPill(label = label, selected = selectedFilter == label, onClick = { selectedFilter = label })
                    }
                }
            }

            // Continue Learning
            when (uiState) {
                is CurriculumUiState.Loading -> {
                    item {
                        SteppedLoading(
                            steps = listOf("Opening the library…", "Finding your place…"),
                            currentStep = 0,
                            fraction = null,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                is CurriculumUiState.Error -> {
                    item {
                        val msg = (uiState as CurriculumUiState.Error).msg
                        SectionCard(
                            title = "Couldn't load Learn",
                            subtitle = "Lessons, packs and PDFs are on disk — the read just failed.",
                            actionLabel = "Try Again",
                            onAction = { scope.launch { load() } },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            StatusRow(dot = VachakColors.ErrorRed, title = "Library query failed", detail = msg.take(120))
                        }
                    }
                }
                is CurriculumUiState.Empty -> {
                    item {
                        GuidedEmpty(
                            icon = Icons.Outlined.MenuBook,
                            title = "No lessons on this tablet yet",
                            why = "Lessons arrive in an offline content pack — install one from More → Packs.",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                is CurriculumUiState.Ready -> {
                    // Continue = first incomplete lesson under current filters (honest resume).
                    val candidates = filteredLessons.ifEmpty { lessons }
                    val focus = candidates.firstOrNull { !doneIds.contains(it.id) }
                        ?: candidates.firstOrNull()
                        ?: lessons.firstOrNull()
                    if (focus != null) {
                        item {
                            val doneCount = lessons.count { doneIds.contains(it.id) }
                            ContinueLearningCard(
                                title = focus.title.ifBlank { "Letters & Sounds" },
                                gradeLabel = "Grade ${focus.grade} • " + LessonFilter.domainLabel(focus),
                                description = focus.sourceTextHi.ifBlank { "Learn the first sounds and their corresponding Ol Chiki forms." }.take(90),
                                progressLabel = "Progress",
                                progress = doneCount.toFloat() / lessons.size.coerceAtLeast(1).toFloat(),
                                countText = "$doneCount/${lessons.size}",
                                onContinue = { onOpenLesson(focus) }
                            )
                        }
                    }
                }
            }

            // Browse by Grade — cards inline, full list lives on its own page.
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeSectionHeader(
                        title = "Browse by Grade",
                        actionLabel = "View All",
                        onAction = onOpenGrades
                    )
                    val grades = (1..5).toList()
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 8.dp)) {
                        items(grades, key = { "grade-$it" }) { g ->
                            GradeCard(grade = g, lessonCount = gradeCounts[g] ?: 0, onClick = { onOpenGrade(g) })
                        }
                    }
                }
            }

            // Worksheets — generated PDFs on disk (moved in from the old Library hub).
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeSectionHeader(
                        title = "Worksheets",
                        count = pdfs.size,
                        actionLabel = "New",
                        onAction = onOpenWorksheets
                    )
                    if (pdfs.isEmpty()) {
                        Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth().cardShadow()) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    "No worksheets yet — generate one from any lesson.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VachakColors.TextSecondary,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = onOpenWorksheets,
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.buttonColors(containerColor = VachakColors.PrimaryDark, contentColor = Color.White),
                                    modifier = Modifier.height(40.dp)
                                ) { Text("New") }
                            }
                        }
                    } else {
                        val dateFmt = remember { java.text.SimpleDateFormat("d MMM, h:mm a", java.util.Locale.getDefault()) }
                        Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth().cardShadow()) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                pdfs.sortedByDescending { it.lastModified() }.take(3).forEachIndexed { idx, f ->
                                    WorksheetFileRow(
                                        title = f.nameWithoutExtension,
                                        subtitle = "${f.length() / 1024} KB • PDF • ${dateFmt.format(java.util.Date(f.lastModified()))}",
                                        onClick = {
                                            try {
                                                ctx.startActivity(com.vachak.ui.pdf.WorksheetPdf.viewIntent(ctx, f))
                                            } catch (_: Exception) {
                                                pdfNotice = "No PDF viewer installed — file kept offline"
                                            }
                                        }
                                    )
                                    if (idx < 2 && idx < pdfs.size - 1) HorizontalDivider(color = VachakColors.Border.copy(alpha = 0.6f), thickness = 0.8.dp)
                                }
                            }
                        }
                    }
                    pdfNotice?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Flashcards — real installed-pack decks per grade (never mock data).
            item {
                val decks = packGrades?.filter { it.flashcards > 0 }
                    ?.let { list -> gradeFilter?.let { g -> list.filter { it.grade == g } } ?: list }
                    .orEmpty()
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeSectionHeader(
                        title = "Flashcards",
                        count = decks.sumOf { it.flashcards },
                        actionLabel = "Practice",
                        onAction = onOpenFlashcards
                    )
                    val olDigits = listOf("᱐", "᱑", "᱒", "᱓", "᱔", "᱕")
                    when {
                        packGrades == null -> {
                            Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth().height(120.dp)) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = VachakColors.Lavender600)
                                        Text("Loading decks…", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                                    }
                                }
                            }
                        }
                        decks.isEmpty() -> {
                            Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth()) {
                                Text("No flashcard decks yet — install a content pack.", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary, modifier = Modifier.padding(16.dp))
                            }
                        }
                        !expandedDecks -> {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 8.dp)) {
                                items(decks, key = { it.grade }) { pg ->
                                    FlashcardDeckCard(
                                        title = "Grade ${pg.grade}",
                                        subtitle = "${pg.chapters} decks",
                                        count = "${pg.flashcards} cards",
                                        glyph = olDigits.getOrElse(pg.grade) { "ᱚ" },
                                        onClick = { onOpenGrade(pg.grade) }
                                    )
                                }
                            }
                        }
                        else -> {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                decks.forEach { pg ->
                                    FlashcardDeckCard(
                                        title = "Grade ${pg.grade}",
                                        subtitle = "${pg.chapters} decks",
                                        count = "${pg.flashcards} cards",
                                        glyph = olDigits.getOrElse(pg.grade) { "ᱚ" },
                                        onClick = { onOpenGrade(pg.grade) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Saved Items — counts with a door into the Saved sub-page.
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeSectionHeader(title = "Saved Items", actionLabel = "Open", onAction = onOpenSaved)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border),
                        modifier = Modifier.fillMaxWidth().cardShadow(),
                        onClick = onOpenSaved
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            SavedCount(
                                icon = Icons.Outlined.PictureAsPdf,
                                count = pdfs.size,
                                label = "PDFs",
                                modifier = Modifier.weight(1f)
                            )
                            Box(modifier = Modifier.width(1.dp).height(40.dp).background(VachakColors.Border.copy(alpha = 0.6f)))
                            SavedCount(
                                icon = Icons.Outlined.Mic,
                                count = voiceCount,
                                label = "Voice notes",
                                modifier = Modifier.weight(1f)
                            )
                            Box(modifier = Modifier.width(1.dp).height(40.dp).background(VachakColors.Border.copy(alpha = 0.6f)))
                            SavedCount(
                                icon = Icons.Outlined.Folder,
                                count = (packGrades?.sumOf { it.flashcards } ?: 0),
                                label = "Cards",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Recently Viewed
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeSectionHeader(
                        title = "Recently Viewed",
                        actionLabel = if (expandedRecents) "Show Less" else "View All",
                        onAction = { expandedRecents = !expandedRecents }
                    )
                    when (uiState) {
                        is CurriculumUiState.Ready -> {
                            // Real recents: tracked lesson opens, filtered like everything else.
                            val tracked = recentIds.mapNotNull { id -> lessons.firstOrNull { it.id == id } }
                            val scoped = if (selectedFilter != "All" || gradeFilter != null || query.isNotBlank()) {
                                var lst = tracked
                                if (selectedFilter != "All") lst = lst.filter { l -> LessonFilter.matches(l, selectedFilter) }
                                gradeFilter?.let { g -> lst = lst.filter { it.grade == g } }
                                if (query.isNotBlank()) lst = lst.filter { it.title.contains(query, true) || it.id.contains(query, true) }
                                lst
                            } else tracked
                            val recents = (if (expandedRecents) scoped else scoped.take(3))
                            Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth().cardShadow()) {
                                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    if (recents.isEmpty()) {
                                        Text(
                                            if (recentIds.isEmpty()) "Lessons you open will appear here."
                                            else "No recently viewed lessons match these filters.",
                                            style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary, modifier = Modifier.padding(vertical = 12.dp)
                                        )
                                    } else {
                                        recents.forEachIndexed { idx, lesson ->
                                            RecentLessonRow(
                                                title = lesson.title,
                                                subtitle = "Grade ${lesson.grade} • " + LessonFilter.domainLabel(lesson),
                                                completed = doneIds.contains(lesson.id),
                                                onClick = { onOpenLesson(lesson) }
                                            )
                                            if (idx < recents.lastIndex) HorizontalDivider(color = VachakColors.Border.copy(alpha = 0.6f), thickness = 0.8.dp)
                                        }
                                    }
                                }
                            }
                        }
                        is CurriculumUiState.Loading -> {
                            Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth().height(120.dp)) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = VachakColors.Lavender600)
                                        Text("Loading recents…", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                                    }
                                }
                            }
                        }
                        else -> {
                            Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth()) {
                                Box(modifier = Modifier.padding(16.dp)) { Text("Lessons will appear here once available.", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary) }
                            }
                        }
                    }
                }
            }

            // Empty search result
            if (uiState is CurriculumUiState.Ready && filteredLessons.isEmpty() && (query.isNotBlank() || selectedFilter != "All")) {
                item {
                    GuidedEmpty(
                        icon = Icons.Outlined.Search,
                        title = "No lessons match",
                        why = if (query.isNotBlank()) "Nothing titled like “$query” under these filters — try a shorter word." else "Nothing under these filters — widen them to browse everything.",
                        actionLabel = "Clear filters",
                        onAction = { selectedFilter = "All"; gradeFilter = null; query = "" },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        if (showGradeSheet) {
            GradeFilterSheet(
                selected = gradeFilter,
                onSelect = { gradeFilter = it; showGradeSheet = false },
                onDismiss = { showGradeSheet = false }
            )
        }
    }
}

/** Single generated-PDF row: title + tabular size/date, chevron affordance. */
@Composable
private fun WorksheetFileRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(shape = RoundedCornerShape(10.dp), color = VachakColors.Lavender100, modifier = Modifier.size(40.dp)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(Icons.Outlined.Description, null, tint = VachakColors.DeepLavender, modifier = Modifier.size(18.dp))
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = VachakColors.TextPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Surface(shape = CircleShape, color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.size(32.dp)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(Icons.Outlined.PictureAsPdf, null, tint = VachakColors.TextSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Saved-section count cell: icon + tabular number + label. */
@Composable
private fun SavedCount(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, tint = VachakColors.DeepLavender, modifier = Modifier.size(20.dp))
        Text("$count", style = MaterialTheme.typography.titleLarge, color = VachakColors.TextPrimary, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text(label, style = MaterialTheme.typography.labelSmall, color = VachakColors.TextSecondary)
    }
}
