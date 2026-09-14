package com.vachak.engine

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single logging facade for the whole APK: every Vachak-* line goes to BOTH
 * logcat AND an in-process ring buffer, so Diagnostics → Live Log shows the
 * same lines `adb logcat` would — no adb needed on the tablet.
 *
 * Tag contract (brief, grep-able):
 * Vachak-ASR mic/capture/decode/model · Vachak-VAD segments/gate ·
 * Vachak-MT pair/load/translate/timeout · Vachak-TTS synth/playback/voice ·
 * Vachak-Latency stage timings + verdicts · Vachak-Diag storage/fingerprint ·
 * Vachak-Pack install/checksum/rollback · Vachak-Stop stop-button pipeline.
 * Keep messages one line, past-tense, with the numbers that matter
 * (samples, ms, sizes). Diagnosis reads newest-first; no multi-line dumps.
 */
object VachakLog {
    private const val MAX_LINES = 300
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
        append("D/$tag: $msg")
    }

    fun i(tag: String, msg: String) {
        Log.i(tag, msg)
        append("I/$tag: $msg")
    }

    fun w(tag: String, msg: String) {
        Log.w(tag, msg)
        append("W/$tag: $msg")
    }

    fun w(tag: String, msg: String, tr: Throwable) {
        Log.w(tag, msg, tr)
        append("W/$tag: $msg :: ${tr.message?.take(120)}")
    }

    fun e(tag: String, msg: String) {
        Log.e(tag, msg)
        append("E/$tag: $msg")
    }

    fun e(tag: String, msg: String, tr: Throwable) {
        Log.e(tag, msg, tr)
        append("E/$tag: $msg :: ${tr.message?.take(120)}")
    }

    /** Append-only (for legacy mirror paths) — no logcat dup. */
    fun appendRaw(line: String) {
        append(line)
    }

    fun clear() {
        _lines.value = emptyList()
    }

    private fun append(line: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.ROOT).format(java.util.Date())
        val cur = _lines.value.toMutableList()
        cur.add("[$ts] $line")
        while (cur.size > MAX_LINES) cur.removeAt(0)
        _lines.value = cur
    }
}
