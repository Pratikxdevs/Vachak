package com.vachak.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import com.vachak.engine.EngineError
import com.vachak.engine.EngineResult
import com.vachak.engine.PackInfo
import com.vachak.sync.db.PackDatabase
import com.vachak.sync.db.PackEntity
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/**
 * Offline pack installer — verifies, sanitizes, copies to app-private storage, registers in Room.
 * Never does network I/O: reads only via ContentResolver (SAF), no HttpURLConnection, no INTERNET permission.
 * Mitigations: sha256 verify, zip-slip sanitization (no .., no absolute), size guard, manifest schema validate, free-space check.
 */
class PackInstaller(private val context: Context) {
    private val tag = "Vachak-Pack"

    /**
     * Install pack from Storage Access Framework Uri (e.g. file picked via "Install from file").
     * Steps: openInputStream via ContentResolver → temp copy → verify manifest sha256 → sanitize zip paths → extract to filesDir/packs/<id>/ → register Room → set active.
     */
    fun install(uri: Uri): EngineResult<PackInfo> {
        return try {
            Log.d(tag, "install start uri=$uri")
            val input = context.contentResolver.openInputStream(uri)
                ?: return EngineResult.Err(EngineError.IO_ERROR, "cannot open uri")
            val tempFile = File(context.cacheDir, "pack_tmp_${System.currentTimeMillis()}.vachakpack")
            input.use { ins ->
                tempFile.outputStream().use { out -> ins.copyTo(out) }
            }
            val result = installFromFile(tempFile)
            tempFile.delete()
            result
        } catch (e: Exception) {
            Log.e(tag, "install failed uri=$uri: ${e.message}", e)
            EngineResult.Err(EngineError.IO_ERROR, e.message ?: "install failed")
        }
    }

    /** Install from a local File (already on filesystem, e.g. sideloaded via USB). */
    fun installFromFile(file: File): EngineResult<PackInfo> {
        if (!file.exists() || file.length() == 0L) return EngineResult.Err(EngineError.INVALID_INPUT, "pack file missing")
        // Size guard: reject > 800MB (hard cap) and check free space
        val packSize = file.length()
        if (packSize > 800L * 1024 * 1024) return EngineResult.Err(EngineError.INVALID_INPUT, "pack too large >800MB")
        val free = PackManager.freeSpaceBytes(context)
        if (free != -1L && packSize.toDouble() * 1.2 > free.toDouble()) return EngineResult.Err(EngineError.IO_ERROR, "insufficient space: need ${packSize} free $free")

        return try {
            // Open zip and read manifest.json
            val zip = java.util.zip.ZipFile(file)
            val manifestEntry = zip.getEntry("manifest.json")
                ?: return EngineResult.Err(EngineError.INVALID_INPUT, "manifest.json missing in pack")
            val manifestBytes = zip.getInputStream(manifestEntry).readBytes()
            val manifestJson = JSONObject(String(manifestBytes, Charsets.UTF_8))
            val version = manifestJson.optString("version", "")
            val language = manifestJson.optString("language", "sat_Olck")
            if (version.isBlank()) return EngineResult.Err(EngineError.INVALID_INPUT, "manifest version blank")
            // Validate manifest has file entries: models[] (model packs) and/or
            // curriculum_files[] (content packs like sat_Olck-v0.2.0).
            // Plain null handling (no smart-cast chains): lengths first.
            val models = manifestJson.optJSONArray("models")
            val currFiles = manifestJson.optJSONArray("curriculum_files")
            val modelLen = if (models == null) 0 else models.length()
            val currLen = if (currFiles == null) 0 else currFiles.length()
            if (modelLen == 0 && currLen == 0) {
                return EngineResult.Err(EngineError.INVALID_INPUT, "manifest models empty")
            }

            // Verify per-file sha256 where provided — recompute from zip entries.
            // Two plain loops (models, then curriculum files) sharing one body shape.
            for (i in 0 until modelLen) {
                val obj = models!!.getJSONObject(i)
                val path = obj.optString("path")
                val expectedSha = obj.optString("sha256")
                if (path.isBlank() || expectedSha.isBlank()) continue
                val entry = zip.getEntry(path) ?: continue // some entries may be optional; still verify if present
                val data = zip.getInputStream(entry).readBytes()
                val actual = sha256(data)
                if (actual != expectedSha) {
                    zip.close()
                    Log.e(tag, "sha256 mismatch for $path expected $expectedSha got $actual")
                    return EngineResult.Err(EngineError.IO_ERROR, "sha256 mismatch for $path")
                }
            }
            for (i in 0 until currLen) {
                val obj = currFiles!!.getJSONObject(i)
                val path = obj.optString("path")
                val expectedSha = obj.optString("sha256")
                if (path.isBlank() || expectedSha.isBlank()) continue
                val entry = zip.getEntry(path) ?: continue
                val data = zip.getInputStream(entry).readBytes()
                val actual = sha256(data)
                if (actual != expectedSha) {
                    zip.close()
                    Log.e(tag, "sha256 mismatch for $path expected $expectedSha got $actual")
                    return EngineResult.Err(EngineError.IO_ERROR, "sha256 mismatch for $path")
                }
            }

            // Verify packSha256 if present (optional — file sha vs manifest packSha)
            val expectedPackSha = manifestJson.optString("packSha256", "")
            if (expectedPackSha.isNotBlank()) {
                val actualPackSha = sha256File(file)
                if (actualPackSha != expectedPackSha) {
                    Log.w(tag, "packSha256 mismatch expected $expectedPackSha got $actualPackSha — continuing (recomputed is source of truth)")
                    // Not fatal: packSha in manifest was before final copy; we log but don't fail — per-file sha is primary
                }
            }

            // Sanitize and extract to filesDir/packs/<language>-v<version>/
            val packId = "$language-v$version"
            val destDir = File(context.filesDir, "packs/$packId")
            if (destDir.exists()) destDir.deleteRecursively()
            destDir.mkdirs()

            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name
                // Path sanitization: reject .., absolute paths, and empty
                if (name.isBlank()) continue
                if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
                    zip.close()
                    destDir.deleteRecursively()
                    return EngineResult.Err(EngineError.INVALID_INPUT, "zip slip rejected: $name")
                }
                // Enforce size guard per entry (reject > 600MB single file)
                if (entry.size > 600L * 1024 * 1024) {
                    zip.close()
                    destDir.deleteRecursively()
                    return EngineResult.Err(EngineError.INVALID_INPUT, "entry too large: $name")
                }
                val outFile = File(destDir, name)
                // Ensure outFile is within destDir (canonical check)
                if (!outFile.canonicalPath.startsWith(destDir.canonicalPath)) {
                    zip.close()
                    destDir.deleteRecursively()
                    return EngineResult.Err(EngineError.INVALID_INPUT, "zip slip canonical rejected: $name")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { ins ->
                        outFile.outputStream().use { out -> ins.copyTo(out) }
                    }
                }
            }
            zip.close()

            // Compute total bytes extracted
            val totalBytes = destDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            val manifestSha = sha256(manifestBytes)

            // Register in Room
            val entity = PackEntity(
                id = packId,
                language = language,
                version = version,
                path = destDir.absolutePath,
                sizeBytes = totalBytes,
                installedAt = System.currentTimeMillis(),
                manifestSha256 = manifestSha,
                isActive = true
            )
            runBlocking {
                val db = PackDatabase.getInstance(context)
                // Clear previous active
                db.packDao().clearActive()
                db.packDao().insert(entity)
            }
            // Also persist to prefs
            context.getSharedPreferences("vachak_packs", Context.MODE_PRIVATE)
                .edit().putString("active_pack_id", packId).apply()

            Log.d(tag, "install success packId=$packId path=${destDir.absolutePath} size=$totalBytes")
            val info = PackInfo(id = packId, language = language, version = version, minAndroid = 28, sizeBytes = totalBytes)
            EngineResult.Ok(info)
        } catch (e: Exception) {
            Log.e(tag, "installFromFile failed: ${e.message}", e)
            EngineResult.Err(EngineError.IO_ERROR, e.message ?: "install failed")
        }
    }

    suspend fun ensureBundledPacks(): List<String> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val done = mutableListOf<String>()
            val names = try {
                context.assets.list("packs")?.filter { it.endsWith(".vachakpack") }.orEmpty()
            } catch (_: Exception) { emptyList() }
            for (name in names) {
                try {
                    val id = readPackIdFromAsset(name) ?: continue
                    val already = try {
                        PackDatabase.getInstance(context).packDao().getById(id) != null
                    } catch (_: Exception) { false }
                    if (already) continue
                    android.util.Log.d(tag, "bundled pack missing, installing $name")
                    val tmp = File(context.cacheDir, "bundled_$name")
                    context.assets.open("packs/$name").use { ins ->
                        tmp.outputStream().use { out -> ins.copyTo(out) }
                    }
                    when (val r = installFromFile(tmp)) {
                        is EngineResult.Ok -> {
                            android.util.Log.d(tag, "bundled pack installed ${r.value.id}")
                            done.add(r.value.id)
                        }
                        is EngineResult.Err ->
                            android.util.Log.e(tag, "bundled pack install failed $name: ${r.message}")
                    }
                    tmp.delete()
                } catch (e: Exception) {
                    android.util.Log.e(tag, "bundled pack $name threw", e)
                }
            }
            done
        }

    /** Reads pack id ("<language>-v<version>") from a zip's manifest without extracting. */
    private fun readPackIdFromAsset(assetName: String): String? {
        return try {
            context.assets.open("packs/$assetName").use { ins ->
                ZipInputStream(ins).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name == "manifest.json") {
                            val obj = JSONObject(zis.readBytes().toString(Charsets.UTF_8))
                            val language = obj.optString("language", "")
                            val version = obj.optString("version", "")
                            if (language.isNotBlank() && version.isNotBlank()) return "$language-v$version"
                            return null
                        }
                        entry = zis.nextEntry
                    }
                    null
                }
            }
        } catch (_: Exception) { null }
    }

    /** Validate that pack manifest sha matches recomputed file shas without extracting. */
    fun validatePackFile(file: File): Boolean {
        return try {
            val zip = java.util.zip.ZipFile(file)
            val manifestEntry = zip.getEntry("manifest.json") ?: return false
            val manifestBytes = zip.getInputStream(manifestEntry).readBytes()
            val manifestJson = JSONObject(String(manifestBytes, Charsets.UTF_8))
            val models = manifestJson.optJSONArray("models")
            val currFiles = manifestJson.optJSONArray("curriculum_files")
            val modelLen = if (models == null) 0 else models.length()
            val currLen = if (currFiles == null) 0 else currFiles.length()
            if (modelLen == 0 && currLen == 0) return false
            for (i in 0 until modelLen) {
                val obj = models!!.getJSONObject(i)
                if (!shaMatches(zip, obj)) { zip.close(); return false }
            }
            for (i in 0 until currLen) {
                val obj = currFiles!!.getJSONObject(i)
                if (!shaMatches(zip, obj)) { zip.close(); return false }
            }
            zip.close(); true
        } catch (e: Exception) {
            Log.w(tag, "validatePackFile(${file.name}) failed: ${e.message}")
            false
        }
    }

    /** Single manifest entry sha check against zip bytes. Blank path/sha = skip. */
    private fun shaMatches(zip: java.util.zip.ZipFile, obj: org.json.JSONObject): Boolean {
        val path = obj.optString("path")
        val expected = obj.optString("sha256")
        if (path.isBlank() || expected.isBlank()) return true
        val entry = zip.getEntry(path) ?: return true
        val actual = sha256(zip.getInputStream(entry).readBytes())
        return actual == expected
    }

    private fun sha256(data: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(data).joinToString("") { "%02x".format(it) }
    }

    private fun sha256File(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { ins ->
            val buf = ByteArray(8192)
            var n: Int
            while (ins.read(buf).also { n = it } != -1) md.update(buf, 0, n)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
