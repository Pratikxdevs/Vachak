package com.vachak.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vachak.engine.ActiveLanguage
import com.vachak.engine.EngineProvider
import com.vachak.engine.EngineResult
import com.vachak.engine.Lesson
import com.vachak.ui.components.BreadcrumbTrail
import com.vachak.ui.components.GuidedEmpty
import com.vachak.ui.components.InsetWell
import com.vachak.ui.theme.VachakColors
import com.vachak.ui.theme.cardShadow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Learn sub-page keys. Kept (with the mapper) so old tools deep links and
 *  the route-table tests keep resolving — the Library tab itself is gone,
 *  merged into Learn. */
enum class ToolsPanel { Hub, Worksheets, Flashcards, Saved }

/** Maps a sub-page segment (worksheets/flashcards/saved) to its panel. */
fun toolsPanelFromRoute(segment: String): ToolsPanel = when (segment) {
    "worksheets" -> ToolsPanel.Worksheets
    "flashcards" -> ToolsPanel.Flashcards
    "saved" -> ToolsPanel.Saved
    else -> ToolsPanel.Hub
}

/** Library loader shared by the panes: real lessons, no silent first-lesson binding. */
private suspend fun loadLibraryLessons(engine: EngineProvider): List<Lesson> = withContext(Dispatchers.IO) {
    val ce = engine.curriculum as? com.vachak.content.ContentEngine
    val res = ce?.getLessons() ?: engine.curriculum.listLessons(0).let { r ->
        when (r) {
            is EngineResult.Ok -> EngineResult.Ok(r.value.map { ref -> Lesson(ref.id, ref.title, ref.grade, "", "", true, ref.domain) })
            is EngineResult.Err -> r as EngineResult<List<Lesson>>
        }
    }
    (res as? EngineResult.Ok)?.value.orEmpty()
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
                Text("Chapter not on this device — install the content pack (More → Packs).", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
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
                Text("Chapter not on this device — install the content pack (More → Packs).", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
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

/** Learn → Worksheets sub-page. Self-loads the library when opened directly
 *  (deep link / redirect) instead of inheriting a caller-provided lesson. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorksheetPane(engine: EngineProvider, lesson: Lesson?, allLessons: List<Lesson> = emptyList(), onBack: () -> Unit) {
    // Real lesson picker — the pane never silently binds the first lesson.
    // selId initialises once (not on every lessons-list reload) so switching
    // lessons always resets the generated output — no stale "same sheet".
    var selId by remember { mutableStateOf(lesson?.id ?: allLessons.firstOrNull()?.id) }
    // Direct entry (no caller lessons): load the real library once.
    var libLessons by remember { mutableStateOf(allLessons) }
    LaunchedEffect(Unit) { if (libLessons.isEmpty()) libLessons = loadLibraryLessons(engine) }
    val library = libLessons
    LaunchedEffect(lesson?.id) { if (lesson != null) selId = lesson.id }
    LaunchedEffect(library.firstOrNull()?.id) { if (selId == null) selId = library.firstOrNull()?.id }
    val activeLesson = library.firstOrNull { it.id == selId } ?: lesson
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
        // sub-header with back + breadcrumb (Learn / Worksheets)
        Surface(color = Color.White, shadowElevation = 0.dp, tonalElevation = 0.dp) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to Learn", modifier = Modifier.size(20.dp)) }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BreadcrumbTrail(listOf("Learn", "Worksheets"))
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
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(color = VachakColors.Lavender600)
                    Text("Loading lessons…", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                }
            }
        } else {
            Column(modifier = Modifier.weight(1f).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (library.size > 1) {
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
                            library.forEach { l ->
                                DropdownMenuItem(
                                    text = { Text("Grade ${l.grade} • ${l.title}") },
                                    onClick = { selId = l.id; pickerOpen = false }
                                )
                            }
                        }
                    }
                }
                Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth().cardShadow()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(lesson.title, style = MaterialTheme.typography.titleSmall, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("Grade ${lesson.grade} • Lesson ${lesson.id}", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary, fontFamily = FontFamily.Monospace)
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
                            label = { Text("$n", fontFamily = FontFamily.Monospace) }
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
                // Offline PDF: renders the generated template to filesDir and
                // opens it in an installed viewer.
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
                    InsetWell {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = VachakColors.TextPrimary)
                    }
                }
            }
        }
    }
}

/** Learn → Saved sub-page: offline PDFs + recent voice translations. */
@Composable
internal fun SavedPane(onBack: () -> Unit, onOpenWorksheets: () -> Unit = {}, onOpenLive: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var pdfs by remember { mutableStateOf<List<java.io.File>>(emptyList()) }
    var notice by remember { mutableStateOf<String?>(null) }
    fun refresh() { pdfs = com.vachak.ui.pdf.WorksheetPdf.list(context) }
    LaunchedEffect(Unit) { refresh() }
    val recentVoice = LiveConversationStore.items.takeLast(5).reversed()

    Column(modifier = Modifier.fillMaxSize().background(VachakColors.Background)) {
        Surface(color = Color.White, shadowElevation = 0.dp, tonalElevation = 0.dp) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to Learn", modifier = Modifier.size(20.dp)) }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BreadcrumbTrail(listOf("Learn", "Saved"))
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
                Text("Worksheet PDFs (${pdfs.size})", style = MaterialTheme.typography.titleSmall, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
            }
            if (pdfs.isEmpty()) {
                item {
                    GuidedEmpty(
                        icon = Icons.Outlined.Description,
                        title = "No saved worksheets yet",
                        why = "Generate one from any lesson — it saves here as an offline PDF.",
                        actionLabel = "New worksheet",
                        onAction = onOpenWorksheets
                    )
                }
            } else {
                items(pdfs, key = { it.absolutePath }) { f ->
                    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth().cardShadow(RoundedCornerShape(16.dp))) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Outlined.PictureAsPdf, null, tint = VachakColors.DeepLavender, modifier = Modifier.size(20.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(f.nameWithoutExtension, style = MaterialTheme.typography.bodyMedium, color = VachakColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${f.length() / 1024} KB", style = MaterialTheme.typography.labelSmall, color = VachakColors.TextSecondary, fontFamily = FontFamily.Monospace)
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
                Text("Recent voice translations (${recentVoice.size})", style = MaterialTheme.typography.titleSmall, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
            }
            if (recentVoice.isEmpty()) {
                item {
                    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Nothing spoken yet.", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary, modifier = Modifier.weight(1f))
                            TextButton(onClick = onOpenLive) { Text("Open Translate") }
                        }
                    }
                }
            } else {
                items(recentVoice, key = { it.id }) { item ->
                    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth().cardShadow(RoundedCornerShape(16.dp))) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(item.hindiText, style = MaterialTheme.typography.bodyMedium, color = VachakColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(item.santaliText ?: item.error ?: "…", style = MaterialTheme.typography.bodySmall, color = VachakColors.DeepLavender, maxLines = 2, overflow = TextOverflow.Ellipsis)
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

/** Learn → Flashcards sub-page. Prebuilt decks, real PNG art, offline. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FlashcardPane(engine: EngineProvider, lesson: Lesson?, allLessons: List<Lesson> = emptyList(), onBack: () -> Unit) {
    var selId by remember { mutableStateOf(lesson?.id ?: allLessons.firstOrNull()?.id) }
    // Direct entry (no caller lessons): load the real library once.
    var libLessons by remember { mutableStateOf(allLessons) }
    LaunchedEffect(Unit) { if (libLessons.isEmpty()) libLessons = loadLibraryLessons(engine) }
    val library = libLessons
    LaunchedEffect(lesson?.id) { if (lesson != null) selId = lesson.id }
    LaunchedEffect(library.firstOrNull()?.id) { if (selId == null) selId = library.firstOrNull()?.id }
    val activeLesson = library.firstOrNull { it.id == selId } ?: lesson
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
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to Learn") }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BreadcrumbTrail(listOf("Learn", "Flashcards"))
                    Text("Flashcards", style = MaterialTheme.typography.titleMedium, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text("${ActiveLanguage.label(activeLang)} • Prebuilt assets • Offline", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
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
                GuidedEmpty(
                    icon = Icons.Outlined.Style,
                    title = "No flashcards for this lesson yet",
                    why = "Decks ship inside content packs — open a packed lesson or pick another one.",
                    actionLabel = "Back to Learn",
                    onAction = onBack
                )
            }
            return
        }
        val card = cards[idx.coerceIn(cards.indices)]
        // Real prebuilt art from APK assets; missing art renders
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
        if (library.size > 1) {
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
                    library.forEach { l ->
                        DropdownMenuItem(
                            text = { Text("Grade ${l.grade} • ${l.title}") },
                            onClick = { selId = l.id; pickerOpen = false }
                        )
                    }
                }
            }
        }
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp).cardShadow(RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border),
                tonalElevation = 0.dp,
                onClick = { flipped = !flipped }
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (flipped) ActiveLanguage.label(activeLang) else "Hindi", style = MaterialTheme.typography.labelMedium, color = VachakColors.TextSecondary, letterSpacing = 1.sp, maxLines = 1)
                    Text(if (flipped) card.back else card.front, style = MaterialTheme.typography.headlineSmall, color = VachakColors.TextPrimary, fontWeight = FontWeight.Bold)
                    cardBitmap?.let { bmp ->
                        Image(
                            bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 220.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, Color.Black.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                    }
                    Text(if (flipped) "Tap to see Hindi" else "Tap to flip → ${ActiveLanguage.label(activeLang)}", style = MaterialTheme.typography.bodySmall, color = VachakColors.DeepLavender)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { if (idx > 0) { idx--; flipped = false } }, enabled = idx > 0, shape = RoundedCornerShape(50)) { Text("Previous") }
                Text("${idx + 1} / ${cards.size}", style = MaterialTheme.typography.labelMedium, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                Button(onClick = { if (idx < cards.lastIndex) { idx++; flipped = false } }, enabled = idx < cards.lastIndex, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = VachakColors.PrimaryDark, contentColor = Color.White)) { Text("Next") }
            }
            FilledTonalButton(onClick = { flipped = !flipped }, shape = RoundedCornerShape(50)) { Text(if (flipped) "Show Hindi" else "Show ${ActiveLanguage.label(activeLang)}") }
        }
    }
}
