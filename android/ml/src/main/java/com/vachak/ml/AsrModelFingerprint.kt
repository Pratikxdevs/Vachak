package com.vachak.ml

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Identifies EXACTLY which ASR model bytes are on this device.
 *
 * Why this exists: filesDir models survive app updates (`install -r` keeps
 * app data), so a tablet can run a NEW app build against a STALE model from
 * months ago — e.g. the broken [B,T,80] rewrite vs the correct [B,80,T]
 * sherpa-native layout (Sep 2026 revert). The resulting ORT shape errors are
 * otherwise indistinguishable from code bugs.
 * This reads ground truth (input dims, metadata, sha256) straight from the
 * resolved model file. One-shot, on-demand (Diagnostics button): opening an
 * ORT session transiently maps ~140MB, so never on the hot path.
 */
object AsrModelFingerprint {

    data class Fingerprint(
        val path: String,
        val bytes: Long,
        val sha256prefix: String,
        /** e.g. audio_signal:[?,?,80] */
        val inputs: String,
        val metadata: Map<String, String>
    ) {
        override fun toString(): String =
            "asr=$path ${bytes / 1024 / 1024}MB sha=$sha256prefix inputs=$inputs " +
                "meta=${metadata.entries.joinToString { "${it.key}=${it.value}" }}"
    }

    fun read(modelDir: File): Fingerprint? {
        val model = File(modelDir, "model.onnx")
            .takeIf { it.exists() }
            ?: File(modelDir, "model.int8.onnx").takeIf { it.exists() }
            ?: run {
                Log.w("Vachak-ASR", "fingerprint: no model.onnx in $modelDir")
                return null
            }
        return try {
            val bytes = model.length()
            val sha = shaPrefix(model)
            var inputs = "?"
            var meta: Map<String, String> = emptyMap()
            try {
                val env = OrtEnvironment.getEnvironment()
                val opts = OrtSession.SessionOptions()
                env.createSession(model.absolutePath, opts).use { session ->
                    inputs = session.inputInfo.entries.joinToString { (name, info) ->
                        val shape = (info.info as? ai.onnxruntime.TensorInfo)?.shape
                            ?.joinToString(",", "[", "]") { if (it <= 0) "?" else it.toString() }
                            ?: "?"
                        "$name:$shape"
                    }
                    meta = try {
                        session.metadata.customMetadata
                    } catch (_: Exception) {
                        emptyMap()
                    }
                }
            } catch (e: Exception) {
                Log.w("Vachak-ASR", "fingerprint: ORT inspect failed (file still hashed): ${e.message}")
            }
            Fingerprint(model.absolutePath, bytes, sha, inputs, meta).also {
                Log.d("Vachak-ASR", "fingerprint: $it")
            }
        } catch (e: Exception) {
            Log.w("Vachak-ASR", "fingerprint failed: ${e.message}")
            null
        }
    }

    private fun shaPrefix(f: File): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(f).use { inp ->
                val buf = ByteArray(1 shl 20)
                while (true) {
                    val n = inp.read(buf)
                    if (n <= 0) break
                    digest.update(buf, 0, n)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }.take(16)
        } catch (e: Exception) {
            "unreadable:${e.message?.take(40)}"
        }
    }
}
