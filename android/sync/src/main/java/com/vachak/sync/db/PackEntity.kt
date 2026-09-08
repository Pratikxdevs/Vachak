package com.vachak.sync.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for offline language packs installed via [com.vachak.sync.PackInstaller].
 * No network — packs are side-loaded via Storage Access Framework.
 */
@Entity(tableName = "packs")
data class PackEntity(
    @PrimaryKey val id: String, // e.g. sat_Olck-v0.1.0
    val language: String, // sat_Olck
    val version: String, // 0.1.0
    val path: String, // filesDir/packs/sat_Olck-v0.1.0
    val sizeBytes: Long,
    val installedAt: Long,
    val manifestSha256: String,
    val isActive: Boolean = false
)
