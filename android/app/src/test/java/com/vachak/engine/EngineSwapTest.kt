package com.vachak.engine

import com.vachak.engine.mock.MockTranslationEngine
import org.junit.Assert.*
import org.junit.Test

/**
 * PHASE 1 proof: a real adapter can be swapped for the mock WITHOUT any change
 * to caller (UI) code. We define a fake "real" translation engine, inject it
 * through the same EngineProvider the UI uses, and assert the UI-facing call
 * surface (translate / supports) behaves identically at the interface level.
 */
class FakeRealTranslationEngine : TranslationEngine {
    override fun supports(pair: LanguagePair) = pair.source == "hi" && pair.target == "mund"
    override fun loadModel(packId: String) = EngineResult.Ok(Unit)
    override fun translate(text: String, pair: LanguagePair): EngineResult<String> =
        if (supports(pair)) EngineResult.Ok("[REAL-mund] $text")
        else EngineResult.Err(EngineError.UNSUPPORTED_LANGUAGE, "pair $pair")
}

class EngineSwapTest {

    @Test
    fun `mock and real engines satisfy the same interface contract`() {
        val uiUsingMock = EngineProvider.mock()
        val uiUsingReal = EngineProvider.mock().copy(translation = FakeRealTranslationEngine())

        val pair = LanguagePair("hi", "mund")
        val mockOut = uiUsingMock.translation.translate("पाठ", pair)
        val realOut = uiUsingReal.translation.translate("पाठ", pair)

        // Caller (UI) handles both identically via EngineResult.
        assertTrue(mockOut is EngineResult.Ok)
        assertTrue(realOut is EngineResult.Ok)
        assertEquals("hi→mund supported by both", true, uiUsingMock.translation.supports(pair))
        assertEquals("hi→mund supported by both", true, uiUsingReal.translation.supports(pair))
        // Only the echoed prefix differs — the UI never branches on it.
        assertEquals((mockOut as EngineResult.Ok).value, "[DEV-FIXTURE-mund] पाठ")
        assertEquals((realOut as EngineResult.Ok).value, "[REAL-mund] पाठ")
    }

    @Test
    fun `unsupported language pair rejected uniformly`() {
        val r = EngineProvider.mock().translation.translate("x", LanguagePair("en", "fr"))
        assertTrue(r is EngineResult.Err)
        assertEquals(EngineError.UNSUPPORTED_LANGUAGE, (r as EngineResult.Err).code)
    }

    @Test
    fun `all mock engines construct via provider`() {
        val p = EngineProvider.mock()
        assertNotNull(p.asr)
        assertNotNull(p.tts)
        assertNotNull(p.curriculum)
        assertNotNull(p.worksheet)
        assertNotNull(p.flashcard)
        assertNotNull(p.packs)
        assertNotNull(p.sync)
        assertNotNull(p.benchmark)
    }
}
