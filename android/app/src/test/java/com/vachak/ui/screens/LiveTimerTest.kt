package com.vachak.ui.screens

import org.junit.Assert.*
import org.junit.Test

/** Listen timer formatting lock: mm:ss, floors seconds, clamps negatives. */
class LiveTimerTest {

    @Test
    fun `zero and seconds`() {
        assertEquals("0:00", formatListenTimer(0))
        assertEquals("0:05", formatListenTimer(5_000))
        assertEquals("0:59", formatListenTimer(59_999))
    }

    @Test
    fun `minutes roll over`() {
        assertEquals("1:00", formatListenTimer(60_000))
        assertEquals("2:03", formatListenTimer(123_456))
    }

    @Test
    fun `negative clamps to zero`() {
        assertEquals("0:00", formatListenTimer(-1))
    }
}
