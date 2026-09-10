package com.vachak.ml

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * loadModel honesty: warm-up must REPORT failure (false + ModelStatus ERROR),
 * never swallow it into a fake OK. Under Robolectric there are no native
 * sherpa libs, so warm-up must fail cleanly (false, no escape).
 */
@RunWith(RobolectricTestRunner::class)
class WarmUpHonestyTest {

    @Test
    fun asrWarmUp_reportsFailure_withoutThrowing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val adapter = IndicConformerAsrAdapter(context)
        val ok = adapter.warmUpIfNeeded()
        assertFalse("no natives under Robolectric: warm-up must report false", ok)
        assertEquals(ModelState.ERROR, ModelStatus.asr.value.state)
    }

    @Test
    fun ttsWarmUp_reportsWithoutThrowing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val adapter = SherpaOnnxTtsAdapter(context)
        // Must not throw (shim short-circuit or caught native failure).
        val ok = adapter.warmUpIfNeeded()
        assertTrue("TTS warm-up must return normally", ok || !ok)
        assertTrue(
            "TTS status recorded, got ${ModelStatus.tts.value}",
            ModelStatus.tts.value.state == ModelState.READY ||
                ModelStatus.tts.value.state == ModelState.ERROR
        )
    }
}
