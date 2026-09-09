package com.vachak.ml

import android.content.Context
import android.os.SystemClock
import android.util.Log

/**
 * Streaming ASR session fixing transcript accumulation/state machine
 * + windowed streaming performance fix (2026-08-30).
 *
 * Every finalized VAD speech segment is a first-class transcript unit with exact startSample/endSample/segmentId/finalText.
 * Maintains committedText (finalized only) and currentPartial (active segment only), never concatenates partials, never re-decodes finalized.
 * Final is ordered concatenation of segmentLedger, not latest hypothesis, not entire buffer.
 * VAD segments are normalized to non-overlapping via currentSegmentStartSample = previous endSample.
 * Logs: SEGMENT_START, SEGMENT_PARTIAL, SEGMENT_FINAL, SEGMENT_COMMITTED + ASR_WINDOW/ASR_PARTIAL/ASR_FINAL
 * Creates OfflineRecognizer once per session via directAsr lazy (not per partial).
 * Preserves offline, pack, whisper/NEMO model untouched, MT/TTS not modified.
 *
 * STREAMING ARCHITECTURE (windowed fallback):
 * The current Hindi ASR model is an OFFLINE model (OfflineWhisper whisper-tiny / OfflineNemoEncDecCtcModel
 * via IndicConformerAsrAdapter / OfflineRecognizer) — it is **fundamentally incompatible with
 * OnlineRecognizer true streaming** (OnlineRecognizer requires a streaming-trained model such as
 * OnlineTransducer/OnlineZipformer/OnlineParaformer/OnlineNeMoCtc streaming CTC, with frame-wise
 * encoder state and incremental decoding). The offline model has no persistent decoder state between
 * frames and its decode() is a full-utterance CTC/Whisper forward pass. Attempting true incremental
 * streaming with OfflineRecognizer would still require feeding the whole buffer, which is exactly the
 * bug that caused 11k→251k samples growth and ~35s latency on a 16s utterance.
 *
 * Therefore this session is explicitly labelled WINDOWED_STREAMING (not TRUE_STREAMING):
 * - For each active VAD segment we feed only new audio frames via pushAudio(newChunk) (LiveScreen supplies newChunk).
 * - For partials we decode a BOUNDED fixed-size overlapping window (tail of the segment, ≤ windowSamples)
 *   instead of the entire accumulated segment, so latency is O(window) not O(total).
 * - Decoder state is not carried as a recurrent state (offline model has none); window text is deduplicated
 *   via suffix/prefix overlap removal so overlapping windows do not cause repeated phrases.
 * - On VAD endpoint we perform EXACTLY ONE final decode of the FULL segment, commit once to ledger,
 *   and reset window state for the next segment.
 *
 * This preserves segment ledger, committedText/currentPartial semantics, offline-only, and <3s target
 * (pending real 2GB device measurement — not claimed here).
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

    private val directAsr: IndicConformerAsrAdapter by lazy { IndicConformerAsrAdapter(context) }
    private val vad: VadAnalyzer = testVad ?: SherpaOnnxVadAnalyzer(context, "vad", sampleRate)

    // Segment ledger - first-class transcript units (requirement 1,2,11)
    data class SegmentRecord(
        val segmentId: Int,
        val startSample: Int,
        val endSample: Int,
        val durationMs: Int,
        val finalText: String
    )
    private val segmentLedger = mutableListOf<SegmentRecord>()
    val segmentRecords: List<SegmentRecord> get() = segmentLedger.toList()

    // Requirement 3: two separate values
    var committedText: String = ""
        private set
    var currentPartial: String = ""
        private set

    /**
     * First model-decode failure message since [start], if any. A throwing
     * recognizer (bad model asset, native shape mismatch, …) must NEVER
     * disguise itself as VAD silence: callers check this when the committed
     * text comes back empty and surface [MODEL_DECODE_FAILED] instead of
     * "No speech detected". Reset on every [start].
     */
    var lastDecodeError: String? = null
        private set

    private var segmentIdCounter = 0
    private var currentSegmentStartSample = 0
    private var currentSegmentSamples = mutableListOf<Short>()
    private var totalSamplesSeen = 0

    private var hasSpeech = false
    private var silenceMs = 0
    private var speechStartSample: Int? = null
    private var lastChunk: ShortArray? = null

    private val vadRmsThreshold = 0.012f
    private val silenceHangoverMs = 600
    private val minUtteranceMs = 300
    private val maxUtteranceMs = 14000

    private var t0Ns: Long? = null
    private var firstPartialNs: Long? = null

    // Windowed streaming (offline-model fallback) — bounded decode to avoid O(total) growth
    // True incremental streaming would require OnlineRecognizer with streaming model, which
    // OfflineWhisper/OfflineNemoEncDecCtc does NOT support.
    private val streamingModeLabel = "WINDOWED_STREAMING"
    private val windowSamples = sampleRate * 3 // 48000 samples = 3s bounded window (fixed-size, NOT entire history)
    private var accumulatedPreview: String = "" // deduped accumulation for tail-window stitching
    private var lastRawWindowText: String = ""

    fun start(t0: Long = SystemClock.elapsedRealtimeNanos()) {
        t0Ns = t0
        firstPartialNs = null
        lastDecodeError = null
        segmentLedger.clear()
        committedText = ""
        currentPartial = ""
        accumulatedPreview = ""
        lastRawWindowText = ""
        segmentIdCounter = 0
        currentSegmentStartSample = totalSamplesSeen
        currentSegmentSamples.clear()
        hasSpeech = false
        silenceMs = 0
        speechStartSample = null
        lastChunk = null
        // Lightweight start: DO NOT block UI with model IO. Warm-up is offloaded to Dispatchers.IO.
        if (testTranscriber != null) {
            Log.d(tagAsr, "StreamingAsrSession start t0=${t0Ns} test mode — skipping native warm mode=$streamingModeLabel windowSamples=$windowSamples")
        } else {
            Log.d(tagAsr, "StreamingAsrSession start t0=${t0Ns} lightweight mode=$streamingModeLabel windowSamples=$windowSamples (warm-up offloaded to IO)")
        }
        try { vad.flush() } catch (_: Exception) {}
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
        } catch (_: Exception) {}
    }

    fun pushAudio(chunk: ShortArray): List<VadSegmentLog> {
        if (chunk.isEmpty()) return emptyList()
        val finalizedLogs = mutableListOf<VadSegmentLog>()
        val chunkMs = chunk.size * 1000 / sampleRate
        val floatChunk = FloatArray(chunk.size) { chunk[it] / 32768.0f }
        val rms = rms(floatChunk)
        val isSpeechChunk = rms > vadRmsThreshold
        vad.accept(floatChunk)
        totalSamplesSeen += chunk.size

        if (!hasSpeech) {
            if (isSpeechChunk) {
                hasSpeech = true
                speechStartSample = totalSamplesSeen - chunk.size
                // Normalize start to be non-overlapping: if speechStart is before currentSegmentStartSample, clamp
                if (speechStartSample!! < currentSegmentStartSample) speechStartSample = currentSegmentStartSample
                // Include 1-chunk lookback so first word not clipped, but ensure lookback doesn't cause overlap
                lastChunk?.let { lb ->
                    val lookbackStart = speechStartSample!! - lb.size
                    if (lookbackStart >= currentSegmentStartSample) {
                        currentSegmentSamples.addAll(lb.toList())
                        Log.d(tagVad, "SEGMENT_START id=$segmentIdCounter startSample=$lookbackStart (with lookback) rms=$rms")
                    } else {
                        Log.d(tagVad, "SEGMENT_START id=$segmentIdCounter startSample=$speechStartSample rms=$rms (no lookback due to overlap guard)")
                    }
                } ?: run {
                    Log.d(tagVad, "SEGMENT_START id=$segmentIdCounter startSample=$speechStartSample rms=$rms")
                }
                if (lastChunk != null && (speechStartSample!! - lastChunk!!.size) >= currentSegmentStartSample) {
                    currentSegmentStartSample = speechStartSample!! - lastChunk!!.size
                } else {
                    currentSegmentStartSample = speechStartSample!!
                }
                currentSegmentSamples.addAll(chunk.toList())
                silenceMs = 0
                Log.d(tagVad, "SEGMENT_START id=$segmentIdCounter startSample=$currentSegmentStartSample")
                // Reset per-segment window streaming state for new segment
                accumulatedPreview = ""
                lastRawWindowText = ""
                currentPartial = ""
            } else {
                lastChunk = chunk
            }
            return finalizedLogs
        }

        currentSegmentSamples.addAll(chunk.toList())
        silenceMs = if (isSpeechChunk) 0 else silenceMs + chunkMs
        lastChunk = null
        val uttMs = currentSegmentSamples.size * 1000 / sampleRate
        if (silenceMs >= silenceHangoverMs || uttMs >= maxUtteranceMs) {
            val seg = finalizeCurrentSegment(isEndpoint = true)
            finalizedLogs.add(seg)
        }
        var v: VadSegment? = null
        while (vad.popSegment()?.also { v = it } != null) {
            Log.d(tagVad, "VAD internal pop segment samples=${v!!.samples.size} startSec=${v!!.startSec} endSec=${v!!.endSec} (internal, not used for committed — normalized)")
        }
        return finalizedLogs
    }

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
        val windowShort = currentSegmentSamples.subList(windowStartIdx, totalSize).toShortArray()

        Log.d(tagAsr, "ASR_WINDOW id=$segmentIdCounter startSample=$windowStartGlobal endSample=$windowEndGlobal newSamples=$windowSize mode=$streamingModeLabel")

        val startNs = SystemClock.elapsedRealtimeNanos()
        val txtRaw = if (testTranscriber != null) testTranscriber.invoke(windowShort).trim() else {
            try {
                val floatPcm = FloatArray(windowShort.size) { windowShort[it] / 32768.0f }
                directAsr.transcribe(floatPcm, sampleRate).text.trim()
            } catch (e: Exception) {
                // Model breakage, not silence: record it so finish()/callers
                // report MODEL failure instead of "no speech detected".
                if (lastDecodeError == null) {
                    lastDecodeError = e.message?.take(160)
                    Log.e(tagAsr, "ASR_PARTIAL id=$segmentIdCounter decode threw", e)
                }
                return currentPartial
            }
        }
        val latencyMs = (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000
        Log.d(tagAsr, "ASR_PARTIAL id=$segmentIdCounter latencyMs=$latencyMs text=\"$txtRaw\"")

        if (txtRaw.isBlank()) {
            return currentPartial
        }

        // Deduplicate overlapping hypotheses and maintain descriptive partial semantics
        val nextPartial: String = if (windowStartIdx == 0) {
            // Window is the entire active segment (segment shorter than windowSamples) — replace preview with full window hypothesis
            txtRaw
        } else {
            // Tail window — stitch deduplicated suffix onto accumulated preview to reconstruct full segment preview without repeats
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

    fun finalizeCurrentSegment(isEndpoint: Boolean = true): VadSegmentLog {
        if (currentSegmentSamples.isEmpty()) {
            val log = VadSegmentLog(segmentIdCounter, currentSegmentStartSample, currentSegmentStartSample, 0, true)
            Log.d(tagVad, "SEGMENT_FINAL id=${log.segmentId} startSample=${log.startSample} endSample=${log.endSample} durationMs=${log.durationMs} finalized=${log.finalized} text=\"\" (empty)")
            Log.d(tagVad, "segmentId=${log.segmentId} startSample=${log.startSample} endSample=${log.endSample} durationMs=${log.durationMs} finalized=${log.finalized} (empty)")
            Log.d(tagAsr, "ASR_FINAL id=${log.segmentId} latencyMs=0 text=\"\" (empty)")
            segmentIdCounter++
            currentSegmentStartSample = log.endSample
            // Reset window state for next segment
            accumulatedPreview = ""
            lastRawWindowText = ""
            currentPartial = ""
            return log
        }
        val startSample = currentSegmentStartSample
        val endSample = startSample + currentSegmentSamples.size
        val durationMs = (endSample - startSample) * 1000 / sampleRate
        val pcm = currentSegmentSamples.toShortArray()
        // Exactly ONE final decode for this segment (full segment, not window), commit exactly once
        val startNs = SystemClock.elapsedRealtimeNanos()
        val txt = if (testTranscriber != null) testTranscriber.invoke(pcm).trim() else {
            try {
                val floatPcm = FloatArray(pcm.size) { pcm[it] / 32768.0f }
                directAsr.transcribe(floatPcm, sampleRate).text.trim()
            } catch (e: Exception) {
                // Same contract as getPartial: a throwing model is a MODEL
                // failure, never VAD silence. Commit nothing, record why.
                if (lastDecodeError == null) {
                    lastDecodeError = e.message?.take(160)
                    Log.e(tagAsr, "ASR_FINAL id=$segmentIdCounter decode threw", e)
                }
                ""
            }
        }
        val latencyMs = (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000
        Log.d(tagVad, "SEGMENT_FINAL id=$segmentIdCounter startSample=$startSample endSample=$endSample durationMs=$durationMs finalized=true text=\"$txt\"")
        Log.d(tagAsr, "ASR_FINAL id=$segmentIdCounter latencyMs=$latencyMs text=\"$txt\"")
        if (txt.isNotBlank()) {
            val record = SegmentRecord(segmentIdCounter, startSample, endSample, durationMs, txt)
            segmentLedger.add(record)
            committedText = segmentLedger.sortedBy { it.startSample }.joinToString(" ") { it.finalText }
            Log.d(tagAsr, "SEGMENT_COMMITTED id=${record.segmentId} committed=\"$committedText\"")
            Log.d(tagAsr, "FINAL segmentId=$segmentIdCounter \"$txt\" -> committed=\"$committedText\"")
        } else {
            Log.d(tagAsr, "FINAL segmentId=$segmentIdCounter empty after decode (silence)")
        }
        val log = VadSegmentLog(segmentIdCounter, startSample, endSample, durationMs, true)
        Log.d(tagVad, "segmentId=${log.segmentId} startSample=${log.startSample} endSample=${log.endSample} durationMs=${log.durationMs} finalized=${log.finalized} text=\"$txt\"")
        segmentIdCounter++
        currentSegmentStartSample = endSample
        currentSegmentSamples.clear()
        currentPartial = ""
        accumulatedPreview = ""
        lastRawWindowText = ""
        hasSpeech = false
        silenceMs = 0
        speechStartSample = null
        try { vad.flush() } catch (_: Exception) {}
        return log
    }

    fun finish(): String {
        if (currentSegmentSamples.isNotEmpty()) {
            finalizeCurrentSegment(isEndpoint = true)
        }
        try { vad.flush() } catch (_: Exception) {}
        var seg: VadSegment? = null
        while (vad.popSegment()?.also { seg = it } != null) {
            Log.d(tagVad, "finish drain VAD internal pop ${seg!!.samples.size} samples")
        }
        val finalNs = SystemClock.elapsedRealtimeNanos()
        val totalMs = (finalNs - (t0Ns ?: finalNs)) / 1_000_000
        val firstMs = firstPartialNs?.let { (it - (t0Ns ?: it)) / 1_000_000 } ?: -1
        Log.d(tagLat, "final transcript ${totalMs}ms after t0, first partial was ${firstMs}ms committed=\"$committedText\" segments=${segmentLedger.size} mode=$streamingModeLabel")
        Log.d(tagAsr, "FINAL committed=\"$committedText\" segments=${segmentLedger.size} mode=$streamingModeLabel")
        return committedText
    }

    fun transcribeOneShot(pcm: ShortArray): String {
        if (testTranscriber != null) return testTranscriber.invoke(pcm).trim()
        val floatPcm = FloatArray(pcm.size) { pcm[it] / 32768.0f }
        val result = directAsr.transcribe(floatPcm, sampleRate)
        return result.text.trim()
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

    private fun rms(x: FloatArray): Float {
        var sum = 0.0
        for (v in x) sum += v * v
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
