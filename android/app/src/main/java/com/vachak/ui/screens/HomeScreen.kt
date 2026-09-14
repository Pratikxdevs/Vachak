package com.vachak.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.vachak.engine.ActiveLanguage
import com.vachak.engine.EngineProvider
import com.vachak.engine.EngineResult
import com.vachak.engine.Lesson
import com.vachak.engine.LessonFilter
import com.vachak.ui.components.*
import com.vachak.ui.theme.VachakColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Ready(
        val focus: Lesson,
        val secondary: Lesson?,
        val recents: List<Lesson>,
        val totalLessons: Int,
        val completedCount: Int
    ) : HomeUiState
    data object Empty : HomeUiState
    data class Error(val msg: String) : HomeUiState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    engine: EngineProvider,
    onNavigateLive: () -> Unit,
    onNavigateTools: () -> Unit,
    onNavigateCurriculum: () -> Unit,
    onContinueLesson: (Lesson) -> Unit,
    modifier: Modifier = Modifier
) {
    var uiState by remember { mutableStateOf<HomeUiState>(HomeUiState.Loading) }
    var selectedFilter by remember { mutableStateOf("All") }
    var gradeFilter by remember { mutableStateOf<Int?>(null) }
    var showGradeSheet by remember { mutableStateOf(false) }
    var expandedRecents by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var showReminders by remember { mutableStateOf(false) }
    var allLessons by remember { mutableStateOf<List<Lesson>>(emptyList()) }
    // Real recently-viewed order + completion set, refreshed every time Home
    // regains focus (returning from a lesson updates both immediately).
    var recentIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var doneIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var reloadTick by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(context) { com.vachak.ui.prefs.VachakPrefs(context) }
    val activeLang by engine.activeLanguage.collectAsState()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) reloadTick++
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    suspend fun load() = withContext(Dispatchers.IO) {
        val ce = engine.curriculum as? com.vachak.content.ContentEngine
        val res = ce?.getLessons() ?: engine.curriculum.listLessons(0).let { r ->
            when (r) {
                is EngineResult.Ok -> EngineResult.Ok(r.value.map { ref -> Lesson(ref.id, ref.title, ref.grade, "", "", true, ref.domain) })
                is EngineResult.Err -> r as EngineResult<List<Lesson>>
            }
        }
        // Prefs reads are cheap; do them off-main alongside the query.
        val recents = prefs.recentlyViewed()
        val done = prefs.completedLessons()
        withContext(Dispatchers.Main) {
            recentIds = recents
            doneIds = done
            when (res) {
                is EngineResult.Ok -> {
                    val list = res.value
                    allLessons = list
                    if (list.isEmpty()) uiState = HomeUiState.Empty
                    else {
                        val focus = list.first()
                        val secondary = list.getOrNull(1)
                        // Real recents first; fall back to list order until used.
                        val tracked = recents.mapNotNull { id -> list.firstOrNull { it.id == id } }
                        val recents = tracked.ifEmpty { list.drop(1).take(3).ifEmpty { list.take(3) } }
                        uiState = HomeUiState.Ready(focus, secondary, recents, list.size, list.count { done.contains(it.id) })
                    }
                }
                is EngineResult.Err -> {
                    allLessons = emptyList()
                    uiState = HomeUiState.Error(res.message)
                }
            }
        }
    }

    LaunchedEffect(reloadTick) { load() }

    // filter derived — computed only when filter or data changes, not on every recomposition
    val displayState by remember(allLessons, selectedFilter, gradeFilter, uiState) {
        derivedStateOf {
            when (val s = uiState) {
                is HomeUiState.Ready -> {
                    if (selectedFilter == "All" && gradeFilter == null) s
                    else {
                        var filtered = allLessons
                        if (selectedFilter != "All") filtered = filtered.filter { l -> LessonFilter.matches(l, selectedFilter) }
                        gradeFilter?.let { g -> filtered = filtered.filter { it.grade == g } }
                        if (filtered.isEmpty()) s else s.copy(focus = filtered.first(), secondary = filtered.getOrNull(1), recents = filtered.drop(1).take(3))
                    }
                }
                else -> null
            }
        }
    }

    // Use LocalConfiguration instead of nested BoxWithConstraints to avoid double-measure at 120Hz
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isTablet = remember(configuration.screenWidthDp) { configuration.screenWidthDp >= 840 }
    val hPad = if (isTablet) 32.dp else 20.dp
    val titleSize = if (isTablet) 40.sp else 30.sp
    Box(modifier = modifier.fillMaxSize().background(VachakColors.Background)) {
        // Decorative arcs are static — draw behind LazyColumn to avoid overdraw on scroll at 120Hz
        HomeDecorativeArcs(modifier = Modifier.fillMaxSize())
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = hPad, end = hPad, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (isTablet) 20.dp else 14.dp)
        ) {
            // Header
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(VachakColors.Lavender200),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Person, null, tint = VachakColors.DeepLavender, modifier = Modifier.size(20.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), shadowElevation = 0.dp, modifier = Modifier.size(44.dp)) {
                             Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                 IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) searchQuery = "" }, modifier = Modifier.size(44.dp)) {
                                     Icon(Icons.Outlined.Search, null, tint = VachakColors.TextPrimary, modifier = Modifier.size(20.dp))
                                 }
                             }
                         }
                         Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp)) {
                             Surface(shape = CircleShape, color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), shadowElevation = 0.dp, modifier = Modifier.size(44.dp)) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    IconButton(onClick = { showReminders = true }, modifier = Modifier.size(44.dp)) {
                                        Icon(Icons.Outlined.Notifications, null, tint = VachakColors.TextPrimary, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                            Box(modifier = Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp).size(10.dp).clip(CircleShape).background(VachakColors.Lavender500).padding(2.dp))
                        }
                    }
                }
            }
            if (searchOpen) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search lessons…", color = VachakColors.TextSecondary) },
                        leadingIcon = { Icon(Icons.Outlined.Search, null, tint = VachakColors.TextSecondary) },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Outlined.Close, null) }
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

            // Greeting
            item {
                val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
                val daypart = when (hour) { in 0..11 -> "morning"; in 12..16 -> "afternoon"; else -> "evening" }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Good $daypart,", style = MaterialTheme.typography.titleSmall, color = VachakColors.TextSecondary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text("${prefs.teacherName}", style = MaterialTheme.typography.headlineLarge, color = VachakColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = titleSize, lineHeight = titleSize, letterSpacing = (-0.5).sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text("Let's continue your learning journey.", style = MaterialTheme.typography.bodyMedium, color = VachakColors.TextSecondary, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }

            // Category filters
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

            // Continue Learning
            when (val state = uiState) {
                is HomeUiState.Loading -> {
                    item {
                        SteppedLoading(
                            steps = listOf("Opening your library…", "Finding where you left off…"),
                            currentStep = 0,
                            fraction = null,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                is HomeUiState.Empty -> {
                    item {
                        GuidedEmpty(
                            icon = Icons.Outlined.MenuBook,
                            title = "Ready to start learning?",
                            why = "Lessons live in Learn, grouped by grade — open it and pick your class to begin.",
                            actionLabel = "Explore Learn",
                            onAction = { onNavigateCurriculum() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                is HomeUiState.Error -> {
                    item {
                        SectionCard(
                            title = "Couldn't load lessons",
                            subtitle = "Your progress is safe — this is a storage read problem, not lost data.",
                            actionLabel = "Try Again",
                            onAction = { scope.launch { uiState = HomeUiState.Loading; load() } },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            StatusRow(
                                dot = VachakColors.ErrorRed,
                                title = "Library unreadable",
                                detail = state.msg.take(120)
                            )
                        }
                    }
                }
                is HomeUiState.Ready -> {
                    val ready = displayState ?: state
                    item {
                        ContinueLearningCard(
                            title = ready.focus.title.ifBlank { "Letters & Sounds" },
                            gradeLabel = "Grade ${ready.focus.grade} • " + LessonFilter.domainLabel(ready.focus),
                            description = ready.focus.sourceTextHi.ifBlank { "Learn the first sounds and their corresponding Ol Chiki forms." }.take(90),
                            progressLabel = "Progress  •  ${ready.completedCount} of ${ready.totalLessons} lessons",
                            progress = if (ready.totalLessons > 0) ready.completedCount.toFloat() / ready.totalLessons.coerceAtLeast(1) else 0.12f,
                            onContinue = { onContinueLesson(ready.focus) }
                        )
                    }
                    // Secondary lesson
                    ready.secondary?.let { sec ->
                        item {
                            SecondaryLessonRow(
                                title = sec.title,
                                subtitle = "Grade ${sec.grade} • " + LessonFilter.domainLabel(sec),
                                onClick = { onContinueLesson(sec) }
                            )
                        }
                    }
                }
            }

            // Quick Actions (always visible)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeSectionHeader(title = "Quick Actions", actionLabel = "View All", onAction = onNavigateTools)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        QuickActionCard(
                            title = "Live Translate",
                            subtitle = "Translate Hindi to ${ActiveLanguage.label(activeLang)} instantly",
                            icon = Icons.Outlined.Mic,
                            tint = VachakColors.Lavender600,
                            containerColor = VachakColors.SoftLavender,
                            onClick = onNavigateLive,
                            modifier = Modifier.weight(1f).heightIn(min = 160.dp)
                        )
                        QuickActionCard(
                            title = "Worksheets",
                            subtitle = "Generate practice worksheets",
                            icon = Icons.Outlined.Description,
                            tint = VachakColors.AccentDeep,
                            containerColor = VachakColors.AccentLight,
                            onClick = onNavigateTools,
                            modifier = Modifier.weight(1f).heightIn(min = 160.dp)
                        )
                    }
                }
            }

            // Recent Lessons
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HomeSectionHeader(
                        title = "Recent Lessons",
                        actionLabel = if (expandedRecents) "Show Less" else "View All",
                        onAction = { expandedRecents = !expandedRecents }
                    )
                    when (val s = uiState) {
                        is HomeUiState.Ready -> {
                            // Same Literacy/Numeracy + grade + search filters as
                            // Continue Learning — recents never show stale scope.
                            // Real tracked order first; collapsed shows 3.
                            val base = run {
                                val tracked = recentIds.mapNotNull { id -> allLessons.firstOrNull { it.id == id } }
                                (if (tracked.isEmpty()) allLessons else tracked)
                            }
                            var list = base
                            if (selectedFilter != "All") list = list.filter { l -> LessonFilter.matches(l, selectedFilter) }
                            gradeFilter?.let { g -> list = list.filter { it.grade == g } }
                            if (searchQuery.isNotBlank()) {
                                list = list.filter { it.title.contains(searchQuery, true) || it.id.contains(searchQuery, true) }
                            }
                            val shown = if (expandedRecents) list else list.take(3)
                            val filtersActive = selectedFilter != "All" || gradeFilter != null || searchQuery.isNotBlank()
                            Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    shown.forEachIndexed { idx, lesson ->
                                        val completed = doneIds.contains(lesson.id)
                                        RecentLessonRow(
                                            title = lesson.title,
                                            subtitle = "Grade ${lesson.grade} • " + LessonFilter.domainLabel(lesson),
                                            status = if (completed) "Completed" else "In Progress",
                                            statusIcon = if (completed) Icons.Outlined.CheckCircle else null,
                                            onClick = { onContinueLesson(lesson) }
                                        )
                                        if (idx < shown.lastIndex) HorizontalDivider(color = VachakColors.Border.copy(alpha = 0.6f), thickness = 0.8.dp)
                                    }
                                    if (shown.isEmpty()) {
                                        Text(
                                            if (recentIds.isEmpty() && !filtersActive) "Lessons you open will appear here."
                                            else "No lessons match these filters.",
                                            style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary, modifier = Modifier.padding(vertical = 12.dp)
                                        )
                                        if (filtersActive) {
                                            TextButton(
                                                onClick = { selectedFilter = "All"; gradeFilter = null; searchQuery = "" },
                                                contentPadding = PaddingValues(0.dp)
                                            ) { Text("Clear Filters") }
                                        }
                                    }
                                }
                            }
                        }
                        is HomeUiState.Loading -> {
                            Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth().height(120.dp)) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) }
                            }
                        }
                        else -> {
                            Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth()) {
                                Box(modifier = Modifier.padding(16.dp)) {
                                    Text("Lessons will appear here once available.", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                                }
                            }
                        }
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
    if (showReminders) {
        AlertDialog(
            onDismissRequest = { showReminders = false },
            title = { Text("Learning reminders") },
            text = {
                Text(
                    if (prefs.notificationsOn) "Reminders are ON — see Settings → Notifications to change."
                    else "Reminders are OFF — enable them in Settings → Notifications.",
                    style = MaterialTheme.typography.bodyMedium, color = VachakColors.TextPrimary
                )
            },
            confirmButton = {
                TextButton(onClick = { showReminders = false }) { Text("Close") }
            },
            containerColor = Color.White
        )
    }
}
