package com.vachak.ui.prefs

import android.content.Context
import androidx.core.content.edit

/**
 * Offline preferences (SharedPreferences, no network, no DataStore dep).
 * Teacher profile + playback + reminder flags the Settings screen edits.
 */
class VachakPrefs(context: Context) {
    private val sp = context.getSharedPreferences("vachak", Context.MODE_PRIVATE)

    var teacherName: String
        get() = sp.getString("teacher_name", "Vaibhav") ?: "Vaibhav"
        set(v) = sp.edit { putString("teacher_name", v.ifBlank { "Vaibhav" }) }

    var grade: Int
        get() = sp.getInt("grade", 1).coerceIn(1, 5)
        set(v) = sp.edit { putInt("grade", v.coerceIn(1, 5)) }

    var notificationsOn: Boolean
        get() = sp.getBoolean("notifications", true)
        set(v) = sp.edit { putBoolean("notifications", v) }

    var autoPlayTts: Boolean
        get() = sp.getBoolean("autoplay_tts", true)
        set(v) = sp.edit { putBoolean("autoplay_tts", v) }

    /** Completed lesson ids (drives Continue Learning progress for real). */
    fun completedLessons(): Set<String> =
        sp.getStringSet("completed_lessons", emptySet()) ?: emptySet()

    fun markLessonComplete(id: String) {
        if (id.isBlank()) return
        sp.edit { putStringSet("completed_lessons", completedLessons() + id) }
    }

    fun isLessonComplete(id: String): Boolean = completedLessons().contains(id)

    /** Completed chapters as "grade/slug" keys (pack-based study progress —
     *  separate from lesson ids, shown as Done chips on grade pages). */
    fun completedChapters(): Set<String> =
        sp.getStringSet("completed_chapters", emptySet()) ?: emptySet()

    fun markChapterComplete(grade: Int, slug: String) {
        if (slug.isBlank()) return
        sp.edit { putStringSet("completed_chapters", completedChapters() + "$grade/$slug") }
    }

    fun isChapterComplete(grade: Int, slug: String): Boolean =
        completedChapters().contains("$grade/$slug")

    /** Recently viewed lesson ids, most-recent first (max 10). Empty until used. */
    fun recentlyViewed(): List<String> =
        (sp.getStringSet("recently_viewed", emptySet()) ?: emptySet())
            .sortedByDescending { it.substringBefore(":").toLongOrNull() ?: 0L }
            .map { it.substringAfter(":") }

    fun recordViewed(id: String) {
        if (id.isBlank()) return
        val now = System.currentTimeMillis()
        val keep = (sp.getStringSet("recently_viewed", emptySet()) ?: emptySet())
            .filter { it.substringAfter(":") != id }
            .sortedByDescending { it.substringBefore(":").toLongOrNull() ?: 0L }
            .take(9)
        sp.edit { putStringSet("recently_viewed", (setOf("$now:$id") + keep).take(10).toSet()) }
    }

    /** Chapter title language on grade pages: 0 = English, 1 = Hindi, 2 = Santali. */
    var chapterTitleLang: Int
        get() = sp.getInt("chapter_title_lang", 0).coerceIn(0, 2)
        set(v) = sp.edit { putInt("chapter_title_lang", v.coerceIn(0, 2)) }
}
