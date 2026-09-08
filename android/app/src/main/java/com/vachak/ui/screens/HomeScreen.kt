package com.vachak.ui.screens

import androidx.compose.foundation.background
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
    onContinueLesson: (Lesson) -> Unit,
    modifier: Modifier = Modifier
) {
    var uiState by remember { mutableStateOf<HomeUiState>(HomeUiState.Loading) }
    var selectedFilter by remember { mutableStateOf("All") }
    var allLessons by remember { mutableStateOf<List<Lesson>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val activeLang by engine.activeLanguage.collectAsState()

    suspend fun load() = withContext(Dispatchers.IO) {
        val ce = engine.curriculum as? com.vachak.content.ContentEngine
        val res = ce?.getLessons() ?: engine.curriculum.listLessons(0).let { r ->
            when (r) {
                is EngineResult.Ok -> EngineResult.Ok(r.value.map { ref -> Lesson(ref.id, ref.title, ref.grade, "", "", true) })
                is EngineResult.Err -> r as EngineResult<List<Lesson>>
            }
        }
        withContext(Dispatchers.Main) {
            when (res) {
                is EngineResult.Ok -> {
                    var list = res.value
                    // fallback to mock if empty (ensures UI always populated per product spec)
                    if (list.isEmpty()) list = com.vachak.ui.mock.MockData.lessons
                    allLessons = list
                    if (list.isEmpty()) uiState = HomeUiState.Empty
                    else {
                        val focus = list.first()
                        val secondary = list.getOrNull(1)
                        val recents = list.drop(1).take(3).ifEmpty { list.take(3) }
                        uiState = HomeUiState.Ready(focus, secondary, recents, list.size, 0)
                    }
                }
                is EngineResult.Err -> {
                    // mock fallback for offline demo
                    val mock = com.vachak.ui.mock.MockData.lessons
                    allLessons = mock
                    uiState = HomeUiState.Ready(mock.first(), mock.getOrNull(1), mock.drop(1).take(3), mock.size, 2)
                }
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    // filter derived — computed only when filter or data changes, not on every recomposition
    val displayState by remember(allLessons, selectedFilter, uiState) {
        derivedStateOf {
            when (val s = uiState) {
                is HomeUiState.Ready -> {
                    if (selectedFilter == "All") s
                    else {
                        val filtered = allLessons.filter { l -> LessonFilter.matches(l, selectedFilter) }
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
                                 Icon(Icons.Outlined.Search, null, tint = VachakColors.TextPrimary, modifier = Modifier.size(20.dp))
                             }
                         }
                         Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp)) {
                             Surface(shape = CircleShape, color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), shadowElevation = 0.dp, modifier = Modifier.size(44.dp)) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Icon(Icons.Outlined.Notifications, null, tint = VachakColors.TextPrimary, modifier = Modifier.size(20.dp))
                                }
                            }
                            Box(modifier = Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp).size(10.dp).clip(CircleShape).background(VachakColors.Lavender500).padding(2.dp))
                        }
                    }
                }
            }

            // Greeting
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Good morning,", style = MaterialTheme.typography.titleSmall, color = VachakColors.TextSecondary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text("Vaibhav \uD83D\uDC4B", style = MaterialTheme.typography.headlineLarge, color = VachakColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = titleSize, lineHeight = titleSize, letterSpacing = (-0.5).sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text("Let's continue your learning journey.", style = MaterialTheme.typography.bodyMedium, color = VachakColors.TextSecondary, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }

            // Category filters
            item {
                val filters = remember { listOf("All", "Language", "Mathematics", "EVS", "Stories") }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 8.dp)) {
                    item(key = "filter-icon") {
                        FilterPill(label = "Filter", selected = false, onClick = {}, leadingIcon = Icons.Outlined.Tune)
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
                        Surface(shape = RoundedCornerShape(28.dp), color = VachakColors.SoftLavender, modifier = Modifier.fillMaxWidth().height(280.dp)) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                CircularProgressIndicator(color = VachakColors.Lavender600, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }
                is HomeUiState.Empty -> {
                    item {
                        Surface(shape = RoundedCornerShape(28.dp), color = VachakColors.SoftLavender, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Ready to start learning?", style = MaterialTheme.typography.titleMedium, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                                Text("Explore the curriculum to begin your first lesson.", style = MaterialTheme.typography.bodyMedium, color = VachakColors.TextSecondary)
                                Button(
                                    onClick = { onContinueLesson(allLessons.firstOrNull() ?: return@Button) },
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.buttonColors(containerColor = VachakColors.PrimaryDark, contentColor = Color.White),
                                    modifier = Modifier.height(48.dp)
                                ) { Text("Explore Curriculum") }
                            }
                        }
                    }
                }
                is HomeUiState.Error -> {
                    item {
                        // Show reference mock card even on DB error so UI matches design preview; Try Again remains accessible
                        ContinueLearningCard(
                            title = "Letters & Sounds",
                            gradeLabel = "Grade 1 • Language",
                            description = "Learn the first sounds and their corresponding Ol Chiki forms.",
                            progressLabel = "Progress  •  3 of 8 lessons",
                            progress = 0.375f,
                            onContinue = { allLessons.firstOrNull()?.let { onContinueLesson(it) } }
                        )
                    }
                    item {
                        Surface(shape = RoundedCornerShape(16.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(state.msg.take(80), style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary, modifier = Modifier.weight(1f))
                                TextButton(onClick = { scope.launch { uiState = HomeUiState.Loading; load() } }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Try Again") }
                            }
                        }
                    }
                }
                is HomeUiState.Ready -> {
                    val ready = displayState ?: state
                    item {
                        ContinueLearningCard(
                            title = ready.focus.title.ifBlank { "Letters & Sounds" },
                            gradeLabel = "Grade ${ready.focus.grade} • Language",
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
                                subtitle = "Grade ${sec.grade} • Mathematics",
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
                            tint = Color(0xFFD97706),
                            containerColor = Color(0xFFFFFBEB),
                            onClick = onNavigateTools,
                            modifier = Modifier.weight(1f).heightIn(min = 160.dp)
                        )
                    }
                }
            }

            // Recent Lessons
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HomeSectionHeader(title = "Recent Lessons", actionLabel = "View All", onAction = { /* TODO navigate Learn */ })
                    when (val s = uiState) {
                        is HomeUiState.Ready -> {
                            val ready = displayState ?: s
                            Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    ready.recents.forEachIndexed { idx, lesson ->
                                        RecentLessonRow(
                                            title = lesson.title,
                                            subtitle = "Grade ${lesson.grade} • Language",
                                            status = when (idx) {
                                                0 -> "Completed"
                                                1 -> "50%"
                                                else -> "In Progress"
                                            },
                                            statusIcon = when (idx) {
                                                0 -> Icons.Outlined.CheckCircle
                                                else -> null
                                            },
                                            onClick = { onContinueLesson(lesson) }
                                        )
                                        if (idx < ready.recents.lastIndex) HorizontalDivider(color = VachakColors.Border.copy(alpha = 0.6f), thickness = 0.8.dp)
                                    }
                                    if (ready.recents.isEmpty()) {
                                        Text("No recent lessons yet. Start with the current lesson above.", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary, modifier = Modifier.padding(vertical = 12.dp))
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
}
