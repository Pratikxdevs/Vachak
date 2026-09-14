package com.vachak.ml

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression test for overlapping VAD segments bug:
 * User spoke:
 * 1. "नमस्ते आप कैसे हैं"
 * 2. "आपका क्या नाम है"
 * Final was corrupted: "आप क का क्या नाम है" with overlapping VAD [2085,2879] and [2351,4399].
 * Fixed pipeline must produce stable non-overlapping segments and committedText "नमस्ते आप कैसे हैं। आपका क्या नाम है।"
 */
@RunWith(RobolectricTestRunner::class)
class StreamingAsrSessionRegressionTest {

    private fun createFakeVad(): VadAnalyzer = object : VadAnalyzer {
        var pos = 0f
        override fun accept(chunk: FloatArray) { pos += chunk.size / 16000f }
        override fun isSpeech(): Boolean = false
        override fun popSegment(): VadSegment? = null
        override fun flush() {}
    }

    private fun pcmFor(id: Int, size: Int = 16000): ShortArray = ShortArray(size) { (8000 + id).toShort() } // 8000 amplitude >0.012 RMS

    @Test
    fun test_nonOverlappingSegments_twoUtterances_finalIsCorrect() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fakeTranscriber: (ShortArray) -> String = { pcm ->
            // Distinguish by first sample value
            when (pcm.firstOrNull()?.toInt()) {
                8001 -> "नमस्ते आप कैसे हैं"
                8002 -> "आपका क्या नाम है"
                else -> if (pcm.size == 16000) "नमस्ते आप कैसे हैं" else "आपका क्या नाम है"
            }
        }
        val session = StreamingAsrSession(context, sampleRate = 16000, testTranscriber = fakeTranscriber, testVad = createFakeVad())
        session.start()

        val pcm1 = pcmFor(1, 16000)
        session.pushAudio(pcm1)
        val partial1 = session.getPartial()
        assertTrue("first partial should be first utterance", partial1 == "नमस्ते आप कैसे हैं")
        val log1 = session.finalizeCurrentSegment(true)
        assertEquals(0, log1.segmentId)
        assertEquals(0, log1.startSample)
        assertEquals(16000, log1.endSample)
        assertEquals(1000, log1.durationMs)
        assertTrue(log1.finalized)
        assertEquals("नमस्ते आप कैसे हैं", session.committedText)
        assertEquals("", session.currentPartial)

        val pcm2 = pcmFor(2, 16000)
        session.pushAudio(pcm2)
        val partial2 = session.getPartial()
        assertEquals("आपका क्या नाम है", partial2)
        val log2 = session.finalizeCurrentSegment(true)
        assertEquals(1, log2.segmentId)
        assertEquals(16000, log2.startSample)
        assertEquals(32000, log2.endSample)
        assertEquals(1000, log2.durationMs)
        assertFalse("segments must not overlap", log1.endSample > log2.startSample)
        assertEquals("नमस्ते आप कैसे हैं आपका क्या नाम है", session.committedText.replace("।", "").trim())
        val final = session.finish()
        assertEquals("नमस्ते आप कैसे हैं आपका क्या नाम है", final.replace("।", "").trim())
    }

    @Test
    fun test_overlappingVadSegmentsAreFixed() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fakeTranscriber: (ShortArray) -> String = { _ -> "आपका क्या नाम है" }
        val session = StreamingAsrSession(context, sampleRate = 16000, testTranscriber = fakeTranscriber, testVad = createFakeVad())
        session.start()
        val pcmA = ShortArray(2879) { 8000 }
        session.pushAudio(pcmA)
        val logA = session.finalizeCurrentSegment(true)
        assertEquals(0, logA.segmentId)
        assertEquals(0, logA.startSample)
        assertEquals(2879, logA.endSample)

        val pcmB = ShortArray(1520) { 8000 } // 4399-2879 =1520
        session.pushAudio(pcmB)
        val logB = session.finalizeCurrentSegment(true)
        assertEquals(1, logB.segmentId)
        assertEquals(2879, logB.startSample)
        assertFalse("fixed segments must not overlap [2085,2879] and [2351,4399] had overlap, now [0,2879] and [2879,4399] no overlap", logB.startSample < logA.endSample)
        assertEquals(95, logB.durationMs) // 1520/16=95ms
    }

    @Test
    fun test_doNotConcatenatePartialHypotheses() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var call = 0
        val fakeTranscriber: (ShortArray) -> String = { _ ->
            call++
            when (call) {
                1 -> "नमस्ते आप"
                2 -> "नमस्ते आप कैसे"
                3 -> "नमस्ते आप कैसे हैं"
                else -> "नमस्ते आप कैसे हैं"
            }
        }
        val session = StreamingAsrSession(context, sampleRate = 16000, testTranscriber = fakeTranscriber, testVad = createFakeVad())
        session.start()
        val pcm = ShortArray(8000) { 8000 }
        session.pushAudio(pcm)
        val p1 = session.getPartial()
        assertEquals("नमस्ते आप", p1)
        session.pushAudio(ShortArray(2000) { 8000 })
        val p2 = session.getPartial()
        assertEquals("नमस्ते आप कैसे", p2)
        assertFalse("should not concatenate partials", p2.contains("नमस्ते आपनमस्ते"))
        session.pushAudio(ShortArray(2000) { 8000 })
        val p3 = session.getPartial()
        assertEquals("नमस्ते आप कैसे हैं", p3)
        session.finalizeCurrentSegment(true)
        assertEquals("नमस्ते आप कैसे हैं", session.committedText)
        assertEquals("", session.currentPartial)
    }

    @Test
    fun test_onlyCurrentActiveSegmentGeneratesPartial() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var call = 0
        val fakeTranscriber: (ShortArray) -> String = { pcm ->
            call++
            // First segment's pcm will be 16000 size with value 8001, second with 8002
            if (pcm.firstOrNull() == 8001.toShort() || call <= 2) "नमस्ते आप" else "आपका क्या नाम है"
        }
        // Use distinct pcm values to distinguish
        val session = StreamingAsrSession(context, sampleRate = 16000, testTranscriber = fakeTranscriber, testVad = createFakeVad())
        session.start()
        val pcm1 = pcmFor(1, 16000)
        session.pushAudio(pcm1)
        val partial1 = session.getPartial()
        assertEquals("नमस्ते आप", partial1)
        session.finalizeCurrentSegment(true)
        assertEquals("नमस्ते आप", session.committedText)
        val pcm2 = pcmFor(2, 16000)
        session.pushAudio(pcm2)
        val partial2 = session.getPartial()
        assertEquals("आपका क्या नाम है", partial2)
        assertFalse("partial should not contain previous committed", partial2.contains("नमस्ते"))
    }

    @Test
    fun test_oneShotDecodingCleanSegments() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fakeTranscriber: (ShortArray) -> String = { pcm ->
            if (pcm.size == 16000) "नमस्ते आप कैसे हैं" else "आपका क्या नाम है"
        }
        val session = StreamingAsrSession(context, sampleRate = 16000, testTranscriber = fakeTranscriber, testVad = createFakeVad())
        session.start()
        val cleanPcm1 = ShortArray(16000) { 8000 }
        val oneShot1 = session.transcribeOneShot(cleanPcm1)
        assertEquals("नमस्ते आप कैसे हैं", oneShot1)
        val cleanPcm2 = ShortArray(12000) { 8000 }
        val oneShot2 = session.transcribeOneShot(cleanPcm2)
        assertEquals("आपका क्या नाम है", oneShot2)
    }

    @Test
    fun test_finalTranscriptExpectedTwoSentences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var idx = 0
        val expected = listOf("नमस्ते आप कैसे हैं", "आपका क्या नाम है")
        val fakeTranscriber: (ShortArray) -> String = { pcm ->
            // Use pcm first value to decide, but also fallback to idx
            val v = pcm.firstOrNull()?.toInt() ?: 0
            when {
                v == 8001 -> expected[0]
                v == 8002 -> expected[1]
                else -> expected[idx++ % expected.size]
            }
        }
        val session = StreamingAsrSession(context, sampleRate = 16000, testTranscriber = fakeTranscriber, testVad = createFakeVad())
        session.start()
        session.pushAudio(pcmFor(1, 16000))
        session.finalizeCurrentSegment(true)
        session.pushAudio(pcmFor(2, 14000))
        session.finalizeCurrentSegment(true)
        val final = session.finish()
        assertTrue(final.contains("नमस्ते आप कैसे हैं"))
        assertTrue(final.contains("आपका क्या नाम है"))
        assertFalse("should not be corrupted 'आप क का क्या नाम है'", final == "आप क का क्या नाम है")
        assertEquals("नमस्ते आप कैसे हैं आपका क्या नाम है", final.replace("।", "").trim())
    }

    @Test
    fun test_threePhraseSequence_namaste_kyaKarRaheHo_aapKaiseHo() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val expected = listOf("नमस्ते", "क्या कर रहे हो", "आप कैसे हो")
        var idx = 0
        val fakeTranscriber: (ShortArray) -> String = { pcm ->
            val v = pcm.firstOrNull()?.toInt() ?: 0
            when {
                v == 8001 -> expected[0]
                v == 8002 -> expected[1]
                v == 8003 -> expected[2]
                else -> expected[idx++ % expected.size]
            }
        }
        val session = StreamingAsrSession(context, sampleRate = 16000, testTranscriber = fakeTranscriber, testVad = createFakeVad())
        session.start()
        session.pushAudio(pcmFor(1, 8000))
        session.pushAudio(ShortArray(9600) { 0 })
        if (session.committedText.isEmpty()) session.finalizeCurrentSegment(true)
        assertTrue(session.committedText.contains("नमस्ते"))
        session.pushAudio(pcmFor(2, 12000))
        session.pushAudio(ShortArray(9600) { 0 })
        if (session.committedText == "नमस्ते") session.finalizeCurrentSegment(true)
        assertTrue(session.committedText.contains("क्या कर रहे हो"))
        session.pushAudio(pcmFor(3, 10000))
        session.pushAudio(ShortArray(9600) { 0 })
        if (!session.committedText.contains("आप कैसे हो")) session.finalizeCurrentSegment(true)
        val final = session.finish()
        assertTrue(final.contains("नमस्ते"))
        assertTrue(final.contains("क्या कर रहे हो"))
        assertTrue(final.contains("आप कैसे हो"))
        assertEquals("नमस्ते क्या कर रहे हो आप कैसे हो", final.replace("।", "").trim())
        assertEquals(3, session.segmentRecords.size)
        for (i in 1 until session.segmentRecords.size) {
            assertFalse("segments must not overlap", session.segmentRecords[i].startSample < session.segmentRecords[i-1].endSample)
            assertEquals(i, session.segmentRecords[i].segmentId)
        }
        assertEquals("नमस्ते", session.segmentRecords[0].finalText)
        assertEquals("क्या कर रहे हो", session.segmentRecords[1].finalText)
        assertEquals("आप कैसे हो", session.segmentRecords[2].finalText)
    }

    @Test
    fun test_throwingModel_recordsDecodeError_neverDisguisedAsVadSilence() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val throwing: (ShortArray) -> String = { throw RuntimeException("INVALID_ARGUMENT : Invalid rank for input") }
        val session = StreamingAsrSession(context, sampleRate = 16000, testTranscriber = throwing, testVad = createFakeVad())
        session.start()
        assertEquals(null, session.lastDecodeError)
        session.pushAudio(pcmFor(1, 16000))
        // Partial path: must not propagate, must record the cause.
        assertEquals("", session.getPartial())
        assertTrue(session.lastDecodeError?.contains("INVALID_ARGUMENT") == true)
        // Final path: must not propagate either; commit stays empty WITH cause.
        val log = session.finalizeCurrentSegment(true)
        assertTrue(log.finalized)
        assertEquals("", session.committedText)
        assertEquals("", session.finish())
        assertTrue(session.lastDecodeError?.contains("INVALID_ARGUMENT") == true)
        // Next session starts clean.
        session.start()
        assertEquals(null, session.lastDecodeError)
    }

    private fun createThrowingVad(): VadAnalyzer = object : VadAnalyzer {
        override fun accept(chunk: FloatArray) { throw RuntimeException("Silero native abort") }
        override fun isSpeech(): Boolean = false
        override fun popSegment(): VadSegment? = null
        override fun flush() {}
    }

    @Test
    fun test_throwingVad_rmsPathStillCommits() {
        // A throwing neural VAD must never kill the energy-gated pipeline.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val session = StreamingAsrSession(context, sampleRate = 16000, testTranscriber = { "नमस्ते" }, testVad = createThrowingVad())
        session.start()
        session.pushAudio(pcmFor(1, 16000))
        assertEquals("नमस्ते", session.getPartial())
        session.finalizeCurrentSegment(true)
        assertEquals("नमस्ते", session.finish())
        assertTrue("VAD failures counted, pipeline survived", session.vadFailures > 0)
    }

    @Test
    fun test_digitalSilence_statsProveDeadMic() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val session = StreamingAsrSession(context, sampleRate = 16000, testTranscriber = { "never" }, testVad = createFakeVad())
        session.start()
        session.pushAudio(ShortArray(16000) { 0 })
        session.pushAudio(ShortArray(16000) { 0 })
        assertEquals("", session.finish())
        assertEquals(2, session.chunksSeen)
        assertEquals(0f, session.maxRmsSeen)
        assertEquals(2, session.zeroChunks)
        assertTrue(session.signalReport().contains("maxRms=0.0"))
    }
    @Test
    fun test_loudSpeech_statsShowSignal() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val session = StreamingAsrSession(context, sampleRate = 16000, testTranscriber = { "है" }, testVad = createFakeVad())
        session.start()
        session.pushAudio(pcmFor(9, 16000))
        session.finalizeCurrentSegment(true)
        assertEquals("है", session.finish())
        assertTrue("peak above speech gate", session.maxRmsSeen > 0.012f)
        assertEquals(0, session.zeroChunks)
    }

    @Test
    fun test_longContinuousSpeech_commitsInChunksWhileSpeaking() {
        // <3s hard limit: continuous speech flags pending every STREAM_COMMIT_SEC
        // (3s); the decode lane runs one bounded final decode per flag, so no
        // decode exceeds ~3s of audio and words land mid-press.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val session = StreamingAsrSession(context, sampleRate = 16000, testTranscriber = { "शब्द" }, testVad = createFakeVad())
        session.start()
        session.pushAudio(ShortArray(16000) { 8000 }) // 1s: partials allowed
        assertFalse("early chunk: partial lane must run", session.isChunkImminent())
        repeat(2) { session.pushAudio(ShortArray(16000) { 8000 }) } // 3s continuous
        assertTrue("late chunk: partial lane must idle for the commit", session.isChunkImminent())
        assertTrue("3s chunk must flag pending while speaking", session.consumePendingFinalize())
        session.finalizeCurrentSegment(true) // decode lane's single bounded decode
        assertTrue("first 3s chunk must commit while speaking", session.committedText.contains("शब्द"))
        assertEquals(1, session.segmentRecords.size)
        assertEquals(listOf("शब्द"), session.drainNewCommits())
        assertTrue("drain consumes", session.drainNewCommits().isEmpty())
        session.pushAudio(ShortArray(16000) { 8000 }) // +1s remainder
        val final = session.finish()
        assertEquals(2, session.segmentRecords.size)
        assertEquals("शब्द शब्द", final)
        for (i in 1 until session.segmentRecords.size) {
            assertFalse("chunks must not overlap", session.segmentRecords[i].startSample < session.segmentRecords[i - 1].endSample)
        }
    }
}
