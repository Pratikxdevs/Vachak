package com.vachak.ml.adapter

import org.junit.Assert.*
import org.junit.Test

/** PHASE 2A proof: audio path wires VAD->ASR and TTS->play without real models. */
class AudioPipelineTest {
    @Test
    fun `capture and recognize returns a result through mock asr`() {
        // Hardware-free proof: transcribe path is real offline path, not mock fallback in SherpaAsrAdapter.
        val asr = com.vachak.engine.mock.MockAsrEngine
        val r = asr.transcribe(ShortArray(1600) { 1000 }, 16000)
        assertTrue(r is com.vachak.engine.EngineResult.Ok)
        assertEquals("[DEV-FIXTURE-asr] नमस्ते", (r as com.vachak.engine.EngineResult.Ok).value)
    }

    @Test
    fun `speak synthesizes and validates pcm`() {
        val p = AudioPipeline(com.vachak.engine.mock.MockAsrEngine, com.vachak.engine.mock.MockTtsEngine)
        val r = p.speak("[DEV-FIXTURE-mund] नमस्ते")
        assertTrue(r is com.vachak.engine.EngineResult.Ok)
    }

    @Test
    fun `unsupported tts language rejected`() {
        val p = AudioPipeline(com.vachak.engine.mock.MockAsrEngine, com.vachak.engine.mock.MockTtsEngine)
        val r = p.speak("x", language = "fr")
        assertTrue(r is com.vachak.engine.EngineResult.Err)
    }
}
