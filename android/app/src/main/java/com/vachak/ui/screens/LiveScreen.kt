package com.vachak.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.vachak.engine.VachakLog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.vachak.engine.ActiveLanguage
import com.vachak.engine.EngineProvider
import com.vachak.ml.VachakAudio
import com.vachak.ui.components.BreathVisualizer
import com.vachak.ui.components.ConversationMessagePair
import com.vachak.ui.components.GuidedEmpty
import com.vachak.ui.components.InsetWell
import com.vachak.ui.components.LiveInputBar
import com.vachak.ui.components.LiveTranscriptionStrip
import com.vachak.ui.components.SectionCard
import com.vachak.ui.components.StatusRow
import com.vachak.ui.theme.VachakColors
import com.vachak.ui.theme.VachakLayer
import com.vachak.ui.theme.cardShadow
import com.vachak.ui.theme.color
import com.vachak.ui.theme.overlayShadow
import com.vachak.ui.theme.raisedShadow
import com.vachak.ui.theme.tabletHPad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG_ASR = "Vachak-ASR"
private const val TAG_VAD = "Vachak-VAD"
private const val TAG_MT = "Vachak-MT"
private const val TAG_TTS = "Vachak-TTS"
private const val TAG_LAT = "Vachak-Latency"

/** Live is Hindi ⇄ Santali ONLY. The on-device MT bundle supports hi→sat_Olck
 *  and nothing else (OnnxIndicTrans2Adapter.supports); Mundari lives in Learn
 *  packs, never on this page. Both sides of every turn stay playable +
 *  copyable — that pair IS the bidirectional UI. */
private const val LIVE_TARGET_LANG = "sat_Olck"
private const val LIVE_TARGET_LABEL = "Santali (Ol Chiki)"

/** Motion language: snappy ease-out everywhere (cubic-bezier(0.2, 0, 0, 1)). */
private val EaseSnap = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** Pure mm:ss formatter for the listen timer (unit-tested). */
fun formatListenTimer(elapsedMs: Long): String {
    val s = (elapsedMs.coerceAtLeast(0L) / 1000).toInt()
    return "%d:%02d".format(s / 60, s % 60)
}

/** Ticking mm:ss pill while listening (1s cadence — cheap, no meter churn). Tabular so digits never jitter. */
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
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
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
    // Pin Santali: entering Live locks the pair to Hindi ⇄ Santali even if the
    // global switcher was left on Mundari elsewhere. History items keep their
    // own targetLang/voice — the pin only affects NEW translations.
    val globalLang by engine.activeLanguage.collectAsState()
    LaunchedEffect(globalLang) {
        if (ActiveLanguage.normalize(globalLang) != LIVE_TARGET_LANG) {
            ActiveLanguage.set(LIVE_TARGET_LANG)
            (engine.translation as? com.vachak.ml.adapter.AdapterTranslationEngine)?.setActiveLanguage(LIVE_TARGET_LANG)
            VachakLog.d(TAG_MT, "Live pinned to Santali (Ol Chiki) — Hindi ⇄ Santali only")
        }
    }
    // Single pipeline: LiveViewModel owns mic/ASR/MT/TTS sequentially; this
    // screen is a pure renderer of vmState plus permission chrome.
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

    // Live typed translation — SINGLE path: debounced hi→sat lives in
    // LiveViewModel (600ms + pipelineDispatcher + Mutex, sequential ASR->MT->TTS).
    // This screen only forwards keystrokes via onTextChange. Do NOT add a second
    // direct engine.translation.translate() call here — the duplicate path doubled
    // MT load per keystroke and broke the never-parallel RAM guarantee.
    LaunchedEffect(manualHindi) {
        viewModel.onTextChange(manualHindi)
    }

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
            VachakLog.d(TAG_ASR, "permission result isGranted=$isGranted")
        }
    )
    // Auto-request mic permission on first entry (emulator needs explicit prompt)
    LaunchedEffect(Unit) {
        if (!hasMicPermission) {
            VachakLog.d(TAG_ASR, "Auto-requesting RECORD_AUDIO (initial hasMic=false)")
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    // Re-check permission on resume (user may grant in Settings and return)
    DisposableEffect(context) {
        val lifecycle = (context as? androidx.lifecycle.LifecycleOwner)?.lifecycle
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (granted != hasMicPermission) {
                    hasMicPermission = granted
                    VachakLog.d(TAG_ASR, "permission re-check onResume granted=$granted")
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

    val hasConversation by remember { derivedStateOf { conversation.isNotEmpty() } }

    Box(modifier = modifier.fillMaxSize().background(VachakColors.Background).imePadding()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header — deep pine identity band. Direction is FIXED (Hindi ⇄
            // Santali); the old target toggle is gone — Mundari is not on
            // this page. The pair below carries both directions.
            Surface(color = VachakColors.DeepLavender, shadowElevation = 0.dp, tonalElevation = 0.dp) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Translate", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "Hindi ⇄ $LIVE_TARGET_LABEL",
                                style = MaterialTheme.typography.bodySmall,
                                color = VachakColors.Lavender200,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Surface(shape = RoundedCornerShape(50), color = VachakColors.AccentLight) {
                            Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(VachakColors.Success))
                                Text("Offline", style = MaterialTheme.typography.labelSmall, color = VachakColors.ForestDark, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    if (!hasMicPermission) {
                        // Permanent denial ("Don't ask again") makes the system
                        // silently swallow requests — an "Allow" button that does
                        // nothing. Detect it and deep-link to Settings instead.
                        val activity = context as? android.app.Activity
                        val permanentlyDenied = micAskedOnce && activity != null &&
                            !ActivityCompat.shouldShowRequestPermissionRationale(
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
                                        VachakLog.d(TAG_ASR, "opening app Settings for mic permission")
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
            // forced the voice area to recompose on every meter tick.
            val onVmStop = remember(viewModel) { { viewModel.onStop() } }
            val onPermission = remember(permissionLauncher) { { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) } }
            // Combined live capture for the voice area (committed chunks + partial).
            val voiceLiveText = listOf(vmState.liveHindi, vmState.partialText)
                .filter { it.isNotBlank() }.joinToString(" ")

            VoiceArea(
                hasConversation = hasConversation,
                isListening = vmState.isListening,
                isTranslating = vmState.isTranslating,
                isStopping = vmState.isStopping,
                isSynthesizing = vmState.isSynthesizing,
                partialText = voiceLiveText,
                hasMicPermission = hasMicPermission,
                asrReady = asrReady,
                asrDetail = asrStatus.detail
            )
            // Bottom mic cluster: cancel | big mic | direction badge.
            // Cancel/mic taps while draining are ignored (VM guard + disabled
            // Cancel) so the mic can never queue a wedged second stop.
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
            onRequestPermission = onPermission,
            onDrainingTap = {
                scope.launch(Dispatchers.Main) {
                    snackbarHostState.showSnackbar("Still finishing the last run — one moment…")
                }
            }
        )
            if (vmState.asrError != null) {
                SectionCard(
                    title = "Couldn't hear that",
                    subtitle = "The recording worked — the recognizer didn't. Cause below, never a guess.",
                    actionLabel = "Dismiss",
                    onAction = { viewModel.dismissErrors() },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    // [ASR:MODEL] failures surface here verbatim; "no speech
                    // detected" only when the meter genuinely heard silence.
                    StatusRow(
                        dot = VachakColors.ErrorRed,
                        title = "Speech recognition failed",
                        detail = vmState.asrError ?: ""
                    )
                }
            }
            // MT failure kept the Hindi (see LiveViewModel): surface it here too
            // so ASR-OK + MT-fail is never a silent empty screen.
            if (vmState.asrError == null && vmState.error != null) {
                SectionCard(
                    title = "Heard you, translation failed",
                    subtitle = "Your Hindi is kept below — retry sends the same text again.",
                    actionLabel = "Dismiss",
                    onAction = { viewModel.dismissErrors() },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (vmState.committedText.isNotBlank()) {
                            InsetWell {
                                Text(
                                    "“${vmState.committedText}”",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = VachakColors.TextPrimary
                                )
                            }
                        }
                        StatusRow(
                            dot = VachakColors.ErrorRed,
                            title = "Santali translation failed",
                            detail = vmState.error ?: ""
                        )
                    }
                }
            }
            HorizontalDivider(color = VachakColors.Border, thickness = 0.8.dp)

            // Conversation — primary content. The list stays mounted while
            // draining / translating or while a transcript/error is on screen:
            // swapping to a placeholder the moment Stop is tapped hides good
            // transcripts.
            val showPlaceholder = !hasConversation && !vmState.isListening &&
                !vmState.isStopping && !vmState.isTranslating && !vmState.isSynthesizing &&
                vmState.partialText.isBlank() && vmState.committedText.isBlank() &&
                vmState.asrError == null && vmState.error == null
            if (showPlaceholder) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    GuidedEmpty(
                        icon = Icons.Outlined.Mic,
                        title = "Say it in Hindi, read it in Santali",
                        why = "Tap the mic and speak, or type below. Works fully offline — nothing leaves the tablet.",
                        actionLabel = "Start speaking",
                        onAction = guardedStart
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = tabletHPad(12.dp), vertical = 10.dp),
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
                        // Live capture: committed chunks + in-progress partial,
                        // so Hindi appears while speaking.
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
                                            Text(
                                                "Translated in ${ms}ms ${if (within) "< 3s" else "≥ 3s"}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (within) VachakColors.Success else MaterialTheme.colorScheme.error,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                                vmState.latencyMs?.let { total ->
                                    Text("Full run ${total}ms (listening + voice)", style = MaterialTheme.typography.labelSmall, color = VachakColors.TextSecondary, fontFamily = FontFamily.Monospace)
                                }
                                vmState.ttsMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary) }
                            }
                        }
                    }
                }
            }

            // Live typing preview — Santali appears as you type (debounced in
            // the VM). Fixed target: this page never previews another language.
            if (manualHindi.isNotBlank()) {
                SectionCard(
                    title = "$LIVE_TARGET_LABEL · Live",
                    subtitle = if (vmState.livePreviewLoading) "Translating…" else null,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        when {
                            vmState.livePreviewLoading -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = VachakColors.Lavender600)
                                    Text("Translating…", style = MaterialTheme.typography.bodyMedium, color = VachakColors.TextSecondary)
                                }
                            }
                            vmState.livePreviewError != null -> StatusRow(
                                dot = VachakColors.ErrorRed,
                                title = "Preview failed",
                                detail = vmState.livePreviewError ?: ""
                            )
                            vmState.livePreview != null -> Text(vmState.livePreview!!, style = MaterialTheme.typography.bodyLarge, color = VachakColors.TextPrimary, fontSize = 18.sp, lineHeight = 26.sp)
                            else -> Text("Type Hindi to see $LIVE_TARGET_LABEL", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                        }
                        vmState.livePreview?.let { sat ->
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { viewModel.playText(sat, LIVE_TARGET_LANG) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                    Icon(Icons.AutoMirrored.Outlined.VolumeUp, null, tint = VachakColors.DeepLavender, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp)); Text("Play", style = MaterialTheme.typography.labelMedium, color = VachakColors.DeepLavender)
                                }
                                TextButton(onClick = { val t = manualHindi.trim(); if (t.isNotBlank()) { viewModel.commitTyped(t); manualHindi = "" } }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                    Text("Add to conversation", style = MaterialTheme.typography.labelMedium, color = VachakColors.Lavender600)
                                }
                            }
                        }
                    }
                }
            }

            // Input zone — typed Hindi goes through the same VM pipeline.
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
                modifier = Modifier.padding(horizontal = tabletHPad(12.dp), vertical = 8.dp).navigationBarsPadding()
            )
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
    }

    if (showClearConfirm) {
        AlertDialog(
            modifier = Modifier.overlayShadow(RoundedCornerShape(28.dp)),
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear this conversation?") },
            text = { Text("Both sides of every turn — Hindi and Santali — are removed. This cannot be undone.") },
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
                overflow = TextOverflow.Ellipsis,
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
            Text("${it}ms", style = MaterialTheme.typography.labelSmall, color = VachakColors.TextSecondary, fontFamily = FontFamily.Monospace)
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
    onRequestPermission: () -> Unit,
    onDrainingTap: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    val active = isListening || isStopping
    val isButtonEnabled = if (active) true else !isTranslating
    fun tapMic() {
        if (!isButtonEnabled) return
        // Draining taps are ignored in the VM guard too — belt and braces
        // so a second tap can never queue another pipeline run. But the tap
        // is NARRATED here (was: silent return that read as a dead button).
        if (isStopping) {
            onDrainingTap()
            return
        }
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (!hasMicPermission) {
            VachakLog.d(TAG_ASR, "mic permission missing, requesting")
            onRequestPermission()
        } else if (active) {
            VachakLog.d(TAG_ASR, "Stop tapped isListening=$isListening isStopping=$isStopping")
            onStop()
        } else {
            VachakLog.d(TAG_ASR, "Start tapped hasPermission=$hasMicPermission")
            onStart()
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Status line: Listening… + ticking timer, or the honest pipeline stage.
        // Each stage names itself so Stop never looks wedged; warming tells
        // the truth (tap records, decode starts when ready). The text is the
        // STATIC cue — motion is never the only feedback channel.
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
                overflow = TextOverflow.Ellipsis
            )
            if (active) ListenTimer(listeningSinceMs)
        }
        // Waveform while listening.
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
                    color = VachakLayer.Card.color(),
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
            // pine icon + radiating waves (tap-toggle). Press scale 0.96 over
            // 140ms ease-out: the button answers the finger instantly, so taps
            // feel heard even before ASR starts. Icon swaps cross-fade
            // (fade + scale 0.25→1) instead of toggling visibility.
            val micInteraction = remember { MutableInteractionSource() }
            val micPressed by micInteraction.collectIsPressedAsState()
            val micScale by animateFloatAsState(
                targetValue = if (micPressed) 0.96f else 1f,
                animationSpec = tween(durationMillis = 140, easing = EaseSnap),
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
                BreathVisualizer(isListening = active, modifier = Modifier.size(112.dp))
                Surface(
                    shape = CircleShape,
                    color = if (active) VachakLayer.Card.color() else VachakColors.DeepLavender,
                    border = if (active) androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Lavender200) else null,
                    shadowElevation = if (active) 0.dp else 10.dp,
                    modifier = Modifier.size(76.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        AnimatedContent(
                            targetState = if (isStopping && !isListening) "busy" else if (active) "stop" else "mic",
                            transitionSpec = {
                                (fadeIn(tween(150, easing = EaseSnap)) + scaleIn(initialScale = 0.25f, animationSpec = tween(150, easing = EaseSnap))) togetherWith
                                    (fadeOut(tween(150, easing = EaseSnap)) + scaleOut(targetScale = 0.25f, animationSpec = tween(150, easing = EaseSnap)))
                            },
                            label = "micIcon"
                        ) { state ->
                            when (state) {
                                "busy" -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp, color = VachakColors.DeepLavender)
                                "stop" -> Icon(Icons.Filled.Stop, "Stop", tint = VachakColors.DeepLavender, modifier = Modifier.size(30.dp))
                                else -> Icon(Icons.Filled.Mic, "Tap to speak", tint = Color.White, modifier = Modifier.size(30.dp))
                            }
                        }
                    }
                }
            }
            // Direction badge (right): fixed Hindi → Ol Chiki mirror of the header.
            Surface(shape = CircleShape, color = VachakLayer.Card.color(), border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Border), modifier = Modifier.size(56.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("हिं", style = MaterialTheme.typography.labelLarge, color = VachakColors.TextPrimary, fontWeight = FontWeight.Bold)
                        Text("ᱚᱞ", style = MaterialTheme.typography.labelSmall, color = VachakColors.DeepLavender, fontWeight = FontWeight.SemiBold)
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
            progress = { fraction },
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
    // MT done, TTS generate() running — blank text here means the synth
    // wait, not a hang.
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
            // No idle hint line (mic cluster owns idle). Only permission /
            // warming problems speak here; ready-idle collapses so the
            // conversation + mic keep the room.
            if (!hasMicPermission) {
                Text(
                    "Microphone permission needed — see above",
                    style = MaterialTheme.typography.bodySmall,
                    color = VachakColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (!asrReady) {
                Text(
                    (asrDetail?.take(80) ?: "Building ASR… transcription starts when ready") + " • your speech will still be recorded • Offline",
                    style = MaterialTheme.typography.bodySmall,
                    color = VachakColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else if (isTranslating || isStopping || isSynthesizing) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = VachakLayer.Card.color(),
                border = androidx.compose.foundation.BorderStroke(1.dp, VachakColors.Lavender200),
                modifier = Modifier.fillMaxWidth().cardShadow(RoundedCornerShape(16.dp))
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = CircleShape, color = VachakColors.Lavender100, modifier = Modifier.size(40.dp)) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.Filled.Mic, null, tint = VachakColors.DeepLavender, modifier = Modifier.size(18.dp))
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        // Keep the heard Hindi on screen while translating /
                        // draining — a static "Translating…" line would hide a
                        // good transcript for the whole MT window.
                        val heard = partialText.ifBlank { "" }
                        val busyLabel = if (isSynthesizing) "Synthesizing voice…" else "Translating… Hindi → $LIVE_TARGET_LABEL"
                        Text(
                            if (heard.isNotBlank()) "“$heard”" else busyLabel,
                            style = MaterialTheme.typography.bodySmall, color = VachakColors.TextPrimary, maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "Hindi → $LIVE_TARGET_LABEL • Offline",
                            style = MaterialTheme.typography.labelSmall, color = VachakColors.TextSecondary, maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = VachakColors.Lavender600)
                }
            }
        }
    }
}
