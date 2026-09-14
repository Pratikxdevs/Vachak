package com.vachak.ui.screens

import org.junit.Assert.*
import org.junit.Test

/**
 * Live conversation store lock: appends (voice placeholder → in-place update,
 * typed, MT-error) keep order and are visible to readers like the
 * Tools → Saved recent-voice section. Pure JVM — SnapshotStateList works
 * headless.
 */
class LiveConversationStoreTest {

    private fun item(id: String, hindi: String) = ConversationItem(
        id = id,
        hindiText = hindi,
        santaliText = null,
        timestampMillis = 1L,
        isTranslating = true,
        targetLang = "sat_Olck"
    )

    @Test
    fun `placeholder then in-place update keeps order`() {
        LiveConversationStore.clear()
        LiveConversationStore.items.add(item("a", "नमस्ते"))
        LiveConversationStore.items.add(item("b", "धन्यवाद"))
        val i = LiveConversationStore.items.indexOfFirst { it.id == "a" }
        LiveConversationStore.items[i] = LiveConversationStore.items[i].copy(
            santaliText = "ᱡᱚᱦᱟᱨ", isTranslating = false
        )
        assertEquals(2, LiveConversationStore.items.size)
        assertEquals("a", LiveConversationStore.items[0].id)
        assertEquals("ᱡᱚᱦᱟᱨ", LiveConversationStore.items[0].santaliText)
        assertFalse(LiveConversationStore.items[0].isTranslating)
        assertEquals("b", LiveConversationStore.items[1].id)
        LiveConversationStore.clear()
    }

    @Test
    fun `takeLast recent ordering newest first`() {
        LiveConversationStore.clear()
        (1..6).forEach { LiveConversationStore.items.add(item("m$it", "h$it")) }
        val recent = LiveConversationStore.items.takeLast(5).reversed()
        assertEquals(5, recent.size)
        assertEquals("m6", recent.first().id)
        assertEquals("m2", recent.last().id)
        LiveConversationStore.clear()
    }
}
