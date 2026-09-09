package com.vachak

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import android.util.Log
import com.vachak.engine.EngineProvider
import com.vachak.engine.EngineResult
import com.vachak.engine.LanguagePair
import com.vachak.ml.adapter.AdapterTranslationEngine
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Dual-adapter device test (Mundari + Santali, Phase 10):
 * Santali (sat_Olck) via proven ONNX INT8 bundle, Mundari (unr_Deva) via
 * deterministic phrasebook until the LoRA-merged CT2 model ships.
 * Runs on-device: ./gradlew :app:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class BothAdaptersTest {

    private val olChikiRegex = Regex("[\u1C50-\u1C7F]")
    private val devaRegex = Regex("[\u0900-\u097F]")

    private fun engine(): EngineProvider =
        EngineProvider.real(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun santali_modelOutput_isOlChiki_notLoop() {
        val e = engine()
        val r = e.translation.translate("मेरा नाम क्या है", LanguagePair("hi", "sat_Olck"))
        Log.d("Vachak-MT", "sat model result=$r")
        assertTrue(r is EngineResult.Ok)
        val text = (r as EngineResult.Ok).value
        assertTrue("sat output must be Ol Chiki, got: $text", olChikiRegex.containsMatchIn(text))
        // No INT8 repetition loop: same char run must stay short.
        var best = 0
        var cur = 0
        var prev = ' '
        for (c in text) {
            cur = if (c == prev) cur + 1 else 1
            prev = c
            if (cur > best) best = cur
        }
        assertTrue("sat output loops, got: $text", best < 8)
    }

    @Test
    fun santali_gold_curated() {
        val e = engine()
        val r = e.translation.translate("नमस्ते", LanguagePair("hi", "sat_Olck"))
        assertTrue(r is EngineResult.Ok)
        assertEquals("ᱡᱚᱦᱟᱨ", (r as EngineResult.Ok).value)
    }

    @Test
    fun mundari_phrasebook_exact() {
        val e = engine()
        val r = e.translation.translate("वे भी कमजोर पड़ रहे हैं", LanguagePair("hi", "unr_Deva"))
        Log.d("Vachak-MT", "mun exact result=$r")
        assertTrue(r is EngineResult.Ok)
        assertEquals("इनकु कमजोरोःतानाको", (r as EngineResult.Ok).value)
    }

    @Test
    fun mundari_phrasebook_isDevanagari_notSantali() {
        val e = engine()
        // In-corpus exact pair (datasets/hin_mun/corpus.tsv).
        val r = e.translation.translate("यह आपको कहाँ मिल गया?", LanguagePair("hi", "mun_Deva"))
        assertTrue(r is EngineResult.Ok)
        val text = (r as EngineResult.Ok).value
        assertEquals("नेआ आम कोताःम नामलाः?", text)
        assertTrue("mun output must be Devanagari, got: $text", devaRegex.containsMatchIn(text))
        assertFalse("mun output must NOT be Ol Chiki, got: $text", olChikiRegex.containsMatchIn(text))
    }

    @Test
    fun mundari_greeting_curated() {
        val e = engine()
        val r = e.translation.translate("नमस्ते", LanguagePair("hi", "unr_Deva"))
        assertTrue(r is EngineResult.Ok)
        assertEquals("जोहार", (r as EngineResult.Ok).value)
    }

    @Test
    fun mundari_merged_openEnded_novelSentence() {
        // Sentence in NEITHER training file: phrasebook must miss, merged CT2
        // (bundled assets -> filesDir extraction in loadModel) must answer.
        // Gate: the merged model ships separately (Phase 2). Without it the
        // honest phrasebook miss is CORRECT behavior — skip, don't fail.
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val engineCheck = AdapterTranslationEngine(ctx)
        assumeTrue(
            "merged CT2 not installed — phrasebook fallback is correct until Phase 2 ships it",
            engineCheck.isMergedReady()
        )
        val e = engine()
        e.translation.loadModel("")
        val r = e.translation.translate("कल हम बाजार जाएँगे", LanguagePair("hi", "unr_Deva"))
        Log.d("Vachak-MT", "mun merged result=$r")
        assertTrue("merged model must translate novel sentence, got: $r", r is EngineResult.Ok)
        val text = (r as EngineResult.Ok).value
        assertTrue("mun output must be Devanagari, got: $text", devaRegex.containsMatchIn(text))
        assertFalse("mun output must NOT be Ol Chiki, got: $text", olChikiRegex.containsMatchIn(text))
        assertFalse("must not be an echo/miss marker, got: $text", text.contains("CT2-") || text.contains("phrasebook"))
    }

    @Test
    fun mundari_miss_isHonestErr_notEcho() {
        val e = engine()
        val r = e.translation.translate("क्वांटम उलझाव प्रकाश संश्लेषण में", LanguagePair("hi", "unr_Deva"))
        Log.d("Vachak-MT", "mun miss result=$r")
        assertTrue("miss must be Err, got: $r", r is EngineResult.Err)
        if (r is EngineResult.Err) {
            assertFalse(r.message.contains("[CT2-MUNDARI]"))
        }
    }

    @Test
    fun supports_both_adapters() {
        val e = engine()
        assertTrue(e.translation.supports(LanguagePair("hi", "sat_Olck")))
        assertTrue(e.translation.supports(LanguagePair("hi", "unr_Deva")))
        assertTrue(e.translation.supports(LanguagePair("hi", "mun_Deva")))
    }

    @Test
    fun tts_neverPlaysBlip_asSpeech() {
        // TTS voice model is a training placeholder: synthesis must either
        // return >=200ms of audio or an honest Err — never a sub-200ms blip
        // presented as speech, and never a crash.
        val e = engine()
        for (lang in listOf("sat_Olck", "unr_Deva")) {
            val r = e.tts.synthesize("जोहार", lang)
            Log.d("Vachak-TTS", "tts honesty lang=$lang result=$r")
            when (r) {
                is EngineResult.Ok -> {
                    assertTrue(
                        "TTS played ${r.value.size} samples as speech (<200ms blip)",
                        r.value.size >= (0.2 * 22050).toInt()
                    )
                }
                is EngineResult.Err -> {
                    Log.d("Vachak-TTS", "tts honest Err lang=$lang: ${r.message}")
                }
            }
        }
    }
}
