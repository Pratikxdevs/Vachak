package com.vachak.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.vachak.ui.components.GradeFilterSheet
import com.vachak.ui.components.GuidedEmpty
import com.vachak.ui.components.HomeDecorativeArcs
import com.vachak.ui.components.HomeSectionHeader
import com.vachak.ui.components.QuickActionCard
import com.vachak.ui.components.RecentLessonRow
import com.vachak.ui.components.SecondaryLessonRow
import com.vachak.ui.components.SectionCard
import com.vachak.ui.components.StatusRow
import com.vachak.ui.components.SteppedLoading
import com.vachak.ui.theme.VachakColors
import com.vachak.ui.theme.cardShadow
import com.vachak.ui.theme.tabletHPad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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

private val EaseSnap = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * Staged entrance: sections fade + rise 8dp, staggered ~70ms. Runs ONCE —
 * when the library first resolves — never on tab returns. Exits need nothing:
 * sections never conditionally unmount.
 */
@Composable
private fun EnterStage(index: Int, staged: Boolean, content: @Composable () -> Unit) {
    if (!staged) {
        content()
    } else {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(220, delayMillis = index * 70, easing = EaseSnap)) +
                slideInVertically(tween(220, delayMillis = index * 70, easing = EaseSnap)) { it / 12 },
            label = "enter$index"
        ) { content() }
    }
}

@Composable
fun HomeScreen(
    engine: EngineProvider,
    onNavigateLive: () -> Unit,
    onNavigateTools: () -> Unit,
    onNavigateLibrary: () -> Unit,
    onNavigateCurriculum: () -> Unit,
    onNavigateSettings: () -> Unit,
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
    // Entrance runs once per process: set when the library first resolves.
    var staged by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs = remember(context) { com.vachak.ui.prefs.VachakPrefs(context) }
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
    LaunchedEffect(uiState) { if (uiState is HomeUiState.Ready) staged = true }

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
    // Search matches across the whole library (title + id), independent of scope filters.
    val searchHits by remember(allLessons, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) emptyList()
            else allLessons.filter { it.title.contains(searchQuery, true) || it.id.contains(searchQuery, true) }
        }
    }

    // Use LocalConfiguration instead of nested BoxWithConstraints to avoid double-measure at 120Hz
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isTablet = remember(configuration.screenWidthDp) { configuration.screenWidthDp >= 840 }
    val hPad = tabletHPad(20.dp)
    val titleSize = if (isTablet) 40.sp else 30.sp
    val todayLine = remember {
        SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(Date()) +
            " • Offline"
    }
    // Bell dot reflects something real: reminders ON. OFF = no dot, no noise.
    val remindersOn = prefs.notificationsOn

    Box(modifier = modifier.fillMaxSize().background(VachakColors.Background)) {
        // Decorative arcs are static — draw behind LazyColumn to avoid overdraw on scroll at 120Hz
        HomeDecorativeArcs(modifier = Modifier.fillMaxSize())
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = hPad, end = hPad, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (isTablet) 20.dp else 14.dp)
        ) {
            // Header: greeting + date/progress context left; working actions right.
            // (The profile avatar did nothing — replaced by the progress chip,
            // which answers "where do I stand?" at a glance.)
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
                        val daypart = when (hour) { in 0..11 -> "morning"; in 12..16 -> "afternoon"; else -> "evening" }
                        Text("Good $daypart,", style = MaterialTheme.typography.titleSmall, color = VachakColors.TextSecondary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text(
                            prefs.teacherName,
                            style = MaterialTheme.typography.headlineLarge,
                            color = VachakColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = titleSize,
                            lineHeight = titleSize,
                            letterSpacing = (-0.5).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val ready = displayState
                        Text(
                            if (ready != null) "$todayLine • ${ready.completedCount} of ${ready.totalLessons} done"
                            else todayLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = VachakColors.TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), shadowElevation = 0.dp, modifier = Modifier.size(44.dp)) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) searchQuery = "" }, modifier = Modifier.size(44.dp)) {
                                    Icon(if (searchOpen) Icons.Outlined.Close else Icons.Outlined.Search, null, tint = VachakColors.TextPrimary, modifier = Modifier.size(20.dp))
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
                            if (remindersOn) {
                                Box(modifier = Modifier.align(Alignment.TopEnd).size(10.dp).clip(CircleShape).background(VachakColors.Lavender500))
                            }
                        }
                    }
                }
            }
            if (searchOpen) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search lessons…", color = VachakColors.TextSecondary) },
                            leadingIcon = { Icon(Icons.Outlined.Search, null, tint = VachakColors.TextSecondary) },
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Outlined.Close, null) }
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
                        // Result count = context: is this search narrowing anything?
                        if (searchQuery.isNotBlank()) {
                            val hits = searchHits
                            if (hits.isEmpty()) {
                                Text("No lessons match “$searchQuery” — try a shorter word.", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                            } else {
                                Text(
                                    "${hits.size} of ${allLessons.size} lessons",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VachakColors.TextSecondary,
                                    fontFamily = FontFamily.Monospace
                                )
                                // Search wins over recents: show hits directly.
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color.White,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border),
                                    modifier = Modifier.fillMaxWidth().cardShadow()
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                        hits.take(5).forEachIndexed { idx, lesson ->
                                            RecentLessonRow(
                                                title = lesson.title,
                                                subtitle = "Grade ${lesson.grade} • " + LessonFilter.domainLabel(lesson),
                                                completed = doneIds.contains(lesson.id),
                                                onClick = { onContinueLesson(lesson) }
                                            )
                                            if (idx < hits.take(5).size - 1) HorizontalDivider(color = VachakColors.Border.copy(alpha = 0.6f), thickness = 0.8.dp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Category filters
            item {
                EnterStage(0, staged) {
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
                            title = "No lessons on this tablet yet",
                            why = "Lessons arrive in an offline content pack — install one, then pick your class in Learn.",
                            actionLabel = "Open Learn",
                            onAction = { onNavigateCurriculum() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                is HomeUiState.Error -> {
                    item {
                        SectionCard(
                            title = "Library didn't load",
                            subtitle = "Nothing is lost — the lessons are on disk, the read just failed.",
                            actionLabel = "Try Again",
                            onAction = { scope.launch { uiState = HomeUiState.Loading; load() } },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            StatusRow(
                                dot = VachakColors.ErrorRed,
                                title = "Lesson query failed",
                                detail = state.msg.take(120)
                            )
                        }
                    }
                }
                is HomeUiState.Ready -> {
                    val ready = displayState ?: state
                    item {
                        EnterStage(1, staged) {
                            ContinueLearningCard(
                                title = ready.focus.title.ifBlank { "Letters & Sounds" },
                                gradeLabel = "Grade ${ready.focus.grade} • " + LessonFilter.domainLabel(ready.focus),
                                description = ready.focus.sourceTextHi.ifBlank { "Learn the first sounds and their corresponding Ol Chiki forms." }.take(90),
                                progressLabel = "Your progress",
                                progress = if (ready.totalLessons > 0) ready.completedCount.toFloat() / ready.totalLessons.coerceAtLeast(1) else 0f,
                                countText = "${ready.completedCount}/${ready.totalLessons}",
                                onContinue = { onContinueLesson(ready.focus) }
                            )
                        }
                    }
                    ready.secondary?.let { sec ->
                        item {
                            EnterStage(2, staged) {
                                SecondaryLessonRow(
                                    title = sec.title,
                                    subtitle = "Grade ${sec.grade} • " + LessonFilter.domainLabel(sec),
                                    onClick = { onContinueLesson(sec) }
                                )
                            }
                        }
                    }
                }
            }

            // Quick Actions (always visible — both destinations work offline)
            item {
                EnterStage(3, staged) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HomeSectionHeader(title = "Quick Actions", actionLabel = "Library", onAction = onNavigateLibrary)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            QuickActionCard(
                                title = "Live Translate",
                                subtitle = "Speak Hindi, read Santali — offline",
                                icon = Icons.Outlined.Mic,
                                tint = VachakColors.Lavender600,
                                containerColor = VachakColors.SoftLavender,
                                onClick = onNavigateLive,
                                modifier = Modifier.weight(1f).heightIn(min = 160.dp)
                            )
                            QuickActionCard(
                                title = "Worksheets",
                                subtitle = "Print-ready practice sheets",
                                icon = Icons.Outlined.Description,
                                tint = VachakColors.AccentDeep,
                                containerColor = VachakColors.AccentLight,
                                onClick = onNavigateTools,
                                modifier = Modifier.weight(1f).heightIn(min = 160.dp)
                            )
                        }
                    }
                }
            }

            // Recent Lessons
            item {
                EnterStage(4, staged) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val ready = displayState
                        HomeSectionHeader(
                            title = "Recent Lessons",
                            count = ready?.recents?.size,
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
                                Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth().cardShadow()) {
                                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                        shown.forEachIndexed { idx, lesson ->
                                            RecentLessonRow(
                                                title = lesson.title,
                                                subtitle = "Grade ${lesson.grade} • " + LessonFilter.domainLabel(lesson),
                                                completed = doneIds.contains(lesson.id),
                                                onClick = { onContinueLesson(lesson) }
                                            )
                                            if (idx < shown.lastIndex) HorizontalDivider(color = VachakColors.Border.copy(alpha = 0.6f), thickness = 0.8.dp)
                                        }
                                        if (shown.isEmpty()) {
                                            Column(modifier = Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text(
                                                    if (recentIds.isEmpty() && !filtersActive) "Lessons you open will appear here."
                                                    else "No lessons match these filters.",
                                                    style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary
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
                            }
                            is HomeUiState.Loading -> {
                                Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth().height(120.dp)) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = VachakColors.Lavender600)
                                            Text("Loading recents…", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                                        }
                                    }
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
                    if (remindersOn) "Reminders are ON — change this anytime in Settings → Notifications."
                    else "Reminders are OFF — turn them on in Settings → Notifications.",
                    style = MaterialTheme.typography.bodyMedium, color = VachakColors.TextPrimary
                )
            },
            confirmButton = {
                TextButton(onClick = { showReminders = false; onNavigateSettings() }) { Text("Open Settings") }
            },
            dismissButton = {
                TextButton(onClick = { showReminders = false }) { Text("Close") }
            },
            containerColor = Color.White
        )
    }
}
