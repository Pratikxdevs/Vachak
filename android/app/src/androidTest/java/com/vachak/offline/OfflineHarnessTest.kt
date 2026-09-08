package com.vachak.offline

import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vachak.engine.EngineProvider
import com.vachak.engine.LanguagePair
import com.vachak.ml.adapter.AudioPipeline
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PHASE 2C — instrumented offline harness (runs on-device via gradle
 * connectedCheck). It asserts the three offline acceptance conditions:
 *   1. The app declares NO INTERNET permission (airplane-mode safe).
 *   2. The voice pipeline runs with mock adapters (no network needed).
 *   3. Curriculum is served precomputed from the local store.
 *
 * This is a STUB for the connected-device run; it compiles into the instrumented
 * test APK. Replace mock engines with real sherpa-onnx adapters for the live run.
 */
@RunWith(AndroidJUnit4::class)
class OfflineHarnessTest {

    @Test
    fun app_has_no_internet_permission() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val hasInternet = ctx.checkSelfPermission(android.Manifest.permission.INTERNET) ==
            PackageManager.PERMISSION_GRANTED
        assertFalse("Offline app must NOT hold INTERNET permission", hasInternet)
    }

    @Test
    fun voice_pipeline_runs_fully_offline() {
        // Deterministic offline contract (no live mic — CI devices may deny
        // RECORD_AUDIO to the test package): bundled 16kHz PCM16 WAV
        // (src/androidTest/assets/hindi_sample.wav, read via the TEST context)
        // -> mock ASR -> Ok. Live-mic capture is covered by AudioCapturer +
        // manual demo flow.
        val testCtx = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context
        val pcm = testCtx.assets.open("hindi_sample.wav").use { inp ->
            val bytes = inp.readBytes()
            // 44-byte RIFF header, then little-endian PCM16 mono 16kHz.
            assertTrue("WAV too small: ${bytes.size}", bytes.size > 44)
            val n = (bytes.size - 44) / 2
            ShortArray(n) { i ->
                val lo = bytes[44 + i * 2].toInt() and 0xFF
                val hi = bytes[44 + i * 2 + 1].toInt()
                ((hi shl 8) or lo).toShort()
            }
        }
        assertTrue("WAV PCM must be non-empty", pcm.isNotEmpty())
        val engine = EngineProvider.mock()
        val r = engine.asr.transcribe(pcm, 16000)
        assertTrue("mock ASR must transcribe bundled WAV offline, got: $r", r is com.vachak.engine.EngineResult.Ok)
    }

    @Test
    fun curriculum_is_precomputed_locally() {
        val engine = EngineProvider.mock()
        val lesson = engine.curriculum.getLesson("L1")
        assertTrue(lesson is com.vachak.engine.EngineResult.Ok)
        assertEquals(true, (lesson as com.vachak.engine.EngineResult.Ok).value.precomputed)
    }
}
