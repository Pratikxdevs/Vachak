package com.vachak.ui.debug

import com.vachak.engine.VachakLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-app log access: delegates to [VachakLog] (the APK-wide facade), so the
 * overlay and Diagnostics show every module's lines, not just the UI's.
 * `enabled` still gates the floating overlay only — logging itself is always on.
 */
object VachakLogger {
    val lines: StateFlow<List<String>> get() = VachakLog.lines

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(v: Boolean) { _enabled.value = v }

    fun d(tag: String, msg: String) = VachakLog.d(tag, msg)
    fun w(tag: String, msg: String) = VachakLog.w(tag, msg)
    fun e(tag: String, msg: String) = VachakLog.e(tag, msg)
    fun i(tag: String, msg: String) = VachakLog.i(tag, msg)

    fun clear() = VachakLog.clear()

    // Helper called from existing code paths without changing call sites:
    // mirrors VachakLog.d("Vachak-*", msg) into buffer
    fun mirror(tag: String, msg: String) {
        if (!tag.startsWith("Vachak-")) return
        VachakLog.appendRaw("D/$tag: $msg")
    }
}
