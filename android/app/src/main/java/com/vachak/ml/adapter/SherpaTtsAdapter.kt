package com.vachak.ml.adapter

import android.content.Context
import android.util.Log
import com.vachak.engine.ActiveLanguage
import com.vachak.engine.EngineError
import com.vachak.engine.EngineResult
import com.vachak.engine.TTSEngine
import com.vachak.ml.SherpaOnnxTtsAdapter

/**
 * sherpa-onnx-backed TTS adapter (built over the vendored k2-fsa/sherpa-onnx AAR).
 *
 * Delegates real synthesis to [SherpaOnnxTtsAdapter] in the :ml module (Santali Ol Chiki
 * VITS, 22.05kHz mono, sherpa-onnx OfflineTts). Keeps the same [TTSEngine] surface so UI
 * code is untouched. When [context] is null or [useReal] is false, returns deterministic
 * placeholder PCM (local wiring / unit tests, no model needed).
 *
 * Language support: Santali Ol Chiki family (`sat` etc.) + Mundari
 * (`unr_Deva`/`unr`/`mun_Deva` Karya Flores + legacy `mund`/`mun` alias)
 * + Hindi (`hi`/`hin_Deva`, for Play-Hindi preview) via ActiveLanguage.
 * Pack-path aware: if [packDir] is provided and contains model.onnx, SherpaOnnxTtsAdapter
 * loads from pack; otherwise falls back to bundled assets/vachak_models/tts/.
 */
class SherpaTtsAdapter(
    private val context: Context? = null,
    private val useReal: Boolean = true,
    private val packDir: String? = null
) : TTSEngine {

    private val tag = "Vachak-TTS"
    private var cachedAdapter: SherpaOnnxTtsAdapter? = null
    private val adapterLock = Any()

    override fun supports(language: String): Boolean {
        val l = language.lowercase()
        return l in setOf(
            // Santali (Ol Chiki)
            "sat", "sat_olck", "sat-olck", "olck", "ol_ck", "sat_olchiki",
            // Mundari (Karya Flores unr_Deva + legacy aliases)
            "unr", "unr_deva", "mun", "mun_deva", "mund", "mundari",
            // Hindi (Play-Hindi preview routes through same VITS shim)
            "hi", "hin", "hin_deva"
        )
    }

    private fun isSantaliTarget(language: String): Boolean =
        language.lowercase() in setOf("sat", "sat_olck", "sat-olck", "olck", "ol_ck", "sat_olchiki")

    private fun hasOlChiki(text: String): Boolean =
        text.any { it.code in 0x1C50..0x1C7F }

    override fun loadModel(packId: String): EngineResult<Unit> {
        if (context == null || !useReal) return EngineResult.Ok(Unit)
        synchronized(adapterLock) {
            if (cachedAdapter == null) {
                return runCatching {
                    val created = SherpaOnnxTtsAdapter(context!!, packDir = packDir)
                    cachedAdapter = created
                    // Warm now (IO thread) so first Play has no cold-load pause.
                    // A failed warm-up is an honest Err, never a silent OK.
                    if (!created.warmUpIfNeeded()) {
                        throw IllegalStateException("TTS warm-up failed — see logcat Vachak-TTS")
                    }
                    Log.d(tag, "loadModel packDir=$packDir -> OK"); EngineResult.Ok(Unit)
                }.fold(
                    onSuccess = { it },
                    onFailure = { e -> EngineResult.Err(EngineError.MODEL_LOAD_FAILED, e.message ?: "tts load failed") }
                )
            }
        }
        return EngineResult.Ok(Unit)
    }

    override fun synthesize(text: String, language: String): EngineResult<ShortArray> {
        // Mundari (unr_Deva family), Santali, and Hindi preview all pass through
        // to the bundled VITS model as-is. No language is remapped away.
        val normalizedLang = language
        if (!supports(language)) return EngineResult.Err(EngineError.UNSUPPORTED_LANGUAGE, language)
        // Proof gate: the Santali voice is an Ol Chiki char-token VITS. Feeding
        // it Devanagari/Latin (e.g. MT output that missed the script) produces
        // garbage-or-silence in the native layer — refuse LOUDLY with the cause
        // instead of playing nothing. Mundari (Devanagari) + Hindi pass through.
        if (isSantaliTarget(language) && !hasOlChiki(text)) {
            Log.w(tag, "refusing synth: Santali target but no Ol Chiki in \"${text.take(40)}\" (MT script miss?)")
            return EngineResult.Err(
                EngineError.MODEL_DECODE_FAILED,
                "Voice needs Ol Chiki text — got non-Ol-Chiki (translation shows above; voice can't speak it)"
            )
        }
        if (context == null || !useReal) {
            // Mock path: fabricate audible-length PCM (>200ms) for offline tests
            val sr = com.vachak.ml.VachakAudio.TTS_OUTPUT_HZ
            val minSamples = (0.22 * sr).toInt() // 4851 > 4800 at 24k but for 22.05k
            val n = maxOf(minSamples, (text.length * 220).coerceAtLeast(minSamples))
            Log.d(tag, "synthesize mock \"$text\" ($language -> $normalizedLang) -> $n samples @ $sr Hz (useReal=false)")
            return EngineResult.Ok(ShortArray(n) { i -> (kotlin.math.sin(2 * Math.PI * 220 * i / sr) * 8000).toInt().toShort() })
        }

        return runCatching {
            val adapter = synchronized(adapterLock) {
                cachedAdapter ?: SherpaOnnxTtsAdapter(context!!, packDir = packDir).also { cachedAdapter = it }
            }
            // Shim gate: 55-token placeholder is incompatible with sherpa-onnx VITS
            // and the native layer HARD-ABORTS on load. Fine-tuned 38-token
            // model passes through (isShim=false) for live synthesis.
            val shim = try { adapter.isShim() } catch (_: Exception) { false }
            if (shim) {
                Log.w(tag, "shim voice model detected — refusing native load (would abort process); text stays source of truth")
                val lang = ActiveLanguage.label(language)
                return EngineResult.Err(EngineError.MODEL_NOT_LOADED, "Voice files outdated or missing — $lang text shown (update the app or reinstall the language pack)")
            }
            val audio = adapter.synthesize(text, normalizedLang)
            if (audio.sampleRate != com.vachak.ml.VachakAudio.TTS_OUTPUT_HZ) {
                Log.w(tag, "model emitted ${audio.sampleRate}Hz but playback assumes ${com.vachak.ml.VachakAudio.TTS_OUTPUT_HZ}Hz — pitch/speed will be wrong; refusing silent corruption")
                return EngineResult.Err(EngineError.MODEL_LOAD_FAILED, "Voice sample-rate mismatch (${audio.sampleRate}Hz) — $text shown as text")
            }
            Log.d(tag, "synthesize \"$text\" ($language -> $normalizedLang) -> ${audio.samples.size} samples @ ${audio.sampleRate} Hz via sherpa-onnx (packDir=${packDir ?: "bundled"})")
            // Anti-blip guard: sub-200ms output is NOT speech.
            val minAudible = (0.2 * audio.sampleRate).toInt()
            if (audio.samples.size < minAudible) {
                val why = audio.warning ?: "model returned ${audio.samples.size} samples (<200ms)"
                Log.w(tag, "synthesize inaudible ${audio.samples.size} samples @ ${audio.sampleRate}Hz — $why")
                throw IllegalStateException("TTS inaudible: $why")
            }
            ShortArray(audio.samples.size) {
                (audio.samples[it] * 32768f).toInt().coerceIn(-32768, 32767).toShort()
            }
        }.fold(
            onSuccess = { EngineResult.Ok(it) },
            onFailure = { e ->
                // Latency: shim already resolved pre-synth — reuse it instead of
                // re-statting tokens.txt on the failure path.
                val msg = if ((e.message ?: "").contains("placeholder")) {
                    val lang = ActiveLanguage.label(language)
                    "Voice files outdated or missing — $lang text shown (update the app or reinstall the language pack)"
                } else if ((e.message ?: "").contains("inaudible")) {
                    e.message ?: "TTS run failed"
                } else "TTS run failed: ${e.message}"
                EngineResult.Err(EngineError.MODEL_LOAD_FAILED, msg)
            }
        )
    }
}
