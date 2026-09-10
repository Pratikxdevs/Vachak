package com.vachak.ui.screens

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vachak.engine.ActiveLanguage
import com.vachak.engine.EngineProvider
import com.vachak.engine.EngineResult
import com.vachak.engine.LanguagePair
import com.vachak.ml.AudioCapturer
import com.vachak.ml.LatencySample
import com.vachak.ml.LatencyTracker
import com.vachak.ml.VachakAudio
import com.vachak.ml.StreamingAsrSession
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * LiveViewModel — owns LiveScreen pipeline via StateFlow UiState.
 * Migrated from God LiveScreen 676 lines: debounce 600, AudioCapturer, StreamingAsrSession,
 * LatencyTracker, ensureAdapterExtracted moved to viewModelScope, sequential pipeline
 * limitedParallelism(1) + Mutex for ASR->MT->TTS never parallel.
 */
data class LiveUiState(
    val isListening: Boolean = false,
    /** Stop tapped, pipeline draining (finish decode can take seconds). */
    val isStopping: Boolean = false,
    val partialText: String = "",
    val committedText: String = "",
    // Holds the active-target translation (Santali by default, Mundari if toggled).
    // Named santaliText for compat with existing collectors; label via ActiveLanguage.
    val santaliText: String? = null,
    val latencyMs: Long? = null,
    val error: String? = null,
    val isTranslating: Boolean = false,
    val livePreview: String? = null,
    val livePreviewLoading: Boolean = false,
    val livePreviewError: String? = null,
    val ttsMessage: String? = null,
    val asrError: String? = null
)

@HiltViewModel
class LiveViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engineProvider: EngineProvider
) : ViewModel() {

    // Single AudioCapturer for the Live graph lifetime — sequential pipeline (never parallel)
    val audioCapturer: AudioCapturer = AudioCapturer()

    // Minimal observable for leak diagnostics (not yet driving UI; kept for next slice)
    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    // Primary UiState — StateFlow WhileSubscribed(5000)
    private val _uiState = MutableStateFlow(LiveUiState())
    val uiState: StateFlow<LiveUiState> = _uiState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LiveUiState())
        .let { it } // keep StateFlow; _uiState is source
    // Expose direct for collect without extra stateIn double-wrap
    val uiStateDirect: StateFlow<LiveUiState> = _uiState.asStateFlow()

    // Sequential pipeline: limitedParallelism(1) + Mutex ensures ASR->MT->TTS never parallel (RAM limit)
    private val pipelineDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val pipelineMutex = Mutex()

    private var streamingSession: StreamingAsrSession? = null
    private var t0Ns: Long? = null
    private var debounceJob: Job? = null
    private var manualHindiFlow = MutableStateFlow("")
    /** Samples already fed to the session (partial loop + stop tail share it — never double-feed). */
    private var lastPushedSample: Int = 0
    private var partialJob: Job? = null
    private var meterJob: Job? = null

    /** Live mic level for the meter (polled off-Main; collectors only re-render the meter). */
    private val _meterRms = MutableStateFlow(0f)
    val meterRms = _meterRms.asStateFlow()

    init {
        // Debounce 600ms live typed translation moved to VM
        viewModelScope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            manualHindiFlow.debounce(600).collect { q ->
                val trimmed = q.trim()
                if (trimmed.isBlank() || trimmed.length < 2) {
                    _uiState.value = _uiState.value.copy(livePreview = null, livePreviewError = null, livePreviewLoading = false)
                    return@collect
                }
                _uiState.value = _uiState.value.copy(livePreviewLoading = true, livePreviewError = null)
                withContext(pipelineDispatcher) {
                    pipelineMutex.withLock {
                        val activeLang = ActiveLanguage.current
                        val res = engineProvider.translation.translate(trimmed, LanguagePair("hi", activeLang))
                        withContext(Dispatchers.Main) {
                            if (trimmed != manualHindiFlow.value.trim()) {
                                _uiState.value = _uiState.value.copy(livePreviewLoading = false)
                                return@withContext
                            }
                            when (res) {
                                is EngineResult.Ok -> _uiState.value = _uiState.value.copy(livePreview = res.value, livePreviewError = null, livePreviewLoading = false)
                                is EngineResult.Err -> _uiState.value = _uiState.value.copy(livePreview = null, livePreviewError = res.message, livePreviewLoading = false)
                            }
                        }
                    }
                }
            }
        }
    }

    fun markCapturing(active: Boolean) {
        _isCapturing.value = active
    }

    // Intents — SINGLE mic owner alongside LiveScreen's Uktam-style path.
    // Both share this ViewModel's audioCapturer, so re-entrancy guards below
    // make a double-tap / double-owner start a no-op instead of a mic fight.
    fun onStart() {
        if (audioCapturer.isRecording()) {
            android.util.Log.d("Vachak-ASR", "LiveViewModel.onStart ignored — already capturing")
            return
        }
        val t0 = SystemClock.elapsedRealtimeNanos()
        t0Ns = t0
        _uiState.value = _uiState.value.copy(isListening = true, partialText = "", error = null, asrError = null, ttsMessage = null, latencyMs = null)
        _isCapturing.value = true
        android.util.Log.d("Vachak-ASR", "LiveViewModel.onStart t0=$t0")
        viewModelScope.launch(pipelineDispatcher) {
            val session = StreamingAsrSession(context)
            session.start(t0)
            // Capture FIRST, warm second (same rationale as LiveScreen: meter
            // alive + audio accumulating during the seconds-long warm-up).
            audioCapturer.startRecording(context)
            // startRecording() NEVER throws — it fails silently. Detect it here or
            // UI shows "listening" forever with zero samples (emulator mic busy).
            if (!audioCapturer.isRecording()) {
                android.util.Log.e("Vachak-ASR", "LiveViewModel: AudioCapturer failed to start (mic busy/denied?)")
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isListening = false,
                        asrError = "[ASR:MIC] Microphone failed to start — grant permission or free the mic (emulator: enable virtual mic)"
                    )
                    _isCapturing.value = false
                    streamingSession = null
                }
                return@launch
            }
            withContext(Dispatchers.Main) {
                streamingSession = session
            }
            lastPushedSample = 0
            startPartialLoop()
            startMeterLoop()
            try { session.warmUpAsync() } catch (e: Throwable) { android.util.Log.w("Vachak-ASR", "LiveViewModel warmUp threw (first decode will cold-load)", e) }
        }
    }

    /**
     * Live-subtitle loop (was LiveScreen's LaunchedEffect): every 700ms feeds
     * fresh capture into the session and publishes partials. Owned here so the
     * stop path can cancel it BEFORE pushing the tail (no double-feed, ever).
     */
    private fun startPartialLoop() {
        partialJob?.cancel()
        partialJob = viewModelScope.launch(Dispatchers.Default) {
            var errShown = false
            while (isActive) {
                delay(com.vachak.ml.VachakAudio.PARTIAL_DECODE_MS)
                val session = streamingSession ?: continue
                val snap = try {
                    withContext(Dispatchers.Default) { audioCapturer.snapshotShortArray() }
                } catch (e: Exception) {
                    android.util.Log.e("Vachak-ASR", "VM partial snapshot failed", e)
                    continue
                }
                if (snap.size <= lastPushedSample) continue
                val chunk = snap.copyOfRange(lastPushedSample, snap.size)
                lastPushedSample = snap.size
                try {
                    session.pushAudio(chunk)
                    val partial = session.getPartial()
                    if (partial.isNotBlank()) {
                        errShown = false
                        _uiState.value = _uiState.value.copy(partialText = partial)
                        if (_uiState.value.asrError?.startsWith("[ASR:LIVE]") == true) {
                            _uiState.value = _uiState.value.copy(asrError = null)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("Vachak-ASR", "VM partial iteration failed (loop survives)", e)
                    if (!errShown) {
                        errShown = true
                        _uiState.value = _uiState.value.copy(asrError = "[ASR:LIVE] ${e.message?.take(80)} — still listening…")
                    }
                }
            }
        }
    }

    /** 150ms mic-level poll for the meter (cheap volatile read). */
    private fun startMeterLoop() {
        meterJob?.cancel()
        _meterRms.value = 0f
        meterJob = viewModelScope.launch {
            while (isActive) {
                delay(150)
                _meterRms.value = audioCapturer.lastRms
            }
        }
    }

    private fun stopLoops() {
        partialJob?.cancel()
        partialJob = null
        meterJob?.cancel()
        meterJob = null
        _meterRms.value = 0f
    }

    fun onStop(onCommitted: ((String) -> Unit)? = null) {
        // Snapshot BEFORE clearing: the stop-clear wipes partialText, which
        // used to starve the fallback below (recoverable partials -> VAD-silence).
        val preStopPartial = _uiState.value.partialText.trim()
        // Loops stop FIRST so the tail push below can't double-feed the session.
        stopLoops()
        _uiState.value = _uiState.value.copy(isListening = false, isStopping = true, isTranslating = true, partialText = "")
        viewModelScope.launch(pipelineDispatcher) {
            pipelineMutex.withLock {
                val tracker = LatencyTracker(LatencySample(runId = "live-${SystemClock.elapsedRealtimeNanos()}"))
                val sessionT0 = t0Ns ?: SystemClock.elapsedRealtimeNanos()
                if (t0Ns == null) tracker.markSpeechBegin()
                val pcmFinal = try {
                    withContext(Dispatchers.IO) { audioCapturer.stopAndGetShortArray() }
                } catch (e: Exception) {
                    android.util.Log.e("Vachak-ASR", "LiveViewModel stopAndGetShortArray failed", e)
                    ShortArray(0)
                }
                val session = streamingSession
                var finalCommitted = ""
                if (session != null) {
                    // Tail ONLY from where the partial loop stopped (shared
                    // lastPushedSample — never re-feed from 0, never double-decode).
                    try {
                        if (pcmFinal.size > lastPushedSample) {
                            val tail = pcmFinal.copyOfRange(lastPushedSample, pcmFinal.size)
                            android.util.Log.d("Vachak-ASR", "LiveViewModel pushing tail ${tail.size} samples (already pushed $lastPushedSample)")
                            session.pushAudio(tail)
                            lastPushedSample = pcmFinal.size
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("Vachak-ASR", "LiveViewModel session push failed", e)
                    }
                    try {
                        finalCommitted = session.finish()
                    } catch (e: Exception) {
                        android.util.Log.e("Vachak-ASR", "LiveViewModel session.finish failed", e)
                        finalCommitted = ""
                    }
                    // Fallback: committed empty but live partial had text (VAD miss).
                    // Uses the pre-stop snapshot (the live field is already cleared).
                    if (finalCommitted.isBlank()) {
                        if (preStopPartial.isNotBlank()) {
                            android.util.Log.w("Vachak-ASR", "LiveViewModel committed empty, partial fallback \"$preStopPartial\"")
                            finalCommitted = preStopPartial
                        }
                    }
                    android.util.Log.d("Vachak-ASR", "LiveViewModel streaming finish committed=\"$finalCommitted\" pcmFinal=${pcmFinal.size}")
                } else {
                    val res = engineProvider.asr.transcribe(pcmFinal, 16000)
                    when (res) {
                        is EngineResult.Ok -> finalCommitted = res.value.trim()
                        is EngineResult.Err -> {
                            withContext(Dispatchers.Main) {
                                _uiState.value = _uiState.value.copy(asrError = "[ASR:${res.code}] ${res.message}", isTranslating = false, partialText = "")
                            }
                            tracker.markAsr("")
                            streamingSession = null
                            _isCapturing.value = false
                            return@withLock
                        }
                    }
                }
                if (finalCommitted.isBlank()) {
                    val modelError = session?.lastDecodeError
                    val peakDb = com.vachak.ml.VachakAudio.rmsToDb(session?.maxRmsSeen ?: audioCapturer.peakRms)
                    val micSilent = (session?.maxRmsSeen ?: 0f) < com.vachak.ml.VachakAudio.DIGITAL_SILENCE_RMS && audioCapturer.peakRms < com.vachak.ml.VachakAudio.DIGITAL_SILENCE_RMS
                    android.util.Log.e("Vachak-ASR", "stop empty: modelError=$modelError micSilent=$micSilent silentStart=${audioCapturer.silentStart} src=${audioCapturer.audioSourceUsed} audit=${audioCapturer.lastMicAudit} ${session?.signalReport()}")
                    val auditCulprit = audioCapturer.lastMicAudit?.messageForUser(pcmFinal.size, peakDb)
                    withContext(Dispatchers.Main) {
                        // Same three-way diagnosis as LiveScreen (see there).
                        _uiState.value = _uiState.value.copy(
                            asrError = when {
                                modelError != null -> "[ASR:MODEL] Decode failed — $modelError"
                                auditCulprit != null -> "[ASR:MIC] $auditCulprit"
                                micSilent -> "[ASR:MIC] Microphone delivered silence (${pcmFinal.size} samples, peak $peakDb). Check Quick Settings mic toggle, hardware mute, Bluetooth route, or another app holding the mic."
                                else -> "[ASR:VAD] Heard you (peak $peakDb) but no words decoded — speak closer and louder, then retry. (${pcmFinal.size} samples)"
                            },
                            isTranslating = false, partialText = ""
                        )
                    }
                    tracker.markAsr("")
                    streamingSession = null
                    _isCapturing.value = false
                    return@withLock
                }
                withContext(Dispatchers.Main) { _uiState.value = _uiState.value.copy(asrError = null) }
                tracker.markAsr(finalCommitted)
                val activeLang = ActiveLanguage.current
                val mt = try {
                    kotlinx.coroutines.withTimeout(30_000) {
                        engineProvider.translation.translate(finalCommitted, LanguagePair("hi", activeLang))
                    }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    android.util.Log.e("Vachak-MT", "MT timed out after 30s", e)
                    EngineResult.Err(com.vachak.engine.EngineError.TIMEOUT, "Translation timed out (30s) — tap retry")
                }
                val translated: String?
                when (mt) {
                    is EngineResult.Ok -> {
                        translated = mt.value
                        tracker.markTranslate(translated)
                        // Wire result into the shared conversation store so the
                        // LiveScreen list shows it no matter which owner ran the mic.
                        val itemId = "msg-${SystemClock.elapsedRealtimeNanos()}"
                        withContext(Dispatchers.Main) {
                            _uiState.value = _uiState.value.copy(committedText = finalCommitted, santaliText = translated, error = null)
                            LiveConversationStore.items.add(
                                ConversationItem(
                                    id = itemId,
                                    hindiText = finalCommitted,
                                    santaliText = translated,
                                    timestampMillis = System.currentTimeMillis(),
                                    isTranslating = false
                                )
                            )
                        }
                        // TTS sequential
                        tracker.markTtsBegin()
                        val pcmRes = engineProvider.tts.synthesize(translated, activeLang)
                        tracker.markAudioBegin()
                        // Measured stages -> item footer + last-run holder (never canned).
                        val stages = tracker.result().stageMs()
                        val total = tracker.result().endToEndMs()?.toLong()
                        com.vachak.ml.LastPipelineRun.publish(tracker)
                        // Playback stays on IO: AudioTrack.write blocks up to seconds (ANR on Main).
                        when (pcmRes) {
                            is EngineResult.Ok -> {
                                playPcmLocal(pcmRes.value, VachakAudio.TTS_OUTPUT_HZ)
                                withContext(Dispatchers.Main) {
                                    val i = LiveConversationStore.items.indexOfFirst { it.id == itemId }
                                    if (i != -1) {
                                        LiveConversationStore.items[i] = LiveConversationStore.items[i].copy(
                                            asrMs = stages["asr"]?.toLong(),
                                            mtMs = stages["translate"]?.toLong(),
                                            ttsMs = stages["tts"]?.toLong(),
                                            totalMs = total
                                        )
                                    }
                                    _uiState.value = _uiState.value.copy(ttsMessage = "Playing ${ActiveLanguage.label(activeLang)} audio • ${pcmRes.value.size} samples @${VachakAudio.TTS_OUTPUT_HZ}Hz")
                                }
                            }
                            is EngineResult.Err -> withContext(Dispatchers.Main) {
                                _uiState.value = _uiState.value.copy(ttsMessage = "TTS: ${pcmRes.message}")
                            }
                        }
                    }
                    is EngineResult.Err -> {
                        withContext(Dispatchers.Main) {
                            _uiState.value = _uiState.value.copy(committedText = finalCommitted, error = mt.message)
                        }
                        tracker.markTranslate("")
                    }
                }
                val totalMs = (SystemClock.elapsedRealtimeNanos() - sessionT0) / 1_000_000
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(latencyMs = totalMs, isTranslating = false, isStopping = false, partialText = "")
                    android.util.Log.d("Vachak-Latency", "VOICE END total ${totalMs}ms (${if (totalMs < 3000) "< 3s ✓" else "≥ 3s ⚠"})")
                    onCommitted?.invoke(finalCommitted)
                    streamingSession = null
                    _isCapturing.value = false
                }
            }
        }
    }

    fun onTextChange(text: String) {
        manualHindiFlow.value = text
        // also update committedText mirror for LiveScreen compat
        _uiState.value = _uiState.value.copy(committedText = text)
    }

    /**
     * Typed Hindi → translate → conversation + TTS. The single typed path
     * (screen's old translateAndAppend retired here): sequential under the
     * pipeline Mutex, 30s MT bound, measured timings on the item.
     */
    fun commitTyped(hindi: String) {
        val trimmed = hindi.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch(pipelineDispatcher) {
            pipelineMutex.withLock {
                val itemId = "msg-${SystemClock.elapsedRealtimeNanos()}"
                withContext(Dispatchers.Main) {
                    LiveConversationStore.items.add(
                        ConversationItem(
                            id = itemId, hindiText = trimmed, santaliText = null,
                            timestampMillis = System.currentTimeMillis(), isTranslating = true
                        )
                    )
                    _uiState.value = _uiState.value.copy(isTranslating = true, error = null)
                }
                val tracker = LatencyTracker(LatencySample(runId = "typed-${SystemClock.elapsedRealtimeNanos()}")).also { it.markSpeechBegin() }
                val activeLang = ActiveLanguage.current
                val mt = try {
                    kotlinx.coroutines.withTimeout(30_000) {
                        engineProvider.translation.translate(trimmed, LanguagePair("hi", activeLang))
                    }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    android.util.Log.e("Vachak-MT", "typed MT timed out after 30s", e)
                    EngineResult.Err(com.vachak.engine.EngineError.TIMEOUT, "Translation timed out (30s) — tap retry")
                }
                when (mt) {
                    is EngineResult.Ok -> {
                        tracker.markTranslate(mt.value)
                        withContext(Dispatchers.Main) {
                            val idx = LiveConversationStore.items.indexOfFirst { it.id == itemId }
                            if (idx != -1) {
                                LiveConversationStore.items[idx] = LiveConversationStore.items[idx].copy(
                                    santaliText = mt.value, isTranslating = false, error = null
                                )
                            }
                            _uiState.value = _uiState.value.copy(santaliText = mt.value, error = null)
                        }
                        tracker.markTtsBegin()
                        val pcmRes = engineProvider.tts.synthesize(mt.value, activeLang)
                        tracker.markAudioBegin()
                        val stages = tracker.result().stageMs()
                        val total = tracker.result().endToEndMs()?.toLong()
                        com.vachak.ml.LastPipelineRun.publish(tracker)
                        when (pcmRes) {
                            is EngineResult.Ok -> {
                                playPcmLocal(pcmRes.value, VachakAudio.TTS_OUTPUT_HZ)
                                withContext(Dispatchers.Main) {
                                    val i = LiveConversationStore.items.indexOfFirst { it.id == itemId }
                                    if (i != -1) {
                                        LiveConversationStore.items[i] = LiveConversationStore.items[i].copy(
                                            mtMs = stages["translate"]?.toLong(),
                                            ttsMs = stages["tts"]?.toLong(),
                                            totalMs = total
                                        )
                                    }
                                    _uiState.value = _uiState.value.copy(
                                        isTranslating = false,
                                        ttsMessage = "Playing ${ActiveLanguage.label(activeLang)} audio • ${pcmRes.value.size} samples"
                                    )
                                }
                            }
                            is EngineResult.Err -> withContext(Dispatchers.Main) {
                                _uiState.value = _uiState.value.copy(isTranslating = false, ttsMessage = "TTS: ${pcmRes.message}")
                            }
                        }
                    }
                    is EngineResult.Err -> {
                        android.util.Log.e("Vachak-MT", "typed MT failed ${mt.code}: ${mt.message}")
                        withContext(Dispatchers.Main) {
                            val idx = LiveConversationStore.items.indexOfFirst { it.id == itemId }
                            if (idx != -1) {
                                LiveConversationStore.items[idx] = LiveConversationStore.items[idx].copy(
                                    isTranslating = false, error = mt.message
                                )
                            }
                            _uiState.value = _uiState.value.copy(isTranslating = false, error = mt.message)
                        }
                    }
                }
            }
        }
    }

    /**
     * Store-aware translation retry for a conversation item (replaces the
     * screen-local retry that bypassed the pipeline Mutex).
     */
    fun retryItem(itemId: String, hindi: String) {
        viewModelScope.launch(pipelineDispatcher) {
            pipelineMutex.withLock {
                withContext(Dispatchers.Main) {
                    val idx = LiveConversationStore.items.indexOfFirst { it.id == itemId }
                    if (idx != -1) {
                        LiveConversationStore.items[idx] = LiveConversationStore.items[idx].copy(isTranslating = true, error = null)
                    }
                    _uiState.value = _uiState.value.copy(isTranslating = true, error = null)
                }
                val activeLang = ActiveLanguage.current
                val mt = try {
                    kotlinx.coroutines.withTimeout(30_000) {
                        engineProvider.translation.translate(hindi, LanguagePair("hi", activeLang))
                    }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    EngineResult.Err(com.vachak.engine.EngineError.TIMEOUT, "Translation timed out (30s) — tap retry")
                }
                withContext(Dispatchers.Main) {
                    val i = LiveConversationStore.items.indexOfFirst { it.id == itemId }
                    if (i != -1) {
                        when (mt) {
                            is EngineResult.Ok -> LiveConversationStore.items[i] =
                                LiveConversationStore.items[i].copy(santaliText = mt.value, isTranslating = false, error = null)
                            is EngineResult.Err -> LiveConversationStore.items[i] =
                                LiveConversationStore.items[i].copy(isTranslating = false, error = mt.message)
                        }
                    }
                    when (mt) {
                        is EngineResult.Ok -> _uiState.value = _uiState.value.copy(isTranslating = false, error = null)
                        is EngineResult.Err -> _uiState.value = _uiState.value.copy(isTranslating = false, error = mt.message)
                    }
                }
            }
        }
    }

    /**
     * Speak any text through TTS + local playback (message Play buttons, live
     * preview Play). Runs on IO, never the pipeline dispatcher (playback
     * outlasts inference; holding the Mutex would stall the pipeline).
     */
    fun playText(text: String, lang: String) {
        if (text.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val pcmRes = engineProvider.tts.synthesize(text, lang)
            when (pcmRes) {
                is EngineResult.Ok -> {
                    playPcmLocal(pcmRes.value, VachakAudio.TTS_OUTPUT_HZ)
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            ttsMessage = "Playing ${ActiveLanguage.label(lang)} audio • ${pcmRes.value.size} samples"
                        )
                    }
                }
                is EngineResult.Err -> withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(ttsMessage = "TTS: ${pcmRes.message}")
                }
            }
        }
    }

    /** Clear transient error lines (debug dialog Dismiss). Next action re-arms them. */
    fun dismissErrors() {
        _uiState.value = _uiState.value.copy(asrError = null, error = null, livePreviewError = null, ttsMessage = null)
    }

    fun ensureAdapterExtractedDebug(): String? {
        // Move ensureAdapterExtracted to VM viewModelScope helper — logs adapter present
        return try {
            val activeLang = ActiveLanguage.current
            val adapterPath = when (activeLang) { "sat_Olck" -> "modelpacks/santali_adapter" else -> "modelpacks/mundari_adapter" }
            val filesAdapter = java.io.File(context.filesDir, adapterPath)
            if (java.io.File(filesAdapter, "adapter_model.safetensors").exists()) filesAdapter.absolutePath else null
        } catch (_: Exception) { null }
    }

    fun forceReleaseMic() {
        try {
            stopLoops()
            _isCapturing.value = false
            _uiState.value = _uiState.value.copy(isListening = false, isStopping = false)
            audioCapturer.release()
            android.util.Log.d("Vachak-ASR", "LiveViewModel.forceReleaseMic() done")
        } catch (e: Exception) {
            android.util.Log.e("Vachak-ASR", "LiveViewModel.forceReleaseMic failed", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            android.util.Log.d("Vachak-ASR", "LiveViewModel.onCleared -> close capturer")
            audioCapturer.close()
        } catch (_: Exception) {}
    }

    /** Offline AudioTrack playback for the VM-owned pipeline (mirrors LiveScreen.tryPlayPcm). */
    private fun playPcmLocal(pcm: ShortArray, sampleRate: Int = VachakAudio.TTS_OUTPUT_HZ) {
        if (pcm.isEmpty()) return
        try {
            val minBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
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
            while (offset < pcm.size) {
                val len = minOf(2048, pcm.size - offset)
                track.write(pcm, offset, len)
                offset += len
            }
            Thread {
                Thread.sleep((pcm.size * 1000L / sampleRate).coerceAtMost(4000))
                try { track.stop() } catch (_: Exception) {}
                try { track.release() } catch (_: Exception) {}
            }.start()
        } catch (e: Exception) {
            android.util.Log.e("Vachak-TTS", "LiveViewModel playback failed", e)
        }
    }
}
