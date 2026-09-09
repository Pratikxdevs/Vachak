package com.vachak.ml

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * OS mic audit: must never crash and must name culprits deterministically.
 * (On-device it distinguishes OS-mute / other-recorders / call / BT route —
 * the digital-silence causes permission can't catch.)
 */
@RunWith(RobolectricTestRunner::class)
class MicAuditTest {

    @Test
    fun audit_runsClean_withoutCrashing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val capturer = AudioCapturer()
        val audit = capturer.auditMic(context)
        assertSame(audit, capturer.lastMicAudit)
        // Robolectric shadows: no mute, no recorders, no call.
        assertFalse(audit.osMuted)
        assertFalse(audit.inCall)
        assertFalse(audit.suspicious())
        assertNull(audit.messageForUser(16000, "-76dB"))
    }

    @Test
    fun messageForUser_namesEachCulprit() {
        val muted = MicAuditResult(true, false, emptyList(), false, false, emptyList())
        assertTrue(muted.messageForUser(100, "-76dB")!!.contains("MUTED"))
        val busy = MicAuditResult(false, false, listOf("src=1"), false, false, emptyList())
        assertTrue(busy.messageForUser(100, "-76dB")!!.contains("Another app"))
        val call = MicAuditResult(false, false, emptyList(), true, false, emptyList())
        assertTrue(call.messageForUser(100, "-76dB")!!.contains("call"))
        val bt = MicAuditResult(false, false, emptyList(), false, true, emptyList())
        assertTrue(bt.messageForUser(100, "-76dB")!!.contains("Bluetooth"))
        val clean = MicAuditResult(false, false, emptyList(), false, false, listOf("1:Built-In Mic"))
        assertNull(clean.messageForUser(100, "-20dB"))
    }
}
