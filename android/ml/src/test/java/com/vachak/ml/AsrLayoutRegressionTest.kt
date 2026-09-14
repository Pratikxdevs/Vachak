package com.vachak.ml

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.FloatBuffer

/**
 * Pins the sherpa-native ASR input layout: channel-first [batch, 80, time].
 *
 * Background (Sep 2026): sherpa-onnx `OfflineNemoEncDecCtcModel::Forward`
 * computes fbank as (B,T,80) and then applies `Transpose12` itself --
 * `(B, T, C) -> (B, C, T)` -- before running the graph. So the
 * `asr/model.onnx` graph must declare `audio_signal` as [B,80,T] (the
 * original AI4Bharat IndicConformer export). A well-meaning
 * `scripts/fix_asr_input_layout.py` rewrote it to [B,T,80] behind a leading
 * Transpose; that double-transpose threw
 * `index: 2 Got: <T> Expected: 80` on EVERY device decode (Sep 10 2026
 * logcat), fixed by `scripts/revert_asr_layout_fix.py`.
 *
 * This test FAILS on the broken [B,T,80] bytes (shape[1] dynamic, shape[2]
 * == 80) and passes on the correct asset. It feeds the graph exactly what
 * sherpa feeds it post-Transpose12.
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
    fun asrModelAcceptsSherpaChannelFirstLayout() {
        val env = OrtEnvironment.getEnvironment()
        val opts = OrtSession.SessionOptions()
        env.createSession(modelFile.absolutePath, opts).use { session ->
            val tensorInfo = session.inputInfo.getValue("audio_signal").info as ai.onnxruntime.TensorInfo
            val shape = tensorInfo.shape
            assertEquals("input must be rank 3 [B,80,T]", 3, shape.size)
            assertEquals("channel dim must be 80 (sherpa feeds Transpose12 fbank)", 80L, shape[1])
            assertTrue("time dim must be dynamic, got ${shape[2]}", shape[2] <= 0L)
            val meta = session.metadata.customMetadata
            assertEquals("4", meta["subsampling_factor"])
            assertEquals("5633", meta["vocab_size"])
            // A sherpa-shaped forward must run (broken [B,T,80] bytes throw here).
            val t = 16
            val buf = FloatBuffer.allocate(1 * 80 * t)
            val inputs = mapOf(
                "audio_signal" to ai.onnxruntime.OnnxTensor.createTensor(env, buf, longArrayOf(1, 80, t.toLong())),
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
