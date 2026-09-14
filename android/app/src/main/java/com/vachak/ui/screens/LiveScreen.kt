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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.graphics.graphicsLayer
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

/** Pure mm:ss formatter for the listen timer (unit-tested). */
fun formatListenTimer(elapsedMs: Long): String {
    val s = (elapsedMs.coerceAtLeast(0L) / 1000).toInt()
    return "%d:%02d".format(s / 60, s % 60)
}

/** Ticking mm:ss pill while listening (1s cadence — cheap, no meter churn). */
@Composable
private fun ListenTimer(sinceMs: Long?) {
    var now by remember(sinceMs) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sinceMs) {
        while (sinceMs != null) {
            kotlinx.coroutines.delay(1000)
            now = System.currentTimeMillis()
        }
    }
    if (sinceMs != null) {
        Text(
            formatListenTimer(now - sinceMs),
            style = MaterialTheme.typography.labelMedium,
            color = VachakColors.Lavender600,
            fontWeight = FontWeight.SemiBold
        )
    }
}

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
    // "Model live" signal: ASR dot + mic button read this. IDLE/LOADING =
    // still building (mic still works — decode starts when ready).
    val asrStatus by com.vachak.ml.ModelStatus.asr.collectAsState()
    val asrReady = asrStatus.state == com.vachak.ml.ModelState.READY
    var manualHindi by remember { mutableStateOf("") }
    var showClearConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    DisposableEffect(Unit) {
        onDispose { viewModel.forceReleaseMic() }
    }
    val listState = rememberLazyListState()
    val conversation = LiveConversationStore.items
    val inputFocus = remember { androidx.compose.ui.focus.FocusRequester() }

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

    // auto-scroll on new messages AND on in-place updates (translating →
    // translated swaps content without changing size — the old size-only key
    // left fresh results below the fold looking like "not updating").
    val lastItemSig = conversation.lastOrNull()?.let { it.id + (it.santaliText ?: "") + (it.error ?: "") + it.isTranslating }
    LaunchedEffect(conversation.size, lastItemSig) {
        if (conversation.isNotEmpty()) {
            // Use scrollToItem (no animation) for 60fps; animateScroll is heavy on 2GB
            listState.scrollToItem(conversation.size - 1)
        }
    }

    // Voice pipeline retired to LiveViewModel (single sequential pipeline).
    val hasConversation by remember { derivedStateOf { conversation.isNotEmpty() } }

    Box(modifier = modifier.fillMaxSize().background(VachakColors.Background).imePadding()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header — deep pine identity band: title + direction + offline
            // badge in one row; target toggle as a segmented control below.
            Surface(color = VachakColors.DeepLavender, shadowElevation = 0.dp, tonalElevation = 0.dp) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Live Translation", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            // P4: direction follows the toggle below (was hardcoded Santali).
                            Text("Hindi → ${ActiveLanguage.label(activeLang)}", style = MaterialTheme.typography.bodySmall, color = VachakColors.Lavender200, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                        // Offline badge only — history (scroll-to-top), debug
                        // and overflow buttons removed; Clear lives on the
                        // Conversation header row below.
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(50), color = VachakColors.AccentLight) {
                                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(VachakColors.Success))
                                    Text("Offline", style = MaterialTheme.typography.labelSmall, color = VachakColors.ForestDark, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                    // P4: target toggle on the mic page — Hi→Santali vs
                    // Hi→Mundari. Mic stays Hindi-only (ASR is Hindi-only);
                    // this flips the MT target + TTS voice. History keeps each
                    // item's own language (item.targetLang), so toggling never
                    // rewrites past messages.
                    TargetSegmentedToggle(
                        activeLang = activeLang,
                        onSelect = { code ->
                            ActiveLanguage.set(code)
                            (engine.translation as? com.vachak.ml.adapter.AdapterTranslationEngine)?.setActiveLanguage(code)
                            android.util.Log.d(TAG_MT, "Live target toggled to $code")
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 10.dp)
                    )
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

            ModelStatusRow(onTtsTest = viewModel::testVoice)
    // Hoisted callbacks: inline lambdas here re-created per recomposition and
    // forced VoiceArea/MicCluster to recompose on every 150ms meter tick.
    val onVmStop = remember(viewModel) { { viewModel.onStop() } }
    val onPermission = remember(permissionLauncher) { { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) } }
    // Combined live capture for the voice area (committed chunks + partial).
    val voiceLiveText = listOf(vmState.liveHindi, vmState.partialText)
        .filter { it.isNotBlank() }.joinToString(" ")
    // P5: single-column layout — voice cluster always visible (the old
    // focus-mode collapse left with the duplicate hints).
    VoiceArea(
        hasConversation = hasConversation,
        isListening = vmState.isListening,
        isTranslating = vmState.isTranslating,
        isStopping = vmState.isStopping,
        isSynthesizing = vmState.isSynthesizing,
        partialText = voiceLiveText,
        hasMicPermission = hasMicPermission,
        activeLang = activeLang,
        asrReady = asrReady,
        asrDetail = asrStatus.detail
    )
            // Bottom mic cluster (reference design): cancel | big mic | lang.
            // P2: Cancel/mic taps while draining are ignored (VM guard +
            // disabled Cancel) so the mic can never queue a wedged second stop.
            // Sequential-RAM rule: never capture while translating — narrate
            // the tap instead of swallowing it.
            val guardedStart: () -> Unit = remember(viewModel, vmState.isTranslating) {
                {
                    if (vmState.isTranslating) {
                        scope.launch(Dispatchers.Main) {
                            snackbarHostState.showSnackbar("Finishing translation — tap mic again in a moment")
                        }
                    } else {
                        viewModel.onStart()
                    }
                }
            }
    MicCluster(
        isListening = vmState.isListening,
        isStopping = vmState.isStopping,
        isTranslating = vmState.isTranslating,
        isSynthesizing = vmState.isSynthesizing,
        hasMicPermission = hasMicPermission,
        asrReady = asrReady,
        meterRms = vmMeterRms,
        listeningSinceMs = vmState.listeningSinceMs,
        onStart = guardedStart,
        onStop = onVmStop,
        onRequestPermission = onPermission
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
            // MT failure kept the Hindi (see LiveViewModel): surface it here too
            // so ASR-OK + MT-fail is never a silent empty screen.
            if (vmState.asrError == null && vmState.error != null) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), shape = RoundedCornerShape(12.dp)) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (vmState.committedText.isNotBlank()) {
                                Text("Heard: “${vmState.committedText}”", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextPrimary)
                            }
                            Text("Translation failed: ${vmState.error}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        TextButton(onClick = { viewModel.dismissErrors() }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Dismiss", style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
            HorizontalDivider(color = VachakColors.Border, thickness = 0.8.dp)

            // Conversation (live.md §4 — primary content, LazyColumn)
            // Keep the list (and InlineTranscription) mounted while draining /
            // translating or while a transcript/error is on screen — the old
            // !hasConversation && !isListening gate swapped to a placeholder
            // the moment Stop was tapped, hiding a good transcript.
            val showPlaceholder = !hasConversation && !vmState.isListening &&
                !vmState.isStopping && !vmState.isTranslating && !vmState.isSynthesizing &&
                vmState.partialText.isBlank() && vmState.committedText.isBlank() &&
                vmState.asrError == null && vmState.error == null
            if (showPlaceholder) {
                // P5: single hint — the duplicate "Type in Hindi" button and
                // idle lines are gone; the input row below is the type entry.
                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Your translations will appear here — tap the mic or type below.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = VachakColors.TextSecondary
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                            // Item's own language: history keeps its label +
                            // voice across toggles instead of inheriting current.
                            onPlaySantali = { item.santaliText?.let { viewModel.playText(it, item.targetLang) } },
                            targetLabel = ActiveLanguage.label(item.targetLang),
                            onRetry = { viewModel.retryItem(item.id, item.hindiText) }
                        )
                    }
                    item {
                        // Live capture: committed 3s chunks + in-progress
                        // partial, so Hindi appears while speaking.
                        val liveText = listOf(vmState.liveHindi, vmState.partialText)
                            .filter { it.isNotBlank() }.joinToString(" ")
                            .ifBlank { vmState.committedText }
                        InlineTranscription(
                            liveText,
                            vmState.isListening,
                            vmState.isStopping,
                            vmState.isTranslating,
                            vmState.isSynthesizing
                        )
                    }
                    // Benchmark: ASR+MT headline (stop-tap → translated text,
                    // the <3s budget) with voice/full-run as verdict-free
                    // secondary. TTS starts AFTER the headline by definition.
                    if (vmState.translateMs != null || vmState.latencyMs != null || vmState.ttsMessage != null) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                vmState.translateMs?.let { ms ->
                                    val within = ms < 3000
                                    Surface(shape = RoundedCornerShape(50), color = if (within) VachakColors.SuccessLight else MaterialTheme.colorScheme.errorContainer) {
                                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(if (within) "✓" else "⚠", style = MaterialTheme.typography.labelSmall, color = if (within) VachakColors.Success else MaterialTheme.colorScheme.error)
                                            Text("Translated in ${ms}ms ${if (within) "< 3s" else "≥ 3s"}", style = MaterialTheme.typography.labelSmall, color = if (within) VachakColors.Success else MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                                vmState.latencyMs?.let { total ->
                                    Text("Full run ${total}ms (listening + voice)", style = MaterialTheme.typography.labelSmall, color = VachakColors.TextSecondary)
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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

            // Input zone — single type row (P5: divider + duplicate hints
            // removed; the space went to the conversation + mic above).
            // Input bar — typed Hindi goes through the same VM pipeline (commitTyped).
            LiveInputBar(
                text = manualHindi,
                onTextChange = { manualHindi = it },
                onTranslate = {
                    val t = manualHindi.trim()
                    if (t.isNotBlank()) { viewModel.commitTyped(t); manualHindi = "" }
                },
                enabled = !vmState.isListening,
                focusRequester = inputFocus,
                onFocusChange = {},
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).navigationBarsPadding()
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
}

/**
 * Target-language segmented control for the pine header. Single M3
 * component instead of mismatched Button/OutlinedButton pair — the
 * selected segment reads instantly, and unselected stays legible on pine.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetSegmentedToggle(
    activeLang: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = ActiveLanguage.all()
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, (code, name) ->
            val selected = ActiveLanguage.normalize(activeLang) == ActiveLanguage.normalize(code)
            SegmentedButton(
                selected = selected,
                onClick = { if (!selected) onSelect(code) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = VachakColors.Accent,
                    activeContentColor = VachakColors.ForestDark,
                    inactiveContainerColor = Color.Transparent,
                    inactiveContentColor = Color.White
                ),
                border = SegmentedButtonDefaults.borderStroke(Color.White.copy(alpha = 0.4f))
            ) {
                Text(name, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

/**
 * Always-visible model health strip: ASR / MT / TTS dots driven by ModelStatus.
 * Green = live, amber = ready-but-limited (placeholder voice) or warming,
 * red = failed (detail carries the cause). The models behind a red dot are
 * exactly what the next pipeline failure will blame — no more guessing.
 */
@Composable
private fun ModelStatusRow(onTtsTest: () -> Unit = {}) {
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
        // One-tap voice proof (known-good string → synth → speaker).
        TextButton(onClick = onTtsTest, contentPadding = PaddingValues(horizontal = 4.dp)) {
            Text("Test voice", style = MaterialTheme.typography.labelSmall, color = VachakColors.DeepLavender)
        }
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
private fun MicCluster(
    isListening: Boolean,
    isStopping: Boolean,
    isTranslating: Boolean,
    isSynthesizing: Boolean = false,
    hasMicPermission: Boolean,
    asrReady: Boolean,
    meterRms: Float,
    listeningSinceMs: Long?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRequestPermission: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val active = isListening || isStopping
    val isButtonEnabled = if (active) true else !isTranslating
    fun tapMic() {
        if (!isButtonEnabled) return
        // P2: draining taps are ignored in the VM guard too — belt and braces
        // so a second tap can never queue another pipeline run.
        if (isStopping) return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (!hasMicPermission) {
            Log.d(TAG_ASR, "mic permission missing, requesting")
            onRequestPermission()
        } else if (active) {
            Log.d(TAG_ASR, "Stop tapped isListening=$isListening isStopping=$isStopping")
            onStop()
        } else {
            Log.d(TAG_ASR, "Start tapped hasPermission=$hasMicPermission")
            onStart()
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Status line: Listening… + ticking timer, or the honest pipeline stage.
        // P2/P3: each stage names itself so Stop never looks wedged; warming
        // tells the truth (tap records, decode starts when ready).
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (isListening) "Listening…"
                else if (isStopping) "Transcribing…"
                else if (isSynthesizing) "Synthesizing voice…"
                else if (isTranslating) "Translating…"
                else if (!asrReady) "Warming models… tap mic — speech is recorded"
                else "Tap the mic and speak in Hindi",
                style = MaterialTheme.typography.labelLarge,
                color = if (active) VachakColors.Lavender600 else VachakColors.TextSecondary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            if (active) ListenTimer(listeningSinceMs)
        }
        // Waveform while listening (reference: bars under Listening…).
        if (isListening) {
            MicMeter(rms = meterRms)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Cancel (left): stops the session while listening; hidden when
            // idle to keep symmetry. Disabled while draining (isStopping) so
            // Cancel taps can't queue a second stop behind the first.
            if (active || isTranslating) {
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border),
                    modifier = Modifier.size(56.dp).clip(CircleShape).clickable(
                        enabled = isListening,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (isListening) onStop()
                        }
                    )
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.Outlined.Close, null, tint = VachakColors.TextPrimary, modifier = Modifier.size(24.dp))
                    }
                }
            } else {
                Spacer(Modifier.size(56.dp))
            }
            // Big mic: idle = deep pine w/ white icon; active = white w/
            // pine icon + radiating waves (reference design, tap-toggle).
            // Press scale (0.94, 140ms ease-out): the button answers the
            // finger instantly, so taps feel heard even before ASR starts.
            val micInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val micPressed by micInteraction.collectIsPressedAsState()
            val micScale by animateFloatAsState(
                targetValue = if (micPressed) 0.94f else 1f,
                animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
                label = "micPress"
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(112.dp)
                    .graphicsLayer(scaleX = micScale, scaleY = micScale)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = micInteraction,
                        indication = null,
                        enabled = isButtonEnabled
                    ) { tapMic() }
            ) {
                if (active) {
                    BreathVisualizer(isListening = true, modifier = Modifier.size(112.dp))
                } else {
                    BreathVisualizer(isListening = false, modifier = Modifier.size(112.dp))
                }
                Surface(
                    shape = CircleShape,
                    color = if (active) Color.White else VachakColors.DeepLavender,
                    border = if (active) androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Lavender200) else null,
                    modifier = Modifier.size(76.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        if (isStopping && !isListening) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp, color = VachakColors.DeepLavender)
                        } else {
                            Icon(
                                if (active) Icons.Filled.Stop else Icons.Filled.Mic,
                                contentDescription = if (active) "Stop" else "Tap to speak",
                                tint = if (active) VachakColors.DeepLavender else Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }
            }
            // Direction badge (right): display-only mirror of the toggle above.
            Surface(shape = CircleShape, color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.size(56.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("हिं", style = MaterialTheme.typography.labelLarge, color = VachakColors.TextPrimary, fontWeight = FontWeight.Bold)
                        Text("अ.", style = MaterialTheme.typography.labelSmall, color = VachakColors.DeepLavender, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun MicMeter(rms: Float) {
    // -50dB floor .. 0dB ceiling mapped to 0..1; speech gate marked in text.
    val db = if (rms <= 0f) -50f else (20 * kotlin.math.log10(rms)).coerceIn(-50f, 0f)
    val fraction = ((db + 50f) / 50f).coerceIn(0f, 1f)
    val hearing = rms > VachakAudio.VAD_RMS_THRESHOLD
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
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
    isListening: Boolean,
    isStopping: Boolean = false,
    isTranslating: Boolean = false,
    // Phase 4: MT done, TTS generate() running — blank text here means the
    // synth wait, not a hang.
    isSynthesizing: Boolean = false
) {
    // Show live text while listening AND while draining/translating: the old
    // isListening-only gate blanked the screen the moment Stop was tapped,
    // even when ASR had succeeded.
    if (text.isNotBlank()) {
        LiveTranscriptionStrip(text = text, isListening = isListening)
    } else if (isSynthesizing) {
        LiveTranscriptionStrip(text = "Synthesizing voice…", isListening = false)
    } else if (isStopping || isTranslating) {
        LiveTranscriptionStrip(text = "Transcribing…", isListening = false)
    } else if (isListening) {
        LiveTranscriptionStrip(text = "Listening… speak now", isListening = true)
    }
}

@Composable
private fun VoiceArea(
    hasConversation: Boolean,
    isListening: Boolean,
    isTranslating: Boolean,
    isStopping: Boolean,
    isSynthesizing: Boolean = false,
    partialText: String,
    hasMicPermission: Boolean,
    activeLang: String,
    asrReady: Boolean,
    asrDetail: String?
) {
    // Slim status zone: the bottom MicCluster owns all mic visuals now.
    // This area only shows live transcription / heard-Hindi / warming state,
    // with flexible height so the chat list always keeps room to breathe.
    // Sand tint (not white) so it reads as a zone distinct from the
    // conversation cards below it.
    Column(
        modifier = Modifier.fillMaxWidth().background(VachakColors.SoftLavender)
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .heightIn(min = 0.dp, max = 300.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isListening) {
            LiveTranscriptionStrip(text = partialText.ifBlank { "Listening… speak now" }, isListening = true)
        } else if (!hasConversation && !isTranslating && !isStopping && !isSynthesizing) {
            // P5: no idle hint line (mic cluster owns idle). Only
            // permission/warming problems speak here; ready-idle collapses
            // so the conversation + mic keep the room.
            if (!hasMicPermission) {
                Text(
                    "Microphone permission needed — see above",
                    style = MaterialTheme.typography.bodySmall,
                    color = VachakColors.TextSecondary,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            } else if (!asrReady) {
                Text(
                    (asrDetail?.take(80) ?: "Building ASR… transcription starts when ready") + " • your speech will still be recorded • Offline",
                    style = MaterialTheme.typography.bodySmall,
                    color = VachakColors.TextSecondary,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        } else if (isTranslating || isStopping || isSynthesizing) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = CircleShape, color = VachakColors.SoftLavender, border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Lavender200), modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.Filled.Mic, null, tint = VachakColors.DeepLavender, modifier = Modifier.size(18.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    // Keep the heard Hindi on screen while translating /
                    // draining — a static "Translating…" line would hide a
                    // good transcript for the whole MT window.
                    val heard = partialText.ifBlank { "" }
                    // Phase 4: name the MT→TTS wait honestly.
                    val busyLabel = if (isSynthesizing) "Synthesizing voice…" else "Translating… Hindi → ${ActiveLanguage.label(activeLang)}"
                    Text(
                        if (heard.isNotBlank()) "“$heard”" else busyLabel,
                        style = MaterialTheme.typography.bodySmall, color = VachakColors.TextPrimary, maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        "Hindi → ${ActiveLanguage.label(activeLang)} • Offline",
                        style = MaterialTheme.typography.labelSmall, color = VachakColors.TextSecondary, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = VachakColors.Lavender600)
            }
        }
    }
}
