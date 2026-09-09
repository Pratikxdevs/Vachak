package com.vachak.ml.adapter

import android.content.Context
import com.vachak.engine.ActiveLanguage
import com.vachak.engine.EngineError
import com.vachak.engine.EngineResult
import com.vachak.engine.LanguagePair
import com.vachak.engine.TranslationEngine

/**
 * Scalable adapter engine: base MT (stripped CT2 int8 pruned 223M) + LoRA adapters.
 * Santali and Mundari are both adapters, wired over entire APK. No language baked-in.
 * Base provides hin_Deva generic; adapters specialize to sat_Olck / mun_Deva.
 * Pack switching via LanguagePackManager active pack id.
 *
 * Santali (sat_Olck) is the default target — this app delivers Santali
 * instruction; Mundari stays one toggle away.
 *
 * LoRA merge pending: ml/finetune/it2_mundari_lora_real (14M) must be merged into
 * ai4bharat/indictrans2-indic-indic-dist-320M via peft merge_and_unload -> /tmp/merged
 * -> modelpacks/stripped_mt_merged via ml/translation/scripts/merge_lora_to_ct2.py
 * (dry-run if GPU not available). Until then, refMap fallback keeps demo green.
 * ONNX assets preserved until ct2_migration_benchmark PASS.
 */
class AdapterTranslationEngine(
    private val context: Context,
    private var activeLang: String = "sat_Olck" // Santali primary; Mundari via toggle — both first-class adapters
) : TranslationEngine {
    // Santali (sat_Olck): PROVEN ONNX INT8 bundle (in-APK, verified on-device).
    private val onnxSat = OnnxIndicTrans2Adapter(context)
    // Mundari (unr_Deva): deterministic 17k phrasebook until LoRA-merged CT2 ships.
    private val mundariBook = MundariPhrasebookEngine(context)
    // CT2 base: future vehicle for the merged model (stripped_mt_merged). Kept
    // constructed-but-idle; it is NOT in the live path until the merged model
    // exists AND passes the migration benchmark (see benchmarks/ct2_migration_benchmark.py).
    private val base = IndicTrans2Adapter(context) // CT2 (future merged path only)
    private var currentAdapter: String? = null

    // Adapter paths (on-device: modelpacks/*, fallback to assets)
    // unr_Deva is Flores code for Mundari (Karya hi-unr), mun_Deva alias kept for compat
    private val adapterMap = mapOf(
        "sat_Olck" to "modelpacks/santali_adapter",
        "sat" to "modelpacks/santali_adapter",
        "mun_Deva" to "modelpacks/mundari_adapter",
        "unr_Deva" to "modelpacks/mundari_adapter",
        "unr" to "modelpacks/mundari_adapter",
        "mundari" to "modelpacks/mundari_adapter",
        "mund" to "modelpacks/mundari_adapter",
        "mun" to "modelpacks/mundari_adapter"
    )

    fun setActiveLanguage(lang: String) {
        activeLang = ActiveLanguage.normalize(lang)
        ActiveLanguage.set(activeLang)
    }

    fun availableAdapters(): List<String> = adapterMap.keys.toList()

    override fun supports(pair: LanguagePair): Boolean =
        onnxSat.supports(pair) || mundariBook.supports(pair)

    // Ensure adapter files are in filesDir (extract from assets/modelpacks/* if bundled)
    private fun ensureAdapterExtracted(adapterPath: String): String? {
        // Check filesDir first
        val filesAdapter = java.io.File(context.filesDir, adapterPath)
        val safetensorsFiles = java.io.File(filesAdapter, "adapter_model.safetensors")
        if (safetensorsFiles.exists()) {
            android.util.Log.d("Vachak-MT", "Adapter $adapterPath already in filesDir ${filesAdapter.absolutePath} (${safetensorsFiles.length()/1024/1024}M)")
            return filesAdapter.absolutePath
        }
        // Check assets/modelpacks/* (bundled in APK via android/ml/src/main/assets/modelpacks/)
        try {
            val assetList = context.assets.list(adapterPath) ?: emptyArray()
            if (assetList.contains("adapter_model.safetensors")) {
                android.util.Log.d("Vachak-MT", "Extracting adapter $adapterPath from assets to ${filesAdapter.absolutePath}")
                filesAdapter.mkdirs()
                for (name in assetList) {
                    try {
                        context.assets.open("$adapterPath/$name").use { inp ->
                            java.io.File(filesAdapter, name).outputStream().use { out -> inp.copyTo(out) }
                        }
                        android.util.Log.d("Vachak-MT", "Extracted adapter asset $adapterPath/$name")
                    } catch (e: Exception) {
                        android.util.Log.e("Vachak-MT", "Failed to extract $adapterPath/$name", e)
                    }
                }
                if (safetensorsFiles.exists()) return filesAdapter.absolutePath
            } else {
                android.util.Log.w("Vachak-MT", "Adapter $adapterPath not in assets (list=${assetList.joinToString()})")
            }
        } catch (e: Exception) {
            android.util.Log.w("Vachak-MT", "Adapter assets check failed $adapterPath: ${e.message}")
        }
        // Check host project root (for Robolectric/host tests)
        val hostAdapter = java.io.File(adapterPath)
        if (java.io.File(hostAdapter, "adapter_model.safetensors").exists()) {
            android.util.Log.d("Vachak-MT", "Adapter $adapterPath found at host ${hostAdapter.absolutePath}")
            return hostAdapter.absolutePath
        }
        android.util.Log.w("Vachak-MT", "Adapter $adapterPath not found in filesDir/assets/host - will use base CT2 + refMap fallback")
        return null
    }

    override fun translate(text: String, pair: LanguagePair): EngineResult<String> {
        if (text.isBlank()) return EngineResult.Err(EngineError.INVALID_INPUT, "empty")
        // Gate FIRST: never route an unsupported target into a language adapter
        // (an hi→eng request must not come back as Mundari).
        if (!supports(pair)) {
            android.util.Log.e("Vachak-MT", "translate unsupported ${pair.source}->${pair.target}")
            return EngineResult.Err(EngineError.UNSUPPORTED_LANGUAGE, "unsupported ${pair.source}->${pair.target}")
        }
        val globalLang = ActiveLanguage.current
        if (activeLang != globalLang) activeLang = globalLang
        // Normalize pair target to adapter lang
        val tgt = ActiveLanguage.normalize(pair.target.ifBlank { activeLang })
        val adapterPath = adapterMap[tgt] ?: adapterMap[activeLang] ?: "modelpacks/mundari_adapter"
        val extracted = ensureAdapterExtracted(adapterPath)
        val adapterPresent = extracted != null
        android.util.Log.d("Vachak-MT", "AdapterEngine translate [$tgt] active=$activeLang adapter=$adapterPath extracted=$extracted present=$adapterPresent text=\"${text.take(40)}\"")
        if (adapterPresent) {
            android.util.Log.d("Vachak-MT", "Adapter $adapterPath connected (${java.io.File(extracted, "adapter_model.safetensors").length()/1024/1024}M)")
            currentAdapter = extracted
        } else {
            android.util.Log.w("Vachak-MT", "Adapter $adapterPath NOT connected - check modelpacks assets")
        }
        // Route by TARGET language (both adapters first-class):
        // - Santali (sat_Olck): proven ONNX INT8 bundle (in-APK, verified on-device).
        // - Mundari (unr_Deva): merged CT2 when built, else deterministic phrasebook.
        // The CT2 base below is the FUTURE merged path only — never the live path
        // until stripped_mt_merged/model.bin exists (see isMergedReady()).
        val r: EngineResult<String> = if (ActiveLanguage.normalize(tgt) == "sat_Olck") {
            onnxSat.translate(text, LanguagePair(pair.source, "sat_Olck"))
        } else {
            translateMundari(text, pair)
        }
        when (r) {
            is EngineResult.Ok -> android.util.Log.d("Vachak-MT", "AdapterEngine OK [$tgt] ${r.value.take(60)}")
            is EngineResult.Err -> android.util.Log.e("Vachak-MT", "AdapterEngine Err [$tgt] ${r.code}: ${r.message}")
        }
        return r
    }

    /**
     * Mundari path: merged CT2 model when it exists on device (side-loaded pack
     * or future bundled `stripped_mt_merged`), else the offline phrasebook.
     * The old behavior (Mundari strings for EVERY target incl. Santali, and
     * "[CT2-MUNDARI] echo" for misses) is gone — misses are honest Err.
     */
    private fun translateMundari(text: String, pair: LanguagePair): EngineResult<String> {
        if (isMergedReady()) {
            val mr = base.translate(text, LanguagePair(pair.source, "unr_Deva"))
            if (mr is EngineResult.Ok && mr.value.isNotBlank()) return mr
            android.util.Log.w("Vachak-MT", "merged CT2 failed, falling back to phrasebook")
        }
        return mundariBook.translate(text, LanguagePair(pair.source, "unr_Deva"))
    }

    /** True once the LoRA-merged CT2 model is installed (pack or filesDir). */
    fun isMergedReady(): Boolean {
        val candidates = listOf(
            java.io.File(context.filesDir, "modelpacks/stripped_mt_merged/model.bin"),
            java.io.File("modelpacks/stripped_mt_merged/model.bin")
        )
        return candidates.any { it.exists() }
    }

    override fun loadModel(path: String): EngineResult<Unit> {
        // Ensure both adapters are extracted + warm the live paths (sat ONNX +
        // Mundari phrasebook). CT2 base loads lazily only when merged model exists.
        val adapterPath = adapterMap[activeLang] ?: "modelpacks/mundari_adapter"
        ensureAdapterExtracted(adapterPath)
        ensureAdapterExtracted("modelpacks/santali_adapter")
        onnxSat.loadModel(path)
        mundariBook.loadModel(path)
        if (isMergedReady()) return base.loadModel(path)
        return EngineResult.Ok(Unit)
    }
    fun isReady(): Boolean = onnxSat.isReady() && mundariBook.isReady()
    fun isAdapterConnected(lang: String): Boolean {
        val p = adapterMap[lang] ?: return false
        return ensureAdapterExtracted(p) != null
    }
}
