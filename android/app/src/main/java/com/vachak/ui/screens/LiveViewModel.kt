package com.vachak.ui.screens

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
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
    /** Hindi committed in chunks WHILE listening (3s rhythm). Shown live;
     * full text still goes through MT at Stop. */
    val liveHindi: String = "",
    val committedText: String = "",
    // Holds the active-target translation (Santali by default, Mundari if toggled).
    // Named santaliText for compat with existing collectors; label via ActiveLanguage.
    val santaliText: String? = null,
    val latencyMs: Long? = null,
    val error: String? = null,
    val isTranslating: Boolean = false,
    /** Phase 4: MT done, blocking TTS generate() running. The MT→TTS wait
     * used to look hung (placeholder showed translated text with no status).
     * Mic gating still rides on isTranslating (unchanged). */
    val isSynthesizing: Boolean = false,
    val livePreview: String? = null,
    val livePreviewLoading: Boolean = false,
    val livePreviewError: String? = null,
    val ttsMessage: String? = null,
    val asrError: String? = null,
    /** Wall-clock ms when the current listen started (null when idle) — drives the mm:ss timer. */
    val listeningSinceMs: Long? = null,
    /** Stop-tap → translated-text-visible ms (the <3s headline: ASR+MT only, voice separate). */
    val translateMs: Long? = null
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
    private var laneJob: Job? = null
    private var meterJob: Job? = null
    /** Lane generation: stopLoops() bumps it so zombie lanes (a lane blocked
     * in native decode ignores cancellation) exit without touching the next
     * press — the old second-run corruption (stale lane pushing into the new
     * session) is impossible. Checked alongside session identity each tick. */
    private var laneGen = 0
    /** Stop-tap nanos (Main thread) — benchmark T0 for stop→translate. */
    private var stopTapNs: Long = 0L
    /**
     * Guards [streamingSession]: the capture lane pushes (milliseconds) while
     * the decode lane runs multi-second forward passes. Capture ticks may wait
     * on it — audio itself is never lost (the device buffer keeps recording).
     */
    private val sessionLock = Any()

    private sealed interface LaneResult {
        data class Final(val committedNow: String) : LaneResult
        data class Partial(val text: String) : LaneResult
    }

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
        _uiState.value = _uiState.value.copy(isListening = true, isStopping = false, isSynthesizing = false, partialText = "", liveHindi = "", committedText = "", santaliText = null, livePreview = null, livePreviewError = null, error = null, asrError = null, ttsMessage = null, latencyMs = null, translateMs = null, listeningSinceMs = System.currentTimeMillis())
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
                        listeningSinceMs = null,
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
            startCaptureLane()
            startDecodeLane()
            startMeterLoop()
            try { session.warmUpAsync() } catch (e: Throwable) { android.util.Log.w("Vachak-ASR", "LiveViewModel warmUp threw (first decode will cold-load)", e) }
        }
    }

    /**
     * CAPTURE lane (was the first half of startPartialLoop): every 700ms feeds
     * fresh mic audio into the session and surfaces newly committed 3s chunks.
     * Millisecond cost by design — it NEVER decodes, so cadence never stalls
     * behind a forward pass (the old single loop managed ~1 partial per press
     * on slow CPUs because each 2.8s decode serialized the next snapshot).
     */
    private fun startCaptureLane() {
        partialJob?.cancel()
        // Identity snapshot: a zombie of this lane (blocked in decode across
        // a stop) must never push into the NEXT press's session.
        val mySession = streamingSession
        val gen = laneGen
        if (mySession == null) return
        partialJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive && gen == laneGen) {
                delay(com.vachak.ml.VachakAudio.PARTIAL_DECODE_MS)
                if (streamingSession !== mySession || gen != laneGen) return@launch
                val session = mySession
                // Phase 2: delta read — one bounded copy of fresh samples only
                // (was: full-buffer copyOf + copyOfRange, O(total) per tick).
                val chunk = try {
                    audioCapturer.snapshotShortArrayFrom(lastPushedSample)
                } catch (e: Exception) {
                    // Stop cancels this loop: CancellationException is control
                    // flow, never an [ASR:LIVE] error (was screenshotted as one).
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    android.util.Log.e("Vachak-ASR", "VM capture snapshot failed", e)
                    continue
                }
                if (chunk.isEmpty()) continue
                lastPushedSample += chunk.size
                try {
                    val fresh = synchronized(sessionLock) {
                        session.pushAudio(chunk)
                        session.drainNewCommits()
                    }
                    if (fresh.isNotEmpty()) publishLiveHindi(fresh)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    android.util.Log.e("Vachak-ASR", "VM capture push failed (loop survives)", e)
                }
            }
        }
    }

    /**
     * DECODE lane: single coroutine, one forward pass at a time (sequential
     * RAM rule holds — never two models, never two decodes). Prefers pending
     * chunk finalizes, else a bounded window partial when warm. Slow decodes
     * simply lower its cadence; the capture lane is unaffected (coalescing by
     * construction, no explicit busy flag needed).
     */
    private fun startDecodeLane() {
        laneJob?.cancel()
        val mySession = streamingSession
        val gen = laneGen
        if (mySession == null) return
        laneJob = viewModelScope.launch(Dispatchers.Default) {
            var errShown = false
            while (isActive && gen == laneGen) {
                delay(250)
                if (streamingSession !== mySession || gen != laneGen) return@launch
                val session = mySession
                try {
                    if (!session.isWarm) continue
                    val result = synchronized(sessionLock) {
                        if (session.consumePendingFinalize()) {
                            session.finalizeCurrentSegment(true)
                            LaneResult.Final(session.committedText)
                        } else if (!session.isChunkImminent()) {
                            // Early-chunk preview only: starting a partial
                            // later would still be decoding when the commit
                            // becomes pending, starving it (logcat-proven).
                            LaneResult.Partial(session.getPartial())
                        } else {
                            // Chunk due: idle this tick, commit runs next.
                            LaneResult.Partial("")
                        }
                    }
                    when (result) {
                        is LaneResult.Final -> {
                            val fresh = synchronized(sessionLock) { session.drainNewCommits() }
                            if (fresh.isNotEmpty()) {
                                errShown = false
                                publishLiveHindi(fresh)
                                if (_uiState.value.asrError?.startsWith("[ASR:LIVE]") == true) {
                                    _uiState.value = _uiState.value.copy(asrError = null)
                                }
                            }
                        }
                        is LaneResult.Partial -> {
                            if (result.text.isNotBlank()) {
                                errShown = false
                                _uiState.value = _uiState.value.copy(partialText = result.text)
                                if (_uiState.value.asrError?.startsWith("[ASR:LIVE]") == true) {
                                    _uiState.value = _uiState.value.copy(asrError = null)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    android.util.Log.e("Vachak-ASR", "VM decode lane failed (lane survives)", e)
                    if (!errShown) {
                        errShown = true
                        _uiState.value = _uiState.value.copy(asrError = "[ASR:LIVE] ${e.message?.take(80)} — still listening…")
                    }
                }
            }
        }
    }

    /** Append freshly committed Hindi chunks to the live on-screen capture. */
    private fun publishLiveHindi(fresh: List<String>) {
        val prev = _uiState.value.liveHindi
        val joined = (if (prev.isBlank()) fresh else listOf(prev) + fresh).joinToString(" ").trim()
        _uiState.value = _uiState.value.copy(liveHindi = joined, partialText = "")
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
        laneJob?.cancel()
        laneJob = null
        meterJob?.cancel()
        meterJob = null
        _meterRms.value = 0f
        // Invalidate zombie lanes: cancel() can't interrupt a lane blocked in
        // native decode, so the generation bump + session-identity checks make
        // it exit at its next tick instead of poisoning the next press.
        laneGen++
    }

    fun onStop(onCommitted: ((String) -> Unit)? = null) {
        // P2: re-entrancy guard — Stop/Cancel taps while draining are ignored
        // instead of queueing a second pipeline run behind the first (the old
        // code's comment claimed this, but no guard existed).
        if (_uiState.value.isStopping) {
            android.util.Log.d("Vachak-ASR", "LiveViewModel.onStop ignored — already stopping")
            return
        }
        // Snapshot BEFORE clearing: the stop-clear wipes partialText, which
        // used to starve the fallback below (recoverable partials -> VAD-silence).
        val preStopPartial = _uiState.value.partialText.trim()
        // Loops stop FIRST so the tail push below can't double-feed the session.
        // Graceful stop: cancel only, NEVER join — a lane blocked in a
        // multi-second native decode ignores cancellation, and joining is what
        // made Stop hang in "Transcribing…" (zombies are generation-fenced).
        stopTapNs = SystemClock.elapsedRealtimeNanos()
        stopLoops()
        // KEEP the live partial on screen while draining: clearing it here
        // erased the transcription the user just saw for the whole stop->MT
        // window (seconds). The final block clears it after commit.
        _uiState.value = _uiState.value.copy(isListening = false, isStopping = true, isTranslating = true, partialText = preStopPartial)
        viewModelScope.launch(pipelineDispatcher) {
            // P1 holders: synth output crosses the lock boundary so blocking
            // AudioTrack playback never holds pipelineMutex (it stalled every
            // queued preview + the next mic tap for the whole utterance).
            var outPcm: ShortArray? = null
            var outPcmError: String? = null
            var outAutoOff = false
            var outStages: Map<String, Float?> = emptyMap()
            var outTtsSynthMs: Long? = null
            var outTotal: Long? = null
            var outItemId: String? = null
            var outLang = ActiveLanguage.current
            var outFinal = ""
            var outSessionT0 = 0L
            var outMtOk = false
            var stopCompleted = false
            pipelineMutex.withLock {
                val sessionT0 = t0Ns ?: SystemClock.elapsedRealtimeNanos()
                t0Ns = null // consume: a later stop without a fresh start must not reuse a stale T0
                val tracker = LatencyTracker(LatencySample(runId = "live-${SystemClock.elapsedRealtimeNanos()}", t0SpeechBegin = sessionT0))
                // Headline benchmark T0: the stop tap (ASR+MT only, voice separate).
                tracker.markStopTap(stopTapNs)
                val pcmFinal = try {
                    withContext(Dispatchers.IO) { audioCapturer.stopAndGetShortArray() }
                } catch (e: Exception) {
                    android.util.Log.e("Vachak-ASR", "LiveViewModel stopAndGetShortArray failed", e)
                    ShortArray(0)
                }
                val session = streamingSession
                var finalCommitted = ""
                if (session != null) {
                    // Tail ONLY from where the capture lane stopped (shared
                    // lastPushedSample — never re-feed from 0, never double-decode).
                    try {
                        if (pcmFinal.size > lastPushedSample) {
                            val tail = pcmFinal.copyOfRange(lastPushedSample, pcmFinal.size)
                            android.util.Log.d("Vachak-ASR", "LiveViewModel pushing tail ${tail.size} samples (already pushed $lastPushedSample)")
                            synchronized(sessionLock) { session.pushAudio(tail) }
                            lastPushedSample = pcmFinal.size
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("Vachak-ASR", "LiveViewModel session push failed", e)
                    }
                    // ONE bounded tail decode (the lane already committed 3s
                    // chunks live; this covers only the uncommitted remainder,
                    // so Stop costs ~1 decode, never a full-history re-decode).
                    // No lane joins. A throwing decoder yields "" and the live
                    // text fallback below still translates what was heard.
                    try {
                        synchronized(sessionLock) { session.finalizeCurrentSegment(true) }
                    } catch (e: Exception) {
                        android.util.Log.e("Vachak-ASR", "LiveViewModel tail finalize failed (live-text fallback)", e)
                    }
                    val fresh = synchronized(sessionLock) { session.drainNewCommits() }
                    if (fresh.isNotEmpty()) publishLiveHindi(fresh)
                    // Live text first (committed chunks + tail). The pre-stop
                    // partial is fallback ONLY (joining it would duplicate words
                    // the tail decode just committed).
                    finalCommitted = _uiState.value.liveHindi.trim()
                    if (finalCommitted.isBlank() && preStopPartial.isNotBlank()) {
                        android.util.Log.w("Vachak-ASR", "LiveViewModel tail empty, partial fallback \"$preStopPartial\"")
                        finalCommitted = preStopPartial
                    }
                    if (finalCommitted.isBlank()) {
                        finalCommitted = session.committedText.trim()
                    }
                    android.util.Log.d("Vachak-ASR", "LiveViewModel stop committed=\"$finalCommitted\" pcmFinal=${pcmFinal.size}")
                } else {
                    val res = engineProvider.asr.transcribe(pcmFinal, 16000)
                    when (res) {
                        is EngineResult.Ok -> finalCommitted = res.value.trim()
                        is EngineResult.Err -> {
                            withContext(Dispatchers.Main) {
                                _uiState.value = _uiState.value.copy(asrError = "[ASR:${res.code}] ${res.message}", isTranslating = false, isStopping = false, listeningSinceMs = null, partialText = "")
                            }
                            tracker.markAsr("")
                            streamingSession = null
                            _isCapturing.value = false
                            return@withLock
                        }
                    }
                }
                if (finalCommitted.isBlank()) {
                    // Graceful empty: nothing decodable was heard — reset
                    // silently (no error card, no conversation item, no hang).
                    // Next tap just works.
                    android.util.Log.d("Vachak-ASR", "LiveViewModel stop empty (silent reset) pcmFinal=${pcmFinal.size} ${session?.signalReport()}")
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            isListening = false, isStopping = false, isTranslating = false,
                            isSynthesizing = false, listeningSinceMs = null, partialText = ""
                        )
                    }
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
                // Placeholder FIRST (mirrors commitTyped): the conversation list
                // updates the moment Stop lands, even if MT takes seconds.
                val itemId = "msg-${SystemClock.elapsedRealtimeNanos()}"
                withContext(Dispatchers.Main) {
                    LiveConversationStore.items.add(
                        ConversationItem(
                            id = itemId,
                            hindiText = finalCommitted,
                            santaliText = null,
                            targetLang = activeLang,
                            timestampMillis = System.currentTimeMillis(),
                            isTranslating = true
                        )
                    )
                }
                when (mt) {
                    is EngineResult.Ok -> {
                        translated = mt.value
                        tracker.markTranslate(translated)
                        // Wire result into the shared conversation store so the
                        // LiveScreen list shows it no matter which owner ran the mic.
                        // Phase 4: text is visible BUT voice is still
                        // synthesizing — flag it so the MT→TTS wait never looks hung.
                        withContext(Dispatchers.Main) {
                            // Benchmark T1: translated text hits the screen HERE
                            // (ASR+MT headline — TTS starts after, measured apart).
                            tracker.markTranslateShown()
                            val shownMs = (tracker.result().stopToTranslateMs() ?: 0f).toLong()
                            android.util.Log.d("Vachak-Latency", "TRANSLATE SHOWN stop→text ${shownMs}ms (${if (shownMs < 3000) "< 3s ✓" else "≥ 3s ⚠"})")
                            _uiState.value = _uiState.value.copy(committedText = finalCommitted, santaliText = translated, error = null, isSynthesizing = true, translateMs = shownMs)
                            val i = LiveConversationStore.items.indexOfFirst { it.id == itemId }
                            if (i != -1) {
                                LiveConversationStore.items[i] = LiveConversationStore.items[i].copy(
                                    santaliText = translated,
                                    isTranslating = false,
                                    isSynthesizing = true
                                )
                            }
                        }
                        // TTS sequential (autoplay honors the Settings toggle).
                        tracker.markTtsBegin()
                        val autoPlay = try {
                            com.vachak.ui.prefs.VachakPrefs(context).autoPlayTts
                        } catch (_: Exception) { true }
                        val pcmRes = if (autoPlay) {
                            // Phase 4: a throwing TTS must surface as a message,
                            // never wedge isTranslating/isSynthesizing forever.
                            runCatching { engineProvider.tts.synthesize(translated, activeLang) }
                                .getOrElse { e ->
                                    android.util.Log.e("Vachak-TTS", "voice synth threw", e)
                                    EngineResult.Err(com.vachak.engine.EngineError.MODEL_DECODE_FAILED, "TTS failed: ${e.message?.take(120)}")
                                }
                        } else {
                            null
                        }
                        tracker.markAudioBegin()
                        // Phase 4: blocking generate() returned — synthesis is
                        // over (playback next). Clear the flag HERE, not after
                        // playback, so "Synthesizing…" never covers playback.
                        withContext(Dispatchers.Main) {
                            _uiState.value = _uiState.value.copy(isSynthesizing = false)
                            val i = LiveConversationStore.items.indexOfFirst { it.id == itemId }
                            if (i != -1) {
                                LiveConversationStore.items[i] = LiveConversationStore.items[i].copy(isSynthesizing = false)
                            }
                        }
                        // Measured stages -> item footer + last-run holder (never canned).
                        val stages = tracker.result().stageMs()
                        // Phase 4: TTS footer is the REAL synth cost (generate
                        // runs markTtsBegin->markAudioBegin); stages["tts"] is
                        // only the queue gap and reads ~0ms.
                        val ttsSynthMs = tracker.result().ttsSynthMs()?.toLong()
                        val total = tracker.result().endToEndMs()?.toLong()
                        com.vachak.ml.LastPipelineRun.publish(tracker)
                        // P1: stash synth output — blocking playback runs AFTER
                        // the lock is released (holding it stalled the queue).
                        outStages = stages
                        outTtsSynthMs = ttsSynthMs
                        outTotal = total
                        outItemId = itemId
                        outLang = activeLang
                        outMtOk = true
                        stopCompleted = true
                        when (pcmRes) {
                            null -> outAutoOff = true
                            is EngineResult.Ok -> outPcm = pcmRes.value
                            is EngineResult.Err -> outPcmError = pcmRes.message
                        }
                    }
                    is EngineResult.Err -> {
                        // P1: MT-fail still needs the shared tail (final UI
                        // update moved post-lock), so mark completion here.
                        stopCompleted = true
                        withContext(Dispatchers.Main) {
                            // MT failed but ASR succeeded: keep the Hindi visible
                            // in the conversation (with retry) instead of dropping
                            // it — otherwise a good transcript vanishes.
                            val i = LiveConversationStore.items.indexOfFirst { it.id == itemId }
                            if (i != -1) {
                                LiveConversationStore.items[i] = LiveConversationStore.items[i].copy(
                                    isTranslating = false,
                                    error = mt.message
                                )
                            }
                            _uiState.value = _uiState.value.copy(committedText = finalCommitted, error = mt.message)
                        }
                        tracker.markTranslate("")
                    }
                }
                outFinal = finalCommitted
                outSessionT0 = sessionT0
            }
            // P1: outside pipelineMutex — blocking audio + final UI update.
            // Early ASR-fail paths return@withLock above with stopCompleted=false.
            if (!stopCompleted) return@launch
            if (outMtOk) {
                if (outAutoOff) {
                    withContext(Dispatchers.Main) {
                        val i = LiveConversationStore.items.indexOfFirst { it.id == outItemId }
                        if (i != -1) {
                            LiveConversationStore.items[i] = LiveConversationStore.items[i].copy(
                                asrMs = outStages["asr"]?.toLong(),
                                mtMs = outStages["translate"]?.toLong(),
                                totalMs = outTotal
                            )
                        }
                        _uiState.value = _uiState.value.copy(ttsMessage = "Auto-play off — tap Play on the message")
                    }
                } else if (outPcm != null) {
                    // Playback stays off Main AND off the pipeline lock:
                    // AudioTrack.write blocks up to seconds (ANR on Main, stall on lock).
                    playPcmLocal(outPcm!!, VachakAudio.TTS_OUTPUT_HZ)
                    val played = outPcm!!
                    withContext(Dispatchers.Main) {
                        val i = LiveConversationStore.items.indexOfFirst { it.id == outItemId }
                        if (i != -1) {
                            LiveConversationStore.items[i] = LiveConversationStore.items[i].copy(
                                asrMs = outStages["asr"]?.toLong(),
                                mtMs = outStages["translate"]?.toLong(),
                                ttsMs = outTtsSynthMs,
                                totalMs = outTotal
                            )
                        }
                        _uiState.value = _uiState.value.copy(ttsMessage = "Playing ${ActiveLanguage.label(outLang)} audio • ${played.size} samples @${VachakAudio.TTS_OUTPUT_HZ}Hz")
                    }
                } else if (outPcmError != null) {
                    // Loud TTS failure (mismatch / inaudible OOV / synth throw):
                    // text stays source of truth, cause is shown, never silence.
                    val msg = outPcmError!!
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(ttsMessage = "TTS: $msg")
                    }
                }
            }
            val totalMs = (SystemClock.elapsedRealtimeNanos() - outSessionT0) / 1_000_000
            withContext(Dispatchers.Main) {
                // KEEP the heard Hindi on screen after done (partialText =
                // final): wiping it here made good transcripts flash and
                // vanish at the exact moment the user looks for them.
                _uiState.value = _uiState.value.copy(latencyMs = totalMs, isTranslating = false, isStopping = false, isSynthesizing = false, listeningSinceMs = null, partialText = outFinal)
                android.util.Log.d("Vachak-Latency", "VOICE END total ${totalMs}ms (${if (totalMs < 3000) "< 3s ✓" else "≥ 3s ⚠"})")
                onCommitted?.invoke(outFinal)
                streamingSession = null
                _isCapturing.value = false
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
            // P1 holders: typed playback also leaves the lock (same stall).
            var tPcm: ShortArray? = null
            var tPcmError: String? = null
            var tAutoOff = false
            var tStages: Map<String, Float?> = emptyMap()
            var tTtsSynthMs: Long? = null
            var tTotal: Long? = null
            var tItemId: String? = null
            var tLang = ActiveLanguage.current
            var tDone = false
            pipelineMutex.withLock {
                val itemId = "msg-${SystemClock.elapsedRealtimeNanos()}"
                withContext(Dispatchers.Main) {
                    LiveConversationStore.items.add(
                        ConversationItem(
                            id = itemId, hindiText = trimmed, santaliText = null,
                            timestampMillis = System.currentTimeMillis(), isTranslating = true,
                            targetLang = ActiveLanguage.current
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
                        // Phase 4: same Synthesizing flag as the voice path.
                        withContext(Dispatchers.Main) {
                            val idx = LiveConversationStore.items.indexOfFirst { it.id == itemId }
                            if (idx != -1) {
                                LiveConversationStore.items[idx] = LiveConversationStore.items[idx].copy(
                                    santaliText = mt.value, isTranslating = false, isSynthesizing = true, error = null
                                )
                            }
                            _uiState.value = _uiState.value.copy(santaliText = mt.value, error = null, isSynthesizing = true)
                        }
                        tracker.markTtsBegin()
                        val typedAutoPlay = try {
                            com.vachak.ui.prefs.VachakPrefs(context).autoPlayTts
                        } catch (_: Exception) { true }
                        val pcmRes = if (typedAutoPlay) {
                            // Phase 4: throwing TTS surfaces, never wedges.
                            runCatching { engineProvider.tts.synthesize(mt.value, activeLang) }
                                .getOrElse { e ->
                                    android.util.Log.e("Vachak-TTS", "typed synth threw", e)
                                    EngineResult.Err(com.vachak.engine.EngineError.MODEL_DECODE_FAILED, "TTS failed: ${e.message?.take(120)}")
                                }
                        } else {
                            null
                        }
                        tracker.markAudioBegin()
                        // Phase 4: generate() returned — synthesis over, playback next.
                        withContext(Dispatchers.Main) {
                            _uiState.value = _uiState.value.copy(isSynthesizing = false)
                            val idx = LiveConversationStore.items.indexOfFirst { it.id == itemId }
                            if (idx != -1) {
                                LiveConversationStore.items[idx] = LiveConversationStore.items[idx].copy(isSynthesizing = false)
                            }
                        }
                        val stages = tracker.result().stageMs()
                        // Phase 4: real synth cost, not the stages["tts"] queue gap (see onStop).
                        val typedTtsSynthMs = tracker.result().ttsSynthMs()?.toLong()
                        val total = tracker.result().endToEndMs()?.toLong()
                        com.vachak.ml.LastPipelineRun.publish(tracker)
                        // P1: stash — playback + footer run post-lock below.
                        tStages = stages
                        tTtsSynthMs = typedTtsSynthMs
                        tTotal = total
                        tItemId = itemId
                        tLang = activeLang
                        tDone = true
                        when (pcmRes) {
                            null -> tAutoOff = true
                            is EngineResult.Ok -> tPcm = pcmRes.value
                            is EngineResult.Err -> tPcmError = pcmRes.message
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
            // P1: typed playback outside pipelineMutex (blocking write).
            if (!tDone) return@launch
            if (tAutoOff) {
                withContext(Dispatchers.Main) {
                    val i = LiveConversationStore.items.indexOfFirst { it.id == tItemId }
                    if (i != -1) {
                        LiveConversationStore.items[i] = LiveConversationStore.items[i].copy(
                            mtMs = tStages["translate"]?.toLong(),
                            totalMs = tTotal
                        )
                    }
                    _uiState.value = _uiState.value.copy(
                        isTranslating = false,
                        ttsMessage = "Auto-play off — tap Play on the message"
                    )
                }
            } else if (tPcm != null) {
                playPcmLocal(tPcm!!, VachakAudio.TTS_OUTPUT_HZ)
                val played = tPcm!!
                withContext(Dispatchers.Main) {
                    val i = LiveConversationStore.items.indexOfFirst { it.id == tItemId }
                    if (i != -1) {
                        LiveConversationStore.items[i] = LiveConversationStore.items[i].copy(
                            mtMs = tStages["translate"]?.toLong(),
                            ttsMs = tTtsSynthMs,
                            totalMs = tTotal
                        )
                    }
                    _uiState.value = _uiState.value.copy(
                        isTranslating = false,
                        ttsMessage = "Playing ${ActiveLanguage.label(tLang)} audio • ${played.size} samples"
                    )
                }
            } else if (tPcmError != null) {
                val msg = tPcmError!!
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isTranslating = false, ttsMessage = "TTS: $msg")
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
                // Retry in the ITEM's language, not the current toggle: retrying
                // a Santali failure after switching to Mundari must not silently
                // replace it with Mundari text.
                val itemLang = LiveConversationStore.items.firstOrNull { it.id == itemId }?.targetLang
                    ?: ActiveLanguage.current
                val mt = try {
                    kotlinx.coroutines.withTimeout(30_000) {
                        engineProvider.translation.translate(hindi, LanguagePair("hi", itemLang))
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
                        is EngineResult.Ok -> _uiState.value = _uiState.value.copy(isTranslating = false, error = null, santaliText = mt.value)
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
            // P1: serialize native generate() with the pipeline (OfflineTts is
            // not thread-safe); playback itself stays outside the lock.
            val pcmRes = pipelineMutex.withLock { engineProvider.tts.synthesize(text, lang) }
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

    /**
     * One-tap voice proof: synthesizes a KNOWN-GOOD string in the active
     * target's script (ᱡᱚᱦᱟᱨ / जोहार) and plays it, reporting the exact
     * outcome. If the device stays silent, the message names the stage that
     * failed (script gate / native synth / playback) — silence is never bare.
     */
    fun testVoice() {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(ttsMessage = "Testing voice…")
            }
            val lang = ActiveLanguage.current
            val probe = if (ActiveLanguage.isOlChiki(lang)) "ᱡᱚᱦᱟᱨ" else "जोहार"
            val res = pipelineMutex.withLock { engineProvider.tts.synthesize(probe, lang) }
            when (res) {
                is EngineResult.Ok -> {
                    playPcmLocal(res.value, VachakAudio.TTS_OUTPUT_HZ)
                    val secs = res.value.size / VachakAudio.TTS_OUTPUT_HZ.toFloat()
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            ttsMessage = "Test voice OK • ${res.value.size} samples (~${"%.1f".format(secs)}s) @${VachakAudio.TTS_OUTPUT_HZ}Hz — proof the chain speaks"
                        )
                    }
                    android.util.Log.d("Vachak-TTS", "testVoice OK ${res.value.size} samples lang=$lang")
                }
                is EngineResult.Err -> {
                    android.util.Log.e("Vachak-TTS", "testVoice FAILED [${res.code}]: ${res.message}")
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(ttsMessage = "Test voice FAILED: ${res.message}")
                    }
                }
            }
        }
    }

    /** Clear transient error lines (debug dialog Dismiss). Next action re-arms them. */
    fun dismissErrors() {
        _uiState.value = _uiState.value.copy(asrError = null, error = null, livePreviewError = null, ttsMessage = null)
    }

    fun forceReleaseMic() {
        try {
            stopLoops()
            _isCapturing.value = false
            _uiState.value = _uiState.value.copy(isListening = false, isStopping = false, isSynthesizing = false, listeningSinceMs = null)
            // P2: drop stale session state so a tab-switch mid-stop can never
            // resurrect a finished session on the next press.
            streamingSession = null
            t0Ns = null
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

    /** Offline AudioTrack playback for the VM-owned pipeline (mirrors LiveScreen.tryPlayPcm).
     * Blocking write + synchronous stop/release on the caller (IO) thread: the
     * old fire-and-forget sleep capped at 4000ms CUT OFF utterances longer
     * than 4s (multi-sentence lesson playback). Callers already run off Main. */
    private suspend fun playPcmLocal(pcm: ShortArray, sampleRate: Int = VachakAudio.TTS_OUTPUT_HZ) {
        if (pcm.isEmpty()) return
        try {
            val minBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf.coerceAtLeast(pcm.size * 2))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            try {
                track.play()
                var offset = 0
                while (offset < pcm.size) {
                    val len = minOf(2048, pcm.size - offset)
                    track.write(pcm, offset, len, AudioTrack.WRITE_BLOCKING)
                    offset += len
                }
                // P1 drain: stop() discards audio still queued in the native
                // buffer — short Santali clips vanished entirely ("silence").
                // Wait until the head reaches the written frames (bounded).
                val drainMs = ((pcm.size * 1000L / sampleRate) + 2000).coerceAtMost(15_000)
                val startMs = SystemClock.elapsedRealtime()
                while (SystemClock.elapsedRealtime() - startMs < drainMs) {
                    val head = try { track.playbackHeadPosition } catch (_: Exception) { pcm.size }
                    if (head >= pcm.size) break
                    delay(50)
                }
            } finally {
                try { track.stop() } catch (_: Exception) {}
                track.release()
            }
        } catch (e: Exception) {
            android.util.Log.e("Vachak-TTS", "LiveViewModel playback failed", e)
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(ttsMessage = "Audio playback failed on this device — text shown")
            }
        }
    }
}
