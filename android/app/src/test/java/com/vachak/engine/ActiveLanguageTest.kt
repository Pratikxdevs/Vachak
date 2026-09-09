package com.vachak.engine

import org.junit.Assert.*
import org.junit.Test

/**
 * The app delivers Santali instruction: Santali (Ol Chiki) is the default
 * target, Mundari stays one toggle away. If this fails, first-run users land
 * in the wrong language (phrasebook misses on arbitrary Hindi).
 */
class ActiveLanguageTest {

    @Test
    fun defaultTargetIsSantali() {
        // Fresh-JVM default (verified: no other unit test mutates ActiveLanguage).
        // If this flakes, a new test is leaking language state — fix that test.
        assertEquals("sat_Olck", ActiveLanguage.current)
        assertTrue(ActiveLanguage.isOlChiki())
        assertEquals(LanguagePair("hi", "sat_Olck"), ActiveLanguage.pair())
    }

    @Test
    fun normalizeAliases() {
        assertEquals("sat_Olck", ActiveLanguage.normalize("sat"))
        assertEquals("sat_Olck", ActiveLanguage.normalize("olck"))
        assertEquals("unr_Deva", ActiveLanguage.normalize("mundari"))
        assertEquals("unr_Deva", ActiveLanguage.normalize("mun_Deva"))
    }
}
