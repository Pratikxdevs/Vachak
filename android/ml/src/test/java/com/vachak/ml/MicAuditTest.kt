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
        assertTrue(audit.appOpMode.isNotEmpty())
        // micList under shadows may be empty (counts as suspicious by design);
        // the invariant is coherence: empty hardware list must be suspicious.
        if (audit.micList.isEmpty()) {
            assertTrue(audit.suspicious())
        }
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
        val clean = MicAuditResult(false, false, emptyList(), false, false, listOf("1:Built-In Mic"), appOpMode = "allowed", micList = listOf("1:mic"))
        assertNull(clean.messageForUser(100, "-20dB"))
    }

    @Test
    fun messageForUser_namesAppOpAndMissingHardware() {
        val ignored = MicAuditResult(false, false, emptyList(), false, false, emptyList(), appOpMode = "IGNORED")
        assertTrue(ignored.messageForUser(100, "-76dB")!!.contains("blocking"))
        val noHw = MicAuditResult(false, false, emptyList(), false, false, emptyList(), appOpMode = "allowed", micList = emptyList())
        assertTrue(noHw.messageForUser(100, "-76dB")!!.contains("no microphone"))
        val clean = MicAuditResult(false, false, emptyList(), false, false, listOf("1:x"), appOpMode = "allowed", micList = listOf("1:y"))
        assertNull(clean.messageForUser(100, "-20dB"))
    }
}
