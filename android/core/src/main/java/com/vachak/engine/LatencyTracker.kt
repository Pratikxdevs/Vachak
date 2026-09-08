package com.vachak.engine

import android.util.Log

/**
 * Local, offline latency probe for the sequential SIH voice pipeline (ASR -> MT -> TTS).
 * Per the acceptance test the total must be < 3000ms. Every stage is logged with tag
 * "Vachak-Latency" and the measured deltas are kept for the debug overlay.
 */
class LatencyTracker {
    private val marks = mutableMapOf<String, Long>()
    private var started = 0L
    var totalMs: Long = 0L
        private set

    fun start() {
        started = System.currentTimeMillis()
        marks.clear()
    }

    fun mark(stage: String) {
        marks[stage] = System.currentTimeMillis() - started
    }

    fun stop(): Long {
        totalMs = System.currentTimeMillis() - started
        return totalMs
    }

    fun report(): String = buildString {
        appendLine("Latency (ms): total=$totalMs")
        marks.forEach { (s, t) -> appendLine("  $s @ $t") }
        appendLine("within <3s budget: ${totalMs < LatencyBudget.TOTAL_MS}")
    }

    fun log() = Log.d("Vachak-Latency", report())
}
