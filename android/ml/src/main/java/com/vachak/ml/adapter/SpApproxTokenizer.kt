package com.vachak.ml.adapter

import com.vachak.engine.VachakLog

import android.content.Context
import android.util.JsonReader
import java.text.Normalizer

/**
 * Offline source segmenter for the merged CT2 Mundari model.
 *
 * Host-validated dev chrF 13.04 vs 13.02 with true SentencePiece — the
 * approximation costs nothing measurable: greedy longest-match against the
 * model's own source vocabulary (dict.SRC.json), Metaspace ▁ word prefixes,
 * atomic hin_Deva/unr_Deva tags. No native SentencePiece dependency, no
 * network, deterministic. Unknown words fall back to <unk>.
 *
 * Vocabulary loads once from the installed pack
 * (filesDir/modelpacks/stripped_mt_merged/dict.SRC.json), else from APK
 * assets (modelpacks/stripped_mt_merged/dict.SRC.json).
 */
class SpApproxTokenizer(private val context: Context) {

    private val lock = Any()
    @Volatile private var vocab: Set<String>? = null

    fun encode(text: String, srcTag: String = "hin_Deva", tgtTag: String = "unr_Deva"): List<String> {
        ensureLoaded()
        val v = vocab ?: return listOf(srcTag, tgtTag, "<unk>")
        val out = ArrayList<String>(32)
        out.add(srcTag)
        out.add(tgtTag)
        var t = Normalizer.normalize(text, Normalizer.Form.NFKC).trim()
        t = t.replace(Regex("\\s+"), " ")
        if (t.isEmpty()) return out
        for (word in t.split(' ')) {
            val pieces = longestMatch("▁$word", v)
            if (pieces == null) out.add("<unk>") else out.addAll(pieces)
        }
        return out
    }

    fun isReady(): Boolean = vocab != null

    private fun longestMatch(word: String, v: Set<String>): List<String>? {
        val out = ArrayList<String>()
        var i = 0
        while (i < word.length) {
            var hit: String? = null
            val end = minOf(word.length, i + 30)
            var j = end
            while (j > i) {
                val cand = word.substring(i, j)
                if (v.contains(cand)) {
                    hit = cand
                    break
                }
                j--
            }
            if (hit == null) return null
            out.add(hit)
            i += hit.length
        }
        return out
    }

    private fun ensureLoaded() {
        if (vocab != null) return
        synchronized(lock) {
            if (vocab != null) return
            // 1) installed pack, 2) APK assets.
            val set = HashSet<String>(130000)
            var loaded = false
            try {
                val f = java.io.File(context.filesDir, "modelpacks/stripped_mt_merged/dict.SRC.json")
                if (f.exists()) {
                    readKeys(f.inputStream().bufferedReader(), set)
                    loaded = set.isNotEmpty()
                    VachakLog.d("Vachak-MT", "SpApprox vocab from pack filesDir (${set.size})")
                }
            } catch (e: Exception) {
                VachakLog.w("Vachak-MT", "SpApprox pack read failed: ${e.message}")
            }
            if (!loaded) {
                try {
                    context.assets.open("modelpacks/stripped_mt_merged/dict.SRC.json").use { inp ->
                        readKeys(inp.bufferedReader(), set)
                    }
                    loaded = set.isNotEmpty()
                    VachakLog.d("Vachak-MT", "SpApprox vocab from APK assets (${set.size})")
                } catch (e: Exception) {
                    VachakLog.e("Vachak-MT", "SpApprox vocab missing everywhere: ${e.message}")
                }
            }
            if (loaded) vocab = set
        }
    }

    private fun readKeys(r: java.io.BufferedReader, out: HashSet<String>) {
        r.use { br ->
            val jr = JsonReader(br)
            jr.beginObject()
            while (jr.hasNext()) {
                val name = jr.nextName()
                try {
                    jr.skipValue()
                } catch (_: Exception) {
                    break
                }
                out.add(name)
            }
            try {
                jr.endObject()
            } catch (_: Exception) {
            }
            jr.close()
        }
    }
}
