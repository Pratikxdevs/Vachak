package com.vachak.ml

import android.os.SystemClock
import java.util.concurrent.TimeUnit

/**
 * 5D — Latency instrumentation (Kotlin). Mirrors ml/pipeline/latency.py.
 *
 * Marks (monotonic, not wall-clock):
 *   T0 speech begins | T1 ASR | T2 translate | T3 TTS begins | T4 audio begins
 *   end_to_end = T4 - T0
 *
 * IMPORTANT: latency MUST be MEASURED on the real device (2GB RAM Android 9). With mock/
 * fixture backends these numbers are NOT representative of production. Do NOT claim <=3s
 * from fixtures — the <3s target is a device-measurement goal, not a code guarantee.
 */
data class LatencySample(
    val runId: String,
    var t0SpeechBegin: Long? = null, // nanoseconds
    var t1Asr: Long? = null,
    var t2Translate: Long? = null,
    var t3TtsBegin: Long? = null,
    var t4AudioBegin: Long? = null,
    var asrText: String = "",
    var targetText: String = "",
    var backend: String = "",
    var isFixture: Boolean = false
) {
    fun endToEndMs(): Float? {
        if (t0SpeechBegin == null || t4AudioBegin == null) return null
        return (t4AudioBegin!! - t0SpeechBegin!!) / 1_000_000f
    }

    fun stageMs(): Map<String, Float?> = mapOf(
        "asr" to span(t0SpeechBegin, t1Asr),
        "translate" to span(t1Asr, t2Translate),
        "tts" to span(t2Translate, t3TtsBegin),
        "render" to span(t3TtsBegin, t4AudioBegin)
    )

    private fun span(a: Long?, b: Long?): Float? =
        if (a == null || b == null) null else (b - a) / 1_000_000f
}

class LatencyTracker(private val sample: LatencySample) {
    fun markSpeechBegin(text: String = "") { if (sample.t0SpeechBegin == null) sample.t0SpeechBegin = now(); sample.asrText = text }
    fun markAsr(text: String = "") { sample.t1Asr = now(); if (text.isNotEmpty()) sample.asrText = text }
    fun markTranslate(targetText: String = "") { sample.t2Translate = now(); if (targetText.isNotEmpty()) sample.targetText = targetText }
    fun markTtsBegin() { sample.t3TtsBegin = now() }
    fun markAudioBegin() { sample.t4AudioBegin = now() }
    fun result(): LatencySample = sample
    private fun now(): Long = SystemClock.elapsedRealtimeNanos()
}
