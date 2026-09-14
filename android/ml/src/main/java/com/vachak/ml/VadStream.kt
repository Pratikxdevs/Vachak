package com.vachak.ml

import android.content.Context
import android.media.AudioRecord
import com.vachak.engine.VachakLog
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig

/**
 * 5A — Streaming audio (Kotlin reference): Microphone -> 10-100ms chunks -> VAD -> segments.
 *
 * Mirrors ml/pipeline/vad_stream.py. Uses sherpa-onnx Silero VAD (Apache-2.0) as the
 * reference detector. A [MockVadAnalyzer] energy gate is provided so the pipeline wires
 * end-to-end without a model (DEV FIXTURE — not a neural VAD).
 *
 * Chunk size is derived from [chunkMs] in [10, 100]; 100ms is the reference granularity.
 */
interface VadAnalyzer {
    fun accept(chunk: FloatArray)
    fun isSpeech(): Boolean
    fun popSegment(): VadSegment?
    fun flush()
}

data class VadSegment(
    val samples: FloatArray,
    val sampleRate: Int,
    val startSec: Float,
    val endSec: Float
) {
    val durationSec: Float get() = endSec - startSec
}

/**
 * REAL Silero VAD via the vendored sherpa-onnx AAR (k2-fsa/sherpa-onnx, Apache-2.0), built
 * over the cloned reference repo's Android API (`com.k2fsa.sherpa.onnx.Vad`).
 *
 * Requires a silero_vad.onnx asset (downloaded by scripts/fetch_android_models.sh from
 * k2-fsa/sherpa-onnx releases). This is a DEV-FIXTURE detector config; swap the model for a
 * tuned VAD if needed. Use [MockVadAnalyzer] for local wiring without a model.
 */
class SherpaOnnxVadAnalyzer(
    private val context: Context,
    private val modelDir: String = "vad",
    private val sampleRate: Int = 16000
) : VadAnalyzer {

    private var vad: Vad? = null
    private val tag = "Vachak-VAD"
    private var streamPos = 0f

    @Synchronized
    private fun ensure(): Vad {
        if (vad != null) return vad!!
        val baseDir = SherpaAssets.prepare(context, modelDir)
        val cfg = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(model = "$baseDir/silero_vad.onnx"),
            sampleRate = sampleRate,
            numThreads = 1
        )
        VachakLog.d(tag, "creating Vad (dir=$baseDir)")
        // Models are extracted to the filesystem (filesDir), so pass null AssetManager.
        vad = Vad(null, cfg)
        VachakLog.d(tag, "Vad ready")
        VachakLog.d("Vachak-VAD", "Silero VAD ready dir=$baseDir sr=$sampleRate threads=1")
        return vad!!
    }

    override fun accept(chunk: FloatArray) {
        ensure().acceptWaveform(chunk)
        streamPos += chunk.size / sampleRate.toFloat()
    }

    override fun isSpeech(): Boolean = ensure().isSpeechDetected()

    override fun popSegment(): VadSegment? {
        val v = ensure()
        if (v.empty()) return null
        val seg = v.front()
        v.pop()
        val start = (streamPos - seg.samples.size / sampleRate.toFloat()).coerceAtLeast(0f)
        return VadSegment(seg.samples, sampleRate, start, streamPos)
    }

    override fun flush() = ensure().flush()
}

/** DEV FIXTURE energy-gate VAD. Not a neural detector; local wiring only. */
class MockVadAnalyzer(
    private val sampleRate: Int = 16000,
    private val threshold: Float = 0.012f,
    private val minSpeechSamples: Int = 1600,
    private val padSamples: Int = 1600
) : VadAnalyzer {
    private val buf = mutableListOf<Float>()
    private var speaking = false
    private var speechStart = 0f
    private var streamPos = 0f
    private var segStartIdx = 0

    override fun accept(chunk: FloatArray) {
        val energy = kotlin.math.sqrt(chunk.sumOf { (it * it).toDouble() } / chunk.size).toFloat()
        val now = streamPos
        streamPos += chunk.size / sampleRate.toFloat()
        if (energy >= threshold) {
            if (!speaking) {
                speaking = true
                segStartIdx = maxOf(0, buf.size - padSamples)
                speechStart = now - padSamples / sampleRate.toFloat()
            }
            buf.addAll(chunk.toList())
        } else if (speaking) {
            buf.addAll(chunk.takeLast(padSamples).toList())
        }
    }

    override fun isSpeech(): Boolean = speaking

    override fun popSegment(): VadSegment? {
        if (!speaking) return null
        val seg = buf.subList(segStartIdx, buf.size).toFloatArray()
        val start = speechStart
        val end = start + seg.size / sampleRate.toFloat()
        buf.clear(); speaking = false
        return VadSegment(seg, sampleRate, start, end)
    }

    override fun flush() { buf.clear(); speaking = false }
}

/**
 * Drives a [VadAnalyzer] from an [AudioRecord] in [chunkMs] chunks. Call from a background
 * thread. [onSegment] fires for each detected speech segment.
 */
fun runMicrophoneVad(
    record: AudioRecord,
    analyzer: VadAnalyzer,
    chunkMs: Int = 100,
    onSegment: (VadSegment) -> Unit
) {
    require(chunkMs in 10..100) { "chunkMs must be in [10, 100]" }
    val chunk = (chunkMs / 1000f * record.sampleRate).toInt()
    val buffer = FloatArray(chunk)
    while (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
        val read = record.read(buffer, 0, chunk, AudioRecord.READ_BLOCKING)
        if (read <= 0) break
        analyzer.accept(buffer.copyOf(read))
        analyzer.popSegment()?.let(onSegment)
    }
    analyzer.flush()
    analyzer.popSegment()?.let(onSegment)
}
