package com.vachak.ml.adapter

import android.content.Context
import android.util.Log
import com.vachak.engine.ASREngine
import com.vachak.engine.EngineError
import com.vachak.engine.EngineResult
import com.vachak.ml.IndicConformerAsrAdapter

/**
 * sherpa-onnx-backed Hindi ASR adapter (built over the vendored k2-fsa/sherpa-onnx AAR).
 *
 * Delegates real recognition to [IndicConformerAsrAdapter] in the :ml module (offline Hindi ASR).
 * No mock fallback — production path is always real (offline, pack-aware). Mock adapters remain
 * in :core for isolated unit tests only (EngineProvider.mock), not in this real adapter.
 */
class SherpaAsrAdapter(
    private val context: Context,
    private val vad: VadDetector
) : ASREngine {

    private var real: IndicConformerAsrAdapter? = null

    override fun supports(language: String) = language == "hi"

    override fun loadModel(packId: String): EngineResult<Unit> {
        return runCatching {
            val adapter = IndicConformerAsrAdapter(context)
            real = adapter
            // Actually warm the 140MB recognizer now (IO thread) so the first
            // mic press transcribes immediately — "model always live".
            // Status (READY/ERROR) is recorded by the adapter itself; a failed
            // warm-up is an honest Err, never a silent OK (preload must not lie).
            if (!adapter.warmUpIfNeeded()) {
                throw IllegalStateException("ASR warm-up failed — see logcat Vachak-ASR")
            }
        }.fold(
            onSuccess = { EngineResult.Ok(Unit) },
            onFailure = { e -> EngineResult.Err(EngineError.MODEL_LOAD_FAILED, e.message ?: "asr load failed") }
        )
    }

    override fun transcribe(pcm16: ShortArray, sampleRateHz: Int): EngineResult<String> {
        if (sampleRateHz <= 0) return EngineResult.Err(EngineError.INVALID_INPUT, "bad sample rate")
        if (pcm16.isEmpty()) return EngineResult.Err(EngineError.INVALID_INPUT, "empty pcm")

        val t0 = android.os.SystemClock.elapsedRealtimeNanos()
        val engine = real ?: IndicConformerAsrAdapter(context)
        val segments = vad.detect(pcm16, sampleRateHz)
        Log.d("Vachak-VAD", "VAD segments=${segments.size} for ${pcm16.size} samples @ $sampleRateHz Hz: $segments")
        Log.d("Vachak-ASR", "VAD gated ${segments.size} segments from ${pcm16.size} samples")
        if (segments.isEmpty()) {
            Log.d("Vachak-ASR", "transcribed 0 segments (silence) -> Err INVALID_INPUT")
            return EngineResult.Err(
                EngineError.INVALID_INPUT,
                "VAD heard no speech (${pcm16.size} samples) — speak closer/louder and retry"
            )
        }

        val texts = segments.mapNotNull { seg ->
            val s = (seg.startMs * sampleRateHz / 1000).coerceAtLeast(0)
            val e = (seg.endMs * sampleRateHz / 1000).coerceAtMost(pcm16.size)
            if (e <= s) return@mapNotNull null
            // Short PCM16 -> Float32 bridge: short/32768.0f (unifies :app ShortArray with :ml FloatArray)
            val floatSeg = FloatArray(e - s) { pcm16[s + it] / 32768.0f }
            try {
                val asrResult = engine.transcribe(floatSeg, sampleRateHz)
                Log.d("Vachak-ASR", "segment [${seg.startMs},${seg.endMs}] ${floatSeg.size} floats -> \"${asrResult.text}\" isFixture=${asrResult.isFixture}")
                asrResult.text
            } catch (ex: Exception) {
                // A throwing model is a MODEL failure, never silence: fail the
                // whole transcription with the cause instead of returning "".
                Log.e("Vachak-ASR", "segment decode threw", ex)
                return EngineResult.Err(EngineError.MODEL_DECODE_FAILED, "ASR decode failed: ${ex.message?.take(120)}")
            }
        }.filter { it.isNotBlank() }

        val result = texts.joinToString(" ").trim()
        if (result.isBlank()) {
            Log.w("Vachak-ASR", "decoded ${segments.size} segments but all blank -> Err INVALID_INPUT")
            return EngineResult.Err(
                EngineError.INVALID_INPUT,
                "ASR decoded no words (${segments.size} segments, ${pcm16.size} samples) — speak closer/louder and retry"
            )
        }
        val latencyMs = (android.os.SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000f
        Log.d("Vachak-ASR", "transcribed ${pcm16.size} samples @ $sampleRateHz Hz -> \"$result\" (${latencyMs}ms, ${segments.size} segments)")
        Log.d("Vachak-Latency", "ASR total ${latencyMs}ms for ${pcm16.size} samples, withinBudget=${latencyMs <= 1000}")
        if (latencyMs > 1000) Log.w("Vachak-Latency", "ASR latency ${latencyMs}ms exceeds 1000ms budget")
        return EngineResult.Ok(result)
    }
}
