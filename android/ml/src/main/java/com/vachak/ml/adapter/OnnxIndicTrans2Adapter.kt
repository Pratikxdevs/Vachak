package com.vachak.ml.adapter

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.JsonReader
import com.vachak.engine.EngineError
import com.vachak.engine.EngineResult
import com.vachak.engine.LanguagePair
import com.vachak.engine.TranslationEngine
import com.vachak.ml.IndicProcessorPort
import com.vachak.ml.SherpaAssets
import java.io.File
import java.nio.LongBuffer
import java.util.concurrent.locks.ReentrantLock

/**
 * Santali (sat_Olck, Ol Chiki) translation via the PROVEN ONNX INT8 bundle.
 *
 * This is the hi→sat path verified on-device (distinct non-looping output,
 * e.g. `मेरा नाम क्या है → ᱤᱧᱟᱹᱜ ᱧᱩᱛᱩᱢ ᱪᱮᱫ?`): 3-graph opset17
 * (`encoder_model.onnx` + `decoder_model.onnx` + `decoder_with_past_model.onnx`,
 * shared external data) + HF fast-tokenizer BPE (245k merges, Metaspace ▁,
 * `single: $A </s>` template) + greedy decode with shape-preserving past KV +
 * GOLD curated pre-check for known INT8 failure modes.
 *
 * Bundle lives in APK assets (`vachak_models/mt/`, 357M) and is extracted once
 * to filesDir via [SherpaAssets.prepare]. ORT Mobile, 1 thread, sequential
 * under [lock] (never parallel — 2GB RAM). No network, no training.
 *
 * Serves sat_Olck/sat ONLY. Mundari (unr_Deva family) is served by
 * [MundariPhrasebookEngine] until the LoRA-merged CT2 model ships.
 */
class OnnxIndicTrans2Adapter(
    private val context: Context,
    private val modelDir: String = "mt"
) : TranslationEngine {

    private val lock = ReentrantLock()
    @Volatile private var ready = false
    private var baseDir: String? = null

    private var ortEnv: OrtEnvironment? = null
    private var encSession: OrtSession? = null
    private var decSession: OrtSession? = null
    private var decPastSession: OrtSession? = null

    // BPE codec (loaded once from tokenizer_src/tgt.json).
    private var srcVocab: Map<String, Int> = emptyMap()
    private var tgtIdToToken: List<String> = emptyList()
    private var mergeRank: Map<String, Int> = emptyMap()
    private var srcAdded: Map<String, Int> = emptyMap()

    private var srcDictSize = 122706
    private var tgtDictSize = 122672
    private var maxSourcePositions = 256
    private val unkId = 3
    private val eosId = 2
    private val decoderStartId = 2
    private val maxTargetLen = 128

    private val olChiki = Regex("[\u1C50-\u1C7F]")

    override fun supports(pair: LanguagePair): Boolean {
        val src = pair.source.lowercase()
        val tgt = pair.target.lowercase()
        return src in setOf("hi", "hin", "hin_deva") &&
                tgt in setOf("sat_olck", "sat", "sat-olck", "olck", "ol_ck", "sat_olchiki")
    }

    override fun loadModel(packId: String): EngineResult<Unit> {
        lock.lock()
        try {
            if (ready) return EngineResult.Ok(Unit)
            val dir = SherpaAssets.prepare(context, modelDir)
            val t0 = android.os.SystemClock.elapsedRealtimeNanos()
            android.util.Log.d("Vachak-MT", "ONNX load from $dir")
            for (f in listOf("encoder_model.onnx", "decoder_model.onnx", "decoder_with_past_model.onnx", "tokenizer_src.json", "tokenizer_tgt.json")) {
                if (!File(dir, f).exists()) {
                    android.util.Log.e("Vachak-MT", "ONNX bundle missing $f in $dir")
                    return EngineResult.Err(EngineError.MODEL_NOT_LOADED, "ONNX bundle missing $f")
                }
            }
            readMeta(File(dir, "tokenizer_meta.json"))
            readMaxPositions(File(dir, "config.json"))
            val env = OrtEnvironment.getEnvironment()
            val opts = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(1)
                setInterOpNumThreads(1)
            }
            encSession = env.createSession("$dir/encoder_model.onnx", opts)
            decSession = env.createSession("$dir/decoder_model.onnx", opts)
            decPastSession = env.createSession("$dir/decoder_with_past_model.onnx", opts)
            ortEnv = env
            loadBpe(File(dir, "tokenizer_src.json"), File(dir, "tokenizer_tgt.json"))
            baseDir = dir
            ready = true
            val ms = (android.os.SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000
            android.util.Log.d("Vachak-MT", "ONNX ready in ${ms}ms (enc+dec+past, threads=1)")
            return EngineResult.Ok(Unit)
        } catch (e: Exception) {
            android.util.Log.e("Vachak-MT", "ONNX load failed", e)
            return EngineResult.Err(EngineError.MODEL_LOAD_FAILED, "ONNX load failed: ${e.message}")
        } finally {
            lock.unlock()
        }
    }

    fun isReady(): Boolean = ready

    override fun translate(text: String, pair: LanguagePair): EngineResult<String> {
        if (text.isBlank()) return EngineResult.Err(EngineError.INVALID_INPUT, "empty")
        if (!supports(pair)) return EngineResult.Err(EngineError.UNSUPPORTED_LANGUAGE, "unsupported ${pair.source}->${pair.target}")
        val lr = loadModel(modelDir)
        if (lr is EngineResult.Err) return lr
        // Tier 0: GOLD curated pre-check for known INT8 failure modes (exact match).
        GOLD[text.trim()]?.let {
            android.util.Log.d("Vachak-MT", "ONNX curated \"${text.take(30)}\" -> \"${it.take(30)}\"")
            return EngineResult.Ok(it)
        }
        lock.lock()
        try {
            val t0 = android.os.SystemClock.elapsedRealtimeNanos()
            val env = ortEnv ?: return EngineResult.Err(EngineError.MODEL_NOT_LOADED, "ORT env missing")
            val pre = IndicProcessorPort.preprocessBatch(listOf(text), "hin_Deva", "sat_Olck")[0]
            var ids = encode(pre)
            android.util.Log.d("Vachak-MT", "ONNX tokenize [${ids.take(6).joinToString(",")}${if (ids.size > 6) ",..." else ""}] len=${ids.size}")
            if (ids.size > maxSourcePositions) {
                ids = ids.take(maxSourcePositions - 1) + eosId
                android.util.Log.d("Vachak-MT", "ONNX truncate len->${ids.size} (max $maxSourcePositions)")
            }
            val n = ids.size
            val idBuf = LongBuffer.allocate(1 * n)
            ids.forEach { idBuf.put(it.toLong()) }
            idBuf.rewind()
            val maskBuf = LongBuffer.allocate(1 * n)
            repeat(n) { maskBuf.put(1L) }
            maskBuf.rewind()
            val encInputs = mapOf(
                "input_ids" to OnnxTensor.createTensor(env, idBuf, longArrayOf(1, n.toLong())),
                "attention_mask" to OnnxTensor.createTensor(env, maskBuf, longArrayOf(1, n.toLong()))
            )
            val encOut = encSession!!.run(encInputs)
            @Suppress("UNCHECKED_CAST")
            val hidden = (encOut.get(0).value as Array<Array<FloatArray>>)
            val flatHidden = FloatArray(n * 512)
            for (i in 0 until n) System.arraycopy(hidden[0][i], 0, flatHidden, i * 512, 512)
            encOut.close()
            encInputs.values.forEach { try { it.close() } catch (_: Throwable) {} }
            android.util.Log.d("Vachak-MT", "ONNX encode done shape=[1,$n,512]")

            // Greedy decode: step 0 full decoder, steps 1..N with past KV.
            val outIds = ArrayList<Int>(64)
            var past: OrtSession.Result? = null
            var nextId = decoderStartId
            var steps = 0
            try {
                while (steps < maxTargetLen) {
                    val inIdBuf = LongBuffer.allocate(1)
                    inIdBuf.put(nextId.toLong())
                    inIdBuf.rewind()
                    val inputs = HashMap<String, OnnxTensor>()
                    inputs["input_ids"] = OnnxTensor.createTensor(env, inIdBuf, longArrayOf(1, 1))
                    inputs["encoder_attention_mask"] = maskTensor(env, n)
                    val res: OrtSession.Result
                    if (past == null) {
                        // Step 0: full decoder needs raw encoder states; it returns
                        // cached decoder+encoder KVs for all following steps.
                        inputs["encoder_hidden_states"] = hiddenTensor(env, flatHidden, n)
                        res = decSession!!.run(inputs)
                    } else {
                        // Steps 1..N: past-key-values only (74 inputs total —
                        // the with-past graph has NO encoder_hidden_states input).
                        feedPast(inputs, past!!)
                        res = decPastSession!!.run(inputs)
                    }
                    inputs.values.forEach { try { it.close() } catch (_: Throwable) {} }
                    @Suppress("UNCHECKED_CAST")
                    val logits = (res.get(0).value as Array<Array<FloatArray>>)[0][0]
                    nextId = argmax(logits)
                    past?.close()
                    past = res
                    if (nextId == eosId) break
                    outIds.add(nextId)
                    steps++
                }
            } finally {
                try { past?.close() } catch (_: Throwable) {}
            }
            var decoded = decode(outIds)
            decoded = IndicProcessorPort.postprocessBatch(listOf(decoded), "sat_Olck")[0]
            val ms = (android.os.SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000
            if (!olChiki.containsMatchIn(decoded)) {
                android.util.Log.w("Vachak-MT", "ONNX output lacks Ol Chiki (numerals/placeholders?) \"${decoded.take(40)}\"")
            }
            android.util.Log.d("Vachak-MT", "ONNX MT ${ms}ms \"${text.take(30)}\" -> \"${decoded.take(40)}\"")
            if (decoded.isBlank()) return EngineResult.Err(EngineError.MODEL_NOT_LOADED, "empty translation")
            return EngineResult.Ok(decoded)
        } catch (e: Exception) {
            android.util.Log.e("Vachak-MT", "ONNX translate failed", e)
            return EngineResult.Err(EngineError.MODEL_LOAD_FAILED, "ONNX translate failed: ${e.message}")
        } finally {
            lock.unlock()
        }
    }

    // ---- codec ----

    private fun encode(preprocessed: String): List<Int> {
        val out = ArrayList<Int>()
        for (piece in preprocessed.split(' ')) {
            if (piece.isEmpty()) continue
            srcAdded[piece]?.let { out.add(it); return@let }
            if (srcAdded.containsKey(piece)) continue
            // Metaspace prepend_scheme=always: every word gets ▁ (added tokens excluded above).
            out.addAll(bpe("▁$piece"))
        }
        // TemplateProcessing single: $A </s>
        out.add(eosId)
        return out
    }

    private fun bpe(word: String): List<Int> {
        srcVocab[word]?.let { return listOf(clampSrc(it)) }
        var parts = word.map { it.toString() }.toMutableList()
        while (true) {
            var bestRank = Int.MAX_VALUE
            var bestIdx = -1
            for (i in 0 until parts.size - 1) {
                val r = mergeRank["${parts[i]}\u0000${parts[i + 1]}"] ?: continue
                if (r < bestRank) {
                    bestRank = r
                    bestIdx = i
                }
            }
            if (bestIdx == -1) break
            val merged = parts[bestIdx] + parts[bestIdx + 1]
            val next = ArrayList<String>(parts.size - 1)
            for (i in parts.indices) {
                if (i == bestIdx) {
                    next.add(merged)
                } else if (i == bestIdx + 1) {
                    // consumed by merge
                } else next.add(parts[i])
            }
            parts = next
        }
        val ids = ArrayList<Int>(parts.size)
        for (p in parts) {
            val id = srcVocab[p] ?: return listOf(unkId)
            ids.add(clampSrc(id))
        }
        return ids
    }

    private fun clampSrc(id: Int): Int = if (id < srcDictSize) id else unkId

    private fun decode(ids: List<Int>): String {
        val sb = StringBuilder()
        for (id in ids) {
            if (id == 0 || id == 1 || id == 2) continue // skip <s>/<pad>/</s>
            if (id == 3) continue // drop <unk>
            if (id < 0 || id >= tgtIdToToken.size) continue
            sb.append(tgtIdToToken[id])
        }
        // Metaspace decode: join("") then ▁ -> space.
        return sb.toString().replace("▁", " ").trim()
    }

    private fun argmax(logits: FloatArray): Int {
        var best = 0
        var bestV = logits[0]
        for (i in 1 until logits.size) {
            val v = logits[i]
            if (v > bestV) {
                bestV = v
                best = i
            }
        }
        return if (best < tgtDictSize) best else unkId
    }

    private fun maskTensor(env: OrtEnvironment, n: Int): OnnxTensor {
        val b = LongBuffer.allocate(n)
        repeat(n) { b.put(1L) }
        b.rewind()
        return OnnxTensor.createTensor(env, b, longArrayOf(1, n.toLong()))
    }

    private fun hiddenTensor(env: OrtEnvironment, flat: FloatArray, n: Int): OnnxTensor {
        return OnnxTensor.createTensor(env, java.nio.FloatBuffer.wrap(flat), longArrayOf(1, n.toLong(), 512))
    }

    private fun feedPast(inputs: MutableMap<String, OnnxTensor>, past: OrtSession.Result) {
        // present.{i}.{decoder,encoder}.{key,value} -> past_key_values.{i}.*
        for (i in 0 until 18) {
            for (io in listOf("decoder", "encoder")) {
                for (kv in listOf("key", "value")) {
                    val outName = "present.$i.$io.$kv"
                    val inName = "past_key_values.$i.$io.$kv"
                    // ORT >= 1.17: Result.get(String) returns Optional<OnnxValue>.
                    // Unwrap to the nested float arrays via OnnxTensor.value.
                    val opt = past.get(outName) as java.util.Optional<ai.onnxruntime.OnnxValue>
                    val raw = (opt.orElse(null) as? OnnxTensor)?.value
                    when (raw) {
                        is Array<*> -> {
                            // 4-D [1,8,S,64]: flatten generically.
                            val flat = ArrayList<Float>()
                            fun walk(o: Any?) {
                                when (o) {
                                    is FloatArray -> o.forEach { flat.add(it) }
                                    is Array<*> -> o.forEach { walk(it) }
                                    is Number -> flat.add(o.toFloat())
                                }
                            }
                            walk(raw)
                            // shapes are [1,8,S,64] where S=1 for decoder self-attn
                            // and S=encLen for encoder cross-attn; recover S from count.
                            val seq = flat.size / (8 * 64)
                            val buf = java.nio.FloatBuffer.allocate(flat.size)
                            flat.forEach { buf.put(it) }
                            buf.rewind()
                            inputs[inName] = OnnxTensor.createTensor(
                                ortEnv!!, buf, longArrayOf(1, 8, seq.toLong(), 64)
                            )
                            if (io == "decoder" && seq != 1) android.util.Log.w("Vachak-MT", "past $inName seq=$seq (expected 1)")
                        }
                        else -> android.util.Log.w("Vachak-MT", "past $outName unexpected type")
                    }
                }
            }
        }
    }

    private fun readMeta(f: File) {
        if (!f.exists()) return
        try {
            val t = f.readText()
            Regex("\"src_dict_size\"\\s*:\\s*(\\d+)").find(t)?.let { srcDictSize = it.groupValues[1].toInt() }
            Regex("\"tgt_dict_size\"\\s*:\\s*(\\d+)").find(t)?.let { tgtDictSize = it.groupValues[1].toInt() }
        } catch (_: Exception) {}
    }

    private fun readMaxPositions(f: File) {
        if (!f.exists()) return
        try {
            Regex("\"max_source_positions\"\\s*:\\s*(\\d+)").find(f.readText())?.let {
                maxSourcePositions = it.groupValues[1].toInt().coerceAtMost(258)
            }
        } catch (_: Exception) {}
    }

    private fun loadBpe(srcJson: File, tgtJson: File) {
        android.util.Log.d("Vachak-MT", "ONNX loading BPE codec (245k merges, may take seconds, once)")
        val t0 = android.os.SystemClock.elapsedRealtimeNanos()
        val vocab = HashMap<String, Int>(140000)
        val added = HashMap<String, Int>()
        val ranks = HashMap<String, Int>(250000)
        // Streamed parse (JsonReader) — never hold the 23MB DOM.
        srcJson.reader().buffered().use { br ->
            val jr = JsonReader(br)
            jr.beginObject()
            while (jr.hasNext()) {
                when (jr.nextName()) {
                    "model" -> {
                        jr.beginObject()
                        while (jr.hasNext()) {
                            when (jr.nextName()) {
                                "vocab" -> {
                                    jr.beginObject()
                                    while (jr.hasNext()) vocab[jr.nextName()] = jr.nextInt()
                                    jr.endObject()
                                }
                                "merges" -> {
                                    jr.beginArray()
                                    var rank = 0
                                    while (jr.hasNext()) {
                                        jr.beginArray()
                                        val a = jr.nextString()
                                        val b = jr.nextString()
                                        jr.endArray()
                                        ranks["$a\u0000$b"] = rank++
                                    }
                                    jr.endArray()
                                }
                                else -> jr.skipValue()
                            }
                        }
                        jr.endObject()
                    }
                    "added_tokens" -> {
                        jr.beginArray()
                        while (jr.hasNext()) {
                            var content: String? = null
                            var id = -1
                            jr.beginObject()
                            while (jr.hasNext()) {
                                when (jr.nextName()) {
                                    "content" -> content = jr.nextString()
                                    "id" -> id = jr.nextInt()
                                    else -> jr.skipValue()
                                }
                            }
                            jr.endObject()
                            if (content != null && id >= 0) added[content] = id
                        }
                        jr.endArray()
                    }
                    else -> jr.skipValue()
                }
            }
            jr.endObject()
            jr.close()
        }
        // Target id->token (tgt vocab).
        var maxId = 0
        val pairs = ArrayList<Pair<Int, String>>(130000)
        tgtJson.reader().buffered().use { br ->
            val jr = JsonReader(br)
            jr.beginObject()
            while (jr.hasNext()) {
                if (jr.nextName() == "model") {
                    jr.beginObject()
                    while (jr.hasNext()) {
                        if (jr.nextName() == "vocab") {
                            jr.beginObject()
                            while (jr.hasNext()) {
                                val k = jr.nextName()
                                val id = jr.nextInt()
                                pairs.add(id to k)
                                if (id > maxId) maxId = id
                            }
                            jr.endObject()
                        } else jr.skipValue()
                    }
                    jr.endObject()
                } else jr.skipValue()
            }
            jr.endObject()
            jr.close()
        }
        val rev = MutableList(maxId + 1) { "" }
        for ((id, tok) in pairs) if (id in rev.indices) rev[id] = tok
        srcVocab = vocab
        srcAdded = added
        mergeRank = ranks
        tgtIdToToken = rev
        val ms = (android.os.SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000
        android.util.Log.d("Vachak-MT", "ONNX BPE ready vocab=${vocab.size} merges=${ranks.size} tgtIds=${rev.size} in ${ms}ms")
    }

    companion object {
        /**
         * GOLD curated pre-check (hi→sat, exact match): deterministic cover for
         * known INT8 failure modes (e.g. `नमस्ते` repetition). Sourced from
         * `datasets/hin_sat/corpus.gold_verified.tsv` (human-verified tier).
         * Versioned here, never silently edited at runtime.
         */
        private val GOLD: Map<String, String> = mapOf(
            "नमस्ते" to "ᱡᱚᱦᱟᱨ",
            "आज" to "ᱛᱮᱦᱮᱧ",
            "चार" to "ᱯᱩᱱ",
            "बाद में मिलते हैं" to "ᱛᱟᱭᱚᱢ ᱛᱮ ᱞᱟᱝ ᱧᱟᱯᱟᱢᱚᱜᱼᱟ",
            "मैं समझता हूँ" to "ᱤᱧ ᱵᱩᱡᱷᱟᱹᱣ ᱮᱫᱟᱹᱧ",
            "पुलिस को बुलाओ" to "ᱯᱩᱞᱤᱥ ᱦᱚᱦᱚ ᱠᱚᱢ",
            "शुभ दोपहर" to "ᱥᱟᱹᱜᱩᱱ ᱛᱟᱨᱟᱥᱤᱧ",
            "आपसे मिलकर अच्छा लगा" to "ᱟᱢ ᱥᱟᱶ ᱧᱟᱯᱟᱢ ᱠᱟᱛᱮ ᱠᱩᱥᱤ ᱮᱱᱟᱹᱧ",
            "क्या आप अंग्रेज़ी बोलते हैं?" to "ᱪᱮᱫ ᱟᱢ ᱤᱝᱨᱟᱡᱤ ᱨᱚᱲ ᱫᱟᱲᱮᱭᱟᱜ ᱠᱟᱱᱟᱢ?",
            "आप कैसे हैं?" to "ᱟᱢ ᱪᱮᱫ ᱞᱮᱠᱟ ᱢᱮᱱᱟᱢᱟ?",
            // Verified interrogatives/shorts from corpus.gold_verified.tsv
            // (human-verified tier) — deterministic cover where the finetuned
            // model falls back to generic completions on short questions.
            "रेलवे स्टेशन कहां है?" to "ᱴᱨᱮᱱ ᱥᱴᱮᱥᱚᱱ ᱚᱠᱟᱨᱮ ᱢᱮᱱᱟᱜᱼᱟ?",
            "मैं टिकट कहाँ से खरीद सकता हूँ?" to "ᱤᱧ ᱚᱠᱟ ᱠᱷᱚᱱ ᱴᱤᱠᱤᱴ ᱠᱤᱨᱤᱧ ᱫᱟᱲᱮᱭᱟᱜᱼᱟᱹᱧ?",
            "हवाई अड्डा कहाँ है?" to "ᱮᱭᱟᱨᱯᱚᱨᱴ ᱚᱠᱟᱨᱮ ᱢᱮᱱᱟᱜᱼᱟ?",
            "क्या आप क्रेडिट कार्ड स्वीकार करते हैं?" to "ᱪᱮᱫ ᱟᱢ ᱠᱨᱮᱰᱤᱴ ᱠᱟᱨᱰ ᱮᱢ ᱟᱝᱜᱚᱪ ᱮᱫᱟ?",
            "इसकी लागत कितनी है?" to "ᱱᱚᱣᱟ ᱨᱮᱭᱟᱜ ᱜᱚᱱᱚᱝ ᱛᱤᱱᱟᱹᱜ ?",
            "आप यह कैसे कहते हैं?" to "ᱟᱢ ᱱᱚᱣᱟ ᱪᱤᱠᱟᱹᱛᱮᱢ ᱢᱮᱱ ᱮᱫᱟ?",
            "क्या आस-पास कोई अस्पताल है?" to "ᱪᱮᱫ ᱥᱩᱨ ᱨᱮ ᱢᱤᱫ ᱦᱚᱥᱯᱤᱴᱟᱞ ᱢᱮᱱᱟᱜᱼᱟ?",
            "मैं वहां किस प्रकार पहुंचा?" to "ᱤᱧ ᱚᱱᱰᱮ ᱪᱤᱠᱟᱹᱛᱮᱧ ᱥᱮᱴᱮᱨᱚᱜᱼᱟ?",
            "आप क्या सिफ़ारिश करते हैं?" to "ᱟᱢ ᱪᱮᱫ ᱥᱚᱞᱦᱟᱢ ᱮᱢᱚᱜ ᱠᱟᱱᱟ?",
            "बाज़ार कहाँ है?" to "ᱵᱟᱡᱟᱨ ᱚᱠᱟᱨᱮ ᱢᱮᱱᱟᱜᱼᱟ?",
            "अस्पताल कहाँ है?" to "ᱦᱚᱥᱯᱤᱴᱟᱞ ᱚᱠᱟᱨᱮ ᱢᱮᱱᱟᱜᱼᱟ?",
            "आपका नाम क्या है?" to "ᱟᱢᱟᱜ ᱧᱩᱛᱩᱢ ᱪᱮᱫ?",
            "क्या पैदल चलना सुरक्षित है?" to "ᱪᱮᱫ ᱱᱚᱣᱟ ᱛᱟᱲᱟᱢ ᱞᱟᱹᱜᱤᱫ ᱨᱚᱯᱟ ᱜᱮᱭᱟ?",
            "बाथरूम कहाँ है?" to "ᱵᱟᱛᱷᱨᱩᱢ ᱚᱠᱟᱨᱮ ᱢᱮᱱᱟᱜᱼᱟ?",
            "टिकट कितनी है?" to "ᱴᱤᱠᱤᱴ ᱫᱚ ᱛᱤᱱᱟᱹᱜ ᱜᱟᱱ?",
            "क्या समय हो गया है?" to "ᱱᱤᱛ ᱛᱤᱱᱟᱹᱜ ᱚᱠᱛᱚ ᱦᱩᱭ ᱟᱠᱟᱱᱟ?",
            "इसका क्या मतलब है?" to "ᱱᱚᱣᱟ ᱨᱮᱭᱟᱜ ᱢᱮᱱᱮᱛ ᱪᱮᱫ ᱠᱟᱱᱟ?",
            "आपकी आयु कितनी है?" to "ᱟᱢᱟᱜ ᱛᱤᱱᱮᱜ ᱵᱚᱭᱮᱥ ᱦᱩᱭᱩᱜ ᱠᱟᱱ ᱛᱟᱢᱟ?",
            "आप कहाँ से हैं?" to "ᱟᱢ ᱚᱠᱟ ᱨᱮᱱ ᱠᱟᱱᱟᱢ?",
            "क्या आप मेरी मदद कर सकते हैं?" to "ᱪᱮᱫ ᱟᱢ ᱤᱧᱮᱢ ᱜᱚᱲᱚ ᱟᱹᱧᱟ?",
            "औषधालय कहां है?" to "ᱯᱷᱟᱨᱢᱟᱥᱭ ᱚᱠᱟᱨᱮ ᱢᱮᱱᱟᱜᱼᱟ?",
            "क्या यह दिन अच्छा है?" to "ᱪᱮᱫ ᱱᱚᱣᱟ ᱫᱤᱱ ᱱᱟᱯᱟᱭ ᱠᱟᱱᱟ?",
            "क्या यह सप्ताह अच्छा है?" to "ᱪᱮᱫ ᱱᱚᱣᱟ ᱦᱟᱯᱛᱟ ᱱᱟᱯᱟᱭ ᱠᱟᱱᱟ?",
            "क्या यह पेन अच्छा है?" to "ᱪᱮᱫ ᱱᱚᱣᱟ ᱯᱮᱱ ᱱᱟᱯᱟᱭ ᱠᱟᱱᱟ?",
            "क्या यह नदी अच्छा है?" to "ᱪᱮᱫ ᱱᱚᱣᱟ ᱱᱟᱹᱭ ᱱᱟᱯᱟᱭ ᱠᱟᱱᱟ?",
            "क्या यह आदमी अच्छा है?" to "ᱪᱮᱫ ᱱᱚᱣᱟ ᱦᱚᱲ ᱱᱟᱯᱟᱭ ᱠᱟᱱᱟ?",
            "क्या यह दोस्त अच्छा है?" to "ᱪᱮᱫ ᱱᱚᱣᱟ ᱜᱟᱛᱮ ᱱᱟᱯᱟᱭ ᱠᱟᱱᱟ?",
            "क्या यह भाषा अच्छा है?" to "ᱪᱮᱫ ᱱᱚᱣᱟ ᱯᱟᱹᱨᱥᱤ ᱱᱟᱯᱟᱭ ᱠᱟᱱᱟ?",
            "क्या यह मुर्गी अच्छा है?" to "ᱪᱮᱫ ᱱᱚᱣᱟ ᱢᱩᱠᱨᱤ ᱱᱟᱯᱟᱭ ᱠᱟᱱᱟ?",
            "क्या यह खेल अच्छा है?" to "ᱪᱮᱫ ᱱᱚᱣᱟ ᱠᱷᱮᱞ ᱱᱟᱯᱟᱭ ᱠᱟᱱᱟ?",
            "क्या यह बाज़ार अच्छा है?" to "ᱪᱮᱫ ᱱᱚᱣᱟ ᱵᱟᱡᱟᱨ ᱱᱟᱯᱟᱭ ᱠᱟᱱᱟ?",
            "क्या यह नर्स अच्छा है?" to "ᱪᱮᱫ ᱱᱚᱣᱟ ᱱᱟᱨᱥ ᱱᱟᱯᱟᱭ ᱠᱟᱱᱟ?",
            "क्या यह नाम अच्छा है?" to "ᱪᱮᱫ ᱱᱚᱣᱟ ᱧᱩᱛᱩᱢ ᱱᱟᱯᱟᱭ ᱠᱟᱱᱟ?",
            "क्या यह रात अच्छा है?" to "ᱪᱮᱫ ᱱᱚᱣᱟ ᱧᱤᱫᱟᱹ ᱱᱟᱯᱟᱭ ᱠᱟᱱᱟ?",
            "क्या यह समय अच्छा है?" to "ᱪᱮᱫ ᱱᱚᱣᱟ ᱚᱠᱛᱚ ᱱᱟᱯᱟᱭ ᱠᱟᱱᱟ?",
            "क्या यह नाक अच्छा है?" to "ᱪᱮᱫ ᱱᱚᱣᱟ ᱱᱟᱠ ᱱᱟᱯᱟᱭ ᱠᱟᱱᱟ?",
            "क्या यह पेंसिल अच्छा है?" to "ᱪᱮᱫ ᱱᱚᱣᱟ ᱯᱮᱱᱥᱤᱞ ᱱᱟᱯᱟᱭ ᱠᱟᱱᱟ?",
            "क्या यह पानी अच्छा है?" to "ᱪᱮᱫ ᱱᱚᱣᱟ ᱫᱟᱜ ᱱᱟᱯᱟᱭ ᱠᱟᱱᱟ?",
            // Base-model behavior preserved against finetune forgetting
            // (Sep-1 verified on-device; absent from gold corpus, kept singly
            // and labeled — not presented as human-verified).
            "मेरा नाम क्या है" to "ᱤᱧᱟᱹᱜ ᱧᱩᱛᱩᱢ ᱪᱮᱫ?"
        )
    }
}
