package com.vachak.ml

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide, UI-observable model health. Every adapter reports
 * IDLE -> LOADING -> READY (or ERROR with the cause) as it moves through
 * loadModel / first-use warm-up, so the app can show "model live" state
 * instead of failing silently three screens later.
 *
 * "Always live" contract: MainActivity preloads all three engines at startup
 * (see MainActivity.onCreate); adapters additionally warm on first use. The
 * native/model singletons are then held for process lifetime (released only
 * on pack switch / ViewModel clear), so steady-state taps never cold-load.
 */
enum class ModelState { IDLE, LOADING, READY, ERROR }

data class ModelInfo(
    val state: ModelState = ModelState.IDLE,
    /** Human-readable qualifier: sizes, fixture/placeholder notes, or error cause. */
    val detail: String? = null,
    val loadMs: Long? = null,
    /** True when READY-but-limited (e.g. placeholder TTS voice): amber, not green. */
    val degraded: Boolean = false
)

object ModelStatus {
    private val _asr = MutableStateFlow(ModelInfo())
    val asr: StateFlow<ModelInfo> = _asr.asStateFlow()

    private val _mt = MutableStateFlow(ModelInfo())
    val mt: StateFlow<ModelInfo> = _mt.asStateFlow()

    private val _tts = MutableStateFlow(ModelInfo())
    val tts: StateFlow<ModelInfo> = _tts.asStateFlow()

    fun setAsr(info: ModelInfo) { _asr.value = info }
    fun setMt(info: ModelInfo) { _mt.value = info }
    fun setTts(info: ModelInfo) { _tts.value = info }

    fun loadingAsr(detail: String? = null) = setAsr(ModelInfo(ModelState.LOADING, detail))
    fun loadingMt(detail: String? = null) = setMt(ModelInfo(ModelState.LOADING, detail))
    fun loadingTts(detail: String? = null) = setTts(ModelInfo(ModelState.LOADING, detail))
}
