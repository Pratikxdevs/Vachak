package com.vachak.ui.screens

import androidx.compose.runtime.mutableStateListOf

/**
 * In-memory conversation holder that survives navigation (VachakApp recreates
 * LiveScreen on tab switch). Lives for process lifetime; persistence to Room
 * can be added later without UI changes. Spec: "History persistence belongs to
 * data layer. UI only displays it." live.md:767 + live.md:773.
 */
data class ConversationItem(
    val id: String,
    val hindiText: String,
    val santaliText: String?, // null while translating
    val timestampMillis: Long,
    val isTranslating: Boolean = false,
    /** Phase 4: MT done, TTS generate() running — item shows translated text
     * plus a "Synthesizing voice…" row instead of looking finished/hung. */
    val isSynthesizing: Boolean = false,
    val error: String? = null,
    /** Translation target locked at creation: history keeps its own label +
     * voice on toggle, instead of inheriting the CURRENT language. */
    val targetLang: String = "sat_Olck",
    /** Measured pipeline stages for this item (null until the run completes). */
    val asrMs: Long? = null,
    val mtMs: Long? = null,
    val ttsMs: Long? = null,
    val totalMs: Long? = null
)

object LiveConversationStore {
    val items = mutableStateListOf<ConversationItem>()
    fun clear() { items.clear() }
}
