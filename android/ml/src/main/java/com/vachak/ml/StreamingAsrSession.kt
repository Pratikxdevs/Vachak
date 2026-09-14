package com.vachak.ml

import android.content.Context
import android.os.SystemClock
import android.util.Log

/**
 * Windowed-streaming ASR session: mic chunks in, Hindi text out.
 *
 * One session = one mic press. Driven externally in strict order:
 *   start() -> pushAudio()* -> getPartial()* -> finish()
 * with finalizeCurrentSegment() available for VAD-endpointed commits.
 *
 * Architecture (why windowed, not true streaming): the Hindi model is an
 * OFFLINE NeMo CTC graph (`IndicConformerAsrAdapter` / `OfflineRecognizer`).
 * It has no frame-wise encoder state, so every decode is a full forward pass.
 * True incremental streaming would need a streaming-trained model
 * (OnlineNeMoCtc etc.). Hence: partials decode a BOUNDED tail window
 * (O(window), never O(total)), and the endpointed segment gets exactly ONE
 * full decode whose text commits once to the ledger.
 *
 * Transcript model: [committedText] (finalized segments, ordered) vs
 * [currentPartial] (live preview of the ACTIVE segment only). Partials never
 * leak across segments; overlapping windows are stitched via word-level
 * suffix/prefix dedup ([stripOverlap]).
 *
 * Honesty contract: a throwing decoder records [lastDecodeError] and yields
 * "" — never disguised as VAD silence. Signal stats ([chunksSeen],
 * [maxRmsSeen], [zeroChunks], [vadFailures]) distinguish dead-mic from
 * no-speech without any model. See [signalReport].
 */
class StreamingAsrSession(
    private val context: Context,
    private val sampleRate: Int = 16000,
    private val testTranscriber: ((ShortArray) -> String)? = null,
    private val testVad: VadAnalyzer? = null
) {
    private val tagVad = "Vachak-VAD"
    private val tagAsr = "Vachak-ASR"
    private val tagLat = "Vachak-Latency"

    // Leaf decoder (process-wide shared recognizer) + neural endpointing.
    private val directAsr: IndicConformerAsrAdapter by lazy { IndicConformerAsrAdapter(context) }
    private val vad: VadAnalyzer = testVad ?: SherpaOnnxVadAnalyzer(context, "vad", sampleRate)
    // Phase 2: grow-only scratch buffers so the hot paths allocate ~never in
    // steady state. Chunk sizes are stable per device (same tick cadence), so
    // after the first press these reuse exactly. Sequential pipeline: the
    // capture lane fills vadScratch while the decode lane is NOT decoding the
    // same array (pushAudio never decodes inline; lane decodes use
    // decodeScratch under sessionLock) — no sharing hazard.
    private var vadScratch = FloatArray(0)
    private var decodeScratch = FloatArray(0)

    // ---- public state ----
    data class SegmentRecord(
        val segmentId: Int,
        val startSample: Int,
        val endSample: Int,
        val durationMs: Int,
        val finalText: String
    )
    private val segmentLedger = mutableListOf<SegmentRecord>()
    val segmentRecords: List<SegmentRecord> get() = segmentLedger.toList()

    var committedText: String = ""
        private set
    var currentPartial: String = ""
        private set

    var lastDecodeError: String? = null
        private set

    var chunksSeen: Int = 0
        private set
    var maxRmsSeen: Float = 0f
        private set
    var zeroChunks: Int = 0
        private set
    var vadFailures: Int = 0
        private set

    fun signalReport(): String =
        "chunks=$chunksSeen maxRms=$maxRmsSeen (${VachakAudio.rmsToDb(maxRmsSeen)}) " +
            "zeroChunks=$zeroChunks vadFailures=$vadFailures segments=${segmentLedger.size}" +
            (lastDecodeError?.let { " decodeError=${it.take(80)}" } ?: "")

    /** True when the shared recognizer is resident: partials decode hot. */
    val isWarm: Boolean get() = directAsr.isLoaded()

    /** Uncommitted samples in the active segment (drives lane priority). */
    val uncommittedSamples: Int get() = currentSegmentSamples.size

    /**
     * True when a chunk commit is due or imminent: the lane must NOT start a
     * multi-second partial now — it would still be decoding when the 3s chunk
     * becomes pending, starving the commit (seen on device: 5.7s partial
     * blocked the chunk, forcing a 20s full re-decode at Stop). At most one
     * early partial per chunk; the commit itself delivers the words.
     */
    fun isChunkImminent(): Boolean =
        pendingFinalize || currentSegmentSamples.size >= commitChunkSamples - VachakAudio.PARTIAL_HEAD_START_SAMPLES

    // ---- per-segment accumulation ----
    // Phase 2: chunk-list buffer instead of MutableList<Short>. The old list
    // boxed EVERY sample (16k Short objects/sec) via addAll(chunk.toList()),
    // and every partial re-boxed the window via subList().toShortArray().
    // Chunk refs are appended O(1); copies happen only at decode time, and
    // only for the bounded window / trimmed final. Callers always pass fresh
    // arrays (capture-lane copyOfRange / tail copy / test literals), so refs
    // are never mutated after append.
    private class PcmChunks {
        private val chunks = ArrayList<ShortArray>(8)
        var size: Int = 0
            private set

        fun add(chunk: ShortArray) {
            if (chunk.isEmpty()) return
            chunks.add(chunk)
            size += chunk.size
        }

        fun isEmpty(): Boolean = size == 0

        fun isNotEmpty(): Boolean = size != 0

        fun clear() {
            chunks.clear()
            size = 0
        }

        fun toShortArray(): ShortArray {
            if (chunks.isEmpty()) return ShortArray(0)
            if (chunks.size == 1) return chunks[0].copyOf()
            val out = ShortArray(size)
            var pos = 0
            for (c in chunks) {
                System.arraycopy(c, 0, out, pos, c.size)
                pos += c.size
            }
            return out
        }

        /** Last [n] samples across chunk boundaries (windowed partials). */
        fun copyLast(n: Int): ShortArray {
            val take = n.coerceIn(0, size)
            if (take == 0) return ShortArray(0)
            if (take == size) return toShortArray()
            val out = ShortArray(take)
            var pos = take
            for (i in chunks.size - 1 downTo 0) {
                val c = chunks[i]
                val want = minOf(c.size, pos)
                pos -= want
                System.arraycopy(c, c.size - want, out, pos, want)
                if (pos == 0) break
            }
            return out
        }
    }

    private var segmentIdCounter = 0
    private var currentSegmentStartSample = 0
    private val currentSegmentSamples = PcmChunks()
    private var totalSamplesSeen = 0

    private var hasSpeech = false
    private var silenceMs = 0
    private var speechStartSample: Int? = null
    private var lastChunk: ShortArray? = null

    private val vadRmsThreshold = VachakAudio.VAD_RMS_THRESHOLD
    private val silenceHangoverMs = VachakAudio.SILENCE_HANGOVER_MS
    private val minUtteranceMs = VachakAudio.MIN_UTTERANCE_MS
    private val maxUtteranceMs = VachakAudio.MAX_UTTERANCE_MS

    private var t0Ns: Long? = null
    private var firstPartialNs: Long? = null

    // Windowed partials: bounded tail window, dedup-stitched preview.
    private val streamingModeLabel = "WINDOWED_STREAMING"
    private val windowSamples = sampleRate * VachakAudio.STREAM_WINDOW_SEC
    private var accumulatedPreview: String = ""
    private var lastRawWindowText: String = ""
    // Live commit rhythm: force-commit continuous speech in STREAM_COMMIT_SEC
    // chunks so words land while speaking and no decode exceeds ~3s of audio.
    private val commitChunkSamples = sampleRate * VachakAudio.STREAM_COMMIT_SEC
    private val committedQueue = ArrayDeque<String>()
    /**
     * Set by [pushAudio] when a segment is endpointed and awaits its (single,
     * expensive) decode. pushAudio NEVER decodes inline — the decode lane
     * collects it via [consumePendingFinalize] so capture cadence never stalls
     * behind a multi-second forward pass.
     */
    private var pendingFinalize = false

    /** Take the pending-finalize flag (true = lane must run one final decode). */
    fun consumePendingFinalize(): Boolean {
        if (!pendingFinalize) return false
        pendingFinalize = false
        return true
    }

    /**
     * Drain texts committed since the last call (chunk + hangover commits
     * during listening). Lets the UI show live Hindi mid-press; the full
     * [committedText] is still assembled at [finish].
     */
    fun drainNewCommits(): List<String> {
        if (committedQueue.isEmpty()) return emptyList()
        val out = committedQueue.toList()
        committedQueue.clear()
        return out
    }

    fun start(t0: Long = SystemClock.elapsedRealtimeNanos()) {
        t0Ns = t0
        firstPartialNs = null
        lastDecodeError = null
        segmentLedger.clear()
        committedText = ""
        currentPartial = ""
        accumulatedPreview = ""
        lastRawWindowText = ""
        committedQueue.clear()
        segmentIdCounter = 0
        currentSegmentStartSample = totalSamplesSeen
        currentSegmentSamples.clear()
        hasSpeech = false
        silenceMs = 0
        speechStartSample = null
        lastChunk = null
        chunksSeen = 0
        maxRmsSeen = 0f
        zeroChunks = 0
        vadFailures = 0
        pendingFinalize = false
        // Lightweight start: DO NOT block UI with model IO. Warm-up is offloaded to Dispatchers.IO.
        if (testTranscriber != null) {
            Log.d(tagAsr, "StreamingAsrSession start t0=${t0Ns} test mode — skipping native warm mode=$streamingModeLabel windowSamples=$windowSamples")
        } else {
            Log.d(tagAsr, "StreamingAsrSession start t0=${t0Ns} lightweight mode=$streamingModeLabel windowSamples=$windowSamples (warm-up offloaded to IO)")
        }
        try { vad.flush() } catch (e: Exception) { Log.w(tagVad, "vad.flush failed (VAD state may leak into next segment)", e) }
        Log.d(tagVad, "Streaming session start t0=${t0Ns} segmentId reset")
        Log.d(tagVad, "SEGMENT_START id=0 startSample=${currentSegmentStartSample} (session start, awaiting speech)")
        Log.d(tagAsr, "Streaming mode determination: OfflineRecognizer (OfflineWhisper/OfflineNemoEncDecCtc offline, OfflineModelConfig) incompatible with OnlineRecognizer true streaming (requires OnlineTransducer/OnlineNeMoCtc streaming model with incremental state) — using $streamingModeLabel fallback: fixed ${windowSamples} samples (${windowSamples * 1000 / sampleRate}ms) overlapping windows, bounded O(window) decode, deduplicated, pack-aware offline")
    }

    /** Offload heavy ASR/VAD warm-up off the UI thread. Call from Dispatchers.IO after start(). */
    fun warmUpAsync() {
        if (testTranscriber != null) return
        try {
            directAsr.warmUpIfNeeded()
            Log.d(tagAsr, "StreamingAsrSession warmUpAsync ready mode=$streamingModeLabel")
        } catch (e: Exception) {
            Log.w(tagAsr, "warmUpAsync failed: ${e.message}")
        }
        try {
            // Touch VAD once off UI to trigger SherpaAssets.prepare + Vad creation off main.
            vad.flush()
            if (vad is SherpaOnnxVadAnalyzer) {
                // No extra action; flush already ensured Vad creation via ensure().
            }
        } catch (e: Exception) {
            // VAD warm-up is best-effort; real creation errors surface at first accept().
            Log.w(tagVad, "warmUp VAD touch failed: ${e.message}")
        }
    }

    // ---- ingest ----

    fun pushAudio(chunk: ShortArray): List<VadSegmentLog> {
        if (chunk.isEmpty()) return emptyList()
        val finalizedLogs = mutableListOf<VadSegmentLog>()
        val chunkMs = chunk.size * 1000 / sampleRate
        // Phase 2: RMS straight off the shorts (was: FloatArray alloc per
        // chunk just to measure energy). VAD still needs floats — filled into
        // the reused scratch instead of a fresh array per tick.
        val rms = rms(chunk)
        // Signal accounting FIRST (independent of any model): this is what
        // distinguishes "mic dead" from "VAD unsplittable" after the fact.
        chunksSeen++
        if (rms > maxRmsSeen) maxRmsSeen = rms
        if (rms < VachakAudio.DIGITAL_SILENCE_RMS) zeroChunks++
        val isSpeechChunk = rms > vadRmsThreshold
        // Silero must never gate the RMS path: if the neural VAD throws on a
        // chunk, count it and continue with energy endpointing alone.
        try {
            if (vadScratch.size != chunk.size) vadScratch = FloatArray(chunk.size)
            for (i in chunk.indices) vadScratch[i] = chunk[i] / 32768.0f
            vad.accept(vadScratch)
        } catch (e: Exception) {
            vadFailures++
            if (vadFailures <= 3) Log.e(tagVad, "VAD accept threw (RMS path continues) #$vadFailures", e)
        }
        totalSamplesSeen += chunk.size

        if (!hasSpeech) {
            if (isSpeechChunk) beginSegment(chunk, rms) else lastChunk = chunk
            return finalizedLogs
        }

        currentSegmentSamples.add(chunk)
        silenceMs = if (isSpeechChunk) 0 else silenceMs + chunkMs
        lastChunk = null
        val uttMs = currentSegmentSamples.size * 1000 / sampleRate
        // Endpoint on pause, failsafe cap, or commit-chunk rhythm. Marks
        // pending ONLY — the decode lane runs the single expensive decode so
        // this (capture-cadence) path stays at millisecond cost.
        if (silenceMs >= silenceHangoverMs || uttMs >= maxUtteranceMs ||
            currentSegmentSamples.size >= commitChunkSamples
        ) {
            pendingFinalize = true
        }
        drainVadInternals()
        return finalizedLogs
    }

    /** First speech chunk: open a segment with 1-chunk lookback (never overlapping). */
    private fun beginSegment(chunk: ShortArray, rms: Float) {
        hasSpeech = true
        speechStartSample = totalSamplesSeen - chunk.size
        // Normalize start to be non-overlapping with the previous segment.
        if (speechStartSample!! < currentSegmentStartSample) speechStartSample = currentSegmentStartSample
        // Include 1-chunk lookback so the first word is not clipped.
        val lb = lastChunk
        if (lb != null && speechStartSample!! - lb.size >= currentSegmentStartSample) {
            currentSegmentSamples.add(lb)
            Log.d(tagVad, "SEGMENT_START id=$segmentIdCounter startSample=${speechStartSample!! - lb.size} (with lookback) rms=$rms")
            currentSegmentStartSample = speechStartSample!! - lb.size
        } else {
            Log.d(tagVad, "SEGMENT_START id=$segmentIdCounter startSample=$speechStartSample rms=$rms (no lookback due to overlap guard)")
            currentSegmentStartSample = speechStartSample!!
        }
        currentSegmentSamples.add(chunk)
        silenceMs = 0
        Log.d(tagVad, "SEGMENT_START id=$segmentIdCounter startSample=$currentSegmentStartSample")
        // Reset per-segment window streaming state for new segment
        accumulatedPreview = ""
        lastRawWindowText = ""
        currentPartial = ""
    }

    /** Silero's internal segments are informational only — endpointing here is RMS-driven. */
    private fun drainVadInternals() {
        var v: VadSegment? = null
        try {
            while (vad.popSegment()?.also { v = it } != null) {
                Log.d(tagVad, "VAD internal pop segment samples=${v!!.samples.size} startSec=${v!!.startSec} endSec=${v!!.endSec} (internal, not used for committed — normalized)")
            }
        } catch (e: Exception) {
            vadFailures++
            Log.e(tagVad, "VAD popSegment threw (drained part skipped)", e)
        }
    }

    // ---- single decode entry point ----

    /**
     * THE only place model bytes are invoked. Test seam ([testTranscriber])
     * or the shared conformer; any throw is recorded as [lastDecodeError]
     * (MODEL failure, never VAD silence) and yields "".
     */
    private fun decode(pcm: ShortArray, where: String): String {
        return try {
            val text = if (testTranscriber != null) {
                testTranscriber.invoke(pcm).trim()
            } else {
                // Phase 2: Short->Float into the reused scratch (was: fresh
                // FloatArray per decode). Consecutive window decodes are the
                // same size in steady state, so this reuses exactly; the
                // recognizer consumes the array synchronously under the
                // shared lock, so reuse across sequential decodes is safe.
                if (decodeScratch.size != pcm.size) decodeScratch = FloatArray(pcm.size)
                for (i in pcm.indices) decodeScratch[i] = pcm[i] / 32768.0f
                directAsr.transcribe(decodeScratch, sampleRate).text.trim()
            }
            text
        } catch (e: Exception) {
            // Same contract everywhere: a throwing decoder is a MODEL
            // failure, never VAD silence. Commit nothing, record why.
            if (lastDecodeError == null) {
                lastDecodeError = e.message?.take(160)
                Log.e(tagAsr, "$where decode threw", e)
            }
            ""
        }
    }

    // ---- partials ----

    fun getPartial(): String {
        if (currentSegmentSamples.isEmpty()) {
            if (currentPartial.isNotBlank()) {
                currentPartial = ""
                accumulatedPreview = ""
                lastRawWindowText = ""
                Log.d(tagAsr, "SEGMENT_PARTIAL id=$segmentIdCounter text=\"\" (cleared, empty segment)")
            }
            return ""
        }
        if (currentSegmentSamples.size * 1000 / sampleRate < minUtteranceMs) {
            return currentPartial
        }

        // WINDOWED: bounded fixed-size overlapping window decode (NOT entire history)
        val totalSize = currentSegmentSamples.size
        val windowSize = minOf(totalSize, windowSamples)
        val windowStartIdx = totalSize - windowSize
        val windowStartGlobal = currentSegmentStartSample + windowStartIdx
        val windowEndGlobal = currentSegmentStartSample + totalSize
        // Phase 2: single copy of the tail window (was: subList view + boxed
        // toShortArray re-box).
        val windowShort = currentSegmentSamples.copyLast(windowSize)

        Log.d(tagAsr, "ASR_WINDOW id=$segmentIdCounter startSample=$windowStartGlobal endSample=$windowEndGlobal newSamples=$windowSize mode=$streamingModeLabel")

        val startNs = SystemClock.elapsedRealtimeNanos()
        val txtRaw = decode(windowShort, "ASR_PARTIAL id=$segmentIdCounter")
        if (txtRaw.isBlank()) {
            // Keep the previous partial: a blank window is "no new words",
            // not "clear the screen".
            if (lastDecodeError != null) {
                Log.e(tagAsr, "ASR_PARTIAL id=$segmentIdCounter decode threw")
            }
            return currentPartial
        }
        val latencyMs = (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000
        Log.d(tagAsr, "ASR_PARTIAL id=$segmentIdCounter latencyMs=$latencyMs text=\"$txtRaw\"")

        // Deduplicate overlapping hypotheses: full-segment window replaces,
        // tail window stitches its non-overlapping suffix on.
        val nextPartial: String = if (windowStartIdx == 0) {
            txtRaw
        } else {
            if (accumulatedPreview.isBlank()) {
                txtRaw
            } else {
                val stripped = stripOverlap(accumulatedPreview, txtRaw)
                if (stripped.isBlank()) accumulatedPreview else "$accumulatedPreview $stripped"
            }
        }

        if (nextPartial != currentPartial) {
            currentPartial = nextPartial
            // Keep accumulatedPreview in sync so next tail window dedup is against the committed preview so far
            accumulatedPreview = nextPartial
            Log.d(tagAsr, "SEGMENT_PARTIAL id=$segmentIdCounter text=\"$nextPartial\" (windowed)")
            if (firstPartialNs == null && nextPartial.isNotBlank()) {
                firstPartialNs = SystemClock.elapsedRealtimeNanos()
                val firstMs = (firstPartialNs!! - (t0Ns ?: firstPartialNs!!)) / 1_000_000
                Log.d(tagLat, "first partial transcript ${firstMs}ms -> \"$nextPartial\"")
                Log.d(tagAsr, "PARTIAL (${firstMs}ms): $nextPartial")
            } else if (nextPartial.isNotBlank()) {
                Log.d(tagAsr, "PARTIAL update: $nextPartial")
            }
        }
        lastRawWindowText = txtRaw
        return currentPartial
    }

    // ---- finals ----

    fun finalizeCurrentSegment(isEndpoint: Boolean = true): VadSegmentLog {
        pendingFinalize = false
        if (currentSegmentSamples.isEmpty()) {
            // Nothing to decode: report position WITHOUT consuming a segment
            // id, so explicit finalize calls on empty buffers can't punch gaps
            // in the ledger sequence.
            val log = VadSegmentLog(segmentIdCounter, currentSegmentStartSample, currentSegmentStartSample, 0, true)
            Log.d(tagVad, "SEGMENT_FINAL id=${log.segmentId} startSample=${log.startSample} endSample=${log.endSample} durationMs=${log.durationMs} finalized=${log.finalized} text=\"\" (empty)")
            Log.d(tagVad, "segmentId=${log.segmentId} startSample=${log.startSample} endSample=${log.endSample} durationMs=${log.durationMs} finalized=${log.finalized} (empty)")
            Log.d(tagAsr, "ASR_FINAL id=${log.segmentId} latencyMs=0 text=\"\" (empty)")
            return log
        }
        val startSample = currentSegmentStartSample
        val endSample = startSample + currentSegmentSamples.size
        val durationMs = (endSample - startSample) * 1000 / sampleRate
        // Exactly ONE final decode for this segment: leading/trailing silence
        // trimmed (conformer cost scales with frames), exactly-once commit.
        val pcm = trimSilence(currentSegmentSamples.toShortArray())
        val startNs = SystemClock.elapsedRealtimeNanos()
        val txt = decode(pcm, "ASR_FINAL id=$segmentIdCounter")
        val latencyMs = (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000
        Log.d(tagVad, "SEGMENT_FINAL id=$segmentIdCounter startSample=$startSample endSample=$endSample durationMs=$durationMs finalized=true text=\"$txt\"")
        Log.d(tagAsr, "ASR_FINAL id=$segmentIdCounter latencyMs=$latencyMs text=\"$txt\"")
        if (txt.isNotBlank()) {
            val record = SegmentRecord(segmentIdCounter, startSample, endSample, durationMs, txt)
            segmentLedger.add(record)
            committedText = segmentLedger.sortedBy { it.startSample }.joinToString(" ") { it.finalText }
            committedQueue.addLast(txt)
            Log.d(tagAsr, "SEGMENT_COMMITTED id=${record.segmentId} committed=\"$committedText\"")
            Log.d(tagAsr, "FINAL segmentId=$segmentIdCounter \"$txt\" -> committed=\"$committedText\"")
        } else {
            Log.d(tagAsr, "FINAL segmentId=$segmentIdCounter empty after decode (silence)")
        }
        val log = VadSegmentLog(segmentIdCounter, startSample, endSample, durationMs, true)
        Log.d(tagVad, "segmentId=${log.segmentId} startSample=${log.startSample} endSample=${log.endSample} durationMs=${log.durationMs} finalized=${log.finalized} text=\"$txt\"")
        return advanceAfterFinal(log)
    }

    /** Reset window state for the next segment. */
    private fun advanceAfterFinal(log: VadSegmentLog): VadSegmentLog {
        segmentIdCounter++
        currentSegmentStartSample = log.endSample
        currentSegmentSamples.clear()
        currentPartial = ""
        accumulatedPreview = ""
        lastRawWindowText = ""
        hasSpeech = false
        silenceMs = 0
        return log
    }

    fun finish(): String {
        pendingFinalize = false
        if (currentSegmentSamples.isNotEmpty()) {
            finalizeCurrentSegment(isEndpoint = true)
        }
        try { vad.flush() } catch (e: Exception) { Log.w(tagVad, "vad.flush failed (VAD state may leak into next segment)", e) }
        var seg: VadSegment? = null
        try {
            while (vad.popSegment()?.also { seg = it } != null) {
                Log.d(tagVad, "finish drain VAD internal pop ${seg!!.samples.size} samples")
            }
        } catch (e: Exception) {
            vadFailures++
            Log.e(tagVad, "finish drain popSegment threw", e)
        }
        val finalNs = SystemClock.elapsedRealtimeNanos()
        val totalMs = (finalNs - (t0Ns ?: finalNs)) / 1_000_000
        val firstMs = firstPartialNs?.let { (it - (t0Ns ?: it)) / 1_000_000 } ?: -1
        Log.d(tagLat, "final transcript ${totalMs}ms after t0, first partial was ${firstMs}ms committed=\"$committedText\" segments=${segmentLedger.size} mode=$streamingModeLabel")
        Log.d(tagAsr, "FINAL committed=\"$committedText\" segments=${segmentLedger.size} mode=$streamingModeLabel")
        return committedText
    }

    fun transcribeOneShot(pcm: ShortArray): String {
        return decode(pcm, "ONESHOT").trim()
    }

    /**
     * Deduplicate overlapping window hypotheses at word level.
     * Finds the longest suffix of [accumulated] that equals the prefix of [cur]
     * and returns only the non-overlapping suffix of [cur]. This prevents
     * overlapping fixed-size windows from causing repeated phrases when stitched.
     * If no overlap, returns [cur] whole.
     */
    private fun stripOverlap(accumulated: String, cur: String): String {
        if (accumulated.isBlank() || cur.isBlank()) return cur
        val accWords = accumulated.trim().split(Regex("\\s+"))
        val curWords = cur.trim().split(Regex("\\s+"))
        if (accWords.isEmpty() || curWords.isEmpty()) return cur
        val maxK = minOf(accWords.size, curWords.size)
        for (k in maxK downTo 1) {
            if (accWords.takeLast(k) == curWords.take(k)) {
                return curWords.drop(k).joinToString(" ")
            }
        }
        // Fallback: char-level suffix/prefix for languages without clear word boundaries (keep word fallback)
        return cur
    }

    /**
     * Leading/trailing silence trim for the final decode (see
     * finalizeCurrentSegment). 10ms frames, DIGITAL_SILENCE_RMS gate, 200ms
     * pads. Returns the input untouched when no silence edge exists or the
     * whole buffer is silence (decode then honestly reports empty).
     */
    private fun trimSilence(pcm: ShortArray): ShortArray {
        if (pcm.size <= 6400) return pcm
        val frame = 160 // 10ms @16kHz
        fun frameRms(start: Int, end: Int): Float {
            var sum = 0.0
            for (i in start until end) { val v = pcm[i] / 32768.0; sum += v * v }
            return kotlin.math.sqrt(sum / (end - start)).toFloat()
        }
        var s = 0
        while (s + frame <= pcm.size) {
            if (frameRms(s, s + frame) >= VachakAudio.DIGITAL_SILENCE_RMS) break
            s += frame
        }
        val first = maxOf(0, s - 3200)
        var e = pcm.size
        while (e - frame >= first) {
            if (frameRms(e - frame, e) >= VachakAudio.DIGITAL_SILENCE_RMS) break
            e -= frame
        }
        val last = minOf(pcm.size, e + 3200)
        if (last - first <= 0 || (first == 0 && last == pcm.size)) return pcm
        Log.d(tagAsr, "trimSilence ${pcm.size} -> ${last - first} samples (cut ${(pcm.size - (last - first)) * 1000 / sampleRate}ms silence)")
        return pcm.copyOfRange(first, last)
    }

    private fun rms(x: FloatArray): Float {
        var sum = 0.0
        for (v in x) sum += v * v
        return kotlin.math.sqrt(sum / x.size).toFloat()
    }

    /** Phase 2: energy straight off PCM16 shorts — no FloatArray alloc. */
    private fun rms(x: ShortArray): Float {
        var sum = 0.0
        for (s in x) {
            val v = s / 32768.0
            sum += v * v
        }
        return kotlin.math.sqrt(sum / x.size).toFloat()
    }

    data class VadSegmentLog(
        val segmentId: Int,
        val startSample: Int,
        val endSample: Int,
        val durationMs: Int,
        val finalized: Boolean
    )
}
