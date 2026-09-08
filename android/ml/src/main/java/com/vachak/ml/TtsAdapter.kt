package com.vachak.ml

/**
 * 5C — TTS adapter interface. Production uses [SherpaOnnxTtsAdapter] (VITS 40M, Ol Chiki).
 */
interface TtsAdapter {
    /** Synthesize [text] (lang [lang], default "mun") to PCM FloatArray at [sampleRate]. */
    fun synthesize(text: String, lang: String = "mun"): SynthAudio
}

data class SynthAudio(
    val samples: FloatArray,
    val sampleRate: Int,
    val backend: String,
    /** True when this is a DEV FIXTURE / fallback, not the final Mundari voice. */
    val isFixture: Boolean = false,
    val warning: String? = null
)
