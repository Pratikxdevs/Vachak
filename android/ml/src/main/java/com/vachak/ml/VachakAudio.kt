package com.vachak.ml

/**
 * Single source of truth for pipeline audio rates and VAD/endpoint tuning.
 * Previously these were magic literals scattered across :app/:ml (7x `22050`,
 * RMS/hangover numbers with no rationale), so tuning meant archaeology and a
 * wrong rate meant chipmunk audio with no obvious culprit.
 *
 * TTS_OUTPUT_HZ must track the SHIPPED voice model's native rate (currently
 * the 22050Hz placeholder per its ONNX metadata; MUST follow
 * `models/vits-sat.onnx` metadata when the trained VITS ships).
 */
object VachakAudio {
    /** Mic + ASR + VAD rate everywhere ( sherpa features, Silero, NEMO). */
    const val ASR_HZ = 16000

    /** Playback rate for TTS PCM. See class KDoc before changing. */
    const val TTS_OUTPUT_HZ = 22050

    /** Chunks pushed per VAD accept (100ms @16kHz — Silero granularity). */
    const val VAD_CHUNK_SAMPLES = 1600

    /** RMS speech gate on a chunk (below = silence, segment never starts). */
    const val VAD_RMS_THRESHOLD = 0.012f

    /** Trailing silence that endpoints an utterance. */
    const val SILENCE_HANGOVER_MS = 600

    /** Segments shorter than this never reach the decoder (click guard). */
    const val MIN_UTTERANCE_MS = 300

    /** Hard cap per utterance (2GB RAM bound); longer speech splits. */
    const val MAX_UTTERANCE_MS = 14000

    /** Live-partial decode cadence while listening. */
    const val PARTIAL_DECODE_MS = 700L

    /** Bounded streaming window (tail re-decode, never whole history). */
    const val STREAM_WINDOW_SEC = 3

    /** Below this RMS a stream is digital silence (true zeros / muted route),
     * not quiet speech — room noise floor on a live mic never reads this low. */
    const val DIGITAL_SILENCE_RMS = 0.0005f

    /** Startup probe per audio source (~400ms) to reject silently-dead sources. */
    const val SOURCE_PROBE_SAMPLES = 6400

    /** Human dB for logs/UI ("-38dB", "-∞dB"). */
    fun rmsToDb(rms: Float): String =
        if (rms <= 0f) "-∞dB" else "${(20 * kotlin.math.log10(rms)).toInt()}dB"
}
