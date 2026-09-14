package com.vachak.ui.content

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.vachak.sync.PackManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Reads installed-pack curriculum content (sat_Olck-v0.2.0+).
 * Pack layout under filesDir/packs/<id>/curriculum/class/{g}/… — same shape
 * as the repo's curriculum/class. Everything stays AUTO_EXTRACTED: this reader
 * only surfaces text/images, never approval status.
 */
object PackContentReader {

    /** Installed curriculum root (…/packs/<id>/curriculum) or null. */
    suspend fun curriculumDir(context: Context): File? = withContext(Dispatchers.IO) {
        try {
            PackManager.getActivePackForIO(context, "curriculum")?.let { File(it) }
                ?.takeIf { it.isDirectory }
        } catch (eUnused: Exception) { null }
    }

    fun gradeDir(curriculumRoot: File, grade: Int): File? =
        File(curriculumRoot, "class/$grade").takeIf { it.isDirectory }

    data class InstalledChapter(
        val chapterId: String,
        val subject: String,
        val status: String,
        val textEncoding: String,
        val assignments: List<Assign>,
        val images: List<File>
    )
    data class Assign(val type: String, val confidence: Double, val text: String, val page: Int, val textHi: String = "")
    data class PackQuestion(
        val id: String, val type: String, val prompt: String,
        val answer: String?, val renderCount: Int, val needsReview: List<String>
    )
    data class PackCard(
        val id: String, val frontDeva: String, val target: String?,
        val imageRef: String?, val needsReview: List<String>
    )

    private fun chapterDirSync(root: File, grade: Int, slug: String): File? =
        File(File(root, "class/$grade/chapters"), slug).takeIf { it.isDirectory }

    /** Resolve the curriculum root: active pack first, then any installed pack
     *  under filesDir/packs/<id>/curriculum (same layout). Null = not on device. */
    private fun resolveRootBlocking(context: Context): File? {
        // 1. Active pack (blocking — call off the main thread).
        try {
            PackManager.getActivePackFor(context, "curriculum")?.let { File(it) }
                ?.takeIf { it.isDirectory }?.let { return it }
        } catch (_: Exception) { }
        // 2. Any installed pack with a curriculum tree.
        try {
            val packsDir = File(context.filesDir, "packs")
            packsDir.listFiles()
                ?.filter { it.isDirectory }
                ?.sortedBy { it.name }
                ?.forEach { pack ->
                    listOf(
                        File(pack, "curriculum"),
                        File(pack, "vachak_models/curriculum"),
                        pack
                    ).forEach { cand ->
                        if (cand.isDirectory && File(cand, "class").isDirectory) return cand
                    }
                }
        } catch (_: Exception) { }
        return null
    }

    /** Worksheet file for a chapter: canonical ws_<slug>_bilingual.json first,
     *  then any other worksheets JSON file containing a "questions" array. */
    private fun worksheetFile(chapterDir: File, slug: String): File? {
        val wsDir = File(chapterDir, "worksheets")
        if (!wsDir.isDirectory) return null
        val canonical = File(wsDir, "ws_${slug}_bilingual.json")
        if (canonical.isFile) return canonical
        val cands = wsDir.listFiles { f -> f.isFile && f.extension.lowercase() == "json" }
            ?.sortedBy { it.name }.orEmpty()
        // Prefer files whose JSON actually carries questions.
        cands.forEach { f ->
            try {
                if (JSONObject(f.readText()).has("questions")) return f
            } catch (_: Exception) { }
        }
        return cands.firstOrNull()
    }

    /** Deck file for a chapter: deck_bilingual.json first, then deck*.json. */
    private fun deckFile(chapterDir: File): File? {
        val fcDir = File(chapterDir, "flashcards")
        if (!fcDir.isDirectory) return null
        val canonical = File(fcDir, "deck_bilingual.json")
        if (canonical.isFile) return canonical
        return fcDir.listFiles { f -> f.isFile && f.extension.lowercase() == "json" }
            ?.sortedBy { it.name }
            ?.firstOrNull { f ->
                try { JSONObject(f.readText()).has("cards") } catch (_: Exception) { false }
            }
            ?: fcDir.listFiles { f -> f.isFile && f.extension.lowercase() == "json" }
                ?.sortedBy { it.name }?.firstOrNull()
    }

    /** Pure parser: worksheet JSON text → questions. JVM-testable, no Context. */
    fun parseWorksheetJson(text: String): List<PackQuestion> {
        val arr = JSONObject(text).optJSONArray("questions") ?: return emptyList()
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val prompt = o.optString("prompt_sat_deva", "").ifBlank { o.optString("prompt_target", "") }
            val rawAns = when (val a = o.opt("answer")) {
                null, JSONObject.NULL -> null
                is Number -> a.toString()
                is Boolean -> a.toString()
                else -> a.toString().ifBlank { null }
            }
            PackQuestion(
                id = o.optString("id", "q-$i").ifBlank { "q-$i" },
                type = o.optString("type", "practice").ifBlank { "practice" },
                prompt = prompt,
                answer = rawAns,
                renderCount = o.optInt("render_count", 0),
                needsReview = o.optJSONArray("needs_review")?.let { nr ->
                    List(nr.length()) { k -> nr.optString(k, "") }.filter { it.isNotBlank() }
                }.orEmpty()
            )
        }
    }

    /** Pure parser: deck JSON text → cards. JVM-testable, no Context. */
    fun parseDeckJson(text: String): List<PackCard> {
        val arr = JSONObject(text).optJSONArray("cards") ?: return emptyList()
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            PackCard(
                id = o.optString("card_id", "c-$i").ifBlank { "c-$i" },
                frontDeva = o.optString("concept_sat_deva", "")
                    .ifBlank { o.optString("concept_hi", "") },
                target = o.optString("concept_target", "").ifBlank { null },
                imageRef = o.optString("image_ref", "").ifBlank { null },
                needsReview = o.optJSONArray("needs_review")?.let { nr ->
                    List(nr.length()) { k -> nr.optString(k, "") }.filter { it.isNotBlank() }
                }.orEmpty()
            )
        }
    }

    /** Copy a bundled chapter (json + a bounded set of images) into cacheDir,
     *  preserving the pack's relative layout. Returns (curriculumRoot, chapterDir).
     *  Bounded to chapter.json + assignments.json + 12 images so a chapter view
     *  never unpacks megabytes on a 2GB device. */
    private fun materializeBundledChapter(context: Context, grade: Int, slug: String): Pair<File, File>? {
        val packs = try { context.assets.list("packs")?.toList().orEmpty() } catch (_: Exception) { return null }
        val packFile = packs.firstOrNull { it.endsWith(".vachakpack") } ?: return null
        val base = File(File(context.cacheDir, "bundled_pack"), "curriculum")
        val chapterDir = File(File(base, "class/$grade/chapters"), slug)
        val marker = File(chapterDir, ".materialized")
        if (marker.isFile && File(chapterDir, "chapter.json").isFile && File(chapterDir, "assignments.json").isFile) {
            return base to chapterDir
        }
        return try {
            var chapterText: String? = null
            var assignsDone = false
            context.assets.open("packs/$packFile").use { raw ->
                java.util.zip.ZipInputStream(raw).use { zin ->
                    val prefix = "curriculum/class/$grade/chapters/$slug/"
                    var e = zin.nextEntry
                    // Keep scanning until BOTH json files are collected — stopping
                    // after chapter.json alone used to leave assignments.json
                    // behind and fail every chapter open for affected packs.
                    while (e != null && (chapterText == null || !assignsDone)) {
                        if (!e.isDirectory) {
                            when (e.name) {
                                prefix + "chapter.json" -> {
                                    chapterText = zin.readBytes().toString(Charsets.UTF_8)
                                    writeCacheFile(base, e.name, chapterText!!.toByteArray(Charsets.UTF_8))
                                }
                                prefix + "assignments.json" -> {
                                    writeCacheFile(base, e.name, zin.readBytes())
                                    assignsDone = true
                                }
                            }
                        }
                        zin.closeEntry()
                        e = zin.nextEntry
                    }
                }
            }
            val ct = chapterText ?: return null
            // Second pass: only this chapter's referenced illustrations (≤12).
            val refs = try {
                org.json.JSONObject(ct).optJSONArray("image_refs")?.let { arr ->
                    List(arr.length()) { i -> arr.getJSONObject(i).optString("asset", "") }
                        .filter { it.isNotBlank() }.take(12)
                }.orEmpty()
            } catch (_: Exception) { emptyList() }
            if (refs.isNotEmpty()) {
                val wanted = refs.mapTo(mutableSetOf()) { "curriculum/class/$grade/$it" }
                context.assets.open("packs/$packFile").use { raw ->
                    java.util.zip.ZipInputStream(raw).use { zin ->
                        var e = zin.nextEntry
                        while (e != null && wanted.isNotEmpty()) {
                            if (!e.isDirectory && wanted.remove(e.name)) {
                                writeCacheFile(base, e.name, zin.readBytes())
                            }
                            zin.closeEntry()
                            e = zin.nextEntry
                        }
                    }
                }
            }
            try { marker.createNewFile() } catch (_: Exception) { }
            base to chapterDir
        } catch (_: Exception) { null }
    }

    private fun writeCacheFile(base: File, entryName: String, bytes: ByteArray) {
        // Preserve layout relative to curriculum/ root.
        val rel = entryName.removePrefix("curriculum/")
        val out = File(base, rel)
        out.parentFile?.mkdirs()
        out.writeBytes(bytes)
    }

    private fun readBundledText(context: Context, entryPath: String): String? {
        return try {
            val packs = context.assets.list("packs")?.toList().orEmpty()
            val packFile = packs.firstOrNull { it.endsWith(".vachakpack") } ?: return null
            context.assets.open("packs/$packFile").use { raw ->
                java.util.zip.ZipInputStream(raw).use { zin ->
                    var e = zin.nextEntry
                    while (e != null) {
                        if (!e.isDirectory && e.name == entryPath) {
                            return zin.readBytes().toString(Charsets.UTF_8)
                        }
                        zin.closeEntry()
                        e = zin.nextEntry
                    }
                    null
                }
            }
        } catch (_: Exception) { null }
    }

    /** Worksheet questions for a chapter (null = not on device anywhere). */
    suspend fun readWorksheet(context: Context, grade: Int, slug: String): List<PackQuestion>? =
        withContext(Dispatchers.IO) {
            readWorksheetBlocking(context, grade, slug)
        }

    fun readWorksheetBlocking(context: Context, grade: Int, slug: String): List<PackQuestion>? {
        // 1. Installed pack on device.
        try {
            val root = resolveRootBlocking(context)
            val dir = root?.let { chapterDirSync(it, grade, slug) }
            val f = dir?.let { worksheetFile(it, slug) }
            if (f != null) return try { parseWorksheetJson(f.readText()) } catch (_: Exception) { emptyList() }
            if (dir != null) return emptyList() // chapter dir exists, worksheets missing → honest empty
        } catch (_: Exception) { }
        // 2. Bundled .vachakpack asset (pre-install demo path).
        val entry = "curriculum/class/$grade/chapters/$slug/worksheets/ws_${slug}_bilingual.json"
        readBundledText(context, entry)?.let {
            return try { parseWorksheetJson(it) } catch (_: Exception) { emptyList() }
        }
        // 3. Bundled asset: scan any worksheets/*.json for this chapter.
        try {
            val packs = context.assets.list("packs")?.toList().orEmpty()
            val packFile = packs.firstOrNull { it.endsWith(".vachakpack") } ?: return null
            context.assets.open("packs/$packFile").use { raw ->
                java.util.zip.ZipInputStream(raw).use { zin ->
                    val prefix = "curriculum/class/$grade/chapters/$slug/worksheets/"
                    var fallback: String? = null
                    var e = zin.nextEntry
                    while (e != null) {
                        if (!e.isDirectory && e.name.startsWith(prefix) && e.name.endsWith(".json")) {
                            val text = zin.readBytes().toString(Charsets.UTF_8)
                            if (e.name.endsWith("ws_${slug}_bilingual.json")) return try {
                                parseWorksheetJson(text)
                            } catch (_: Exception) { emptyList() }
                            if (fallback == null && text.contains("\"questions\"")) fallback = text
                        }
                        zin.closeEntry()
                        e = zin.nextEntry
                    }
                    if (fallback != null) return try { parseWorksheetJson(fallback) } catch (_: Exception) { emptyList() }
                }
            }
        } catch (_: Exception) { }
        return null
    }

    /** Flashcard deck for a chapter (null = not on device anywhere). */
    suspend fun readDeck(context: Context, grade: Int, slug: String): List<PackCard>? =
        withContext(Dispatchers.IO) {
            readDeckBlocking(context, grade, slug)
        }

    fun readDeckBlocking(context: Context, grade: Int, slug: String): List<PackCard>? {
        // 1. Installed pack on device.
        try {
            val root = resolveRootBlocking(context)
            val dir = root?.let { chapterDirSync(it, grade, slug) }
            val f = dir?.let { deckFile(it) }
            if (f != null) return try { parseDeckJson(f.readText()) } catch (_: Exception) { emptyList() }
            if (dir != null) return emptyList()
        } catch (_: Exception) { }
        // 2. Bundled .vachakpack asset.
        val entry = "curriculum/class/$grade/chapters/$slug/flashcards/deck_bilingual.json"
        readBundledText(context, entry)?.let {
            return try { parseDeckJson(it) } catch (_: Exception) { emptyList() }
        }
        try {
            val packs = context.assets.list("packs")?.toList().orEmpty()
            val packFile = packs.firstOrNull { it.endsWith(".vachakpack") } ?: return null
            context.assets.open("packs/$packFile").use { raw ->
                java.util.zip.ZipInputStream(raw).use { zin ->
                    val prefix = "curriculum/class/$grade/chapters/$slug/flashcards/"
                    var fallback: String? = null
                    var e = zin.nextEntry
                    while (e != null) {
                        if (!e.isDirectory && e.name.startsWith(prefix) && e.name.endsWith(".json")) {
                            val text = zin.readBytes().toString(Charsets.UTF_8)
                            if (e.name.endsWith("deck_bilingual.json")) return try {
                                parseDeckJson(text)
                            } catch (_: Exception) { emptyList() }
                            if (fallback == null && text.contains("\"cards\"")) fallback = text
                        }
                        zin.closeEntry()
                        e = zin.nextEntry
                    }
                    if (fallback != null) return try { parseDeckJson(fallback) } catch (_: Exception) { emptyList() }
                }
            }
        } catch (_: Exception) { }
        return null
    }

    private fun curriculumDirBlocking(context: Context): File? = resolveRootBlocking(context)

    /**
     * Decodes a deck image_ref to a bitmap. Prebuilt flashcard/assets art
     * ships in the APK (AssetManager); installed-pack asset illustrations
     * live in the pack. Null when missing — never the path as text.
     */
    suspend fun decodeDeckImage(context: Context, grade: Int, imageRef: String?, maxDim: Int = 512): Bitmap? =
        withContext(Dispatchers.IO) {
            if (imageRef.isNullOrBlank()) return@withContext null
            try {
                val ref = imageRef.trimStart('/')
                if (ref.startsWith("flashcard/")) {
                    context.assets.open(ref).use { decodeStream(it, maxDim) }
                } else {
                    val root = curriculumDir(context)
                    // image_refs may be relative to class/{g}/ (assets/…) or to the
                    // curriculum root — try grade dir, then root, then bundled pack.
                    val candidates = listOfNotNull(
                        root?.let { File(File(it, "class/$grade"), ref) },
                        root?.let { File(it, ref) }
                    )
                    val f = candidates.firstOrNull { it.isFile }
                    if (f != null) decodeImage(f, maxDim)
                    else bundledImageBytes(context, ref)?.let { decodeBytes(it, maxDim) }
                }
            } catch (_: Exception) { null }
        }

    /** Stream a single image out of the bundled .vachakpack asset (pre-install
     *  path). Streams the zip sequentially and buffers ONLY the matching entry
     *  (capped), never the whole pack — the old readBytes() of the full pack
     *  OOMed 2GB devices when flipping deck cards. */
    private fun bundledImageBytes(context: Context, ref: String, maxBytes: Int = 4 * 1024 * 1024): ByteArray? {
        return try {
            val packs = context.assets.list("packs")?.toList().orEmpty()
            val packFile = packs.firstOrNull { it.endsWith(".vachakpack") } ?: return null
            context.assets.open("packs/$packFile").use { raw ->
                java.util.zip.ZipInputStream(raw).use { zin ->
                    var e = zin.nextEntry
                    while (e != null) {
                        if (!e.isDirectory && (e.name == ref || e.name.endsWith("/$ref") || e.name.endsWith(ref))) {
                            // Skip absurdly large entries before buffering.
                            val declared = e.compressedSize
                            if (declared > maxBytes * 2L && declared > 0) return null
                            val out = java.io.ByteArrayOutputStream(32 * 1024)
                            val buf = ByteArray(16 * 1024)
                            var total = 0
                            while (true) {
                                val n = zin.read(buf)
                                if (n <= 0) break
                                total += n
                                if (total > maxBytes) return null
                                out.write(buf, 0, n)
                            }
                            return out.toByteArray()
                        }
                        zin.closeEntry()
                        e = zin.nextEntry
                    }
                    null
                }
            }
        } catch (_: Exception) { null }
    }

    private fun decodeStream(ins: java.io.InputStream, maxDim: Int): Bitmap? {
        // Cap buffered image bytes so a corrupt/large asset can't OOM the reader.
        val bytes = ins.readBytes().take(4 * 1024 * 1024)
        if (bytes.isEmpty()) return null
        return decodeBytes(bytes.toByteArray(), maxDim)
    }

    private fun decodeBytes(bytes: ByteArray, maxDim: Int): Bitmap? {
        if (bytes.isEmpty()) return null
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
        var sample = 1
        while (maxOf(opts.outWidth, opts.outHeight) / sample > maxDim) sample *= 2
        return BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                // Illustrations only: halve native memory vs ARGB_8888.
                inPreferredConfig = Bitmap.Config.RGB_565
            }
        )
    }

    /** Full chapter payload: installed pack first, bundled .vachakpack asset
     *  as preview fallback (so Practice/worksheets open even before side-load).
     *  Bundled files are materialized to cacheDir preserving relative image
     *  paths, so callers stay File-based. Null only when nowhere on device. */
    suspend fun readChapter(context: Context, grade: Int, slug: String): InstalledChapter? =
        withContext(Dispatchers.IO) {
            try {
                val root = curriculumDir(context)
                val dir = root?.let { chapterDirSync(it, grade, slug) }
                if (dir != null) return@withContext parseChapterDir(root!!, dir, grade, slug)
            } catch (_: Exception) { }
            try {
                val cached = materializeBundledChapter(context, grade, slug) ?: return@withContext null
                parseChapterDir(cached.first, cached.second, grade, slug)
            } catch (_: Exception) { null }
        }

    private fun parseChapterDir(root: File, dir: File, grade: Int, slug: String): InstalledChapter? {
        return try {
            val ch = JSONObject(File(dir, "chapter.json").readText())
            val assigns = JSONObject(File(dir, "assignments.json").readText())
                .optJSONArray("items")?.let { arr ->
                    List(arr.length()) { i ->
                        val o = arr.getJSONObject(i)
                        Assign(
                            o.optString("type", "?"), o.optDouble("confidence", 0.0),
                            o.optString("text_sat_deva", ""), o.optInt("page", 0),
                            o.optString("text_hi", "")
                        )
                    }
                }.orEmpty()
            val imgs = ch.optJSONArray("image_refs")?.let { arr ->
                List(arr.length()) { i ->
                    arr.getJSONObject(i).optString("asset", "")
                }.mapNotNull { ref ->
                    if (ref.isBlank()) null
                    else File(File(root, "class/$grade"), ref).takeIf { it.isFile }
                }
            }.orEmpty()
            InstalledChapter(
                chapterId = ch.optString("chapter_id", slug),
                subject = ch.optString("subject", ""),
                status = ch.optString("status", "AUTO_EXTRACTED"),
                textEncoding = ch.optString("text_encoding", ""),
                assignments = assigns,
                images = imgs
            )
        } catch (_: Exception) { null }
    }

    /** Decode a pack image file to a bitmap (call off the main thread). */
    fun decodeImage(file: File, maxDim: Int = 768): Bitmap? = try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) null
        else {
            var sample = 1
            while (maxOf(opts.outWidth, opts.outHeight) / sample > maxDim) sample *= 2
            BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
            )
        }
    } catch (eUnused: Exception) { null }

    /** First meaningful line of a chapter's Santali (Deva) source text, for use
     *  as the Santali row title on grade pages. Real pack data only — null when
     *  the chapter isn't on device. Never translated, never invented. */
    suspend fun readChapterTitleDeva(context: Context, grade: Int, slug: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val root = curriculumDir(context)
                var dir = root?.let { chapterDirSync(it, grade, slug) }
                if (dir == null) {
                    val cached = materializeBundledChapter(context, grade, slug)
                    dir = cached?.second
                }
                val f = dir?.let { java.io.File(it, "chapter.json") }?.takeIf { it.isFile }
                    ?: return@withContext null
                val text = org.json.JSONObject(f.readText()).optString("text_sat_deva", "")
                firstContentLine(text)
            } catch (_: Exception) { null }
        }

    private fun firstContentLine(text: String): String? {
        // Skip control chars / artifacts and page-number-only lines.
        val line = text.lines()
            .map { it.replace(Regex("[\\p{C}]+"), "").trim() }
            .firstOrNull { it.length >= 4 && it.any { ch -> ch.isLetter() } }
        return line?.take(80)
    }

    /** Installed grades with chapter counts, for badges and the Packs screen. */
    suspend fun installedGrades(context: Context): Map<Int, Int> = withContext(Dispatchers.IO) {
        try {
            val root = curriculumDir(context) ?: return@withContext emptyMap()
            (1..5).mapNotNull { g ->
                File(File(root, "class/$g/chapters"), "").let { d ->
                    if (d.isDirectory) g to (d.listFiles()?.size ?: 0) else null
                }
            }.toMap()
        } catch (eUnused: Exception) { emptyMap() }
    }

    // ------------------------------------------------------------------
    // ChapterBundle: one guarded load for the whole chapter flow.
    // Every screen (chapter / worksheet / deck) renders from this.
    // Nothing here throws — failures are reported in `notices` so the UI
    // can show WHY something is missing instead of crashing or blanking.
    // ------------------------------------------------------------------

    enum class BundleSource { INSTALLED, BUNDLED, SUMMARY_ONLY }

    data class ChapterBundle(
        val grade: Int,
        val slug: String,
        val source: BundleSource,
        val assignments: List<Assign>,
        val questions: List<PackQuestion>,
        val cards: List<PackCard>,
        val imageFiles: List<File>,
        /** Santali (Deva) opening line, Hindi opening line (often absent). */
        val titleSatDeva: String?,
        val titleHi: String?,
        /** Plain-language reasons, e.g. "Worksheet file missing in pack". */
        val notices: List<String>
    )

    /** Load everything for a chapter in one pass. Null only when the chapter
     *  is unknown in every source (bad slug/grade). */
    suspend fun loadChapterBundle(
        context: Context,
        grade: Int,
        slug: String,
        meta: com.vachak.ui.navigation.PackChapter?
    ): ChapterBundle? = withContext(Dispatchers.IO) {
        try {
            val notices = mutableListOf<String>()
            // 1. Installed side-loaded pack (disk reads, no zip).
            try {
                val root = resolveRootBlocking(context)
                val dir = root?.let { chapterDirSync(it, grade, slug) }
                if (dir != null) {
                    return@withContext bundleFromDir(grade, slug, dir, root!!, BundleSource.INSTALLED, notices)
                }
            } catch (t: Throwable) {
                notices += "Installed pack unreadable (${t.javaClass.simpleName}) — trying bundled copy."
            }
            // 2. Bundled .vachakpack asset: single zip pass for all 4 files.
            try {
                val bundled = readBundledChapterFiles(context, grade, slug, notices)
                if (bundled != null) {
                    val (chapterText, assignsText, wsText, deckText, imgRefs) = bundled
                    val assignments = parseAssignsText(assignsText, notices)
                    val questions = wsText?.let { parseWorksheetJsonSafe(it, notices) }.orEmpty()
                    val cards = deckText?.let { parseDeckJsonSafe(it, notices) }.orEmpty()
                    // Materialize images for the gallery (bounded, cached).
                    val cached = materializeBundledChapter(context, grade, slug)
                    val images = cached?.let { (root, _) ->
                        imgRefs.mapNotNull { ref ->
                            runCatching {
                                File(File(root, "class/$grade"), ref).takeIf { it.isFile }
                            }.getOrNull()
                        }
                    }.orEmpty()
                    if (images.isEmpty() && imgRefs.isNotEmpty()) {
                        notices += "${imgRefs.size} illustrations listed but not unpacked yet."
                    }
                    val (sat, hi) = titleLines(chapterText)
                    return@withContext ChapterBundle(
                        grade, slug, BundleSource.BUNDLED,
                        assignments, questions, cards, images, sat, hi, notices
                    )
                } else {
                    notices += "Chapter files not found in the bundled pack."
                }
            } catch (t: Throwable) {
                notices += "Bundled pack unreadable (${t.javaClass.simpleName})."
            }
            // 3. Summary-only: chapter known from pack_summary, no content files.
            if (meta != null) {
                notices += "Full text unlocks when the content pack is installed."
                return@withContext ChapterBundle(
                    grade, slug, BundleSource.SUMMARY_ONLY,
                    emptyList(), emptyList(), emptyList(), emptyList(),
                    null, null, notices
                )
            }
            null
        } catch (t: Throwable) {
            android.util.Log.e("Vachak-Pack", "loadChapterBundle($grade/$slug) failed", t)
            null
        }
    }

    private fun bundleFromDir(
        grade: Int,
        slug: String,
        dir: File,
        root: File,
        source: BundleSource,
        notices: MutableList<String>
    ): ChapterBundle {
        var assignments: List<Assign> = emptyList()
        var questions: List<PackQuestion> = emptyList()
        var cards: List<PackCard> = emptyList()
        var images: List<File> = emptyList()
        var sat: String? = null
        var hi: String? = null
        val chapterFile = File(dir, "chapter.json")
        if (chapterFile.isFile) {
            runCatching {
                val ch = JSONObject(chapterFile.readText())
                val (s, h) = titleLines(ch.optString("text_sat_deva", ""), ch.optString("text_hi", ""))
                sat = s
                hi = h
                images = ch.optJSONArray("image_refs")?.let { arr ->
                    List(arr.length()) { i ->
                        arr.getJSONObject(i).optString("asset", "")
                    }.mapNotNull { ref ->
                        if (ref.isBlank()) null
                        else File(File(root, "class/$grade"), ref).takeIf { it.isFile }
                    }
                }.orEmpty()
            }.onFailure { notices += "chapter.json is corrupt — details hidden." }
        } else {
            notices += "chapter.json missing in installed pack."
        }
        val assignsFile = File(dir, "assignments.json")
        if (assignsFile.isFile) {
            assignments = runCatching { parseAssignsText(assignsFile.readText(), notices) }.getOrDefault(emptyList())
        } else {
            notices += "No activities file in installed pack."
        }
        val wsFile = worksheetFile(dir, slug)
        if (wsFile != null) {
            questions = runCatching { parseWorksheetJsonSafe(wsFile.readText(), notices) }.getOrDefault(emptyList())
        } else {
            notices += "No worksheet file for this chapter in the installed pack."
        }
        val deck = deckFile(dir)
        if (deck != null) {
            cards = runCatching { parseDeckJsonSafe(deck.readText(), notices) }.getOrDefault(emptyList())
        } else {
            notices += "No flashcard deck for this chapter in the installed pack."
        }
        return ChapterBundle(grade, slug, source, assignments, questions, cards, images, sat, hi, notices)
    }

    private data class BundledFiles(
        val chapter: String?,
        val assigns: String?,
        val worksheet: String?,
        val deck: String?,
        val imageRefs: List<String>
    )

    /** Single sequential scan of the bundled pack for one chapter's 4 files. */
    private fun readBundledChapterFiles(
        context: Context,
        grade: Int,
        slug: String,
        notices: MutableList<String>
    ): BundledFiles? {
        val packs = runCatching { context.assets.list("packs")?.toList().orEmpty() }.getOrDefault(emptyList())
        if (packs.none { it.endsWith(".vachakpack") }) {
            notices += "No .vachakpack found in app assets."
            return null
        }
        val packFile = packs.first { it.endsWith(".vachakpack") }
        val prefix = "curriculum/class/$grade/chapters/$slug/"
        var chapter: String? = null
        var assigns: String? = null
        var worksheet: String? = null
        var deck: String? = null
        context.assets.open("packs/$packFile").use { raw ->
            java.util.zip.ZipInputStream(raw).use { zin ->
                var e = zin.nextEntry
                while (e != null) {
                    if (!e.isDirectory && e.name.startsWith(prefix)) {
                        val tail = e.name.removePrefix(prefix)
                        // Cap per-entry buffering at 2MB (json files are KBs).
                        val bytes = readEntryCapped(zin, 2 * 1024 * 1024) ?: break
                        when {
                            tail == "chapter.json" -> chapter = bytes.toString(Charsets.UTF_8)
                            tail == "assignments.json" -> assigns = bytes.toString(Charsets.UTF_8)
                            tail.startsWith("worksheets/") && tail.endsWith(".json") && worksheet == null ->
                                worksheet = bytes.toString(Charsets.UTF_8)
                            tail.startsWith("flashcards/") && tail.endsWith(".json") && deck == null ->
                                deck = bytes.toString(Charsets.UTF_8)
                        }
                        if (chapter != null && assigns != null && worksheet != null && deck != null) break
                    }
                    zin.closeEntry()
                    e = zin.nextEntry
                }
            }
        }
        if (chapter == null && assigns == null && worksheet == null && deck == null) return null
        if (worksheet == null) notices += "No worksheet file for this chapter in the bundled pack."
        if (deck == null) notices += "No flashcard deck for this chapter in the bundled pack."
        if (assigns == null) notices += "No activities file for this chapter in the bundled pack."
        val refs = runCatching {
            chapter?.let { org.json.JSONObject(it).optJSONArray("image_refs") }?.let { arr ->
                List(arr.length()) { i -> arr.getJSONObject(i).optString("asset", "") }
                    .filter { it.isNotBlank() }.take(12)
            }.orEmpty()
        }.getOrDefault(emptyList())
        return BundledFiles(chapter, assigns, worksheet, deck, refs)
    }

    private fun readEntryCapped(
        zin: java.util.zip.ZipInputStream,
        maxBytes: Int
    ): ByteArray? {
        val out = java.io.ByteArrayOutputStream(16 * 1024)
        val buf = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = zin.read(buf)
            if (n <= 0) break
            total += n
            if (total > maxBytes) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun parseAssignsText(text: String?, notices: MutableList<String>): List<Assign> {
        if (text.isNullOrBlank()) return emptyList()
        return runCatching {
            JSONObject(text).optJSONArray("items")?.let { arr ->
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    Assign(
                        o.optString("type", "?"), o.optDouble("confidence", 0.0),
                        o.optString("text_sat_deva", ""), o.optInt("page", 0),
                        o.optString("text_hi", "")
                    )
                }
            }.orEmpty()
        }.getOrElse {
            notices += "Activities file is corrupt — hidden."
            emptyList()
        }
    }

    private fun parseWorksheetJsonSafe(text: String, notices: MutableList<String>): List<PackQuestion> {
        return runCatching { parseWorksheetJson(text) }.getOrElse {
            notices += "Worksheet file is corrupt — hidden."
            emptyList()
        }.also {
            if (it.isEmpty()) notices += "Worksheet file has 0 readable questions."
        }
    }

    private fun parseDeckJsonSafe(text: String, notices: MutableList<String>): List<PackCard> {
        return runCatching { parseDeckJson(text) }.getOrElse {
            notices += "Flashcard file is corrupt — hidden."
            emptyList()
        }.also {
            if (it.isEmpty()) notices += "Flashcard file has 0 readable cards."
        }
    }

    private fun titleLines(satDeva: String?, hi: String? = null): Pair<String?, String?> =
        firstContentLine(satDeva.orEmpty()) to firstContentLine(hi.orEmpty())

    /** Title lines (Santali Deva, Hindi) for every chapter of a grade in ONE
     *  bundled-pack scan. Installed pack first (disk), bundled second.
     *  Missing lines are simply absent — callers fall back to the pack title. */
    suspend fun readGradeTitleLines(
        context: Context,
        grade: Int,
        slugs: List<String>
    ): Map<String, Pair<String?, String?>> = withContext(Dispatchers.IO) {
        // Cooperative-cancellation handle captured in suspend position (the
        // zip scan below runs inside nested non-suspend `use` lambdas where
        // the coroutineContext intrinsic is not directly visible).
        val ctxJob = coroutineContext[Job]
        try {
            val out = mutableMapOf<String, Pair<String?, String?>>()
            // Installed pack: direct small file reads.
            try {
                val root = resolveRootBlocking(context)
                if (root != null) {
                    slugs.forEach { slug ->
                        runCatching {
                            val f = File(File(File(root, "class/$grade/chapters"), slug), "chapter.json")
                            if (f.isFile) {
                                val ch = JSONObject(f.readText())
                                out[slug] = titleLines(ch.optString("text_sat_deva", ""), ch.optString("text_hi", ""))
                            }
                        }
                    }
                    if (out.size == slugs.size) return@withContext out
                }
            } catch (_: Throwable) { }
            // Bundled pack: one scan for the missing slugs only.
            val missing = slugs.filter { it !in out }.toSet()
            if (missing.isEmpty()) return@withContext out
            val packs = runCatching { context.assets.list("packs")?.toList().orEmpty() }.getOrDefault(emptyList())
            val packFile = packs.firstOrNull { it.endsWith(".vachakpack") } ?: return@withContext out
            context.assets.open("packs/$packFile").use { raw ->
                java.util.zip.ZipInputStream(raw).use { zin ->
                    var e = zin.nextEntry
                    while (e != null && out.size < slugs.size) {
                        if (ctxJob?.isActive == false) throw java.util.concurrent.CancellationException()
                        if (!e.isDirectory) {
                            val hit = missing.firstOrNull { slug ->
                                e.name == "curriculum/class/$grade/chapters/$slug/chapter.json"
                            }
                            if (hit != null && hit !in out) {
                                val bytes = readEntryCapped(zin, 2 * 1024 * 1024)
                                runCatching {
                                    val ch = JSONObject(bytes?.toString(Charsets.UTF_8).orEmpty())
                                    out[hit] = titleLines(ch.optString("text_sat_deva", ""), ch.optString("text_hi", ""))
                                }
                            }
                        }
                        zin.closeEntry()
                        e = zin.nextEntry
                    }
                }
            }
            out
        } catch (t: Throwable) {
            android.util.Log.e("Vachak-Pack", "readGradeTitleLines($grade) failed", t)
            emptyMap()
        }
    }

    /** Bounded gallery decode for a bundle (max 4, RGB_565, cancellable). */
    suspend fun decodeBundleImages(
        files: List<File>,
        max: Int = 4,
        maxDim: Int = 512
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val ctxJob = coroutineContext[Job]
        files.take(max).mapNotNull {
            if (ctxJob?.isActive == false) throw java.util.concurrent.CancellationException()
            runCatching { decodeImage(it, maxDim) }.getOrNull()
        }
    }
}
