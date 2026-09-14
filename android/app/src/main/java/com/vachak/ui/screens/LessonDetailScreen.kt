package com.vachak.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vachak.engine.ActiveLanguage
import com.vachak.engine.EngineProvider
import com.vachak.engine.EngineResult
import com.vachak.engine.FlashcardDeck
import com.vachak.engine.Lesson
import com.vachak.engine.Outcome
import com.vachak.engine.QuizEngine
import com.vachak.ui.theme.VachakColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class DetailTab { Teach, Practice, Check }
private enum class PracticeSection { Activity, Flashcards, Worksheet }
private enum class CheckSection { Assessment, Quiz }

/**
 * Lesson detail (mock grid #4-6): precomputed Hindi + Santali texts with
 * playback, NIPUN-mapped outcomes, and working Flashcards / Worksheet / Quiz
 * tabs bound to this lesson. Everything offline.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonDetailScreen(
    engine: EngineProvider,
    lessonId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember(context) { com.vachak.ui.prefs.VachakPrefs(context) }
    val scope = rememberCoroutineScope()
    val activeLang by engine.activeLanguage.collectAsState()

    var lesson by remember(lessonId) { mutableStateOf<Lesson?>(null) }
    var outcomes by remember(lessonId) { mutableStateOf<List<Outcome>>(emptyList()) }
    var activities by remember(lessonId) { mutableStateOf<List<com.vachak.engine.Activity>>(emptyList()) }
    var assessments by remember(lessonId) { mutableStateOf<List<com.vachak.engine.AssessmentPrompt>>(emptyList()) }
    var deck by remember(lessonId) { mutableStateOf<FlashcardDeck?>(null) }
    var loading by remember(lessonId) { mutableStateOf(true) }
    var loadError by remember(lessonId) { mutableStateOf<String?>(null) }
    var tab by remember(lessonId) { mutableStateOf(DetailTab.Teach) }
    var practiceSel by remember(lessonId) { mutableStateOf(PracticeSection.Activity) }
    var checkSel by remember(lessonId) { mutableStateOf(CheckSection.Assessment) }
    var done by remember(lessonId) { mutableStateOf(prefs.isLessonComplete(lessonId)) }
    var playMsg by remember(lessonId) { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf<String?>(null) }
    // Reading script: Hindi (Deva) / EN (romanized gloss — pack ships no
    // English text) / Santali (Ol Chiki). Same toggle on every reading page.
    var script by remember(lessonId) { mutableStateOf(com.vachak.ui.components.ScriptChoice.HI) }

    suspend fun load() {
        loading = true
        loadError = null
        withContext(Dispatchers.IO) {
            val ce = engine.curriculum as? com.vachak.content.ContentEngine
            val l = ce?.getLesson(lessonId) ?: engine.curriculum.getLesson(lessonId).let {
                when (it) {
                    is EngineResult.Ok -> EngineResult.Ok(it.value)
                    is EngineResult.Err -> it
                }
            }
            val o = ce?.getOutcomes(lessonId) ?: engine.curriculum.getOutcomes(lessonId).let {
                when (it) {
                    is EngineResult.Ok -> EngineResult.Ok(it.value)
                    is EngineResult.Err -> it
                }
            }
            val d = engine.flashcard.listDeck(lessonId)
            // Room queries must stay off the main thread (ContentEngine uses
            // blocking DAO calls) — fetch here on IO, publish below on Main.
            val acts = engine.curriculum.getActivities(lessonId)
            val asses = engine.curriculum.getAssessments(lessonId)
            withContext(Dispatchers.Main) {
                when (l) {
                    is EngineResult.Ok -> {
                        lesson = l.value
                        prefs.recordViewed(lessonId)
                    }
                    is EngineResult.Err -> loadError = l.message
                }
                if (o is EngineResult.Ok) outcomes = o.value
                if (acts is EngineResult.Ok) activities = acts.value
                if (asses is EngineResult.Ok) assessments = asses.value
                if (d is EngineResult.Ok) deck = d.value
                loading = false
            }
        }
    }
    LaunchedEffect(lessonId) { load() }

    fun speak(text: String, lang: String, tag: String) {
        if (playing != null) return
        playing = tag
        playMsg = null
        scope.launch(Dispatchers.IO) {
            val msg = com.vachak.ui.audio.TtsPlayer.play(engine, text, lang)
            withContext(Dispatchers.Main) {
                playMsg = msg
                playing = null
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().background(VachakColors.Background)) {
        // Header
        Surface(color = Color.White, shadowElevation = 0.dp, tonalElevation = 0.dp) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = VachakColors.TextPrimary) }
                Column(modifier = Modifier.weight(1f)) {
                    Text(lesson?.title ?: "Lesson", style = MaterialTheme.typography.titleMedium, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(
                        lesson?.let { "Grade ${it.grade} • ${ActiveLanguage.label(activeLang)}" } ?: "Loading…",
                        style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary, maxLines = 1
                    )
                }
                if (done) {
                    Surface(shape = RoundedCornerShape(50), color = VachakColors.SuccessLight) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Outlined.CheckCircle, null, tint = VachakColors.Success, modifier = Modifier.size(14.dp))
                            Text("Done", style = MaterialTheme.typography.labelSmall, color = VachakColors.Success, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        // Tabs
        ScrollableTabRow(
            selectedTabIndex = tab.ordinal,
            containerColor = Color.White,
            contentColor = VachakColors.DeepLavender,
            edgePadding = 16.dp
        ) {
            DetailTab.values().forEach { t ->
                Tab(
                    selected = tab == t,
                    onClick = { tab = t },
                    text = { Text(t.name, fontWeight = if (tab == t) FontWeight.SemiBold else FontWeight.Normal) }
            )
            }
        }

        when (tab) {
            DetailTab.Teach -> LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (loading) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = VachakColors.Lavender600)
                        }
                    }
                }
                loadError?.let { msg ->
                    item {
                        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Couldn't load lesson", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                                Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                OutlinedButton(onClick = { scope.launch { load() } }, shape = RoundedCornerShape(50)) { Text("Try Again") }
                            }
                        }
                    }
                }
                lesson?.let { l ->
                    item {
                        com.vachak.ui.components.ScriptToggle(selected = script, onSelect = { script = it })
                    }
                    item {
                        Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                when (script) {
                                    com.vachak.ui.components.ScriptChoice.HI -> {
                                        Text("Hindi • पाठ", style = MaterialTheme.typography.labelSmall, color = VachakColors.Lavender600, fontWeight = FontWeight.SemiBold)
                                        Text(l.sourceTextHi.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge, color = VachakColors.TextPrimary, fontSize = 18.sp, lineHeight = 26.sp)
                                        TextButton(
                                            onClick = { speak(l.sourceTextHi, "hi", "hi") },
                                            enabled = playing == null && l.sourceTextHi.isNotBlank(),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            if (playing == "hi") CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                            else Icon(Icons.AutoMirrored.Outlined.VolumeUp, null, tint = VachakColors.DeepLavender, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(if (playing == "hi") "Playing…" else "Play Hindi", style = MaterialTheme.typography.labelMedium, color = VachakColors.DeepLavender)
                                        }
                                    }
                                    com.vachak.ui.components.ScriptChoice.SAT -> {
                                        Text("${ActiveLanguage.label(activeLang)} • precomputed", style = MaterialTheme.typography.labelSmall, color = VachakColors.Lavender600, fontWeight = FontWeight.SemiBold)
                                        Text(l.translatedText.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge, color = VachakColors.TextPrimary, fontSize = 18.sp, lineHeight = 26.sp)
                                        TextButton(
                                            onClick = { speak(l.translatedText, activeLang, "sat") },
                                            enabled = playing == null && l.translatedText.isNotBlank(),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            if (playing == "sat") CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                            else Icon(Icons.AutoMirrored.Outlined.VolumeUp, null, tint = VachakColors.DeepLavender, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(if (playing == "sat") "Playing…" else "Play ${ActiveLanguage.label(activeLang)}", style = MaterialTheme.typography.labelMedium, color = VachakColors.DeepLavender)
                                        }
                                    }
                                    com.vachak.ui.components.ScriptChoice.EN -> {
                                        Text("Sounds like • romanized gloss (not a translation)", style = MaterialTheme.typography.labelSmall, color = VachakColors.Lavender600, fontWeight = FontWeight.SemiBold)
                                        val glossHi = remember(l.sourceTextHi) { com.vachak.ui.text.Romanize.devanagari(l.sourceTextHi) }
                                        val glossSat = remember(l.translatedText) { com.vachak.ui.text.Romanize.auto(l.translatedText) }
                                        if (glossHi.isNotBlank()) Text(glossHi, style = MaterialTheme.typography.bodyLarge, color = VachakColors.TextPrimary, fontSize = 18.sp, lineHeight = 26.sp)
                                        if (glossSat.isNotBlank() && glossSat != glossHi) {
                                            HorizontalDivider(color = VachakColors.Border.copy(alpha = 0.6f))
                                            Text(glossSat, style = MaterialTheme.typography.bodyLarge, color = VachakColors.DeepLavender, fontSize = 18.sp, lineHeight = 26.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    playMsg?.let { msg ->
                        item {
                            Text(msg, style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                        }
                    }
                    if (outcomes.isNotEmpty()) {
                        item {
                            Text("Learning outcomes (NIPUN)", style = MaterialTheme.typography.titleSmall, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                        }
                        outcomes.forEach { oc ->
                            item {
                                Surface(shape = RoundedCornerShape(16.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth()) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Icon(
                                            if (oc.nipunMapped) Icons.Outlined.Verified else Icons.Outlined.Circle,
                                            null,
                                            tint = if (oc.nipunMapped) VachakColors.Success else VachakColors.TextSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(oc.description, style = MaterialTheme.typography.bodyMedium, color = VachakColors.TextPrimary, modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Button(
                            onClick = {
                                prefs.markLessonComplete(l.id)
                                done = true
                            },
                            enabled = !done,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = VachakColors.PrimaryDark, contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Icon(Icons.Outlined.CheckCircle, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (done) "Completed ✓" else "Mark Complete")
                        }
                    }
                }
            }
            DetailTab.Practice -> Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PracticeSection.values().forEach { s ->
                        FilterChip(
                            selected = practiceSel == s,
                            onClick = { practiceSel = s },
                            label = { Text(s.name) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                when (practiceSel) {
                    PracticeSection.Activity -> ActivityPane(
                        activities = activities,
                        loading = loading,
                        activeLang = activeLang,
                        onSpeak = { text, lang, tag -> speak(text, lang, tag) },
                        speakingTag = playing,
                        speakNote = playMsg
                    )
                    PracticeSection.Flashcards -> FlashcardPane(engine, lesson, onBack = { tab = DetailTab.Teach })
                    PracticeSection.Worksheet -> WorksheetPane(engine, lesson, onBack = { tab = DetailTab.Teach })
                }
            }
            DetailTab.Check -> Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CheckSection.values().forEach { s ->
                        FilterChip(
                            selected = checkSel == s,
                            onClick = { checkSel = s },
                            label = { Text(s.name) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                when (checkSel) {
                    CheckSection.Assessment -> AssessmentPane(
                        assessments = assessments,
                        loading = loading,
                        activeLang = activeLang,
                        onSpeak = { text, lang, tag -> speak(text, lang, tag) },
                        speakingTag = playing,
                        speakNote = playMsg
                    )
                    CheckSection.Quiz -> QuizPane(deck = deck, loading = loading)
                }
            }
        }
    }
}

@Composable
private fun ActivityPane(
    activities: List<com.vachak.engine.Activity>,
    loading: Boolean,
    activeLang: String,
    onSpeak: (String, String, String) -> Unit,
    speakingTag: String?,
    speakNote: String?
) {
    if (loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = VachakColors.Lavender600)
        }
        return
    }
    if (activities.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("No activities authored for this lesson yet.", style = MaterialTheme.typography.bodyMedium, color = VachakColors.TextSecondary)
                Text("Activities ship with the book-sourced curriculum manifest (docs/curriculum.md §12).", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
            }
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        activities.forEachIndexed { idx, a ->
            item(key = a.id) {
                Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Activity ${idx + 1} • ${a.titleHi}", style = MaterialTheme.typography.titleSmall, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                        if (a.materials.isNotBlank()) {
                            Text("Materials: ${a.materials}", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                        }
                        Text(a.instructionHi, style = MaterialTheme.typography.bodyMedium, color = VachakColors.TextPrimary)
                        if (a.instructionSat.isNotBlank()) {
                            HorizontalDivider(color = VachakColors.Border.copy(alpha = 0.6f))
                            Text(a.instructionSat, style = MaterialTheme.typography.bodyMedium, color = VachakColors.DeepLavender)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = { onSpeak(a.instructionHi, "hi", "act-hi-$idx") },
                                enabled = speakingTag == null && a.instructionHi.isNotBlank(),
                                contentPadding = PaddingValues(0.dp)
                            ) { Text(if (speakingTag == "act-hi-$idx") "Playing…" else "Play Hindi", style = MaterialTheme.typography.labelMedium, color = VachakColors.DeepLavender) }
                            TextButton(
                                onClick = { onSpeak(a.instructionSat, activeLang, "act-sat-$idx") },
                                enabled = speakingTag == null && a.instructionSat.isNotBlank(),
                                contentPadding = PaddingValues(0.dp)
                            ) { Text(if (speakingTag == "act-sat-$idx") "Playing…" else "Play ${ActiveLanguage.label(activeLang)}", style = MaterialTheme.typography.labelMedium, color = VachakColors.DeepLavender) }
                        }
                    }
                }
            }
        }
        speakNote?.let {
            item { Text(it, style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary) }
        }
    }
}

@Composable
private fun AssessmentPane(
    assessments: List<com.vachak.engine.AssessmentPrompt>,
    loading: Boolean,
    activeLang: String,
    onSpeak: (String, String, String) -> Unit,
    speakingTag: String?,
    speakNote: String?
) {
    if (loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = VachakColors.Lavender600)
        }
        return
    }
    if (assessments.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("No assessment prompts authored for this lesson yet.", style = MaterialTheme.typography.bodyMedium, color = VachakColors.TextSecondary)
                Text("Prompts ship with the book-sourced curriculum manifest (docs/curriculum.md §12).", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
            }
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        assessments.forEachIndexed { idx, a ->
            item(key = a.id) {
                Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Check ${idx + 1}", style = MaterialTheme.typography.labelSmall, color = VachakColors.Lavender600, fontWeight = FontWeight.SemiBold)
                        Text(a.promptHi, style = MaterialTheme.typography.titleSmall, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                        if (a.promptSat.isNotBlank()) {
                            Text(a.promptSat, style = MaterialTheme.typography.bodyMedium, color = VachakColors.DeepLavender)
                        }
                        if (a.expectedResponse.isNotBlank()) {
                            HorizontalDivider(color = VachakColors.Border.copy(alpha = 0.6f))
                            Text("Expected: ${a.expectedResponse}", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = { onSpeak(a.promptHi, "hi", "as-hi-$idx") },
                                enabled = speakingTag == null && a.promptHi.isNotBlank(),
                                contentPadding = PaddingValues(0.dp)
                            ) { Text(if (speakingTag == "as-hi-$idx") "Playing…" else "Ask aloud (Hindi)", style = MaterialTheme.typography.labelMedium, color = VachakColors.DeepLavender) }
                            TextButton(
                                onClick = { onSpeak(a.promptSat, activeLang, "as-sat-$idx") },
                                enabled = speakingTag == null && a.promptSat.isNotBlank(),
                                contentPadding = PaddingValues(0.dp)
                            ) { Text(if (speakingTag == "as-sat-$idx") "Playing…" else "Ask aloud (${ActiveLanguage.label(activeLang)})", style = MaterialTheme.typography.labelMedium, color = VachakColors.DeepLavender) }
                        }
                    }
                }
            }
        }
        speakNote?.let {
            item { Text(it, style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary) }
        }
    }
}

@Composable
private fun QuizPane(deck: FlashcardDeck?, loading: Boolean) {
    var quiz by remember(deck) { mutableStateOf(deck?.let { QuizEngine.build(it, count = 10, seed = System.currentTimeMillis()) }) }
    var answers by remember(deck) { mutableStateOf(mapOf<Int, Int>()) }
    var submitted by remember(deck) { mutableStateOf(false) }

    if (loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = VachakColors.Lavender600)
        }
        return
    }
    val q = quiz
    if (q == null || q.questions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("No quiz for this lesson yet — needs flashcards.", style = MaterialTheme.typography.bodyMedium, color = VachakColors.TextSecondary)
        }
        return
    }
    val (correct, total) = QuizEngine.grade(q, answers)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (submitted) {
            item {
                Surface(shape = RoundedCornerShape(20.dp), color = VachakColors.SoftLavender, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("$correct / $total", style = MaterialTheme.typography.headlineMedium, color = VachakColors.TextPrimary, fontWeight = FontWeight.Bold)
                        Text(
                            if (correct == total) "Perfect! 🎉" else "Good effort — retry to improve.",
                            style = MaterialTheme.typography.bodyMedium, color = VachakColors.TextSecondary
                        )
                        OutlinedButton(
                            onClick = {
                                quiz = deck?.let { QuizEngine.build(it, count = 10, seed = System.currentTimeMillis()) }
                                answers = emptyMap()
                                submitted = false
                            },
                            shape = RoundedCornerShape(50)
                        ) { Text("Retry Quiz") }
                    }
                }
            }
        }
        q.questions.forEachIndexed { idx, question ->
            item(key = "q-$idx") {
                Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Question ${idx + 1} of $total", style = MaterialTheme.typography.labelSmall, color = VachakColors.TextSecondary)
                        Text("What matches “${question.prompt}”?", style = MaterialTheme.typography.titleSmall, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                        question.options.forEachIndexed { oi, opt ->
                            val picked = answers[idx] == oi
                            val revealed = submitted
                            val isAnswer = oi == question.answerIndex
                            val container = when {
                                revealed && isAnswer -> VachakColors.SuccessLight
                                revealed && picked -> MaterialTheme.colorScheme.errorContainer
                                picked -> VachakColors.SoftLavender
                                else -> Color.White
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = container,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (picked || (revealed && isAnswer)) VachakColors.DeepLavender else VachakColors.Border
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { if (!submitted) answers = answers + (idx to oi) }
                            ) {
                                Text(opt, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium, color = VachakColors.TextPrimary)
                            }
                        }
                    }
                }
            }
        }
        if (!submitted) {
            item {
                Button(
                    onClick = { submitted = true },
                    enabled = answers.size == total,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = VachakColors.PrimaryDark, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Submit (${answers.size}/$total)") }
            }
        }
    }
}
