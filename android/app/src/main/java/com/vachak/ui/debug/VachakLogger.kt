package com.vachak.ui.debug

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-proc ring buffer for Vachak-* log tags. Offline, 200 lines.
 * Screens log via VachakLogger.d(tag, msg) which both Logcat and ring buffer.
 * DebugOverlay displays this without needing adb logcat.
 */
object VachakLogger {
    private const val MAX_LINES = 200
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(v: Boolean) { _enabled.value = v }

    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
        append("D/$tag: $msg")
    }
    fun w(tag: String, msg: String) {
        Log.w(tag, msg)
        append("W/$tag: $msg")
    }
    fun e(tag: String, msg: String) {
        Log.e(tag, msg)
        append("E/$tag: $msg")
    }
    fun i(tag: String, msg: String) {
        Log.i(tag, msg)
        append("I/$tag: $msg")
    }

    private fun append(line: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.ROOT).format(java.util.Date())
        val stamped = "[$ts] $line"
        val cur = _lines.value.toMutableList()
        cur.add(stamped)
        if (cur.size > MAX_LINES) cur.removeAt(0)
        _lines.value = cur
    }

    fun clear() { _lines.value = emptyList() }

    // Helper called from existing code paths without changing call sites:
    // mirrors android.util.Log.d("Vachak-*", msg) into buffer
    fun mirror(tag: String, msg: String) {
        if (!tag.startsWith("Vachak-")) return
        append("D/$tag: $msg")
    }
}
