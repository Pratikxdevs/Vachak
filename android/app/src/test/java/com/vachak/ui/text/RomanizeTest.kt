package com.vachak.ui.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RomanizeTest {
    @Test
    fun `devanagari common words`() {
        assertEquals("namaste", Romanize.devanagari("नमस्ते"))
        assertEquals("aap kaise hain", Romanize.devanagari("आप कैसे हैं"))
        assertEquals("haathee", Romanize.devanagari("हाथी"))
    }

    @Test
    fun `olchiki maps to santali latin`() {
        val out = Romanize.olChiki("ᱡᱚᱦᱟᱨ")
        assertEquals("johar", out)
        assertTrue(Romanize.olChiki("ᱪᱮᱫ").isNotBlank())
    }

    @Test
    fun `auto picks by script`() {
        assertEquals(Romanize.devanagari("नमस्ते"), Romanize.auto("नमस्ते"))
        assertEquals(Romanize.olChiki("ᱡᱚᱦᱟᱨ"), Romanize.auto("ᱡᱚᱦᱟᱨ"))
        assertEquals("", Romanize.auto("  "))
    }

    @Test
    fun `unknown codepoints pass through`() {
        assertEquals("hello", Romanize.devanagari("hello"))
    }
}
