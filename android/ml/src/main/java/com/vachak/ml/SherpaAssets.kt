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
    private const val MANIFEST = ".vachak_manifest"

    /**
     * Asset generation per subdir. Bump when bundled assets change so a stale
     * filesDir copy (kept across `install -r` upgrades) re-extracts once instead
     * of serving the old model forever. The manifest only records file sizes, so
     * without this an old copy + old manifest always "matches".
     * tts v2 = 38-token sprint VITS (was v1 55-token shim).
     */
    fun assetVersion(subdir: String): Int = when (subdir) {
        "tts" -> 2
        else -> 1
    }

    /** True when the extracted dir was written by the current asset generation.
     * Internal for tests (unit-test sandbox has no bundled assets to copy). */
    internal fun isCurrentGeneration(outDir: File, subdir: String): Boolean =
        manifestVersionOk(outDir, subdir)

    private fun manifestVersionOk(outDir: File, subdir: String): Boolean {
        return try {
            val first = File(outDir, MANIFEST).bufferedReader().readLine() ?: ""
            first.contains("manifest v${assetVersion(subdir)}")
        } catch (_: Exception) { false }
    }
    @Volatile private var prepared = mutableSetOf<String>()
    private val lock = Any()

    fun prepare(context: Context, subdir: String): String {
        val outDir = File(context.filesDir, "$ASSET_ROOT/$subdir").also { it.mkdirs() }
        // Fast-path: if already prepared in this process and tokens still present, skip IO.
        val key = "$ASSET_ROOT/$subdir"
        synchronized(lock) {
            if (prepared.contains(key) && File(outDir, "tokens.txt").exists()
                && manifestVersionOk(outDir, subdir)) {
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
            // Integrity gate: a kill -9 mid-copy leaves markers behind with
            // truncated bytes. The manifest (written after a verified copy)
            // is the source of truth — mismatch means re-copy once, loudly.
            if (verifyManifest(context, subdir, outDir)) {
                synchronized(lock) { prepared.add(key) }
                Log.d(TAG, "prepare cached (marker+manifest OK): $key -> ${outDir.absolutePath}")
                return outDir.absolutePath
            }
            Log.w(TAG, "prepare manifest MISMATCH for $key — previous copy incomplete, re-copying")
        }
        copyTree(context, "$ASSET_ROOT/$subdir", outDir)
        writeManifest(context, subdir, outDir)
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

    /**
     * Manifest of name->bytes for every file under an extracted subdir,
     * written only after a complete copy. Comparing it on the marker-hit
     * path turns silent truncation rot into a one-time loud re-copy.
     */
    private fun manifestEntries(outDir: File): Map<String, Long> =
        outDir.walkTopDown()
            // .vachak_manifest is the manifest itself; bpe_codec.bin is a runtime
            // cache written AFTER extraction (OnnxIndicTrans2Adapter) — neither
            // must invalidate the manifest, or every launch re-copies 370MB.
            .filter { it.isFile && it.name != MANIFEST && it.name != "bpe_codec.bin" }
            .associate { it.relativeTo(outDir).path to it.length() }

    private fun writeManifest(context: Context, subdir: String, outDir: File) {
        try {
            val lines = manifestEntries(outDir).entries
                .sortedBy { it.key }
                .joinToString("\n") { "${it.key}\t${it.value}" }
            File(outDir, MANIFEST).writeText("# vachak $subdir manifest v${assetVersion(subdir)} (name<TAB>bytes)\n$lines\n")
            Log.d(TAG, "manifest written for $subdir (${lines.lines().size} files)")
        } catch (e: Exception) {
            Log.w(TAG, "manifest write failed for $subdir: ${e.message}")
        }
    }

    private fun verifyManifest(context: Context, subdir: String, outDir: File): Boolean {
        return try {
            val mf = File(outDir, MANIFEST)
            if (!mf.exists()) {
                // Pre-manifest install (upgrade path): trust markers this once,
                // then write the manifest so all future launches verify.
                Log.d(TAG, "no manifest for $subdir (pre-manifest install) — trusting markers once")
                writeManifest(context, subdir, outDir)
                return true
            }
            if (!manifestVersionOk(outDir, subdir)) {
                Log.w(TAG, "asset generation STALE for $subdir (want v${assetVersion(subdir)}) — re-copying once")
                return false
            }
            val expected = mf.readLines()
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .associate {
                    val (name, size) = it.split("\t")
                    name to size.toLong()
                }
            val actual = manifestEntries(outDir)
            if (expected == actual) return true
            val missing = (expected.keys - actual.keys).take(5)
            val wrongSize = expected.keys.intersect(actual.keys)
                .filter { expected[it] != actual[it] }.take(5)
            Log.w(TAG, "manifest mismatch $subdir missing=$missing wrongSize=$wrongSize")
            false
        } catch (e: Exception) {
            Log.w(TAG, "manifest verify failed for $subdir (re-copying to be safe): ${e.message}")
            false
        }
    }

    private fun copyTree(context: Context, assetPath: String, outDir: File) {        val entries = runCatching { context.assets.list(assetPath) }.getOrNull()
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
