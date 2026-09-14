package com.vachak.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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

enum class ToolsPanel { Hub, Worksheets, Flashcards, Saved }

/** Maps a tools sub-route segment (worksheets/flashcards/saved) to its panel. */
fun toolsPanelFromRoute(segment: String): ToolsPanel = when (segment) {
    "worksheets" -> ToolsPanel.Worksheets
    "flashcards" -> ToolsPanel.Flashcards
    "saved" -> ToolsPanel.Saved
    else -> ToolsPanel.Hub
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    engine: EngineProvider,
    startPanel: ToolsPanel = ToolsPanel.Hub,
    onOpenPanel: (ToolsPanel) -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var panel by remember(startPanel) { mutableStateOf(startPanel) }
    // Stay in sync with the nav route: hub ↔ tools/* sub-routes are real
    // destinations now, so the back button and deep-links work via the
    // backstack instead of getting stuck on one page.
    LaunchedEffect(startPanel) { panel = startPanel }
    // Load real lessons for hub recent sections
    var lessons by remember { mutableStateOf<List<Lesson>>(emptyList()) }
    var uiState by remember { mutableStateOf<String?>(null) } // error/loading
    var reloadTick by remember { mutableStateOf(0) }
    // Real data: generated worksheet PDFs on disk + installed pack deck totals.
    var pdfs by remember { mutableStateOf<List<java.io.File>>(emptyList()) }
    var packGrades by remember { mutableStateOf<List<com.vachak.ui.navigation.PackGrade>?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(reloadTick) {
        withContext(Dispatchers.IO) {
            val ce = engine.curriculum as? com.vachak.content.ContentEngine
            val r = ce?.getLessons() ?: engine.curriculum.listLessons(0).let { rr ->
                when (rr) {
                    is EngineResult.Ok -> EngineResult.Ok(rr.value.map { ref -> Lesson(ref.id, ref.title, ref.grade, "", "", true, ref.domain) })
                    is EngineResult.Err -> rr as EngineResult<List<Lesson>>
                }
            }
            val diskPdfs = com.vachak.ui.pdf.WorksheetPdf.list(context)
            val packs = com.vachak.ui.navigation.loadPackSummary(context)
            withContext(Dispatchers.Main) {
                when (r) {
                    is EngineResult.Ok -> lessons = r.value
                    is EngineResult.Err -> uiState = r.message
                }
                pdfs = diskPdfs
                packGrades = packs
            }
        }
    }

    // Hub → sub-route navigation goes through NavController (real destinations);
    // sub-page back pops the backstack (or returns to hub when embedded).
    fun openPanel(p: ToolsPanel) {
        if (startPanel == ToolsPanel.Hub && p != ToolsPanel.Hub) onOpenPanel(p)
        else panel = p
    }
    fun goBack() {
        if (startPanel != ToolsPanel.Hub) onBack() else panel = ToolsPanel.Hub
    }
    when (panel) {
        ToolsPanel.Worksheets -> WorksheetPane(engine, lessons.firstOrNull(), allLessons = lessons, onBack = { goBack() })
        ToolsPanel.Flashcards -> FlashcardPane(engine, lessons.firstOrNull(), allLessons = lessons, onBack = { goBack() })
        ToolsPanel.Saved -> SavedPane(onBack = { goBack() })
        ToolsPanel.Hub -> ToolsHub(
            lessons = lessons,
            pdfs = pdfs,
            packGrades = packGrades,
            error = uiState,
            onOpenWorksheets = { openPanel(ToolsPanel.Worksheets) },
            onOpenFlashcards = { openPanel(ToolsPanel.Flashcards) },
            onOpenSaved = { openPanel(ToolsPanel.Saved) },
            onRetry = { reloadTick++ },
            modifier = modifier
        )
    }
}

@Composable
private fun ToolsHub(
    lessons: List<Lesson>,
    pdfs: List<java.io.File>,
    packGrades: List<com.vachak.ui.navigation.PackGrade>?,
    error: String?,
    onOpenWorksheets: () -> Unit,
    onOpenFlashcards: () -> Unit,
    onOpenSaved: () -> Unit,
    onRetry: () -> Unit,
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
                            subtitle = "Template-based practice worksheets, ready to print.",
                            icon = Icons.Outlined.Description,
                            glyph = "म",
                            onClick = onOpenWorksheets,
                            modifier = Modifier.weight(1f)
                        )
                        PrimaryToolCard(
                            title = "Flashcards",
                            subtitle = "Prebuilt decks for quick classroom revision.",
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
                        QuickActionSmallCard(title = "Generate Flashcards", subtitle = "Print-ready decks", icon = Icons.Outlined.AutoAwesome, onClick = onOpenFlashcards, modifier = Modifier.weight(1f))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        QuickActionSmallCard(title = "My Saved Items", subtitle = "Saved worksheets & decks", icon = Icons.Outlined.Folder, onClick = onOpenSaved, modifier = Modifier.weight(1f))
                        QuickActionSmallCard(title = "Recent Activity", subtitle = "Latest translations & files", icon = Icons.Outlined.History, onClick = onOpenSaved, modifier = Modifier.weight(1f))
                    }
                }
            }

            // Recent Worksheets — real generated PDFs on disk (never fake timestamps).
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeSectionHeader(title = "Recent Worksheets", actionLabel = "View All", onAction = onOpenWorksheets)
                    if (error != null && pdfs.isEmpty()) {
                        Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Couldn't load recent items", style = MaterialTheme.typography.titleSmall, color = VachakColors.TextPrimary)
                                TextButton(onClick = onRetry, contentPadding = PaddingValues(0.dp)) { Text("Try Again") }
                            }
                        }
                    } else if (pdfs.isEmpty()) {
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
                        val recent = pdfs.sortedByDescending { it.lastModified() }.take(3)
                        val dateFmt = remember { java.text.SimpleDateFormat("d MMM, h:mm a", java.util.Locale.getDefault()) }
                        Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                recent.forEachIndexed { idx, f ->
                                    HistoryRow(
                                        title = f.nameWithoutExtension,
                                        subtitle = "${f.length() / 1024} KB • PDF",
                                        meta = dateFmt.format(java.util.Date(f.lastModified())),
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

            // Flashcard decks — real installed-pack totals per grade (never invented counts).
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeSectionHeader(title = "Flashcard Decks", actionLabel = "View All", onAction = onOpenFlashcards)
                    val decks = packGrades?.filter { it.flashcards > 0 }.orEmpty()
                    if (decks.isEmpty()) {
                        Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("No flashcard decks yet", style = MaterialTheme.typography.titleSmall, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                                Text("Start with a curriculum topic.", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                                OutlinedButton(onClick = onOpenFlashcards, shape = RoundedCornerShape(50)) { Text("Explore Curriculum") }
                            }
                        }
                    } else {
                        Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                decks.forEachIndexed { idx, pg ->
                                    HistoryRow(
                                        title = "Grade ${pg.grade} decks",
                                        subtitle = "${pg.chapters} decks • ${pg.flashcards} cards • Grade ${pg.grade}",
                                        meta = "Review-pending pack",
                                        icon = Icons.Outlined.Style,
                                        badge = "Deck",
                                        onClick = onOpenFlashcards
                                    )
                                    if (idx < decks.lastIndex) HorizontalDivider(color = VachakColors.Border.copy(alpha = 0.6f), thickness = 0.8.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PackGradeChapterPickers(
    grades: List<com.vachak.ui.navigation.PackGrade>,
    grade: Int?,
    slug: String?,
    onGrade: (Int?) -> Unit,
    onSlug: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var gradeOpen by remember { mutableStateOf(false) }
    var slugOpen by remember { mutableStateOf(false) }
    val chapters = grades.firstOrNull { it.grade == grade }?.chapterTitles.orEmpty()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(expanded = gradeOpen, onExpandedChange = { gradeOpen = it }, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = grade?.let { "Grade $it" } ?: "Choose grade",
                onValueChange = {}, readOnly = true, label = { Text("Pack grade") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = gradeOpen) },
                modifier = Modifier.fillMaxWidth().menuAnchor(), singleLine = true
            )
            ExposedDropdownMenu(expanded = gradeOpen, onDismissRequest = { gradeOpen = false }) {
                grades.forEach { g ->
                    DropdownMenuItem(text = { Text("Grade ${g.grade} • ${g.chapters} chapters") }, onClick = { onGrade(g.grade); onSlug(null); gradeOpen = false })
                }
            }
        }
        ExposedDropdownMenuBox(expanded = slugOpen, onExpandedChange = { slugOpen = it && grade != null }, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = chapters.firstOrNull { it.slug == slug }?.title ?: "Choose chapter",
                onValueChange = {}, readOnly = true, label = { Text("Pack chapter") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = slugOpen) },
                modifier = Modifier.fillMaxWidth().menuAnchor(), singleLine = true, enabled = grade != null
            )
            ExposedDropdownMenu(expanded = slugOpen, onDismissRequest = { slugOpen = false }) {
                chapters.forEach { c ->
                    DropdownMenuItem(text = { Text(c.title) }, onClick = { onSlug(c.slug); slugOpen = false })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PackWorksheetStudio(modifier: Modifier = Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var grades by remember { mutableStateOf<List<com.vachak.ui.navigation.PackGrade>>(emptyList()) }
    var grade by remember { mutableStateOf<Int?>(null) }
    var slug by remember { mutableStateOf<String?>(null) }
    var questions by remember { mutableStateOf<List<com.vachak.ui.content.PackContentReader.PackQuestion>?>(null) }
    LaunchedEffect(Unit) {
        grades = withContext(Dispatchers.IO) { com.vachak.ui.navigation.loadPackSummary(ctx) }.orEmpty()
        // Auto-select the first available grade+chapter so the studio extracts
        // real pack content immediately instead of sitting on an empty picker.
        val g0 = grades.firstOrNull()
        if (g0 != null) {
            grade = g0.grade
            slug = g0.chapterTitles.firstOrNull()?.slug
        }
    }
    LaunchedEffect(grade, slug) {
        val g = grade; val s = slug
        questions = if (g != null && s != null) withContext(Dispatchers.IO) {
            com.vachak.ui.content.PackContentReader.readWorksheet(ctx, g, s)
        } else null
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PackGradeChapterPickers(grades, grade, slug, { grade = it }, { slug = it })
        val g = grade; val s = slug; val qs = questions
        if (g == null || s == null) {
            Text("Pick a pack grade and chapter to load its extracted worksheet.", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
        } else if (qs == null) {
            Surface(shape = RoundedCornerShape(16.dp), color = VachakColors.SoftLavender, modifier = Modifier.fillMaxWidth()) {
                Text("Chapter not on this device — install the content pack (Settings → Packs).", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
            }
        } else if (qs.isEmpty()) {
            Text("No worksheet items extracted for this chapter.", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
        } else {
            Box(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                PackWorksheetView(qs)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PackDeckStudio(modifier: Modifier = Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var grades by remember { mutableStateOf<List<com.vachak.ui.navigation.PackGrade>>(emptyList()) }
    var grade by remember { mutableStateOf<Int?>(null) }
    var slug by remember { mutableStateOf<String?>(null) }
    var cards by remember { mutableStateOf<List<com.vachak.ui.content.PackContentReader.PackCard>?>(null) }
    LaunchedEffect(Unit) {
        grades = withContext(Dispatchers.IO) { com.vachak.ui.navigation.loadPackSummary(ctx) }.orEmpty()
        val g0 = grades.firstOrNull()
        if (g0 != null) {
            grade = g0.grade
            slug = g0.chapterTitles.firstOrNull()?.slug
        }
    }
    LaunchedEffect(grade, slug) {
        val g = grade; val s = slug
        cards = if (g != null && s != null) withContext(Dispatchers.IO) {
            com.vachak.ui.content.PackContentReader.readDeck(ctx, g, s)
        } else null
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PackGradeChapterPickers(grades, grade, slug, { grade = it }, { slug = it })
        val g = grade; val s = slug; val cs = cards
        if (g == null || s == null) {
            Text("Pick a pack grade and chapter to load its extracted deck.", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
        } else if (cs == null) {
            Surface(shape = RoundedCornerShape(16.dp), color = VachakColors.SoftLavender, modifier = Modifier.fillMaxWidth()) {
                Text("Chapter not on this device — install the content pack (Settings → Packs).", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
            }
        } else if (cs.isEmpty()) {
            Text("No deck extracted for this chapter.", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
        } else {
            Box(modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
                PackDeckView(g, cs)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorksheetPane(engine: EngineProvider, lesson: Lesson?, allLessons: List<Lesson> = emptyList(), onBack: () -> Unit) {
    // Real lesson picker — the pane never silently binds the first lesson.
    // selId initialises once (not on every lessons-list reload) so switching
    // lessons always resets the generated output — no stale "same sheet".
    var selId by remember { mutableStateOf(lesson?.id ?: allLessons.firstOrNull()?.id) }
    LaunchedEffect(lesson?.id) { if (lesson != null) selId = lesson.id }
    LaunchedEffect(allLessons.firstOrNull()?.id) { if (selId == null) selId = allLessons.firstOrNull()?.id }
    val activeLesson = allLessons.firstOrNull { it.id == selId } ?: lesson
    var pickerOpen by remember { mutableStateOf(false) }
    var source by remember { mutableStateOf("Lesson") }
    val lesson = activeLesson
    var generating by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf<String?>(null) }
    var lastWorksheet by remember { mutableStateOf<com.vachak.engine.Worksheet?>(null) }
    var questionCount by remember { mutableStateOf(5) }
    // New lesson ⇒ new worksheet state.
    LaunchedEffect(lesson?.id) { resultText = null; lastWorksheet = null }
    val context = androidx.compose.ui.platform.LocalContext.current
    val ce = engine.curriculum as? com.vachak.content.ContentEngine
    val activeLang by engine.activeLanguage.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(VachakColors.Background)) {
        // sub-header with back
        Surface(color = Color.White, shadowElevation = 0.dp, tonalElevation = 0.dp) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", modifier = Modifier.size(20.dp)) }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Worksheets", style = MaterialTheme.typography.titleMedium, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text("${ActiveLanguage.label(activeLang)} • Choose lesson / type → Generate → Preview", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                }
                Surface(shape = RoundedCornerShape(50), color = VachakColors.Lavender100) {
                    Text(ActiveLanguage.label(activeLang), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = VachakColors.DeepLavender)
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Lesson", "Pack chapter").forEach { s ->
                FilterChip(selected = source == s, onClick = { source = s }, label = { Text(s) }, modifier = Modifier.weight(1f))
            }
        }
        if (source == "Pack chapter") {
            PackWorksheetStudio(modifier = Modifier.weight(1f).padding(horizontal = 24.dp))
        } else if (lesson == null) {
            Box(modifier = Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VachakColors.Lavender600)
            }
        } else {
            Column(modifier = Modifier.weight(1f).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (allLessons.size > 1) {
                    ExposedDropdownMenuBox(expanded = pickerOpen, onExpandedChange = { pickerOpen = it }) {
                        OutlinedTextField(
                            value = "Grade ${lesson.grade} • ${lesson.title}",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Lesson") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = pickerOpen) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            singleLine = true
                        )
                        ExposedDropdownMenu(expanded = pickerOpen, onDismissRequest = { pickerOpen = false }) {
                            allLessons.forEach { l ->
                                DropdownMenuItem(
                                    text = { Text("Grade ${l.grade} • ${l.title}") },
                                    onClick = { selId = l.id; pickerOpen = false }
                                )
                            }
                        }
                    }
                }
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
                        else -> emptyList()
                    }
                }
                Text("Available templates", style = MaterialTheme.typography.titleSmall, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                if (templates.isEmpty()) {
                    Text("No templates stored for this lesson yet.", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(templates, key = { it.first }) { (id, title) ->
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
                // Question count (spec §11: 5 / 10 / 15) — trims the template's
                // items deterministically; PDF prints the same N.
                Text("Questions", style = MaterialTheme.typography.titleSmall, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15).forEach { n ->
                        FilterChip(
                            selected = questionCount == n,
                            onClick = { questionCount = n },
                            label = { Text("$n") }
                        )
                    }
                }
                Button(
                    onClick = {
                        generating = true
                        val res = engine.worksheet.generate(lesson.id, "")
                        resultText = when (res) {
                            is EngineResult.Ok -> {
                                lastWorksheet = res.value
                                "Worksheet ${res.value.id} ready • ${res.value.items.size} items — tap Print for PDF"
                            }
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
                // Offline PDF (mock PDF wired real): renders the generated
                // template to filesDir and opens it in an installed viewer.
                OutlinedButton(
                    onClick = {
                        val ws = lastWorksheet ?: return@OutlinedButton
                        // Deterministic N-question trim (spec §11); PDF matches.
                        val trimmed = ws.copy(items = ws.items.take(questionCount.coerceAtLeast(1)))
                        resultText = try {
                            val saved = com.vachak.ui.pdf.WorksheetPdf.generate(context, trimmed, lesson.title, lesson.grade)
                            try {
                                context.startActivity(com.vachak.ui.pdf.WorksheetPdf.viewIntent(context, saved.file))
                            } catch (_: Exception) {
                                // No viewer installed: file is still saved offline.
                            }
                            "PDF saved • ${saved.file.name} • ${saved.pages} page(s) — see Saved Items"
                        } catch (e: Exception) {
                            "PDF failed: ${e.message}"
                        }
                    },
                    enabled = lastWorksheet != null,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Outlined.PictureAsPdf, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text("Print / Save PDF")
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
private fun SavedPane(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var pdfs by remember { mutableStateOf<List<java.io.File>>(emptyList()) }
    var notice by remember { mutableStateOf<String?>(null) }
    fun refresh() { pdfs = com.vachak.ui.pdf.WorksheetPdf.list(context) }
    LaunchedEffect(Unit) { refresh() }
    val recentVoice = LiveConversationStore.items.takeLast(5).reversed()

    Column(modifier = Modifier.fillMaxSize().background(VachakColors.Background)) {
        Surface(color = Color.White, shadowElevation = 0.dp, tonalElevation = 0.dp) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", modifier = Modifier.size(20.dp)) }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Saved Items", style = MaterialTheme.typography.titleMedium, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text("Offline PDFs + recent voice translations", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                }
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text("Worksheet PDFs (${pdfs.size})", style = MaterialTheme.typography.titleSmall, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            }
            if (pdfs.isEmpty()) {
                item {
                    Text("No saved PDFs yet — generate a worksheet, then Print / Save PDF.", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                }
            } else {
                items(pdfs, key = { it.absolutePath }) { f ->
                    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Outlined.PictureAsPdf, null, tint = VachakColors.DeepLavender, modifier = Modifier.size(20.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(f.nameWithoutExtension, style = MaterialTheme.typography.bodyMedium, color = VachakColors.TextPrimary, maxLines = 1)
                                Text("${f.length() / 1024} KB", style = MaterialTheme.typography.labelSmall, color = VachakColors.TextSecondary)
                            }
                            IconButton(onClick = {
                                try { context.startActivity(com.vachak.ui.pdf.WorksheetPdf.viewIntent(context, f)) }
                                catch (e: Exception) { notice = "No PDF viewer installed — file kept offline" }
                            }) { Icon(Icons.Outlined.OpenInNew, null, tint = VachakColors.TextPrimary) }
                            IconButton(onClick = {
                                if (com.vachak.ui.pdf.WorksheetPdf.delete(f)) refresh() else notice = "Delete failed"
                            }) { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                Text("Recent voice translations (${recentVoice.size})", style = MaterialTheme.typography.titleSmall, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            }
            if (recentVoice.isEmpty()) {
                item {
                    Text("Nothing spoken yet — tap Live and speak.", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                }
            } else {
                items(recentVoice, key = { it.id }) { item ->
                    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(item.hindiText, style = MaterialTheme.typography.bodyMedium, color = VachakColors.TextPrimary, maxLines = 2)
                            Text(item.santaliText ?: item.error ?: "…", style = MaterialTheme.typography.bodySmall, color = VachakColors.DeepLavender, maxLines = 2)
                        }
                    }
                }
            }
            notice?.let {
                item {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardPane(engine: EngineProvider, lesson: Lesson?, allLessons: List<Lesson> = emptyList(), onBack: () -> Unit) {
    var selId by remember { mutableStateOf(lesson?.id ?: allLessons.firstOrNull()?.id) }
    LaunchedEffect(lesson?.id) { if (lesson != null) selId = lesson.id }
    LaunchedEffect(allLessons.firstOrNull()?.id) { if (selId == null) selId = allLessons.firstOrNull()?.id }
    val activeLesson = allLessons.firstOrNull { it.id == selId } ?: lesson
    var pickerOpen by remember { mutableStateOf(false) }
    var source by remember { mutableStateOf("Lesson") }
    var cards by remember { mutableStateOf<List<com.vachak.engine.Flashcard>>(emptyList()) }
    var idx by remember { mutableIntStateOf(0) }
    var flipped by remember { mutableStateOf(false) }
    val activeLang by engine.activeLanguage.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(activeLesson) {
        if (activeLesson == null) return@LaunchedEffect
        when (val r = engine.flashcard.listDeck(activeLesson.id)) {
            is EngineResult.Ok -> cards = r.value.cards
            is EngineResult.Err -> cards = emptyList()
        }
        idx = 0; flipped = false
    }

    Column(modifier = Modifier.fillMaxSize().background(VachakColors.Background)) {
        Surface(color = Color.White, shadowElevation = 0.dp, tonalElevation = 0.dp) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Flashcards", style = MaterialTheme.typography.titleMedium, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text("${ActiveLanguage.label(activeLang)} • Prebuilt assets • Offline images", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                }
                Surface(shape = RoundedCornerShape(50), color = VachakColors.Lavender100) {
                    Text(ActiveLanguage.label(activeLang), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = VachakColors.DeepLavender)
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Lesson", "Pack chapter").forEach { s ->
                FilterChip(selected = source == s, onClick = { source = s }, label = { Text(s) }, modifier = Modifier.weight(1f))
            }
        }
        if (source == "Pack chapter") {
            PackDeckStudio(modifier = Modifier.weight(1f).padding(horizontal = 24.dp))
            return
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
        // Real prebuilt art from APK assets (46 PNGs); missing art renders
        // nothing — the asset path is never shown as text.
        var cardBitmap by remember(card.imageAsset) { mutableStateOf<android.graphics.Bitmap?>(null) }
        LaunchedEffect(card.imageAsset) {
            cardBitmap = withContext(Dispatchers.IO) {
                try {
                    context.assets.open(card.imageAsset.trimStart('/')).use {
                        android.graphics.BitmapFactory.decodeStream(it)
                    }
                } catch (_: Exception) { null }
            }
        }
        if (allLessons.size > 1) {
            ExposedDropdownMenuBox(expanded = pickerOpen, onExpandedChange = { pickerOpen = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                OutlinedTextField(
                    value = activeLesson?.let { "Grade ${it.grade} • ${it.title}" } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Deck source lesson") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = pickerOpen) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    singleLine = true
                )
                ExposedDropdownMenu(expanded = pickerOpen, onDismissRequest = { pickerOpen = false }) {
                    allLessons.forEach { l ->
                        DropdownMenuItem(
                            text = { Text("Grade ${l.grade} • ${l.title}") },
                            onClick = { selId = l.id; pickerOpen = false }
                        )
                    }
                }
            }
        }
        Column(modifier = Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border),
                shadowElevation = 0.dp,
                onClick = { flipped = !flipped }
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (flipped) ActiveLanguage.label(activeLang) else "Hindi", style = MaterialTheme.typography.labelMedium, color = VachakColors.TextSecondary, letterSpacing = 1.sp, maxLines = 1)
                    Text(if (flipped) card.back else card.front, style = MaterialTheme.typography.headlineSmall, color = VachakColors.TextPrimary, fontWeight = FontWeight.Bold)
                    cardBitmap?.let { bmp ->
                        Image(bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 220.dp).clip(RoundedCornerShape(16.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                    }
                    Text(if (flipped) "Tap to see Hindi" else "Tap to flip → ${ActiveLanguage.label(activeLang)}", style = MaterialTheme.typography.bodySmall, color = VachakColors.DeepLavender)
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
