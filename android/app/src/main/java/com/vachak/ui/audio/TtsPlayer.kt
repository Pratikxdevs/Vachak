package com.vachak.ui.audio

import com.vachak.engine.VachakLog

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.vachak.engine.EngineProvider
import com.vachak.engine.EngineResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shared offline TTS playback: any screen can speak text through the engine's
 * TTS without duplicating AudioTrack plumbing. Returns a user-facing message
 * (null when audio actually played). Sequential-safe: callers serialize via
 * their own scope (voice pipeline owns its mutex; lesson taps are rare).
 */
object TtsPlayer {
    suspend fun play(engine: EngineProvider, text: String, lang: String): String? {
        if (text.isBlank()) return null
        val res = withContext(Dispatchers.IO) {
            engine.tts.synthesize(text, lang)
        }
        return when (res) {
            is EngineResult.Ok -> {
                val played = withContext(Dispatchers.IO) { playPcm(res.value, com.vachak.ml.VachakAudio.TTS_OUTPUT_HZ) }
                if (played) null else "Audio playback failed on this device — text shown"
            }
            is EngineResult.Err -> res.message
        }
    }

    /** @return true when PCM actually reached the speaker. */
    private fun playPcm(pcm: ShortArray, sampleRate: Int): Boolean {
        if (pcm.isEmpty()) return true
        try {
            val minBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf.coerceAtLeast(pcm.size * 2))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            try {
                track.play()
                var offset = 0
                while (offset < pcm.size) {
                    val len = minOf(2048, pcm.size - offset)
                    track.write(pcm, offset, len, AudioTrack.WRITE_BLOCKING)
                    offset += len
                }
                // Drain: stop() discards queued audio — short clips went silent.
                val drainMs = ((pcm.size * 1000L / sampleRate) + 2000).coerceAtMost(15_000)
                val startMs = android.os.SystemClock.elapsedRealtime()
                while (android.os.SystemClock.elapsedRealtime() - startMs < drainMs) {
                    val head = try { track.playbackHeadPosition } catch (_: Exception) { pcm.size }
                    if (head >= pcm.size) break
                    try { Thread.sleep(50) } catch (_: InterruptedException) { break }
                }
            } finally {
                try { track.stop() } catch (_: Exception) {}
                track.release()
            }
            return true
        } catch (e: Exception) {
            VachakLog.e("Vachak-TTS", "TtsPlayer playback failed", e)
            return false
        }
    }
}
