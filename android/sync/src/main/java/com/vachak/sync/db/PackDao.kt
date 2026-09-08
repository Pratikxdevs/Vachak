package com.vachak.sync.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Dao
interface PackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pack: PackEntity)

    @Query("SELECT * FROM packs ORDER BY installedAt DESC")
    suspend fun getAll(): List<PackEntity>

    @Query("SELECT * FROM packs WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): PackEntity?

    @Query("SELECT * FROM packs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PackEntity?

    @Query("DELETE FROM packs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE packs SET isActive = 0")
    suspend fun clearActive()

    @Query("UPDATE packs SET isActive = 1 WHERE id = :id")
    suspend fun setActive(id: String)

    @Query("SELECT COUNT(*) FROM packs")
    suspend fun count(): Int

    // Synchronous helpers for adapter cold path (allowMainThreadQueries in DB builder)
    // DEBUG ONLY: these run on calling thread via allowMainThreadQueries. Production must use suspend wrappers below via Dispatchers.IO to avoid ANR.
    @Query("SELECT * FROM packs WHERE isActive = 1 LIMIT 1")
    fun getActiveSync(): PackEntity?

    @Query("SELECT * FROM packs ORDER BY installedAt DESC")
    fun getAllSync(): List<PackEntity>

    // Suspend wrappers that guarantee Dispatchers.IO — use these from ViewModel/coroutine to avoid ANR, even though allowMainThreadQueries is enabled for debug.
    suspend fun getActiveIO(): PackEntity? = withContext(Dispatchers.IO) { getActive() }
    suspend fun getAllIO(): List<PackEntity> = withContext(Dispatchers.IO) { getAll() }
    suspend fun getActiveSyncIO(): PackEntity? = withContext(Dispatchers.IO) { getActiveSync() }
    suspend fun getAllSyncIO(): List<PackEntity> = withContext(Dispatchers.IO) { getAllSync() }
    suspend fun countIO(): Int = withContext(Dispatchers.IO) { count() }
}
