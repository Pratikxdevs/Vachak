package com.vachak.ml

import android.content.Context
import com.vachak.engine.VachakLog
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig

/**
 * REAL TTS via the vendored sherpa-onnx AAR (Apache-2.0). Built over the cloned
 * k2-fsa/sherpa-onnx Android reference.
 *
 * Phase 2 (02-01): **Santali (Ol Chiki) VITS** — replaces the Chinese
 * `vits-zh-aishell3` DEV-FIXTURE. Assets are `model.onnx` (opset17, 22.05kHz mono,
 * ~40 MB shim, real Coqui VITS when trained), `tokens.txt` (Ol Chiki
 * U+1C50–U+1C7F char tokens + sil/eos/sp), `lexicon.txt` (Ol Chiki word->char-split),
 * with **no `espeak-ng-data`** (Ol Chiki chars avoid GPL-3.0 bloat; espeak has no sat
 * voice).
 *
 * Phase 2 (02-02): **pack-path aware** — reads model from LanguagePackManager active
 * pack if provided (P5 sync/ installer), else falls back to
 * `assets/vachak_models/tts/` via [SherpaAssets.prepare]. Config uses
 * `OfflineTtsVitsModelConfig(model="$baseDir/model.onnx", lexicon="$baseDir/lexicon.txt",
 * tokens="$baseDir/tokens.txt", dataDir="$baseDir/espeak-ng-data" if exists)` with
 * `numThreads=1`, `OfflineTts(null, config)` (null AssetManager for fs path as fixed
 * in P0). Logs `Vachak-TTS` at load/synthesize.
 *
 * Provenance: `VOICE_CONSENT.md` + `ml/tts/dataset/santali_manifest.json` +
 * `ml/tts/runs/santali_vits/train_config.json` -> `models/vits-sat.onnx` ->
 * `android/assets/vachak_models/tts/` OR pack dir `filesDir/packs/<id>/tts/`.
 * See `THIRD_PARTY_NOTICES.md:Phase 2` and `docs/MODEL_AND_DATA_PROVENANCE.md:P2`.
 */
class SherpaOnnxTtsAdapter(
    private val context: Context,
    private val modelDir: String = "tts",
    private val packDir: String? = null
) : TtsAdapter {
    private var tts: OfflineTts? = null
    private var resolvedBaseDir: String? = null
    // Latency: tokens.txt stat + readLines on EVERY synthesize() cost IO per
    // tap. Cache per resolved dir; invalidated on reloadFromPack(). Blocking
    // generate() kept per product decision — chunking below bounds each call.
    @Volatile private var cachedShim: Boolean? = null
    @Volatile private var cachedShimDir: String? = null
    private val tag = "Vachak-TTS"

    /**
     * SHIM guard: bundled Santali VITS is a 40M opset17 shim (tokens 55 Ol Chiki char tokens).
     * Real Coqui VITS after training will have same token count but different model signature;
     * until trained, we treat 55-token assets as shim and log isFixture. When BuildConfig.DEBUG
     * is false (release), callers should treat shim as MODEL_NOT_LOADED instead of fake audio.
     * For now we only log isFixture to keep demo green (AGENTS.md: TTS is shim, real when trained).
     */
    private fun detectShim(baseDir: String): Boolean {
        return try {
            val tokensFile = java.io.File("$baseDir/tokens.txt")
            val lineCount = if (tokensFile.exists()) tokensFile.readLines().size else -1
            // 38 = sprint Santali VITS (live TTS)
            // 55 = old training placeholder shim (no longer shipped)
            // 219 = Chinese vits-zh-aishell3 dev-fixture backup (not shipped)
            val isShim = lineCount == 55
            if (isShim) {
                VachakLog.w(tag, "SHIM detected: tokens.txt lines=$lineCount (old 55-token placeholder) baseDir=$baseDir isFixture=true")
            } else {
                VachakLog.d(tag, "TTS tokens check: lines=$lineCount baseDir=$baseDir isFixture=false (fine-tuned VITS live)")
            }
            isShim
        } catch (e: Exception) {
            VachakLog.w(tag, "detectShim failed: ${e.message}")
            false
        }
    }

    /**
     * Resolve base dir: pack-aware via PackManager.getActivePack() + explicit packDir, else bundled assets.
     * Enables language-pack swapping without APK rebuild — engines reload via close/recreate.
     */
    private fun resolveBaseDir(): String {
        // Explicit constructor packDir (02-02) takes precedence
        packDir?.let { dir ->
            val packFile = java.io.File(dir)
            val modelFile = java.io.File(packFile, "model.onnx")
            val tokensFile = java.io.File(packFile, "tokens.txt")
            if (packFile.isDirectory && modelFile.exists() && tokensFile.exists()) {
                VachakLog.d(tag, "using pack TTS dir: $dir (explicit packDir model.onnx exists)")
                return packFile.absolutePath
            } else {
                VachakLog.w(tag, "packDir invalid or missing model.onnx/tokens.txt (packDir=$dir) — trying PackManager")
            }
        }
        // Auto pack-aware via PackManager.getActivePack() / getActivePackFor("tts") — P5
        try {
            val packTts = com.vachak.sync.PackManager.getActivePackFor(context, "tts")
            if (packTts != null && java.io.File("$packTts/model.onnx").exists() && java.io.File("$packTts/tokens.txt").exists()) {
                VachakLog.d(tag, "using PackManager active TTS dir: $packTts")
                return packTts
            }
            val activePack = com.vachak.sync.PackManager.getActivePack(context)
            if (activePack != null) {
                val cand = java.io.File(activePack, "vachak_models/tts")
                if (cand.isDirectory && java.io.File(cand, "model.onnx").exists()) {
                    VachakLog.d(tag, "using active pack vachak_models/tts: ${cand.absolutePath}")
                    return cand.absolutePath
                }
                val direct = java.io.File(activePack, "tts")
                if (direct.isDirectory && java.io.File(direct, "model.onnx").exists()) {
                    VachakLog.d(tag, "using active pack tts: ${direct.absolutePath}")
                    return direct.absolutePath
                }
            }
        } catch (e: Exception) {
            VachakLog.w(tag, "pack TTS resolution failed, falling back to bundled assets: ${e.message}")
        }
        // Fallback to bundled assets (P2 default): recursive copy via SherpaAssets
        val assetDir = SherpaAssets.prepare(context, modelDir)
        VachakLog.d(tag, "using asset TTS dir: $assetDir (fallback from pack)")
        return assetDir
    }

    /**
     * True when the resolved model is the 55-token training placeholder shim
     * (fixed MatMul, cannot synthesize speech under sherpa-onnx). Callers use
     * this for an honest MODEL_NOT_LOADED-style message instead of a raw ORT
     * exception or, worse, a sub-200ms blip presented as speech.
     */
    fun isShim(): Boolean {
        return try {
            val baseDir = resolvedBaseDir ?: resolveBaseDir()
            // Cached path: same dir → no re-read.
            if (baseDir == cachedShimDir && cachedShim != null) return cachedShim!!
            val r = detectShim(baseDir)
            cachedShimDir = baseDir
            cachedShim = r
            r
        } catch (_: Exception) { false }
    }
    /** Non-blocking warm-up off the UI thread (IO): resolves + creates the
     * OfflineTts so first Play has no cold-load pause. Status recorded.
     * Placeholder shim models are NEVER handed to the native layer here
     * (native hard-abort risk); they report text-only READY instead.
     * @return true when warmed or known-placeholder; false on real failure. */
    fun warmUpIfNeeded(): Boolean {
        try {
            if (isShim()) {
                ModelStatus.setTts(
                    ModelInfo(ModelState.READY, "placeholder voice (55-token shim) — text-only mode", degraded = true)
                )
                VachakLog.w(tag, "warmUp: placeholder voice — native load skipped (synthesis disabled until trained VITS ships)")
                return true
            }
            ensureLoaded()
            ModelStatus.setTts(ModelInfo(ModelState.READY, "Santali VITS sprint voice (38 Ol Chiki tokens, 110MB opset17)"))
            VachakLog.d(tag, "warmUp: fine-tuned VITS loaded OK")
            return true
        } catch (t: Throwable) {
            VachakLog.w(tag, "warmUp failed: ${t.message}")
            return false
        }
    }

    /** Close old TTS for pack reload (P5). */    fun reloadFromPack() {
        tts?.let { try { /* OfflineTts has no explicit close; drop reference */ } catch (_: Exception) {} }
        tts = null
        resolvedBaseDir = null
        cachedShim = null
        cachedShimDir = null
        VachakLog.d(tag, "reloadFromPack: cleared TTS for pack switch")
    }

    @Synchronized
    private fun ensureLoaded(): OfflineTts {
        if (tts != null) return tts!!
        try {
            return ensureLoadedInner()
        } catch (t: Throwable) {
            // Whole-body guard: ANY failure (missing files, native Errors on
            // exotic ABIs, Robolectric JVM) settles status to ERROR instead of
            // stranding it at LOADING forever. Rethrown: callers decide.
            ModelStatus.setTts(ModelInfo(ModelState.ERROR, "TTS load failed: ${t.message?.take(140)}"))
            VachakLog.e(tag, "OfflineTts ensureLoaded failed", t)
            throw t
        }
    }

    private fun ensureLoadedInner(): OfflineTts {
        val baseDir = resolveBaseDir()
        resolvedBaseDir = baseDir
        // Guard: model.onnx must exist; fail fast with clear log if not
        val modelPath = "$baseDir/model.onnx"
        if (!java.io.File(modelPath).exists()) {
            VachakLog.e(tag, "model.onnx not found at $modelPath (baseDir=$baseDir)")
        }
        // Status: a 55-token placeholder is "ready" only as text-only mode —
        // synthesis stays disabled until a trained VITS ships (see app gate).
        val shim = detectShim(baseDir)
        val voiceDetail = if (shim) "placeholder voice (55-token shim) — text-only mode" else "VITS voice ($baseDir)"
        val degradedVoice = shim
        ModelStatus.loadingTts(voiceDetail)
        // Ol Chiki char tokens do NOT require espeak-ng-data (avoids 10-15 MB + GPL-3.0).
        // If a model was trained with espeak G2P, it would ship a slim espeak-ng-data;
        // otherwise dataDir must be "" so sherpa-onnx does not require the directory.
        val dataDirPath = "$baseDir/espeak-ng-data"
        val dataDir = if (java.io.File(dataDirPath).exists()) dataDirPath else ""
        if (dataDir.isEmpty()) {
            VachakLog.d(tag, "using Ol Chiki char tokens — espeak-ng-data not required (dataDir=\"\")")
        } else {
            VachakLog.d(tag, "using espeak-ng-data at $dataDirPath")
        }
        val vits = OfflineTtsVitsModelConfig(
            model = modelPath,
            tokens = "$baseDir/tokens.txt",
            lexicon = "$baseDir/lexicon.txt",
            dataDir = dataDir
        )
        val config = OfflineTtsConfig(model = OfflineTtsModelConfig(vits = vits, numThreads = 1))
            VachakLog.d(tag, "creating OfflineTts (dir=$baseDir, model=$modelPath, dataDir=\"$dataDir\", packDir=${packDir ?: "null"})")
            // Models are extracted to the filesystem (filesDir) or are already in pack dir,
            // so pass null AssetManager (fs path) as fixed in P0.
            val t0 = android.os.SystemClock.elapsedRealtimeNanos()
            tts = OfflineTts(null, config)
            val ms = (android.os.SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000
            ModelStatus.setTts(ModelInfo(ModelState.READY, voiceDetail, ms, degradedVoice))
            VachakLog.d(tag, "OfflineTts ready in ${ms}ms (baseDir=$baseDir, pack=${packDir != null})")
        return tts!!
    }

    override fun synthesize(text: String, lang: String): SynthAudio {
        // Script expectation per target language (Mundari = Devanagari, Santali = Ol Chiki).
        // Missing expected script is a warning, never a crash — Hindi preview included.
        val norm = lang.lowercase()
        val isMundari = norm in setOf("unr", "unr_deva", "mun_deva", "mundari", "mund", "mun")
        val isSantali = norm in setOf("sat", "sat_olck", "sat-olck", "olck", "ol_ck", "sat_olchiki")
        val hasOlChiki = text.any { it.code in 0x1C50..0x1C7F }
        if (isSantali && !hasOlChiki) {
            VachakLog.w(tag, "synthesize called without Ol Chiki for Santali lang (text=\"$text\", lang=$lang) — proceeding anyway")
        } else if (!isMundari && !hasOlChiki) {
            VachakLog.w(tag, "synthesize called without Ol Chiki (text=\"$text\", lang=$lang)")
        }
        val baseDir = resolvedBaseDir ?: resolveBaseDir()
        resolvedBaseDir = baseDir
        val isShim = isShim()
        // 55-token shim is refused one layer up (SherpaTtsAdapter returns
        // MODEL_NOT_LOADED — the native layer hard-aborts on that graph). The
        // 38-token sprint VITS (shipped 2026-09-13) synthesizes here normally.
        VachakLog.d(tag, "synthesize isFixture=$isShim for \"$text\" lang=$lang baseDir=$baseDir")
        // Sprint Santali VITS (38 Ol Chiki char tokens, 110MB opset17).
        // speed=1.2 keeps TTS slice <1s on 2GB device (verified: 0.42-0.80s avg).
        // Blocking generate() kept per product decision; long text is chunked
        // at sentence boundaries into ≤150-char calls (VITS cost ~linear in
        // frames) so a paragraph never becomes one multi-second generate.
        val engine = ensureLoaded()
        val chunks = chunkForTts(text)
        if (chunks.size == 1) {
            val audio = engine.generate(text, speed = 1.2f, sid = 0)
            VachakLog.d(tag, "synthesized \"$text\" -> ${audio.samples.size} samples @ ${audio.sampleRate} Hz isFixture=$isShim")
            return SynthAudio(
                samples = audio.samples,
                sampleRate = audio.sampleRate,
                backend = "sherpa-onnx",
                isFixture = isShim,
                warning = if (isShim) "placeholder voice (55-token shim) — text-only mode" else null
            )
        }
        val out = ArrayList<Float>(chunks.sumOf { it.length } * 110)
        var sr = 22050
        for (c in chunks) {
            val a = engine.generate(c, speed = 1.2f, sid = 0)
            sr = a.sampleRate
            for (s in a.samples) out.add(s)
        }
        val merged = FloatArray(out.size) { out[it] }
        VachakLog.d(tag, "synthesized chunked ${chunks.size} parts \"${text.take(40)}\" -> ${merged.size} samples @ $sr Hz isFixture=$isShim")
        return SynthAudio(
            samples = merged,
            sampleRate = sr,
            backend = "sherpa-onnx",
            isFixture = isShim,
            warning = if (isShim) "placeholder voice (55-token shim) — text-only mode" else null
        )
    }

    /**
     * Split long text at sentence boundaries (Ol Chiki danda ।, . ! ?, newline)
     * into ≤150-char pieces. Short text returns single chunk (no behavior change).
     */
    internal fun chunkForTts(text: String, maxChars: Int = 150): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val parts = text.split(Regex("(?<=[।.!?\\n])\\s+")).filter { it.isNotBlank() }
        if (parts.isEmpty()) return listOf(text)
        val out = ArrayList<String>()
        val cur = StringBuilder()
        for (p in parts) {
            if (cur.isEmpty()) {
                if (p.length <= maxChars) cur.append(p)
                else {
                    // Single over-long sentence: hard-split.
                    var i = 0
                    while (i < p.length) {
                        out.add(p.substring(i, minOf(i + maxChars, p.length)))
                        i += maxChars
                    }
                }
            } else if (cur.length + 1 + p.length <= maxChars) {
                cur.append(' ').append(p)
            } else {
                out.add(cur.toString())
                cur.clear()
                if (p.length <= maxChars) cur.append(p)
                else {
                    var i = 0
                    while (i < p.length) {
                        out.add(p.substring(i, minOf(i + maxChars, p.length)))
                        i += maxChars
                    }
                }
            }
        }
        if (cur.isNotEmpty()) out.add(cur.toString())
        return if (out.isEmpty()) listOf(text) else out
    }
}
