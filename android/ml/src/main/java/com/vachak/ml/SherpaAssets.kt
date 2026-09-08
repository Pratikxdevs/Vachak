package com.vachak.ml

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Copies bundled sherpa-onnx model assets (assets/vachak_models/<sub>) into the app's
 * internal files directory so the native runtime can read them by filesystem path.
 *
 * Mirrors the asset-extraction pattern from the cloned k2-fsa/sherpa-onnx Android examples
 * (android/SherpaOnnx). Every copy is DEBUG-logged with the [Log] tag "Vachak-Assets".
 * Copying is RECURSIVE so nested dirs (e.g. tts/espeak-ng-data) are preserved.
 *
 * Pack-path aware (02-02): when a language pack dir is provided to
 * [SherpaOnnxTtsAdapter] (packDir contains model.onnx), this extractor is bypassed
 * entirely — the pack is already on the filesystem (filesDir/packs/<id>/...).
 * Otherwise this helper extracts bundled assets (fallback) via [prepare].
 *
 * Previously these were PUBLIC DEV-FIXTURE assets; post-02-01 they are the Santali
 * Ol Chiki VITS (see THIRD_PARTY_NOTICES.md and docs/MODEL_AND_DATA_PROVENANCE.md).
 */
object SherpaAssets {
    private const val TAG = "Vachak-Assets"
    const val ASSET_ROOT = "vachak_models"
    @Volatile private var prepared = mutableSetOf<String>()
    private val lock = Any()

    fun prepare(context: Context, subdir: String): String {
        val outDir = File(context.filesDir, "$ASSET_ROOT/$subdir").also { it.mkdirs() }
        // Fast-path: if already prepared in this process and tokens still present, skip IO.
        val key = "$ASSET_ROOT/$subdir"
        synchronized(lock) {
            if (prepared.contains(key) && File(outDir, "tokens.txt").exists()) {
                Log.d(TAG, "prepare cached hit: $key -> ${outDir.absolutePath}")
                return outDir.absolutePath
            }
        }
        // Also fast-path if filesDir already has the model from previous launch (persisted).
        // For ASR: model.onnx/model.int8.onnx or encoder.onnx indicates readiness; for VAD: silero_vad.onnx.
        val markerExists = when (subdir) {
            "asr" -> File(outDir, "tokens.txt").exists() && (File(outDir, "model.onnx").exists() || File(outDir, "model.int8.onnx").exists() || File(outDir, "encoder.onnx").exists())
            "vad" -> File(outDir, "silero_vad.onnx").exists()
            "tts" -> File(outDir, "model.onnx").exists()
            "mt" -> File(outDir, "encoder_model.onnx").exists() && File(outDir, "tokenizer_src.json").exists()
            else -> File(outDir, "tokens.txt").exists()
        }
        if (markerExists) {
            synchronized(lock) { prepared.add(key) }
            Log.d(TAG, "prepare cached (marker exists): $key -> ${outDir.absolutePath}")
            return outDir.absolutePath
        }
        copyTree(context, "$ASSET_ROOT/$subdir", outDir)
        synchronized(lock) { prepared.add(key) }
        return outDir.absolutePath
    }

    /**
     * Resolve pack dir for TTS: if packDir is non-null and contains model.onnx + tokens.txt,
     * return its absolute path (pack path); otherwise null (caller should fallback to [prepare]).
     * Kept here for reuse by adapters that need pack-aware asset resolution.
     */
    fun resolvePackDir(packDir: String?): String? {
        if (packDir == null) return null
        val f = File(packDir)
        return if (f.isDirectory && File(f, "model.onnx").exists() && File(f, "tokens.txt").exists()) f.absolutePath else null
    }

    private fun copyTree(context: Context, assetPath: String, outDir: File) {
        val entries = runCatching { context.assets.list(assetPath) }.getOrNull()
        if (entries.isNullOrEmpty()) {
            // Leaf file: copy it.
            runCatching {
                context.assets.open(assetPath).use { input ->
                    File(outDir, assetPath.substringAfterLast('/')).outputStream().use { out -> input.copyTo(out) }
                }
            }.onSuccess { Log.d(TAG, "copied asset: $assetPath") }
                .onFailure { Log.w(TAG, "asset copy failed: $assetPath (${it.message})") }
            return
        }
        for (name in entries) {
            val childPath = if (assetPath.isEmpty()) name else "$assetPath/$name"
            val childOut = File(outDir, name)
            if (runCatching { context.assets.list(childPath) }.getOrNull()?.isEmpty() == false) {
                childOut.mkdirs()
                copyTree(context, childPath, childOut)
            } else {
                childOut.parentFile?.mkdirs()
                runCatching {
                    context.assets.open(childPath).use { input ->
                        childOut.outputStream().use { out -> input.copyTo(out) }
                    }
                }.onSuccess { Log.d(TAG, "copied asset: $childPath") }
                    .onFailure { Log.w(TAG, "asset copy failed: $childPath (${it.message})") }
            }
        }
    }
}
