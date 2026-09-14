package com.vachak.ml.adapter

import com.vachak.engine.VachakLog

import com.vachak.engine.ASREngine
import com.vachak.engine.EngineError
import com.vachak.engine.EngineResult

/**
 * Voice Activity Detection abstraction. Sits between raw mic capture and ASR so
 * we only transcribe speech segments (saves RAM/latency on 2GB devices).
 *
 * Reference: sherpa-onnx VAD (`com.k2fsa.sherpa.onnx.Vad`) is the production
 * backend. This interface is intentionally backend-agnostic; swap the impl, not
 * the callers.
 */
interface VadDetector {
    /** Returns indices [startMs, endMs] of detected speech segments in the buffer. */
    fun detect(pcm16: ShortArray, sampleRateHz: Int): List<SpeechSegment>
}

data class SpeechSegment(val startMs: Int, val endMs: Int)

/**
 * DEV FIXTURE VAD: treats the whole buffer as one speech segment. Replace with
 * sherpa-onnx `Vad` for real endpointing. Clearly a placeholder.
 */
object MockVadDetector : VadDetector {
    override fun detect(pcm16: ShortArray, sampleRateHz: Int): List<SpeechSegment> {
        if (pcm16.isEmpty()) return emptyList()
        val ms = (pcm16.size * 1000) / sampleRateHz
        return listOf(SpeechSegment(0, ms))
    }
}

/**
 * Unifies :ml VadAnalyzer (streaming, FloatArray) with :app VadDetector (whole-buffer, ShortArray).
 * Wraps Silero VAD via chunked streaming so callers using VadDetector get real neural endpointing
 * while the underlying Sherpa Vad stays on the :ml analyzer API. Falls back to MockVadDetector
 * energy gating if Silero model is not yet extracted.
 *
 * Single canonical VAD for Phase 3: SherpaAsrAdapter prefers this when context is available.
 */
class SherpaVadDetector(
    private val context: android.content.Context,
    private val sampleRate: Int = 16000
) : VadDetector {
    // Phase 2: ONE analyzer per detector instead of one per detect() call.
    // The old code rebuilt SherpaOnnxVadAnalyzer every call (SherpaAssets
    // extract-check + Vad native create per transcription). Safe because the
    // pipeline is sequential (Mutex): detect() calls never overlap, and
    // SherpaOnnxVadAnalyzer.ensure() is @Synchronized for the rest.
    private val analyzer by lazy { com.vachak.ml.SherpaOnnxVadAnalyzer(context, "vad", sampleRate) }
    override fun detect(pcm16: ShortArray, sampleRateHz: Int): List<SpeechSegment> {
        require(sampleRateHz == sampleRate) { "SherpaVadDetector requires $sampleRate Hz (got $sampleRateHz)" }
        if (pcm16.isEmpty()) return emptyList()
        // Chunked Float conversion — mirrors VadStream.runMicrophoneVad chunk size 100ms
        val chunkMs = 100
        val chunkSamples = (chunkMs / 1000f * sampleRate).toInt()
        var offset = 0
        val segments = mutableListOf<SpeechSegment>()
        while (offset < pcm16.size) {
            val end = minOf(offset + chunkSamples, pcm16.size)
            val chunk = FloatArray(end - offset) { pcm16[offset + it] / 32768.0f }
            analyzer.accept(chunk)
            analyzer.popSegment()?.let { seg ->
                val startMs = (seg.startSec * 1000).toInt()
                val endMs = (seg.endSec * 1000).toInt()
                segments += SpeechSegment(startMs, endMs)
                VachakLog.d("Vachak-VAD", "VAD segment $startMs..$endMs ms")
            }
            offset = end
        }
        analyzer.flush()
        analyzer.popSegment()?.let { seg ->
            val startMs = (seg.startSec * 1000).toInt()
            val endMs = (seg.endSec * 1000).toInt()
            segments += SpeechSegment(startMs, endMs)
            VachakLog.d("Vachak-VAD", "VAD flush segment $startMs..$endMs ms")
        }
        if (segments.isEmpty()) {
            // No speech detected — return empty so caller skips decode (saves RAM/latency)
            VachakLog.d("Vachak-VAD", "no speech segments for ${pcm16.size} samples")
            return emptyList()
        }
        return segments
    }
}
