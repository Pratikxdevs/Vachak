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
    val error: String? = null
)

object LiveConversationStore {
    val items = mutableStateListOf<ConversationItem>()
    fun clear() { items.clear() }
}
