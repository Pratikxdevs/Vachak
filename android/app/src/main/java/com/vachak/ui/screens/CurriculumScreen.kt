package com.vachak.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vachak.engine.EngineProvider
import com.vachak.engine.EngineResult
import com.vachak.engine.Lesson
import com.vachak.engine.LessonFilter
import com.vachak.ui.components.*
import com.vachak.ui.navigation.PackGrade
import com.vachak.ui.navigation.loadPackSummary
import com.vachak.ui.theme.VachakColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface CurriculumUiState {
    data object Loading : CurriculumUiState
    data class Ready(val lessons: List<Lesson>) : CurriculumUiState
    data object Empty : CurriculumUiState
    data class Error(val msg: String) : CurriculumUiState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurriculumScreen(
    engine: EngineProvider,
    onOpenLesson: (Lesson) -> Unit,
    onOpenGrade: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var uiState by remember { mutableStateOf<CurriculumUiState>(CurriculumUiState.Loading) }
    var query by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var gradeFilter by remember { mutableStateOf<Int?>(null) }
    var showGradeSheet by remember { mutableStateOf(false) }
    var expandedGrades by remember { mutableStateOf(false) }
    var expandedRecents by remember { mutableStateOf(false) }
    var expandedDecks by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var isSearchExpanded by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableStateOf(0) }

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
    // Real recently-viewed order + completion set, refreshed on resume so the
    // section never shows out-of-date data after opening a lesson.
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var recentIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var doneIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                reloadTick++
                recentIds = com.vachak.ui.prefs.VachakPrefs(ctx).recentlyViewed()
                doneIds = com.vachak.ui.prefs.VachakPrefs(ctx).completedLessons()
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
            recentIds = com.vachak.ui.prefs.VachakPrefs(ctx).recentlyViewed()
            doneIds = com.vachak.ui.prefs.VachakPrefs(ctx).completedLessons()
        }
    }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { packGrades = loadPackSummary(ctx) }
    }
    val isTablet = remember(configuration.screenWidthDp) { configuration.screenWidthDp >= 840 }
    val hPad = if (isTablet) 32.dp else 24.dp
    Box(modifier = modifier.fillMaxSize().background(VachakColors.Background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = hPad, end = hPad, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (isTablet) 20.dp else 14.dp)
        ) {
            // Header (cirriculum.md §2)
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Curriculum", style = MaterialTheme.typography.headlineLarge, color = VachakColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text("Explore lessons and build your class curriculum.", style = MaterialTheme.typography.bodyMedium, color = VachakColors.TextSecondary, fontSize = 14.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.size(44.dp)) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                IconButton(onClick = { isSearchExpanded = !isSearchExpanded }, modifier = Modifier.size(44.dp)) {
                                    Icon(Icons.Outlined.Search, null, tint = VachakColors.TextPrimary, modifier = Modifier.size(20.dp))
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
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VachakColors.Lavender300,
                            unfocusedBorderColor = VachakColors.Border,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        ),
                        singleLine = true
                    )
                }
            }

            // Category Filters (cirriculum.md §5)
            item {
                val filters = remember { listOf("All", "Literacy", "Numeracy") }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 8.dp)) {
                    item(key = "filter-icon") {
                        FilterPill(
                            label = gradeFilter?.let { "Grade $it" } ?: "Filter",
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

            // Continue Learning (same language as Home, cirriculum.md §7)
            when (uiState) {
                is CurriculumUiState.Loading -> {
                    item {
                        Surface(shape = RoundedCornerShape(28.dp), color = VachakColors.SoftLavender, modifier = Modifier.fillMaxWidth().height(220.dp)) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                CircularProgressIndicator(color = VachakColors.Lavender600, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }
                is CurriculumUiState.Error -> {
                    item {
                        val msg = (uiState as CurriculumUiState.Error).msg
                        Surface(shape = RoundedCornerShape(28.dp), color = VachakColors.SoftLavender, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Couldn't load curriculum", style = MaterialTheme.typography.titleMedium, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                                Text(msg, style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                                OutlinedButton(onClick = { scope.launch { load() } }, shape = RoundedCornerShape(50)) { Text("Try Again") }
                            }
                        }
                    }
                }
                is CurriculumUiState.Empty -> {
                    item {
                        Surface(shape = RoundedCornerShape(28.dp), color = VachakColors.SoftLavender, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Nothing here yet", style = MaterialTheme.typography.titleMedium, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                                Text("Try another subject or grade.", style = MaterialTheme.typography.bodyMedium, color = VachakColors.TextSecondary)
                                OutlinedButton(onClick = { selectedFilter = "All"; gradeFilter = null; query = "" }, shape = RoundedCornerShape(50)) { Text("Clear Filters") }
                            }
                        }
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

            // Browse by Grade (cirriculum.md §11)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeSectionHeader(
                        title = "Browse by Grade",
                        actionLabel = if (expandedGrades) "Show Less" else "View All",
                        onAction = { expandedGrades = !expandedGrades }
                    )
                    val grades = (1..5).toList()
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 8.dp)) {
                        items(grades, key = { "grade-$it" }) { g ->
                            GradeCard(grade = g, lessonCount = gradeCounts[g] ?: 0, onClick = { onOpenGrade(g) })
                        }
                    }
                    if (expandedGrades) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            filteredLessons.forEach { lesson ->
                                RecentLessonRow(
                                    title = lesson.title,
                                    subtitle = "Grade ${lesson.grade} • " + LessonFilter.domainLabel(lesson),
                                    completed = doneIds.contains(lesson.id),
                                    onClick = { onOpenLesson(lesson) }
                                )
                            }
                        }
                    }
                }
            }

            // Flashcards — real installed-pack decks per grade (never mock data).
            item {
                val decks = packGrades?.filter { it.flashcards > 0 }
                    ?.let { list -> gradeFilter?.let { g -> list.filter { it.grade == g } } ?: list }
                    .orEmpty()
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Flashcards", style = MaterialTheme.typography.titleMedium, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { expandedDecks = !expandedDecks }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text(if (expandedDecks) "Show Less" else "View All", style = MaterialTheme.typography.labelMedium, color = VachakColors.Lavender600)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Outlined.ChevronRight, null, tint = VachakColors.Lavender600, modifier = Modifier.size(16.dp))
                        }
                    }
                    val olDigits = listOf("᱐", "᱑", "᱒", "᱓", "᱔", "᱕")
                    when {
                        packGrades == null -> {
                            Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth().height(120.dp)) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = VachakColors.Lavender600) }
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

            // Recently Viewed (cirriculum.md §20)
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
                            Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth()) {
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
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = VachakColors.Lavender600) }
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
                    Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No lessons found.", style = MaterialTheme.typography.titleSmall, color = VachakColors.TextPrimary)
                            Text("Try another subject or grade.", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                            OutlinedButton(onClick = { selectedFilter = "All"; gradeFilter = null; query = "" }, shape = RoundedCornerShape(50)) { Text("Clear Filters") }
                        }
                    }
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
