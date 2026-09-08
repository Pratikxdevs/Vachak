package com.vachak.ml.adapter

import android.content.Context
import com.vachak.engine.EngineError
import com.vachak.engine.EngineResult
import com.vachak.engine.LanguagePair
import com.vachak.engine.TranslationEngine
import java.text.Normalizer

/**
 * Mundari (unr_Deva) translation via deterministic offline phrasebook retrieval.
 *
 * Why this exists: the CT2 base (`modelpacks/stripped_mt`) has no usable Mundari
 * mapping (verified: tag-echo / wrong-language loops on host), and the LoRA merge
 * (`modelpacks/stripped_mt_merged`, `merge_lora_to_ct2.py`) is still pending GPU
 * work. Until the merged model ships, Mundari is served by exact + fuzzy lookup
 * over 17,826 curated pairs — deterministic, offline, no hallucination.
 *
 * Source: `modelpacks/mundari_phrasebook/corpus.tsv` (APK asset, copy of
 * `datasets/hin_mun/corpus.tsv`, Karya BY-NC-SA-FS quarantined — see asset README
 * and THIRD_PARTY_NOTICES.md). Lookup table only, never training data.
 *
 * Tiers: exact trim match → punctuation-tolerant normalized match →
 * token-overlap retrieval (F1 >= 0.55) → honest Err (never a fake echo).
 */
class MundariPhrasebookEngine(
    private val context: Context,
    private val assetPath: String = "modelpacks/mundari_phrasebook/corpus.tsv",
    private val retrievalThreshold: Double = 0.55
) : TranslationEngine {

    private val lock = Any()
    @Volatile private var loaded = false
    private var exact: Map<String, String> = emptyMap()
    private var normalized: Map<String, String> = emptyMap()
    private var rows: List<Pair<String, String>> = emptyList()
    private var rowTokens: List<Set<String>> = emptyList()

    /**
     * Hand-curated greetings (common-knowledge equivalents, versioned here).
     * Everything else MUST come from the quarantined corpus — never invent mappings.
     */
    private val GREETINGS: Map<String, String> = mapOf(
        "नमस्ते" to "जोहार",
        "हैलो" to "जोहार"
    )

    override fun supports(pair: LanguagePair): Boolean {
        val src = pair.source.lowercase()
        val tgt = pair.target.lowercase()
        return src in setOf("hi", "hin", "hin_deva") &&
                tgt in setOf("unr_deva", "unr", "mun_deva", "mun", "mundari", "mund")
    }

    override fun loadModel(packId: String): EngineResult<Unit> {
        return try {
            ensureLoaded()
            EngineResult.Ok(Unit)
        } catch (e: Exception) {
            android.util.Log.e("Vachak-MT", "phrasebook load failed", e)
            EngineResult.Err(EngineError.MODEL_LOAD_FAILED, "phrasebook load failed: ${e.message}")
        }
    }

    fun isReady(): Boolean = loaded

    fun size(): Int = rows.size

    override fun translate(text: String, pair: LanguagePair): EngineResult<String> {
        if (text.isBlank()) return EngineResult.Err(EngineError.INVALID_INPUT, "empty")
        if (!supports(pair)) return EngineResult.Err(EngineError.UNSUPPORTED_LANGUAGE, "unsupported ${pair.source}->${pair.target}")
        try {
            ensureLoaded()
        } catch (e: Exception) {
            return EngineResult.Err(EngineError.MODEL_LOAD_FAILED, "phrasebook unavailable: ${e.message}")
        }
        val t0 = android.os.SystemClock.elapsedRealtimeNanos()

        // Tier 0: curated greetings (common knowledge, versioned above).
        GREETINGS[text.trim()]?.let {
            logMs(t0, "greeting", text, it)
            return EngineResult.Ok(it)
        }
        // Tier 1: exact (trimmed) match.
        exact[text.trim()]?.let {
            logMs(t0, "exact", text, it)
            return EngineResult.Ok(it)
        }
        // Tier 2: punctuation-tolerant normalized match (try trailing-punct variants).
        val variants = normalizedVariants(text)
        for (v in variants) {
            // Greetings also match punctuation-tolerantly ("नमस्ते।" → जोहार).
            GREETINGS[v]?.let {
                logMs(t0, "greeting", text, it)
                return EngineResult.Ok(it)
            }
            normalized[v]?.let {
                logMs(t0, "normalized", text, it)
                return EngineResult.Ok(it)
            }
        }
        // Tier 3: token-overlap retrieval over normalized tokens.
        val qTokens = tokenize(normalize(text))
        if (qTokens.isNotEmpty()) {
            var best = -1.0
            var bestIdx = -1
            for (i in rows.indices) {
                val rt = rowTokens[i]
                if (rt.isEmpty()) continue
                val inter = qTokens.intersect(rt).size
                if (inter == 0) continue
                val f1 = 2.0 * inter / (qTokens.size + rt.size)
                if (f1 > best) {
                    best = f1
                    bestIdx = i
                }
            }
            if (bestIdx != -1 && best >= retrievalThreshold) {
                val out = rows[bestIdx].second
                android.util.Log.d("Vachak-MT", "phrasebook retrieval f1=${"%.2f".format(best)} \"${text.take(40)}\" -> \"${out.take(40)}\"")
                return EngineResult.Ok(out)
            }
            android.util.Log.d("Vachak-MT", "phrasebook miss bestF1=${"%.2f".format(best)} for \"${text.take(40)}\"")
        }
        val ms = (android.os.SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000
        android.util.Log.d("Vachak-MT", "phrasebook MISS ${ms}ms \"${text.take(40)}\" (17k table, no cover)")
        return EngineResult.Err(
            EngineError.INVALID_INPUT,
            "Mundari phrasebook has no entry for this sentence — try shorter classroom Hindi (merged model pending)"
        )
    }

    private fun logMs(t0: Long, tier: String, inp: String, out: String) {
        val ms = (android.os.SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000
        android.util.Log.d("Vachak-MT", "phrasebook $tier ${ms}ms \"${inp.take(40)}\" -> \"${out.take(40)}\"")
    }

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            android.util.Log.d("Vachak-MT", "loading Mundari phrasebook asset $assetPath")
            val ex = LinkedHashMap<String, String>()
            val norm = LinkedHashMap<String, String>()
            val rws = ArrayList<Pair<String, String>>()
            context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { line ->
                    if (line.isBlank()) return@forEach
                    val tab = line.indexOf('\t')
                    if (tab <= 0) return@forEach
                    val hi = line.substring(0, tab).trim()
                    val mun = line.substring(tab + 1).trim()
                    if (hi.isEmpty() || mun.isEmpty()) return@forEach
                    if (!ex.containsKey(hi)) ex[hi] = mun
                    val n = normalize(hi)
                    if (n.isNotEmpty() && !norm.containsKey(n)) norm[n] = mun
                    rws.add(hi to mun)
                }
            }
            rows = rws
            rowTokens = rws.map { tokenize(normalize(it.first)) }
            exact = ex
            normalized = norm
            loaded = true
            android.util.Log.d("Vachak-MT", "phrasebook ready rows=${rws.size} exact=${ex.size}")
        }
    }

    private fun normalizedVariants(text: String): List<String> {
        val t = text.trim()
        val out = ArrayList<String>(4)
        out.add(normalize(t))
        // Strip one trailing sentence-final mark and re-normalize (। . ? ! ॥ …).
        val stripped = t.trimEnd('।', '.', '?', '!', '॥', '…', ' ').trim()
        if (stripped != t) out.add(normalize(stripped))
        return out.distinct()
    }

    private fun normalize(s: String): String {
        var t = Normalizer.normalize(s, Normalizer.Form.NFKC).trim()
        t = t.replace(Regex("\\s+"), " ")
        return t
    }

    private fun tokenize(normalized: String): Set<String> {
        if (normalized.isBlank()) return emptySet()
        // Strip punctuation to tokens so "पानी?" matches "पानी".
        return normalized.split(' ')
            .map { it.trim('।', '.', ',', '?', '!', '॥', '…', ':', ';', '"', '\'', '(', ')') }
            .filter { it.isNotEmpty() }
            .toSet()
    }
}
