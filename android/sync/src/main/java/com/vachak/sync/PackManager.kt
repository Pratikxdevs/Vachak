package com.vachak.sync

import android.content.Context
import android.content.SharedPreferences
import com.vachak.engine.EngineResult
import com.vachak.engine.PackInfo
import com.vachak.sync.db.PackDatabase
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * PackManager — manages installed offline packs, active selection.
 * Sync/ is installer not client — no HttpURLConnection, no INTERNET.
 * Pack path is filesDir/packs/<id>/ (contains vachak_models and manifest.json)
 * Adapters call [getActivePack] to resolve baseDir, falling back to SherpaAssets.
 */
object PackManager {
    private const val PREFS = "vachak_packs"
    private const val KEY_ACTIVE = "active_pack_id"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Suspend wrapper — preferred: runs PackDao via Dispatchers.IO to avoid ANR even though allowMainThreadQueries is enabled for debug. */
    suspend fun getActivePackIO(context: Context): String? = withContext(Dispatchers.IO) {
        try {
            val db = PackDatabase.getInstance(context)
            val active = db.packDao().getActiveIO() ?: db.packDao().getAllIO().firstOrNull()
            active?.path?.let { p -> if (File(p).isDirectory) p else null }
                ?: prefs(context).getString(KEY_ACTIVE, null)?.let { id ->
                    db.packDao().getById(id)?.path
                }
        } catch (_: Exception) {
            prefs(context).getString(KEY_ACTIVE, null)?.let { id ->
                try { PackDatabase.getInstance(context).packDao().getById(id)?.path } catch (_: Exception) { null }
            }
        }
    }

    /** Returns active pack base dir on filesystem, or null if none (fallback to bundled assets).
     *  Delegates via Dispatchers.IO suspend wrappers to avoid ANR (allowMainThreadQueries kept for debug only). */
    fun getActivePack(context: Context): String? = runBlocking(Dispatchers.IO) { getActivePackIO(context) }

    suspend fun getActivePackForIO(context: Context, subdir: String): String? = withContext(Dispatchers.IO) {
        val base = getActivePackIO(context) ?: return@withContext null
        val candidate = File(base, "vachak_models/$subdir")
        if (candidate.isDirectory && candidate.listFiles()?.isNotEmpty() == true) return@withContext candidate.absolutePath
        val direct = File(base, subdir)
        if (direct.isDirectory) return@withContext direct.absolutePath
        if (File(base).isDirectory) base else null
    }

    /** Returns active pack dir for a specific subdir (e.g. "mt", "tts", "asr") or null.
     *  Sync wrapper delegates via Dispatchers.IO — use getActivePackForIO from coroutine for ANR safety. */
    fun getActivePackFor(context: Context, subdir: String): String? = runBlocking(Dispatchers.IO) { getActivePackForIO(context, subdir) }

    suspend fun setActivePackIO(context: Context, packId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = PackDatabase.getInstance(context)
            db.packDao().clearActive()
            db.packDao().setActive(packId)
            prefs(context).edit().putString(KEY_ACTIVE, packId).apply()
            true
        } catch (_: Exception) { false }
    }

    fun setActivePack(context: Context, packId: String): Boolean = runBlocking(Dispatchers.IO) { setActivePackIO(context, packId) }

    suspend fun installedIO(context: Context): List<PackInfo> = withContext(Dispatchers.IO) {
        try {
            PackDatabase.getInstance(context).packDao().getAllIO().map { e ->
                PackInfo(id = e.id, language = e.language, version = e.version, minAndroid = 28, sizeBytes = e.sizeBytes)
            }
        } catch (_: Exception) {
            val packsDir = File(context.filesDir, "packs")
            packsDir.listFiles()?.mapNotNull { f ->
                if (!f.isDirectory) null else PackInfo(f.name, "sat_Olck", f.name.substringAfterLast("-v", "0.1.0"), 28, 0L)
            } ?: emptyList()
        }
    }

    fun installed(context: Context): List<PackInfo> = runBlocking(Dispatchers.IO) { installedIO(context) }

    suspend fun packEntitiesIO(context: Context): List<com.vachak.sync.db.PackEntity> = withContext(Dispatchers.IO) {
        try { PackDatabase.getInstance(context).packDao().getAllIO() } catch (_: Exception) { emptyList() }
    }

    // Sync wrapper kept for debug/instrumented cold path — delegates via Dispatchers.IO to avoid ANR.
    fun packEntities(context: Context): List<com.vachak.sync.db.PackEntity> = runBlocking(Dispatchers.IO) { packEntitiesIO(context) }

    fun freeSpaceBytes(context: Context): Long {
        return try { context.filesDir.freeSpace } catch (_: Exception) { -1L }
    }

    fun storageUsedBytes(context: Context): Long {
        return try {
            val packsDir = File(context.filesDir, "packs")
            packsDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } catch (_: Exception) { 0L }
    }
}
