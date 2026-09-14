package com.vachak.ui.navigation

import android.content.Context

/** Grade pack summary (scripts/build_pack_summary.py → assets/curriculum/pack_summary.json).
 *  AUTO_EXTRACTED source material — render as review-pending, never as approved lessons.
 *  Shared by Learn hub, Grade pages and Chapter pages so every level reads one source. */
data class PackChapter(
    val slug: String,
    val title: String,
    val subject: String,
    val pages: Int,
    val assignments: Int,
    val worksheets: Int,
    val flashcards: Int,
    val images: Int,
    val grade: Int
)

data class PackGrade(
    val grade: Int,
    val chapters: Int,
    val worksheets: Int,
    val flashcards: Int,
    val pages: Int,
    val assignments: Int,
    val images: Int,
    val packMb: Double,
    val chapterTitles: List<PackChapter>,
    val status: String
)

fun loadPackSummary(context: Context): List<PackGrade>? {
    return try {
        val text = context.assets.open("curriculum/pack_summary.json").bufferedReader().use { it.readText() }
        val root = org.json.JSONObject(text)
        val out = mutableListOf<PackGrade>()
        val grades = root.getJSONArray("grades")
        for (i in 0 until grades.length()) {
            val g = grades.getJSONObject(i)
            val t = g.getJSONObject("totals")
            val chArr = g.getJSONArray("chapters")
            val titles = mutableListOf<PackChapter>()
            for (j in 0 until chArr.length()) {
                val c = chArr.getJSONObject(j)
                titles.add(
                    PackChapter(
                        slug = c.getString("slug"),
                        title = c.getString("title"),
                        subject = c.optString("subject", ""),
                        pages = c.optInt("pages", 0),
                        assignments = c.optInt("assignments", 0),
                        worksheets = c.optInt("worksheets", 0),
                        flashcards = c.optInt("flashcards", 0),
                        images = c.optInt("images", 0),
                        grade = c.optInt("grade", g.getInt("grade"))
                    )
                )
            }
            // Dedupe by slug (shared Class 1-2 sources repeat) and sort for a
            // stable chapter list — every consumer sees one row per chapter.
            val deduped = titles.distinctBy { it.slug }.sortedBy { it.title }
            out.add(
                PackGrade(
                    grade = g.getInt("grade"),
                    chapters = t.getInt("chapters"),
                    worksheets = t.getInt("worksheets"),
                    flashcards = t.getInt("flashcards"),
                    pages = t.getInt("pages"),
                    assignments = t.getInt("assignments"),
                    images = t.getInt("images"),
                    packMb = g.getLong("pack_bytes") / 1048576.0,
                    chapterTitles = deduped,
                    status = g.optString("status", "AUTO_EXTRACTED")
                )
            )
        }
        out
    } catch (_: Exception) { null }
}
