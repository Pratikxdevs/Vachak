package com.vachak.ml

/**
 * 5B — ASR adapter interface. Production uses [IndicConformerAsrAdapter] (NEMO 134M).
 * Per AGENTS.md: models are local, no runtime network. Sequential pipeline only.
 */
interface AsrAdapter {
    /** Transcribe a FloatArray waveform at [sampleRate] Hz to text. */
    fun transcribe(samples: FloatArray, sampleRate: Int): AsrResult
}

data class AsrResult(
    val text: String,
    val confidence: Float,
    /** True when this is a DEV FIXTURE output, not a real recognition. */
    val isFixture: Boolean = false
)
