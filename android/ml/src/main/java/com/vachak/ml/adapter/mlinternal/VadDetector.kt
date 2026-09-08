package com.vachak.ml.adapter.mlinternal

/**
 * Shared VAD abstraction for :ml module (mirrors :app VadDetector so :ml compiles standalone).
 * Renamed package to avoid duplicate class with :app's com.vachak.ml.adapter.VadDetector in release R8 merge.
 * :ml SherpaAsrAdapter shims import from this package; :app's canonical VadDetector remains at com.vachak.ml.adapter.VadDetector.
 */
interface VadDetector {
    fun detect(pcm16: ShortArray, sampleRateHz: Int): List<SpeechSegment>
}

data class SpeechSegment(val startMs: Int, val endMs: Int)

object MockVadDetector : VadDetector {
    override fun detect(pcm16: ShortArray, sampleRateHz: Int): List<SpeechSegment> {
        if (pcm16.isEmpty()) return emptyList()
        val ms = (pcm16.size * 1000) / sampleRateHz
        return listOf(SpeechSegment(0, ms))
    }
}
