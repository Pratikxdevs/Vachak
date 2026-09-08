package com.vachak.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
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
import com.vachak.ui.components.*
import com.vachak.ui.theme.VachakColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class ToolsPanel { Hub, Worksheets, Flashcards }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    engine: EngineProvider,
    modifier: Modifier = Modifier
) {
    var panel by remember { mutableStateOf(ToolsPanel.Hub) }
    // Load real lessons for hub recent sections
    var lessons by remember { mutableStateOf<List<Lesson>>(emptyList()) }
    var uiState by remember { mutableStateOf<String?>(null) } // error/loading
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val ce = engine.curriculum as? com.vachak.content.ContentEngine
            val r = ce?.getLessons() ?: engine.curriculum.listLessons(0).let { rr ->
                when (rr) {
                    is EngineResult.Ok -> EngineResult.Ok(rr.value.map { ref -> Lesson(ref.id, ref.title, ref.grade, "", "", true) })
                    is EngineResult.Err -> rr as EngineResult<List<Lesson>>
                }
            }
            withContext(Dispatchers.Main) {
                when (r) {
                    is EngineResult.Ok -> lessons = r.value
                    is EngineResult.Err -> uiState = r.message
                }
            }
        }
    }

    when (panel) {
        ToolsPanel.Worksheets -> WorksheetPane(engine, lessons.firstOrNull(), onBack = { panel = ToolsPanel.Hub })
        ToolsPanel.Flashcards -> FlashcardPane(engine, lessons.firstOrNull(), onBack = { panel = ToolsPanel.Hub })
        ToolsPanel.Hub -> ToolsHub(
            lessons = lessons,
            error = uiState,
            onOpenWorksheets = { panel = ToolsPanel.Worksheets },
            onOpenFlashcards = { panel = ToolsPanel.Flashcards },
            modifier = modifier
        )
    }
}

@Composable
private fun ToolsHub(
    lessons: List<Lesson>,
    error: String?,
    onOpenWorksheets: () -> Unit,
    onOpenFlashcards: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().background(VachakColors.Background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header (tools.md §3)
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Tools", style = MaterialTheme.typography.headlineLarge, color = VachakColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text("Powerful tools to save time and enhance your teaching.", style = MaterialTheme.typography.bodyMedium, color = VachakColors.TextSecondary, fontSize = 14.sp, maxLines = 2)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.size(44.dp)) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(Icons.Outlined.Search, null, tint = VachakColors.TextPrimary, modifier = Modifier.size(20.dp))
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
            }

            // Primary Tools (tools.md §5)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Primary Tools", style = MaterialTheme.typography.titleMedium, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PrimaryToolCard(
                            title = "Worksheets",
                            subtitle = "Create practice worksheets instantly using AI.",
                            icon = Icons.Outlined.Description,
                            glyph = "म",
                            onClick = onOpenWorksheets,
                            modifier = Modifier.weight(1f)
                        )
                        PrimaryToolCard(
                            title = "Flashcards",
                            subtitle = "Generate and use flashcards for quick learning.",
                            icon = Icons.Outlined.Style,
                            glyph = "अ",
                            onClick = onOpenFlashcards,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Quick Actions (tools.md §9)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Quick Actions", style = MaterialTheme.typography.titleMedium, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        QuickActionSmallCard(title = "New Worksheet", subtitle = "Create a worksheet from scratch", icon = Icons.AutoMirrored.Outlined.NoteAdd, onClick = onOpenWorksheets, modifier = Modifier.weight(1f))
                        QuickActionSmallCard(title = "Generate Flashcards", subtitle = "Cards + sparkle", icon = Icons.Outlined.AutoAwesome, onClick = onOpenFlashcards, modifier = Modifier.weight(1f))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        QuickActionSmallCard(title = "My Saved Items", subtitle = "Saved worksheets & decks", icon = Icons.Outlined.Folder, onClick = {}, modifier = Modifier.weight(1f))
                        QuickActionSmallCard(title = "Recent Activity", subtitle = "Worksheet opened", icon = Icons.Outlined.History, onClick = {}, modifier = Modifier.weight(1f))
                    }
                }
            }

            // Recent Worksheets (tools.md §12)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeSectionHeader(title = "Recent Worksheets", actionLabel = "View All", onAction = onOpenWorksheets)
                    if (error != null && lessons.isEmpty()) {
                        Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Couldn't load recent items", style = MaterialTheme.typography.titleSmall, color = VachakColors.TextPrimary)
                                TextButton(onClick = {}, contentPadding = PaddingValues(0.dp)) { Text("Try Again") }
                            }
                        }
                    } else if (lessons.isEmpty()) {
                        Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("No worksheets yet", style = MaterialTheme.typography.titleSmall, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                                Text("Create your first practice worksheet.", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                                Button(onClick = onOpenWorksheets, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = VachakColors.PrimaryDark, contentColor = Color.White), modifier = Modifier.height(40.dp)) {
                                    Text("Create Worksheet")
                                }
                            }
                        }
                    } else {
                        val recent = lessons.take(3)
                        Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                recent.forEachIndexed { idx, lesson ->
                                    HistoryRow(
                                        title = lesson.title,
                                        subtitle = "Grade ${lesson.grade} • Language",
                                        meta = when (idx) { 0 -> "Generated today, 10:15 AM"; 1 -> "Generated yesterday, 4:30 PM"; else -> "Generated 2 days ago, 9:20 AM" },
                                        icon = Icons.Outlined.Description,
                                        badge = "Worksheet",
                                        onClick = onOpenWorksheets
                                    )
                                    if (idx < recent.lastIndex) HorizontalDivider(color = VachakColors.Border.copy(alpha = 0.6f), thickness = 0.8.dp)
                                }
                            }
                        }
                    }
                }
            }

            // Recent Flashcard Decks (tools.md §15)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeSectionHeader(title = "Recent Flashcard Decks", actionLabel = "View All", onAction = onOpenFlashcards)
                    if (lessons.isEmpty()) {
                        Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("No flashcard decks yet", style = MaterialTheme.typography.titleSmall, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                                Text("Start with a curriculum topic.", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                                OutlinedButton(onClick = onOpenFlashcards, shape = RoundedCornerShape(50)) { Text("Explore Curriculum") }
                            }
                        }
                    } else {
                        val recent = lessons.take(3)
                        Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                recent.forEachIndexed { idx, lesson ->
                                    HistoryRow(
                                        title = when (idx) { 0 -> "Hindi Varnamala"; 1 -> "Plants Around Us"; else -> "Number Names" },
                                        subtitle = "${when (idx) { 0 -> 20; 1 -> 12; else -> 15 }} cards • Grade ${lesson.grade}",
                                        meta = when (idx) { 0 -> "Updated today, 9:45 AM"; 1 -> "Updated yesterday, 3:10 PM"; else -> "Updated 2 days ago, 11:00 AM" },
                                        icon = Icons.Outlined.Style,
                                        badge = "Deck",
                                        onClick = onOpenFlashcards
                                    )
                                    if (idx < recent.lastIndex) HorizontalDivider(color = VachakColors.Border.copy(alpha = 0.6f), thickness = 0.8.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorksheetPane(engine: EngineProvider, lesson: Lesson?, onBack: () -> Unit) {
    var generating by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf<String?>(null) }
    val ce = engine.curriculum as? com.vachak.content.ContentEngine
    val activeLang by engine.activeLanguage.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(VachakColors.Background)) {
        // sub-header with back
        Surface(color = Color.White, shadowElevation = 0.dp, tonalElevation = 0.dp) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, modifier = Modifier.size(20.dp)) }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Worksheets", style = MaterialTheme.typography.titleMedium, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text("${ActiveLanguage.label(activeLang)} • Choose lesson / type → Generate → Preview", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                }
                Surface(shape = RoundedCornerShape(50), color = VachakColors.Lavender100) {
                    Text(ActiveLanguage.label(activeLang), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = VachakColors.DeepLavender)
                }
            }
        }
        if (lesson == null) {
            Box(modifier = Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VachakColors.Lavender600)
            }
        } else {
            Column(modifier = Modifier.weight(1f).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(lesson.title, style = MaterialTheme.typography.titleSmall, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("Grade ${lesson.grade} • Lesson ${lesson.id}", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                        Text(lesson.sourceTextHi.ifBlank { "हिन्दी पाठ — precomputed" }, style = MaterialTheme.typography.bodyMedium, color = VachakColors.TextPrimary)
                        if (!lesson.translatedText.isBlank()) {
                            HorizontalDivider(color = VachakColors.Border.copy(alpha = 0.6f))
                            Text(lesson.translatedText, style = MaterialTheme.typography.bodyMedium, color = VachakColors.DeepLavender)
                        }
                    }
                }
                val templates = remember(lesson) {
                    when (val r = ce?.getWorksheets(lesson.id)) {
                        is EngineResult.Ok -> r.value.map { it.templateType to it.titleHi }
                        else -> listOf("trace_olchiki_G1" to "Trace Ol Chiki", "fill_numbers_G2" to "Fill Numbers")
                    }
                }
                Text("Available templates", style = MaterialTheme.typography.titleSmall, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(templates) { (id, title) ->
                        AssistChip(onClick = {
                            generating = true; resultText = null
                            val res = engine.worksheet.generate(lesson.id, id)
                            resultText = when (res) {
                                is EngineResult.Ok -> "Generated • ${res.value.template} • Asset: ${res.value.items.firstOrNull()?.answerKey ?: id}"
                                is EngineResult.Err -> "Error: ${res.message}"
                            }
                            generating = false
                        }, label = { Text(title) }, leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, null, modifier = Modifier.size(16.dp)) })
                    }
                }
                Button(
                    onClick = {
                        generating = true
                        val res = engine.worksheet.generate(lesson.id, "")
                        resultText = when (res) {
                            is EngineResult.Ok -> "Worksheet ${res.value.id} ready • Open offline PDF at ${res.value.items.firstOrNull()?.answerKey}"
                            is EngineResult.Err -> res.message
                        }
                        generating = false
                    },
                    enabled = !generating,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = VachakColors.PrimaryDark, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    if (generating) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White) else Icon(Icons.Outlined.Print, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text("Generate Worksheet")
                }
                resultText?.let {
                    Surface(shape = RoundedCornerShape(16.dp), color = VachakColors.SoftLavender, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Lavender100)) {
                        Text(it, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = VachakColors.TextPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun FlashcardPane(engine: EngineProvider, lesson: Lesson?, onBack: () -> Unit) {
    var cards by remember { mutableStateOf<List<com.vachak.engine.Flashcard>>(emptyList()) }
    var idx by remember { mutableIntStateOf(0) }
    var flipped by remember { mutableStateOf(false) }
    val activeLang by engine.activeLanguage.collectAsState()

    LaunchedEffect(lesson) {
        if (lesson == null) return@LaunchedEffect
        when (val r = engine.flashcard.listDeck(lesson.id)) {
            is EngineResult.Ok -> cards = r.value.cards
            is EngineResult.Err -> cards = emptyList()
        }
        idx = 0; flipped = false
    }

    Column(modifier = Modifier.fillMaxSize().background(VachakColors.Background)) {
        Surface(color = Color.White, shadowElevation = 0.dp, tonalElevation = 0.dp) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, null) }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Flashcards", style = MaterialTheme.typography.titleMedium, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text("${ActiveLanguage.label(activeLang)} • Prebuilt assets • Offline images", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                }
                Surface(shape = RoundedCornerShape(50), color = VachakColors.Lavender100) {
                    Text(ActiveLanguage.label(activeLang), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = VachakColors.DeepLavender)
                }
            }
        }
        if (cards.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("No flashcards for this lesson yet.", style = MaterialTheme.typography.bodyMedium, color = VachakColors.TextSecondary)
                    OutlinedButton(onClick = onBack, shape = RoundedCornerShape(50)) { Text("Back to Tools") }
                }
            }
            return
        }
        val card = cards[idx.coerceIn(cards.indices)]
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(24.dp)).background(Color.White),
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                shadowElevation = 0.dp,
                onClick = { flipped = !flipped }
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (flipped) ActiveLanguage.label(activeLang) else "Hindi", style = MaterialTheme.typography.labelMedium, color = VachakColors.TextSecondary, letterSpacing = 1.sp)
                        Text(if (flipped) card.back else card.front, style = MaterialTheme.typography.headlineSmall, color = VachakColors.TextPrimary, fontWeight = FontWeight.Bold)
                        Text(card.imageAsset, style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                        Text(if (flipped) "Tap to see Hindi" else "Tap to flip → ${ActiveLanguage.label(activeLang)}", style = MaterialTheme.typography.bodySmall, color = VachakColors.DeepLavender)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { if (idx > 0) { idx--; flipped = false } }, enabled = idx > 0, shape = RoundedCornerShape(50)) { Text("Previous") }
                Text("${idx + 1} / ${cards.size}", style = MaterialTheme.typography.labelMedium, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Button(onClick = { if (idx < cards.lastIndex) { idx++; flipped = false } }, enabled = idx < cards.lastIndex, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = VachakColors.PrimaryDark, contentColor = Color.White)) { Text("Next") }
            }
            FilledTonalButton(onClick = { flipped = !flipped }, shape = RoundedCornerShape(50)) { Text(if (flipped) "Show Hindi" else "Show ${ActiveLanguage.label(activeLang)}") }
        }
    }
}
