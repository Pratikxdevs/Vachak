package com.vachak.ui.screens

import androidx.compose.foundation.background
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
import com.vachak.ui.theme.VachakColors
import kotlinx.coroutines.Dispatchers
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
    modifier: Modifier = Modifier
) {
    var uiState by remember { mutableStateOf<CurriculumUiState>(CurriculumUiState.Loading) }
    var query by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var isSearchExpanded by remember { mutableStateOf(false) }

    suspend fun load() {
        withContext(Dispatchers.IO) {
            val ce = engine.curriculum as? com.vachak.content.ContentEngine
            val res = ce?.getLessons() ?: engine.curriculum.listLessons(0).let { r ->
                when (r) {
                    is EngineResult.Ok -> EngineResult.Ok(r.value.map { ref -> Lesson(ref.id, ref.title, ref.grade, "", "", true) })
                    is EngineResult.Err -> r as EngineResult<List<Lesson>>
                }
            }
            withContext(Dispatchers.Main) {
                uiState = when (res) {
                    is EngineResult.Ok -> {
                        val list = if (res.value.isEmpty()) com.vachak.ui.mock.MockData.lessons else res.value
                        CurriculumUiState.Ready(list)
                    }
                    is EngineResult.Err -> {
                        val mock = com.vachak.ui.mock.MockData.lessons
                        CurriculumUiState.Ready(mock)
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    val lessons = when (val s = uiState) {
        is CurriculumUiState.Ready -> s.lessons
        else -> emptyList()
    }

    val filteredLessons by remember(lessons, query, selectedFilter) {
        derivedStateOf {
            var lst = lessons
            if (selectedFilter != "All") {
                lst = lst.filter { l -> LessonFilter.matches(l, selectedFilter) }
            }
            if (query.isNotBlank()) lst = lst.filter { it.title.contains(query, true) || it.id.contains(query, true) }
            lst
        }
    }

    val gradeCounts by remember(lessons) {
        derivedStateOf { (1..5).associateWith { g -> lessons.count { it.grade == g } } }
    }

    val decks = remember {
        com.vachak.ui.mock.MockData.flashcardDecks.map { Triple(it.title, it.subtitle, it.glyph) }
    }

    // Avoid nested BoxWithConstraints (double-measure cost at 90/120Hz)
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
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
                val filters = remember { listOf("All", "Language", "Mathematics", "EVS", "Stories", "Life Skills") }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 8.dp)) {
                    item(key = "filter-icon") { FilterPill(label = "Filter", selected = false, onClick = {}, leadingIcon = Icons.Outlined.Tune) }
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
                                OutlinedButton(onClick = { /* retry */ }, shape = RoundedCornerShape(50)) { Text("Try Again") }
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
                                OutlinedButton(onClick = { selectedFilter = "All"; query = "" }, shape = RoundedCornerShape(50)) { Text("Clear Filters") }
                            }
                        }
                    }
                }
                is CurriculumUiState.Ready -> {
                    val focus = filteredLessons.firstOrNull() ?: lessons.firstOrNull()
                    if (focus != null) {
                        item {
                            ContinueLearningCard(
                                title = focus.title.ifBlank { "Letters & Sounds" },
                                gradeLabel = "Grade ${focus.grade} • Language",
                                description = focus.sourceTextHi.ifBlank { "Learn the first sounds and their corresponding Ol Chiki forms." }.take(90),
                                progressLabel = "Progress  •  3 of ${lessons.size} lessons",
                                progress = 3f / lessons.size.coerceAtLeast(1).toFloat(),
                                onContinue = { onOpenLesson(focus) }
                            )
                        }
                    }
                }
            }

            // Browse by Grade (cirriculum.md §11)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeSectionHeader(title = "Browse by Grade", actionLabel = "View All", onAction = {})
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 8.dp)) {
                        items((1..5).toList(), key = { "grade-$it" }) { g ->
                            GradeCard(grade = g, lessonCount = gradeCounts[g] ?: 0, onClick = { selectedFilter = when (g) { 1 -> "Language"; 2 -> "Mathematics"; else -> "All" } })
                        }
                    }
                }
            }

            // Flashcards (cirriculum.md §15)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Flashcards", style = MaterialTheme.typography.titleMedium, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text("✨", style = MaterialTheme.typography.titleMedium)
                        }
                        TextButton(onClick = {}, contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text("View All", style = MaterialTheme.typography.labelMedium, color = VachakColors.Lavender600)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Outlined.ChevronRight, null, tint = VachakColors.Lavender600, modifier = Modifier.size(16.dp))
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 8.dp)) {
                        items(decks, key = { (title, _, _) -> title }) { (title, subtitle, glyph) ->
                            val lessonForDeck = lessons.firstOrNull()
                            val deckCount = remember(title, lessonForDeck?.id) { LessonFilter.stableDeckCount(title + (lessonForDeck?.id ?: "")) }
                            FlashcardDeckCard(
                                title = title,
                                subtitle = subtitle,
                                count = deckCount,
                                glyph = glyph,
                                onClick = { lessonForDeck?.let { onOpenLesson(it) } }
                            )
                        }
                    }
                }
            }

            // Recently Viewed (cirriculum.md §20)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeSectionHeader(title = "Recently Viewed", actionLabel = "View All", onAction = {})
                    when (uiState) {
                        is CurriculumUiState.Ready -> {
                            val recents = filteredLessons.take(3).ifEmpty { lessons.take(3) }
                            Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    if (recents.isEmpty()) {
                                        Text("No recently viewed lessons.", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary, modifier = Modifier.padding(vertical = 12.dp))
                                    } else {
                                        recents.forEachIndexed { idx, lesson ->
                                            RecentLessonRow(
                                                title = lesson.title,
                                                subtitle = "Grade ${lesson.grade} • Language",
                                                status = when (idx) {
                                                    0 -> "3/8"
                                                    1 -> "50%"
                                                    else -> "Completed"
                                                },
                                                statusIcon = if (idx == 2) Icons.Outlined.CheckCircle else null,
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
                            OutlinedButton(onClick = { selectedFilter = "All"; query = "" }, shape = RoundedCornerShape(50)) { Text("Clear Filters") }
                        }
                    }
                }
            }
        }
    }
}
