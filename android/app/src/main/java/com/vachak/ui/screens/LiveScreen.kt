package com.vachak.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.SystemClock
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
import com.vachak.engine.EngineResult
import com.vachak.engine.LanguagePair
import com.vachak.ml.LatencySample
import com.vachak.ml.LatencyTracker
import com.vachak.ml.LastPipelineRun
import com.vachak.ml.VachakAudio
import com.vachak.ml.StreamingAsrSession
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
    // LiveViewModel StateFlow wiring — collects UiState WhileSubscribed(5000), intents onStart/onStop/onTextChange/onRetry
    val vmState by viewModel.uiStateDirect.collectAsState()
    val partialTextState = remember { mutableStateOf("") }
    var manualHindi by remember { mutableStateOf("") }
    // Live typing preview — debounced hi→activeLang (key feature: see target as you type)
    var livePreview by remember { mutableStateOf<String?>(null) }
    var livePreviewLoading by remember { mutableStateOf(false) }
    var livePreviewError by remember { mutableStateOf<String?>(null) }
    var asrError by remember { mutableStateOf<String?>(null) }
    var showDebugDialog by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var isTranslating by remember { mutableStateOf(false) }
    // isStopping: stop tapped, pipeline draining (ASR finish can take seconds on emulator).
    // Button stays ENABLED while stopping so mic can never look stuck.
    var isStopping by remember { mutableStateOf(false) }
    var latencyMs by remember { mutableStateOf<Long?>(null) }
    var ttsMessage by remember { mutableStateOf<String?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    // Mic meter: polls the capturer's rolling RMS so the user SEES whether the
    // mic hears them (vs staring at "Listening…" while streaming silence).
    var meterRms by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val audioCapturer = viewModel.audioCapturer
    DisposableEffect(Unit) {
        onDispose { viewModel.forceReleaseMic() }
    }
    var streamingSession by remember { mutableStateOf<StreamingAsrSession?>(null) }
    var t0Ns by remember { mutableStateOf<Long?>(null) }
    var firstPartialNs by remember { mutableStateOf<Long?>(null) }
    var lastPushedSample by remember { mutableStateOf(0) }
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
        // Local mirror cleared fast for <2 chars so the preview card hides
        // immediately even before the VM debounce round-trip returns.
        val q = manualHindi.trim()
        if (q.isBlank() || q.length < 2) {
            livePreview = null; livePreviewError = null; livePreviewLoading = false
        }
    }
    // Sync VM live preview into local state for UI (ensures StateFlow wired)
    LaunchedEffect(vmState.livePreview, vmState.livePreviewLoading, vmState.livePreviewError) {
        if (vmState.livePreview != null || vmState.livePreviewError != null) {
            // Prefer VM state when available (sequenced pipeline + Mutex)
            livePreview = vmState.livePreview
            livePreviewError = vmState.livePreviewError
            livePreviewLoading = vmState.livePreviewLoading
        }
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

    fun playPcm(text: String, lang: String = activeLang) {
        scope.launch(Dispatchers.IO) {
            val pcmRes = engine.tts.synthesize(text, lang)
            if (pcmRes is EngineResult.Ok) tryPlayPcm(pcmRes.value, VachakAudio.TTS_OUTPUT_HZ)
        }
    }

    fun translateAndAppend(hindi: String, tracker: LatencyTracker? = null) {
        if (hindi.isBlank()) return
        val id = "msg-${SystemClock.elapsedRealtimeNanos()}"
        val item = ConversationItem(id = id, hindiText = hindi, santaliText = null, timestampMillis = System.currentTimeMillis(), isTranslating = true)
        LiveConversationStore.items.add(item)
        isTranslating = true
        // Typed path has no voice tracker: measure from here so timings show for every item.
        val tr = tracker ?: LatencyTracker(LatencySample(runId = "typed-${SystemClock.elapsedRealtimeNanos()}")).also { it.markSpeechBegin() }
        scope.launch(Dispatchers.IO) {
            val mtStart = SystemClock.elapsedRealtimeNanos()
            val mt = engine.translation.translate(hindi, LanguagePair("hi", activeLang))
            val translated: String?
            val err: String?
            when (mt) {
                is EngineResult.Ok -> { translated = mt.value; err = null }
                is EngineResult.Err -> {
                    Log.e(TAG_MT, "MT failed ${mt.code}: ${mt.message}")
                    withContext(Dispatchers.Main) { snackbarHostState.showSnackbar("Translation failed: ${mt.message}") }
                    translated = null; err = mt.message
                }
            }
            val mtMs = (SystemClock.elapsedRealtimeNanos() - mtStart) / 1_000_000
            Log.d(TAG_MT, "MT ${mtMs}ms [MT:INFERENCE] \"$hindi\" -> \"$translated\"")
            tr.markTranslate(translated ?: "")
            withContext(Dispatchers.Main) {
                val idx = LiveConversationStore.items.indexOfFirst { it.id == id }
                if (idx != -1) {
                    LiveConversationStore.items[idx] = LiveConversationStore.items[idx].copy(
                        santaliText = translated,
                        isTranslating = false,
                        error = err
                    )
                }
                isTranslating = false
                // fire TTS in background but don't block
                if (translated != null) {
                    scope.launch(Dispatchers.IO) {
                        tr.markTtsBegin()
                        val ttsStart = SystemClock.elapsedRealtimeNanos()
                        val pcmRes = engine.tts.synthesize(translated, activeLang)
                        val ttsMs = (SystemClock.elapsedRealtimeNanos() - ttsStart) / 1_000_000
                        when (pcmRes) {
                            is EngineResult.Ok -> Log.d(TAG_LAT, "TTS ${ttsMs}ms [TTS:SYNTHESIS] ${pcmRes.value.size} samples")
                            is EngineResult.Err -> Log.d(TAG_LAT, "TTS ${ttsMs}ms [TTS:SYNTHESIS] failed ${pcmRes.code}: ${pcmRes.message}")
                        }
                        tr.markAudioBegin()
                        // Measured stages -> item footer + last-run holder (never canned).
                        val stages = tr.result().stageMs()
                        val total = tr.result().endToEndMs()?.toLong()
                        LastPipelineRun.publish(tr)
                        withContext(Dispatchers.Main) {
                            val i = LiveConversationStore.items.indexOfFirst { it.id == id }
                            if (i != -1) {
                                LiveConversationStore.items[i] = LiveConversationStore.items[i].copy(
                                    asrMs = stages["asr"]?.toLong(),
                                    mtMs = stages["translate"]?.toLong(),
                                    ttsMs = stages["tts"]?.toLong(),
                                    totalMs = total
                                )
                            }
                            when (pcmRes) {
                                is EngineResult.Ok -> {
                                    tryPlayPcm(pcmRes.value, VachakAudio.TTS_OUTPUT_HZ)
                                    ttsMessage = "Playing ${ActiveLanguage.label(activeLang)} audio • ${pcmRes.value.size} samples @${VachakAudio.TTS_OUTPUT_HZ}Hz"
                                }
                                is EngineResult.Err -> {
                                    ttsMessage = "TTS: ${pcmRes.message}"
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun startRecordingUktamStyle() {
        if (isListening || isStopping) {
            Log.d(TAG_ASR, "start ignored isListening=$isListening isStopping=$isStopping")
            return
        }
        val t0 = SystemClock.elapsedRealtimeNanos()
        t0Ns = t0
        firstPartialNs = null
        lastPushedSample = 0
        // Immediate UI feedback — never block Main on model/ audio init
        isListening = true
        partialTextState.value = ""
        ttsMessage = null
        latencyMs = null
        asrError = null
        Log.d(TAG_ASR, "Uktam-style startRecording t0=${t0Ns} (UI immediate, session warm-up off Main)")
        scope.launch(Dispatchers.IO) {
            try {
                val session = StreamingAsrSession(context)
                session.start(t0)
                // Heavy model warm-up + AudioRecord creation off UI thread
                try {
                    session.warmUpAsync()
                    Log.d(TAG_ASR, "warmUpAsync done")
                } catch (e: Throwable) {
                    Log.e(TAG_ASR, "warmUpAsync failed", e)
                }
                audioCapturer.startRecording()
                // startRecording() NEVER throws — it fails silently. Detect it here or
                // UI shows "listening" forever with zero samples (emulator mic busy).
                if (!audioCapturer.isRecording()) {
                    Log.e(TAG_ASR, "AudioCapturer failed to start (mic busy/denied?)")
                    withContext(Dispatchers.Main) {
                        isListening = false
                        streamingSession = null
                        asrError = "[ASR:MIC] Microphone failed to start — grant permission or free the mic (emulator: enable virtual mic)"
                    }
                    return@launch
                }
                withContext(Dispatchers.Main) {
                    streamingSession = session
                    Log.d(TAG_ASR, "mic + ASR warm-up ready t0=$t0")
                }
            } catch (e: Exception) {
                Log.e(TAG_ASR, "startRecording failed", e)
                withContext(Dispatchers.Main) {
                    isListening = false
                    streamingSession = null
                    asrError = "[ASR:START] ${e.message}"
                }
            }
        }
    }

    // Mic meter poll — only while listening (150ms cadence, cheap).
    LaunchedEffect(isListening) {
        if (!isListening) {
            meterRms = 0f
            return@LaunchedEffect
        }
        while (isListening) {
            delay(150)
            if (!isListening) break
            meterRms = audioCapturer.lastRms
        }
    }

    // Live Hindi transcription — key feature: see as you speak (windowed 700ms, VAD retained, final offline at stop)
    // NOTE: each iteration is try/catch — if pushAudio/getPartial throws (e.g. native
    // recognizer not ready on emulator) the loop MUST survive, else the strip freezes
    // on "Listening…" forever with mic stuck on.
    LaunchedEffect(isListening) {
        if (!isListening) {
            partialTextState.value = ""
            Log.d(TAG_ASR, "LaunchedEffect stopped, clearing partial")
            return@LaunchedEffect
        }
        val DECODE_INTERVAL_MS = VachakAudio.PARTIAL_DECODE_MS
        var lastPartial = ""
        var errShown = false
        Log.d(TAG_ASR, "LaunchedEffect started isListening=true")
        while (isListening) {
            delay(DECODE_INTERVAL_MS)
            if (!isListening) {
                Log.d(TAG_ASR, "LaunchedEffect break isListening=false")
                break
            }
            try {
                val session = streamingSession
                if (session == null) continue
                val pcmSnap = withContext(Dispatchers.Default) { audioCapturer.snapshotShortArray() }
                if (pcmSnap.size <= lastPushedSample) continue
                val newChunk = withContext(Dispatchers.Default) { pcmSnap.copyOfRange(lastPushedSample, pcmSnap.size) }
                lastPushedSample = pcmSnap.size
                Log.d(TAG_ASR, "pushAudio chunk=${newChunk.size} totalPushed=$lastPushedSample pcmSnap=${pcmSnap.size}")
                val partial = withContext(Dispatchers.Default) {
                    session.pushAudio(newChunk)
                    session.getPartial()
                }
                if (partial.isNotBlank() && partial != lastPartial) {
                    lastPartial = partial
                    errShown = false
                    withContext(Dispatchers.Main) {
                        partialTextState.value = partial
                        if (asrError?.startsWith("[ASR:LIVE]") == true) asrError = null
                    }
                    if (firstPartialNs == null) {
                        firstPartialNs = SystemClock.elapsedRealtimeNanos()
                        val firstMs = (firstPartialNs!! - (t0Ns ?: firstPartialNs!!)) / 1_000_000
                        Log.d(TAG_LAT, "first partial ${firstMs}ms -> \"$partial\"")
                    }
                }
            } catch (e: Exception) {
                // Never kill the loop — surface once, keep listening.
                Log.e(TAG_ASR, "partial iteration failed (loop survives)", e)
                if (!errShown) {
                    errShown = true
                    withContext(Dispatchers.Main) {
                        asrError = "[ASR:LIVE] ${e.message?.take(80)} — still listening…"
                    }
                }
            }
        }
    }

    fun stopRecordingAndProcessUktamStyle() {
        if (isStopping) {
            Log.d(TAG_ASR, "stop ignored, already stopping")
            return
        }
        val wasListening = isListening
        isListening = false
        isStopping = true
        // NOTE: isTranslating is owned by translateAndAppend. isStopping keeps the
        // Stop button enabled while session.finish() decodes (slow on emulator),
        // so the mic can never look stuck.
        Log.d(TAG_ASR, "stopRecording: wasListening=$wasListening lastPushed=$lastPushedSample t0Ns=$t0Ns")
        scope.launch(Dispatchers.IO) {
            try {
                val tracker = LatencyTracker(LatencySample(runId = "live-${SystemClock.elapsedRealtimeNanos()}"))
                val sessionT0 = t0Ns ?: SystemClock.elapsedRealtimeNanos()
                if (t0Ns == null) tracker.markSpeechBegin()
                val startTotal = sessionT0
                Log.d(TAG_ASR, "Live capture stop — session finish, t0Ns=$t0Ns wasListening=$wasListening")
                // Bounded: stopAndGetShortArray has its own 1500ms cancel timeout;
                // outer 5s guard so a wedged AudioRecord can never hang stop forever.
                val pcmFinal = try {
                    withTimeout(5000) { audioCapturer.stopAndGetShortArray() }
                } catch (e: Exception) {
                    Log.e(TAG_ASR, "stopAndGetShortArray timeout/failed", e)
                    ShortArray(0)
                }
                Log.d(TAG_ASR, "pcmFinal size=${pcmFinal.size} @16000Hz, lastPushed=$lastPushedSample")
                val session = streamingSession
                var finalCommitted = ""
                if (session != null) {
                    if (pcmFinal.size > lastPushedSample) {
                        val tail = pcmFinal.copyOfRange(lastPushedSample, pcmFinal.size)
                        Log.d(TAG_ASR, "pushing tail ${tail.size} samples")
                        try {
                            session.pushAudio(tail)
                        } catch (e: Exception) {
                            Log.e(TAG_ASR, "push tail failed", e)
                        }
                        lastPushedSample = pcmFinal.size
                    } else {
                        Log.d(TAG_ASR, "no tail to push lastPushed=$lastPushedSample pcmFinal=${pcmFinal.size}")
                    }
                    finalCommitted = try {
                        session.finish()
                    } catch (e: Exception) {
                        Log.e(TAG_ASR, "session.finish failed, falling back to partial", e)
                        ""
                    }
                    Log.d(TAG_ASR, "Streaming finish committed=\"$finalCommitted\" pcmFinal=${pcmFinal.size} partial=\"${partialTextState.value}\"")
                } else {
                    Log.w(TAG_ASR, "session null, fallback to engine.asr.transcribe pcmFinal=${pcmFinal.size}")
                    try {
                        val res = engine.asr.transcribe(pcmFinal, 16000)
                        when (res) {
                            is EngineResult.Ok -> finalCommitted = res.value.trim()
                            is EngineResult.Err -> {
                                Log.e(TAG_ASR, "ASR failed [ASR:TRANSCRIBE] ${res.code}: ${res.message}")
                                withContext(Dispatchers.Main) {
                                    asrError = "[ASR:${res.code}] ${res.message}"
                                    ttsMessage = null
                                    partialTextState.value = ""
                                }
                                tracker.markAsr("")
                                return@launch
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG_ASR, "one-shot transcribe threw", e)
                        withContext(Dispatchers.Main) {
                            asrError = "[ASR:TRANSCRIBE] ${e.message?.take(80)}"
                            partialTextState.value = ""
                        }
                        tracker.markAsr("")
                        return@launch
                    }
                }
                var asrText = finalCommitted
                // Fallback: committed empty but live partial had text (VAD threshold miss) — use it.
                if (asrText.isBlank()) {
                    val partialFallback = partialTextState.value.trim()
                    if (partialFallback.isNotBlank()) {
                        Log.w(TAG_ASR, "committed empty but partial fallback \"$partialFallback\" pcmFinal=${pcmFinal.size}")
                        asrText = partialFallback
                    }
                }
                if (asrText.isBlank()) {
                    val modelError = session?.lastDecodeError
                    val report = session?.signalReport() ?: "no session"
                    val peakDb = com.vachak.ml.VachakAudio.rmsToDb(session?.maxRmsSeen ?: audioCapturer.peakRms)
                    val micSilent = (session?.maxRmsSeen ?: 0f) < com.vachak.ml.VachakAudio.DIGITAL_SILENCE_RMS && audioCapturer.peakRms < com.vachak.ml.VachakAudio.DIGITAL_SILENCE_RMS
                    Log.e(TAG_ASR, "stop empty: modelError=$modelError micSilent=$micSilent silentStart=${audioCapturer.silentStart} src=${audioCapturer.audioSourceUsed} $report pcm=${pcmFinal.size}")
                    withContext(Dispatchers.Main) {
                        partialTextState.value = ""
                        // Three-way diagnosis (never a bare "no speech" again):
                        // MODEL = decoder threw; MIC = stream was digital silence
                        // (muted route / BT / other app); VAD = audible but undecodable.
                        asrError = when {
                            modelError != null -> "[ASR:MODEL] Decode failed — $modelError"
                            micSilent -> "[ASR:MIC] Microphone delivered silence (${pcmFinal.size} samples, peak $peakDb). Check: hardware mute shutter? Bluetooth earpiece routed elsewhere? Another app holding the mic? Then retry."
                            else -> "[ASR:VAD] Heard you (peak $peakDb) but no words decoded — speak closer and louder, then retry. (${pcmFinal.size} samples)"
                        }
                        ttsMessage = null
                    }
                    Log.d(TAG_VAD, "VAD silence — empty for ${pcmFinal.size} samples")
                    tracker.markAsr("")
                    return@launch
                }
                // Clear prior ASR error on success
                withContext(Dispatchers.Main) { asrError = null }
                tracker.markAsr(asrText)
                val asrMs = (SystemClock.elapsedRealtimeNanos() - startTotal) / 1_000_000
                Log.d(TAG_LAT, "VOICE END ASR ${asrMs}ms -> \"$asrText\"")
                withContext(Dispatchers.Main) { partialTextState.value = "" }
                // append as conversation item and translate with latency tracking
                // translateAndAppend owns isTranslating true/false + TTS
                withContext(Dispatchers.Main) { translateAndAppend(asrText, tracker) }
                // total latency from speech end to first audio
                val totalMs = (SystemClock.elapsedRealtimeNanos() - startTotal) / 1_000_000
                withContext(Dispatchers.Main) {
                    latencyMs = totalMs
                    Log.d(TAG_LAT, "VOICE END total ${totalMs}ms (${if (totalMs < 3000) "< 3s ✓" else "≥ 3s ⚠"})")
                }
                Log.d(TAG_MT, "stopRecording pipeline handed off to translateAndAppend asrText=\"$asrText\" totalMs=$totalMs")
            } catch (e: Exception) {
                Log.e(TAG_ASR, "stop pipeline crashed (mic still released)", e)
                withContext(Dispatchers.Main) {
                    asrError = "[ASR:STOP] ${e.message?.take(80)}"
                    partialTextState.value = ""
                }
            } finally {
                // GUARANTEE: mic state always resets — button can never stay dead.
                withContext(Dispatchers.Main) {
                    isStopping = false
                    streamingSession = null
                    if (!isTranslating) isTranslating = false
                    Log.d(TAG_ASR, "stop finally isStopping=false isTranslating=$isTranslating")
                }
            }
        }
    }

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
                                Icon(Icons.Outlined.BugReport, null, tint = if (asrError != null || livePreviewError != null) MaterialTheme.colorScheme.error else VachakColors.TextPrimary)
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
                                    if (!selected && !isListening && !isStopping) {
                                        Log.d(TAG_MT, "Live toggle -> $code ($name)")
                                        ActiveLanguage.set(code)
                                        (engine.translation as? com.vachak.ml.adapter.AdapterTranslationEngine)?.setActiveLanguage(code)
                                    } else {
                                        Log.d(TAG_MT, "toggle ignored selected=$selected listening=$isListening stopping=$isStopping")
                                    }
                                },
                                enabled = !isListening && !isStopping,
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
            if (isListening) {
                MicMeter(rms = meterRms)
            }
            VoiceArea(
                hasConversation = hasConversation,
                isListening = isListening,
                isTranslating = isTranslating,
                isStopping = isStopping,
                partialTextState = partialTextState,
                hasMicPermission = hasMicPermission,
                activeLang = activeLang,
                onStart = { startRecordingUktamStyle() },
                onStop = { stopRecordingAndProcessUktamStyle() },
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
            )
            if (asrError != null) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), shape = RoundedCornerShape(12.dp)) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Text(asrError ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                        TextButton(onClick = { asrError = null }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Dismiss", style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
            HorizontalDivider(color = VachakColors.Border, thickness = 0.8.dp)

            // Conversation (live.md §4 — primary content, LazyColumn)
            if (!hasConversation && !isListening) {
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
                            onPlayHindi = { playPcm(item.hindiText, "hi") },
                            onPlaySantali = { item.santaliText?.let { playPcm(it, activeLang) } },
                            targetLabel = ActiveLanguage.label(activeLang),
                            onRetry = { // retry translation
                                val idx = LiveConversationStore.items.indexOfFirst { it.id == item.id }
                                if (idx != -1) {
                                    LiveConversationStore.items[idx] = item.copy(isTranslating = true, error = null)
                                    scope.launch(Dispatchers.IO) {
                                        val mt = engine.translation.translate(item.hindiText, LanguagePair("hi", activeLang))
                                        withContext(Dispatchers.Main) {
                                            val i = LiveConversationStore.items.indexOfFirst { it.id == item.id }
                                            if (i != -1) {
                                                when (mt) {
                                                    is EngineResult.Ok -> LiveConversationStore.items[i] = LiveConversationStore.items[i].copy(santaliText = mt.value, isTranslating = false, error = null)
                                                    is EngineResult.Err -> LiveConversationStore.items[i] = LiveConversationStore.items[i].copy(isTranslating = false, error = mt.message)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }
                    item {
                        InlineTranscription(partialTextState, isListening)
                    }
                    if (latencyMs != null || ttsMessage != null) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                latencyMs?.let { ms ->
                                    val within = ms < 3000
                                    Surface(shape = RoundedCornerShape(50), color = if (within) VachakColors.SuccessLight else MaterialTheme.colorScheme.errorContainer) {
                                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(if (within) "✓" else "⚠", style = MaterialTheme.typography.labelSmall, color = if (within) VachakColors.Success else MaterialTheme.colorScheme.error)
                                            Text("${ms}ms ${if (within) "< 3s" else "≥ 3s"}", style = MaterialTheme.typography.labelSmall, color = if (within) VachakColors.Success else MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                                ttsMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary) }
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
                            if (livePreviewLoading) CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = VachakColors.Lavender600)
                        }
                        when {
                            livePreviewLoading -> Text("Translating…", style = MaterialTheme.typography.bodyMedium, color = VachakColors.TextSecondary)
                            livePreviewError != null -> Text(livePreviewError ?: "Error", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            livePreview != null -> Text(livePreview!!, style = MaterialTheme.typography.bodyLarge, color = VachakColors.TextPrimary, fontSize = 18.sp, lineHeight = 26.sp)
                            else -> Text("type Hindi to see ${ActiveLanguage.label(activeLang)}", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                        }
                        livePreview?.let { sat ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { scope.launch(Dispatchers.IO) { engine.tts.synthesize(sat, activeLang).let { if (it is EngineResult.Ok) tryPlayPcm(it.value, VachakAudio.TTS_OUTPUT_HZ) } } }, contentPadding = PaddingValues(0.dp)) {
                                    Icon(Icons.AutoMirrored.Outlined.VolumeUp, null, tint = VachakColors.DeepLavender, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp)); Text("Play", style = MaterialTheme.typography.labelMedium, color = VachakColors.DeepLavender)
                                }
                                TextButton(onClick = { val t = manualHindi.trim(); if (t.isNotBlank()) { translateAndAppend(t); manualHindi = ""; livePreview = null } }, contentPadding = PaddingValues(0.dp)) {
                                    Text("Add to conversation", style = MaterialTheme.typography.labelMedium, color = VachakColors.Lavender600)
                                }
                            }
                        }
                    }
                }
            }

            // Input bar (live.md §23) — wired to IndicTrans2Adapter via EngineProvider.real (MainActivity.kt:24)
            LiveInputBar(
                text = manualHindi,
                onTextChange = { manualHindi = it },
                onTranslate = {
                    val t = manualHindi.trim()
                    if (t.isNotBlank()) { translateAndAppend(t); manualHindi = ""; livePreview = null }
                },
                enabled = !isListening,
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
                    Text("ASR: ${asrError ?: "OK — no error (VAD active)"}", style = MaterialTheme.typography.bodySmall, color = if (asrError != null) MaterialTheme.colorScheme.error else VachakColors.TextSecondary)
                    Text("MT live: ${livePreviewError ?: livePreview ?: "idle — type Hindi or speak"}", style = MaterialTheme.typography.bodySmall, color = if (livePreviewError != null) MaterialTheme.colorScheme.error else VachakColors.TextSecondary)
                    Text("MT conv: ${conversation.lastOrNull()?.error ?: conversation.lastOrNull()?.santaliText?.take(30) ?: "no conv"}", style = MaterialTheme.typography.bodySmall)
                    Text("TTS: ${ttsMessage ?: "idle"}", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                    Text("States: listening=$isListening translating=$isTranslating conv=${conversation.size}", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary)
                    Text("Latency: ${latencyMs?.let { "$it ms ${if (it < 3000) "< 3s ✓" else "≥ 3s ⚠"}" } ?: "not measured"}", style = MaterialTheme.typography.bodySmall, color = if (latencyMs != null && latencyMs!! < 3000) VachakColors.Success else MaterialTheme.colorScheme.error)
                    Text("ABI: ${android.os.Build.SUPPORTED_ABIS.joinToString()}", style = MaterialTheme.typography.labelSmall, color = VachakColors.TextSecondary)
                    Text("Logcat: adb logcat -s Vachak-MT:V Vachak-ASR:V Vachak-VAD:V Vachak-TTS:V Vachak-Latency:V", style = MaterialTheme.typography.labelSmall, color = VachakColors.TextSecondary)
                    Text("Pipeline: App input → preprocess→BPE(245k)→encoder[1,seq,512]→decoder past KV→postprocess→OlChiki U+1C50", style = MaterialTheme.typography.labelSmall, color = VachakColors.TextSecondary)
                }
            },
            confirmButton = { TextButton(onClick = { showDebugDialog = false }) { Text("Close") } },
            dismissButton = { TextButton(onClick = { asrError = null; livePreviewError = null; showDebugDialog = false }) { Text("Clear errors") } }
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
    partialTextState: androidx.compose.runtime.State<String>,
    isListening: Boolean
) {
    val text by partialTextState
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
    partialTextState: androidx.compose.runtime.State<String>,
    hasMicPermission: Boolean,
    activeLang: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRequestPermission: () -> Unit
) {
    val partialText by partialTextState
    val haptic = LocalHapticFeedback.current
    Log.d(TAG_ASR, "VoiceArea recompose isListening=$isListening isStopping=$isStopping isTranslating=$isTranslating hasMic=$hasMicPermission")
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        val voiceHeight = (maxHeight * if (!hasConversation && !isListening) 0.40f else 0.28f).coerceIn(120.dp, 220.dp)
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
                Text("Tap mic or Stop to finish", style = MaterialTheme.typography.bodySmall, color = VachakColors.TextSecondary, maxLines = 1)
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
            // Stop path always tappable: while listening OR draining (isStopping),
            // the button must stay enabled. Only disabled for idle+translating.
            // isStopping taps are ignored inside onStop (re-entrancy guard).
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
                modifier = Modifier.fillMaxWidth().height(44.dp),
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
    }
}

private fun tryPlayPcm(pcm: ShortArray, sampleRate: Int = VachakAudio.TTS_OUTPUT_HZ) {
    if (pcm.isEmpty()) return
    try {
        val minBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        // Streaming avoids MODE_STATIC buffer copy and allows chunked write, lower latency on 2GB
        val track = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf.coerceAtLeast(pcm.size * 2),
            AudioTrack.MODE_STREAM
        )
        track.play()
        var offset = 0
        val chunk = 2048
        while (offset < pcm.size) {
            val len = minOf(chunk, pcm.size - offset)
            track.write(pcm, offset, len)
            offset += len
        }
        // Release audio track asynchronously after playback duration
        // This decouples the sleep from the pipeline latency measurement
        Thread {
            Thread.sleep((pcm.size * 1000L / sampleRate).coerceAtMost(4000))
            try { track.stop() } catch (e: Exception) { Log.w(TAG_TTS, "track.stop failed: ${e.message}") }
            try { track.release() } catch (e: Exception) { Log.w(TAG_TTS, "track.release failed: ${e.message}") }
        }.start()
    } catch (e: Exception) {
        // Playback failure must be visible: the taps-Play-hears-nothing mystery.
        Log.e(TAG_TTS, "tryPlayPcm failed (${pcm.size} samples @${sampleRate}Hz)", e)
    }
}
