package com.vachak.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.vachak.engine.ActiveLanguage
import com.vachak.engine.EngineProvider
import com.vachak.engine.LanguagePair
import com.vachak.ml.VachakAudio
import com.vachak.ui.components.BreathVisualizer
import com.vachak.ui.components.ConversationMessagePair
import com.vachak.ui.components.LiveInputBar
import com.vachak.ui.components.LiveTranscriptionStrip
import com.vachak.ui.theme.VachakColors
import kotlinx.coroutines.*

private const val TAG_ASR = "Vachak-ASR"
private const val TAG_VAD = "Vachak-VAD"
private const val TAG_MT = "Vachak-MT"
private const val TAG_TTS = "Vachak-TTS"
private const val TAG_LAT = "Vachak-Latency"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScreen(
    engine: EngineProvider,
    modifier: Modifier = Modifier,
    viewModel: LiveViewModel = hiltViewModel()
) {
    val activeLang by engine.activeLanguage.collectAsState()
    // Single pipeline: LiveViewModel owns mic/ASR/MT/TTS sequentially; this
    // screen is a pure renderer of vmState plus permission/toggle chrome.
    // (The old screen-owned pipeline is retired — see LiveViewModel.)
    val vmState by viewModel.uiStateDirect.collectAsState()
    val vmMeterRms by viewModel.meterRms.collectAsState()
    var manualHindi by remember { mutableStateOf("") }
    var showDebugDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    DisposableEffect(Unit) {
        onDispose { viewModel.forceReleaseMic() }
    }
    val listState = rememberLazyListState()
    val conversation = LiveConversationStore.items

    // Live typed translation — SINGLE path: debounced hi→activeLang lives in
    // LiveViewModel (600ms + pipelineDispatcher + Mutex, sequential ASR->MT->TTS).
    // This screen only forwards keystrokes via onTextChange and renders
    // vmState.livePreview below. Do NOT add a second direct
    // engine.translation.translate() call here — the duplicate path doubled MT
    // load per keystroke and broke the never-parallel RAM guarantee.
    LaunchedEffect(manualHindi) {
        viewModel.onTextChange(manualHindi)
    }
    // Typed preview renders straight from vmState (single source of truth).

    var hasMicPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    // Tracks whether the system prompt was ever answered: distinguishes
    // first-launch (Allow button) from permanent denial (Settings button).
    var micAskedOnce by remember { mutableStateOf(hasMicPermission) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasMicPermission = isGranted
            micAskedOnce = true
            Log.d(TAG_ASR, "permission result isGranted=$isGranted")
        }
    )
    // Auto-request mic permission on first entry (emulator needs explicit prompt)
    LaunchedEffect(Unit) {
        if (!hasMicPermission) {
            Log.d(TAG_ASR, "Auto-requesting RECORD_AUDIO (initial hasMic=false)")
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    // Re-check permission on resume (user may grant in Settings and return)
    androidx.compose.runtime.DisposableEffect(context) {
        val lifecycle = (context as? androidx.lifecycle.LifecycleOwner)?.lifecycle
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (granted != hasMicPermission) {
                    hasMicPermission = granted
                    Log.d(TAG_ASR, "permission re-check onResume granted=$granted")
                }
            }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    // auto-scroll only on new message — no animation on mic open (was janky), instant scroll
    LaunchedEffect(conversation.size) {
        if (conversation.isNotEmpty()) {
            // Use scrollToItem (no animation) for 60fps; animateScroll is heavy on 2GB
            listState.scrollToItem(conversation.size - 1)
        }
    }

    // Voice pipeline retired to LiveViewModel (single sequential pipeline).
    val hasConversation by remember { derivedStateOf { conversation.isNotEmpty() } }

    Box(modifier = modifier.fillMaxSize().background(VachakColors.Background).imePadding()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header (live.md §5)
            Surface(color = Color.White, shadowElevation = 0.dp, tonalElevation = 0.dp) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Live Translation", style = MaterialTheme.typography.titleMedium, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text("Hindi → ${ActiveLanguage.label(activeLang)}", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(50), color = VachakColors.SuccessLight) {
                                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(VachakColors.Success))
                                    Text("Offline", style = MaterialTheme.typography.labelSmall, color = VachakColors.Success, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            IconButton(onClick = { /* History placeholder */ }, modifier = Modifier.size(44.dp)) {
                                Icon(Icons.Outlined.History, null, tint = VachakColors.TextPrimary)
                            }
                            IconButton(onClick = { showDebugDialog = true }, modifier = Modifier.size(44.dp)) {
                                Icon(Icons.Outlined.BugReport, null, tint = if (vmState.asrError != null || vmState.livePreviewError != null) MaterialTheme.colorScheme.error else VachakColors.TextPrimary)
                            }
                            IconButton(onClick = { showClearConfirm = true }, modifier = Modifier.size(44.dp)) {
                                Icon(Icons.Outlined.MoreVert, null, tint = VachakColors.TextPrimary)
                            }
                        }
                    }
                    // Target-language toggle: Santali (Ol Chiki) <-> Mundari.
                    // The ONLY place Live translation target is chosen — every
                    // translate/playPcm/retry call below uses activeLang, no hard-coded lang.
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Translate to:",
                            style = MaterialTheme.typography.labelMedium,
                            color = VachakColors.TextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        ActiveLanguage.all().forEach { (code, name) ->
                            val selected = ActiveLanguage.normalize(activeLang) == code
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    if (!selected && !vmState.isListening && !vmState.isStopping) {
                                        Log.d(TAG_MT, "Live toggle -> $code ($name)")
                                        ActiveLanguage.set(code)
                                        (engine.translation as? com.vachak.ml.adapter.AdapterTranslationEngine)?.setActiveLanguage(code)
                                    } else {
                                        Log.d(TAG_MT, "toggle ignored selected=$selected listening=${vmState.isListening} stopping=${vmState.isStopping}")
                                    }
                                },
                                enabled = !vmState.isListening && !vmState.isStopping,
                                label = {
                                    Text(
                                        name,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1
                                    )
                                },
                                leadingIcon = if (selected) {
                                    { Icon(Icons.Outlined.Check, null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }
                    if (!hasMicPermission) {
                        // Permanent denial ("Don't ask again") makes the system
                        // silently swallow requests — an "Allow" button that does
                        // nothing. Detect it and deep-link to Settings instead.
                        val activity = context as? android.app.Activity
                        val permanentlyDenied = micAskedOnce && activity != null &&
                            !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                                activity, Manifest.permission.RECORD_AUDIO
                            )
                        Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    if (permanentlyDenied) "Microphone blocked — enable it in Settings" else "Microphone permission needed",
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.weight(1f))
                                if (permanentlyDenied && activity != null) {
                                    FilledTonalButton(onClick = {
                                        Log.d(TAG_ASR, "opening app Settings for mic permission")
                                        val intent = android.content.Intent(
                                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            android.net.Uri.fromParts("package", context.packageName, null)
                                        )
                                        activity.startActivity(intent)
                                    }) { Text("Open Settings") }
                                } else {
                                    FilledTonalButton(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Allow") }
                                }
                            }
                        }
                    }
                }
            }

            ModelStatusRow()
            // Live mic meter — proves the mic hears the user while listening.
            if (vmState.isListening) {
                MicMeter(rms = vmMeterRms)
            }
            VoiceArea(
                hasConversation = hasConversation,
                isListening = vmState.isListening,
                isTranslating = vmState.isTranslating,
                isStopping = vmState.isStopping,
                partialText = vmState.partialText,
                hasMicPermission = hasMicPermission,
                activeLang = activeLang,
                onStop = { viewModel.onStop() },
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
            )
            // Mic action button: sibling of VoiceArea (NOT nested in its capped
            // column). Stop path stays enabled while draining so the mic can
            // never look stuck; isStopping taps are ignored inside onStop.
            // Sequential-RAM rule: never capture while translating — narrate
            // the tap instead of swallowing it.
            val guardedStart: () -> Unit = {
                if (vmState.isTranslating) {
                    scope.launch(Dispatchers.Main) {
                        snackbarHostState.showSnackbar("Finishing translation — tap mic again in a moment")
                    }
                } else {
                    viewModel.onStart()
                }
            }
            MicActionButton(
                isListening = vmState.isListening,
                isStopping = vmState.isStopping,
                isTranslating = vmState.isTranslating,
                hasMicPermission = hasMicPermission,
                activeLang = activeLang,
                onStart = guardedStart,
                onStop = { viewModel.onStop() },
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
            )
            if (vmState.asrError != null) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), shape = RoundedCornerShape(12.dp)) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Text(vmState.asrError ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.dismissErrors() }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Dismiss", style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
            HorizontalDivider(color = VachakColors.Border, thickness = 0.8.dp)

            // Conversation (live.md §4 — primary content, LazyColumn)
            if (!hasConversation && !vmState.isListening) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Your translations will appear here.", style = MaterialTheme.typography.bodyLarge, color = VachakColors.TextSecondary)
                        OutlinedButton(onClick = {}, shape = RoundedCornerShape(50), enabled = false) { Text("Type in Hindi") }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (hasConversation) {
                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Conversation", style = MaterialTheme.typography.titleSmall, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                                TextButton(onClick = { showClearConfirm = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                    Text("Clear", style = MaterialTheme.typography.labelMedium, color = VachakColors.TextSecondary)
                                }
                            }
                        }
                    }
                    items(conversation, key = { it.id }) { item ->
                        ConversationMessagePair(
                            item = item,
                            onPlayHindi = { viewModel.playText(item.hindiText, "hi") },
                            onPlaySantali = { item.santaliText?.let { viewModel.playText(it, activeLang) } },
                            targetLabel = ActiveLanguage.label(activeLang),
                            onRetry = { viewModel.retryItem(item.id, item.hindiText) }
                        )
                    }
                    item {
                        InlineTranscription(vmState.partialText, vmState.isListening)
                    }
                    if (vmState.latencyMs != null || vmState.ttsMessage != null) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                vmState.latencyMs?.let { ms ->
                                    val within = ms < 3000
                                    Surface(shape = RoundedCornerShape(50), color = if (within) VachakColors.SuccessLight else MaterialTheme.colorScheme.errorContainer) {
                                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(if (within) "✓" else "⚠", style = MaterialTheme.typography.labelSmall, color = if (within) VachakColors.Success else MaterialTheme.colorScheme.error)
                                            Text("${ms}ms ${if (within) "< 3s" else "≥ 3s"}", style = MaterialTheme.typography.labelSmall, color = if (within) VachakColors.Success else MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                                vmState.ttsMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary) }
                            }
                        }
                    }
                }
            }

            // Live typing preview — shows active-target text as you type (fixed BPE+past KV, not garbage)
            if (manualHindi.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${ActiveLanguage.label(activeLang)} · Live", style = MaterialTheme.typography.labelSmall, color = VachakColors.Lavender600, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
                            if (vmState.livePreviewLoading) CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = VachakColors.Lavender600)
                        }
                        when {
                            vmState.livePreviewLoading -> Text("Translating…", style = MaterialTheme.typography.bodyMedium, color = VachakColors.TextSecondary)
                            vmState.livePreviewError != null -> Text(vmState.livePreviewError ?: "Error", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            vmState.livePreview != null -> Text(vmState.livePreview!!, style = MaterialTheme.typography.bodyLarge, color = VachakColors.TextPrimary, fontSize = 18.sp, lineHeight = 26.sp)
                            else -> Text("type Hindi to see ${ActiveLanguage.label(activeLang)}", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                        }
                        vmState.livePreview?.let { sat ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { viewModel.playText(sat, activeLang) }, contentPadding = PaddingValues(0.dp)) {
                                    Icon(Icons.AutoMirrored.Outlined.VolumeUp, null, tint = VachakColors.DeepLavender, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp)); Text("Play", style = MaterialTheme.typography.labelMedium, color = VachakColors.DeepLavender)
                                }
                                TextButton(onClick = { val t = manualHindi.trim(); if (t.isNotBlank()) { viewModel.commitTyped(t); manualHindi = "" } }, contentPadding = PaddingValues(0.dp)) {
                                    Text("Add to conversation", style = MaterialTheme.typography.labelMedium, color = VachakColors.Lavender600)
                                }
                            }
                        }
                    }
                }
            }

            // Input bar — typed Hindi goes through the same VM pipeline (commitTyped).
            LiveInputBar(
                text = manualHindi,
                onTextChange = { manualHindi = it },
                onTranslate = {
                    val t = manualHindi.trim()
                    if (t.isNotBlank()) { viewModel.commitTyped(t); manualHindi = "" }
                },
                enabled = !vmState.isListening,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).navigationBarsPadding()
            )
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear this conversation?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { LiveConversationStore.clear(); showClearConfirm = false }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }
    if (showDebugDialog) {
        AlertDialog(
            onDismissRequest = { showDebugDialog = false },
            title = { Text("Pipeline Debug") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ASR: ${vmState.asrError ?: "OK — no error (VAD active)"}", style = MaterialTheme.typography.bodySmall, color = if (vmState.asrError != null) MaterialTheme.colorScheme.error else VachakColors.TextSecondary)
                    Text("MT live: ${vmState.livePreviewError ?: vmState.livePreview ?: "idle — type Hindi or speak"}", style = MaterialTheme.typography.bodySmall, color = if (vmState.livePreviewError != null) MaterialTheme.colorScheme.error else VachakColors.TextSecondary)
                    Text("MT conv: ${conversation.lastOrNull()?.error ?: conversation.lastOrNull()?.santaliText?.take(30) ?: "no conv"}", style = MaterialTheme.typography.bodySmall)
                    Text("TTS: ${vmState.ttsMessage ?: "idle"}", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                    Text("States: listening=${vmState.isListening} stopping=${vmState.isStopping} translating=${vmState.isTranslating} conv=${conversation.size}", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                    Text("Latency: ${vmState.latencyMs?.let { "$it ms ${if (it < 3000) "< 3s ✓" else "≥ 3s ⚠"}" } ?: "not measured"}", style = MaterialTheme.typography.bodySmall, color = if (vmState.latencyMs != null && vmState.latencyMs!! < 3000) VachakColors.Success else MaterialTheme.colorScheme.error)
                    Text("ABI: ${android.os.Build.SUPPORTED_ABIS.joinToString()}", style = MaterialTheme.typography.labelSmall, color = VachakColors.TextSecondary)
                    Text("Logcat: adb logcat -s Vachak-MT:V Vachak-ASR:V Vachak-VAD:V Vachak-TTS:V Vachak-Latency:V", style = MaterialTheme.typography.labelSmall, color = VachakColors.TextSecondary)
                    Text("Pipeline: App input → preprocess→BPE(245k)→encoder[1,seq,512]→decoder past KV→postprocess→OlChiki U+1C50", style = MaterialTheme.typography.labelSmall, color = VachakColors.TextSecondary)
                }
            },
            confirmButton = { TextButton(onClick = { showDebugDialog = false }) { Text("Close") } },
            dismissButton = { TextButton(onClick = { viewModel.dismissErrors(); showDebugDialog = false }) { Text("Clear errors") } }
        )
    }
}

/**
 * Always-visible model health strip: ASR / MT / TTS dots driven by ModelStatus.
 * Green = live, amber = ready-but-limited (placeholder voice) or warming,
 * red = failed (detail carries the cause). The models behind a red dot are
 * exactly what the next pipeline failure will blame — no more guessing.
 */
@Composable
private fun ModelStatusRow() {
    val asr by com.vachak.ml.ModelStatus.asr.collectAsState()
    val mt by com.vachak.ml.ModelStatus.mt.collectAsState()
    val tts by com.vachak.ml.ModelStatus.tts.collectAsState()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModelDot(asr, "ASR")
        ModelDot(mt, "MT")
        ModelDot(tts, "TTS")
        val worst = listOf(asr, mt, tts).firstOrNull { it.state == com.vachak.ml.ModelState.ERROR }
        if (worst != null) {
            Text(
                worst.detail?.take(60) ?: "model error",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        } else {
            val loading = listOf(asr, mt, tts).any { it.state == com.vachak.ml.ModelState.LOADING || it.state == com.vachak.ml.ModelState.IDLE }
            if (loading) {
                Text(
                    "Loading models…",
                    style = MaterialTheme.typography.labelSmall,
                    color = VachakColors.TextSecondary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ModelDot(info: com.vachak.ml.ModelInfo, label: String) {
    val color = when (info.state) {
        com.vachak.ml.ModelState.READY ->
            if (info.degraded) VachakColors.Amber else VachakColors.Success
        com.vachak.ml.ModelState.ERROR -> MaterialTheme.colorScheme.error
        com.vachak.ml.ModelState.LOADING -> VachakColors.Lavender600
        com.vachak.ml.ModelState.IDLE -> VachakColors.Border
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = VachakColors.TextSecondary)
        info.loadMs?.let {
            Text("${it}ms", style = MaterialTheme.typography.labelSmall, color = VachakColors.TextSecondary)
        }
    }
}

@Composable
private fun MicActionButton(
    isListening: Boolean,
    isStopping: Boolean,
    isTranslating: Boolean,
    hasMicPermission: Boolean,
    activeLang: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRequestPermission: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val isButtonEnabled = if (isListening || isStopping) true else !isTranslating
    Button(
        onClick = {
            if (!isButtonEnabled) return@Button
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            if (!hasMicPermission) {
                Log.d(TAG_ASR, "mic permission missing, requesting")
                onRequestPermission()
            } else if (isListening || isStopping) {
                Log.d(TAG_ASR, "Stop tapped isListening=$isListening isStopping=$isStopping")
                onStop()
            } else {
                Log.d(TAG_ASR, "Start tapped hasPermission=$hasMicPermission lang=$activeLang")
                onStart()
            }
        },
        enabled = isButtonEnabled,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isListening || isStopping) MaterialTheme.colorScheme.error else VachakColors.PrimaryDark,
            contentColor = Color.White,
            disabledContainerColor = Color.Gray.copy(alpha = 0.2f)
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(44.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        if (isStopping && !isListening) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
        } else {
            Icon(if (isListening) Icons.Filled.Stop else Icons.Filled.Mic, null, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(6.dp))
        Text(
            if (isListening) "Stop" else if (isStopping) "Stopping…" else "Tap to speak",
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1
        )
    }
}

@Composable
private fun MicMeter(rms: Float) {
    // -50dB floor .. 0dB ceiling mapped to 0..1; speech gate marked in text.
    val db = if (rms <= 0f) -50f else (20 * kotlin.math.log10(rms)).coerceIn(-50f, 0f)
    val fraction = ((db + 50f) / 50f).coerceIn(0f, 1f)
    val hearing = rms > VachakAudio.VAD_RMS_THRESHOLD
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LinearProgressIndicator(
            progress = fraction,
            modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(50)),
            color = if (hearing) VachakColors.Success else VachakColors.TextSecondary,
            trackColor = VachakColors.Border
        )
        Text(
            if (hearing) "Hearing you ✓" else "Can't hear you…",
            style = MaterialTheme.typography.labelSmall,
            color = if (hearing) VachakColors.Success else VachakColors.TextSecondary,
            maxLines = 1
        )
    }
}

@Composable
private fun InlineTranscription(
    text: String,
    isListening: Boolean
) {
    if (isListening && text.isNotBlank()) {
        LiveTranscriptionStrip(text = text, isListening = true)
    }
}

@Composable
private fun VoiceArea(
    hasConversation: Boolean,
    isListening: Boolean,
    isTranslating: Boolean,
    isStopping: Boolean,
    partialText: String,
    hasMicPermission: Boolean,
    activeLang: String,
    onStop: () -> Unit,
    onRequestPermission: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Log.d(TAG_ASR, "VoiceArea recompose isListening=$isListening isStopping=$isStopping isTranslating=$isTranslating hasMic=$hasMicPermission")
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        // Max fits visual + strip + action button: below ~300dp in listening
        // state the Stop button measured 0x0 (invisible Stop) on dense screens.
        // Idle keeps the compact cap; listening gets room for strip + button.
        val cap = if (isListening) 340.dp else 220.dp
        val voiceHeight = (maxHeight * if (!hasConversation && !isListening) 0.40f else 0.28f).coerceIn(120.dp, cap)
        val micVisual = if (maxWidth < 360.dp) 72.dp else 96.dp
        val micInner = if (maxWidth < 360.dp) 48.dp else 56.dp
        val iconSize = if (maxWidth < 360.dp) 20.dp else 24.dp
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = voiceHeight)) {
            if (!hasConversation && !isListening) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(micVisual)) {
                    BreathVisualizer(isListening = false, modifier = Modifier.size(micVisual))
                    Surface(shape = CircleShape, color = VachakColors.DeepLavender, modifier = Modifier.size(micInner)) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.Filled.Mic, null, tint = Color.White, modifier = Modifier.size(iconSize))
                        }
                    }
                }
                Text("Tap to speak", style = MaterialTheme.typography.titleSmall, color = VachakColors.TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text("Speak in Hindi", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary, maxLines = 1)
            } else if (isListening) {
                // Whole visual is tappable to stop — users tap the mic, not just the button.
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(micVisual * 0.85f).clip(CircleShape).clickable {
                    Log.d(TAG_ASR, "Visual Stop tapped isListening=$isListening")
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (hasMicPermission) onStop() else onRequestPermission()
                }) {
                    BreathVisualizer(isListening = true, modifier = Modifier.size(micVisual * 0.85f))
                    Surface(shape = CircleShape, color = VachakColors.PrimaryDark, modifier = Modifier.size(micInner * 0.9f)) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.Filled.Stop, null, tint = Color.White, modifier = Modifier.size(iconSize * 0.9f))
                        }
                    }
                }
                LiveTranscriptionStrip(text = partialText.ifBlank { "Listening… speak now" }, isListening = true)
                // (No helper text here: the Stop button below + tappable visual
                // say it. A third line squeezed the action button to 0px.)
            } else {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = CircleShape, color = VachakColors.SoftLavender, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Lavender200), modifier = Modifier.size(40.dp)) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.Filled.Mic, null, tint = VachakColors.DeepLavender, modifier = Modifier.size(18.dp))
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(if (isTranslating) "Translating… Hindi → ${ActiveLanguage.label(activeLang)}" else "Tap mic and speak", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextPrimary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text("Hindi → ${ActiveLanguage.label(activeLang)} • Offline", style = MaterialTheme.typography.labelSmall, color = VachakColors.TextSecondary, maxLines = 1)
                    }
                    if (isTranslating) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = VachakColors.Lavender600)
                }
            }
            // (Action button lives in the screen body below, outside the capped
            // VoiceArea column — see MicActionButton.)
        }
    }
}
