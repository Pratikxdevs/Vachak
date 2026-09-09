package com.vachak.ml

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.FloatBuffer

/**
 * Regression test for the Sep-2026 total ASR outage: the IndicConformer
 * `asr/model.onnx` expected filterbank input as [batch, 80, time] while the
 * sherpa-onnx runtime ALWAYS feeds [batch, time, 80]. Every decode threw
 * INVALID_ARGUMENT, swallowed as "No speech detected".
 *
 * Fixed by scripts/fix_asr_input_layout.py (leading Transpose). This test
 * pins the sherpa-native layout: it FAILS on the pre-fix bytes (dim[1]==80)
 * and passes on the fixed asset.
 */
class AsrLayoutRegressionTest {

    private val modelFile: File
        get() {
            // The ASR asset ships in the :app module (sole copy); :ml has none.
            // Resolve across plausible runner CWDs. Never machine-absolute.
            val candidates = listOf(
                "../app/src/main/assets/vachak_models/asr/model.onnx", // :ml module dir
                "app/src/main/assets/vachak_models/asr/model.onnx", // android/ project dir
                "android/app/src/main/assets/vachak_models/asr/model.onnx" // repo root
            )
            return candidates.map(::File).firstOrNull { it.exists() }
                ?: error("ASR asset not found from ${System.getProperty("user.dir")} (tried ${candidates.joinToString()})")
        }

    @Test
    fun asrModelAcceptsSherpaTimeMajorLayout() {
        val env = OrtEnvironment.getEnvironment()
        val opts = OrtSession.SessionOptions()
        env.createSession(modelFile.absolutePath, opts).use { session ->
            val tensorInfo = session.inputInfo.getValue("audio_signal").info as ai.onnxruntime.TensorInfo
            val shape = tensorInfo.shape
            assertEquals("input must be rank 3 [B,T,F]", 3, shape.size)
            assertEquals("feature dim must be 80 (sherpa fbank)", 80L, shape[2])
            assertTrue("time dim must be dynamic, got ${shape[1]}", shape[1] <= 0L)
            val meta = session.metadata.customMetadata
            assertEquals("4", meta["subsampling_factor"])
            assertEquals("5633", meta["vocab_size"])
            // A sherpa-shaped forward must run (pre-fix bytes throw here).
            val t = 16
            val buf = FloatBuffer.allocate(1 * t * 80)
            val inputs = mapOf(
                "audio_signal" to ai.onnxruntime.OnnxTensor.createTensor(env, buf, longArrayOf(1, t.toLong(), 80)),
                "length" to ai.onnxruntime.OnnxTensor.createTensor(env, java.nio.LongBuffer.allocate(1).put(0, t.toLong()), longArrayOf(1))
            )
            session.run(inputs).use { out ->
                val logprobs = out.get(0).value as Array<Array<FloatArray>>
                assertEquals(1, logprobs.size)
                assertEquals(t / 4, logprobs[0].size) // 4x subsampling
                assertEquals(5633, logprobs[0][0].size)
            }
            inputs.values.forEach { try { it.close() } catch (_: Exception) { /* test cleanup */ } }
        }
    }
}
