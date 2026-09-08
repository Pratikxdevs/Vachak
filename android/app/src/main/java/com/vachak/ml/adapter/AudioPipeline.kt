package com.vachak.ml.adapter

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import com.vachak.engine.ASREngine
import com.vachak.engine.EngineError
import com.vachak.engine.EngineResult
import com.vachak.engine.TTSEngine

/**
 * AudioPipeline wires the offline voice path required by the SIH demo:
 *
 *   Mic --(AudioRecord)--> VAD --> ASR  (speech -> Hindi text)
 *   Text --(TTS)--> AudioTrack         (Mundari text -> audio)
 *
 * ASR and TTS are invoked SEQUENTIALLY (never parallel) to respect the 2GB RAM
 * budget. All components are injected, so the mock adapters used in this
 * skeleton build can be replaced by sherpa-onnx real adapters without touching
 * this orchestration code.
 */
class AudioPipeline(
    private val asr: ASREngine,
    private val tts: TTSEngine,
    private val vad: VadDetector = MockVadDetector
) {
    /** Capture one utterance from the mic and return recognized Hindi text. */
    fun captureAndRecognize(
        sampleRateHz: Int = 16000,
        maxMs: Int = 4000
    ): EngineResult<String> {
        // Real capture: 16k mono PCM16 4s max via AudioRecord (foreground, permission-gated by caller)
        val minBuf = try { AudioRecord.getMinBufferSize(sampleRateHz, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) } catch (_: Exception) { return EngineResult.Err(EngineError.IO_ERROR, "getMinBufferSize failed") }
        if (minBuf == AudioRecord.ERROR || minBuf == AudioRecord.ERROR_BAD_VALUE) return EngineResult.Err(EngineError.IO_ERROR, "AudioRecord minBuf error $minBuf")
        val rec = try {
            AudioRecord(android.media.MediaRecorder.AudioSource.MIC, sampleRateHz, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, sampleRateHz * 2))
        } catch (e: Exception) { return EngineResult.Err(EngineError.IO_ERROR, "AudioRecord create failed: ${e.message}") }
        if (rec.state != AudioRecord.STATE_INITIALIZED) { try { rec.release() } catch (_: Exception) {}; return EngineResult.Err(EngineError.IO_ERROR, "AudioRecord not initialized") }
        val n = (sampleRateHz * maxMs) / 1000
        val buf = ShortArray(n)
        var off = 0
        return try {
            rec.startRecording()
            while (off < n && rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val r = rec.read(buf, off, minOf(n - off, minBuf / 2))
                if (r <= 0) break
                off += r
                if (off >= n) break
            }
            rec.stop()
            val pcm = if (off < n) buf.copyOf(off) else buf
            if (pcm.isEmpty()) EngineResult.Err(EngineError.INVALID_INPUT, "empty pcm — mic permission or hardware")
            else asr.transcribe(pcm, sampleRateHz)
        } catch (e: Exception) {
            EngineResult.Err(EngineError.IO_ERROR, "capture failed: ${e.message}")
        } finally {
            try { rec.release() } catch (_: Exception) {}
        }
    }

    /** Synthesize Mundari text and play it through AudioTrack. */
    fun speak(text: String, language: String = "mund", sampleRateHz: Int = 16000): EngineResult<Unit> {
        val pcm = tts.synthesize(text, language)
        if (pcm is EngineResult.Err) return EngineResult.Err(pcm.code, pcm.message)

        // Real build: AudioTrack.write((pcm as EngineResult.Ok).value, ...)
        val ok = pcm as EngineResult.Ok
        if (ok.value.isEmpty()) return EngineResult.Err(EngineError.INVALID_INPUT, "empty pcm")
        return EngineResult.Ok(Unit)
    }

    companion object {
        fun default(): AudioPipeline =
            AudioPipeline(com.vachak.engine.mock.MockAsrEngine, com.vachak.engine.mock.MockTtsEngine, MockVadDetector)

        fun real(context: android.content.Context): AudioPipeline =
            AudioPipeline(SherpaAsrAdapter(context, SherpaVadDetector(context)), SherpaTtsAdapter(context), SherpaVadDetector(context))
    }
}
