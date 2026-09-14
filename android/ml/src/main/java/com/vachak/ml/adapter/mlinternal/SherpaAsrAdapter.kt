package com.vachak.ml.adapter.mlinternal

import android.content.Context
import com.vachak.engine.VachakLog
import com.vachak.engine.ASREngine
import com.vachak.engine.EngineError
import com.vachak.engine.EngineResult
import com.vachak.ml.IndicConformerAsrAdapter

/**
 * :ml SherpaAsrAdapter shim — mirrors android/app SherpaAsrAdapter for plan compliance.
 * Renamed to mlinternal to avoid duplicate with :app's com.vachak.ml.adapter.SherpaAsrAdapter in release R8.
 */
class SherpaAsrAdapter(
    private val context: Context,
    private val vad: VadDetector
) : ASREngine {

    private var real: IndicConformerAsrAdapter? = null

    override fun supports(language: String) = language == "hi"

    override fun loadModel(packId: String): EngineResult<Unit> {
        return runCatching { real = IndicConformerAsrAdapter(context) }
            .fold(
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
        VachakLog.d("Vachak-VAD", "VAD segments=${segments.size} for ${pcm16.size} samples @ $sampleRateHz Hz: $segments")
        VachakLog.d("Vachak-ASR", "VAD gated ${segments.size} segments from ${pcm16.size} samples")
        if (segments.isEmpty()) {
            VachakLog.d("Vachak-ASR", "transcribed 0 segments (silence) -> \"\"")
            return EngineResult.Ok("")
        }

        val texts = segments.mapNotNull { seg ->
            val s = (seg.startMs * sampleRateHz / 1000).coerceAtLeast(0)
            val e = (seg.endMs * sampleRateHz / 1000).coerceAtMost(pcm16.size)
            if (e <= s) return@mapNotNull null
            val floatSeg = FloatArray(e - s) { pcm16[s + it] / 32768.0f }
            val asrResult = engine.transcribe(floatSeg, sampleRateHz)
            VachakLog.d("Vachak-ASR", "segment [${seg.startMs},${seg.endMs}] ${floatSeg.size} floats -> \"${asrResult.text}\" isFixture=${asrResult.isFixture}")
            asrResult.text
        }.filter { it.isNotBlank() }

        val result = texts.joinToString(" ").trim()
        val latencyMs = (android.os.SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000f
        VachakLog.d("Vachak-ASR", "transcribed ${pcm16.size} samples @ $sampleRateHz Hz -> \"$result\" (${latencyMs}ms, ${segments.size} segments)")
        VachakLog.d("Vachak-Latency", "ASR total ${latencyMs}ms for ${pcm16.size} samples, withinBudget=${latencyMs <= 1000}")
        if (latencyMs > 1000) VachakLog.w("Vachak-Latency", "ASR latency ${latencyMs}ms exceeds 1000ms budget")
        return EngineResult.Ok(result)
    }
}
