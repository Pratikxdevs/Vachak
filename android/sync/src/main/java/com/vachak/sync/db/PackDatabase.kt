package com.vachak.sync.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PackEntity::class], version = 1, exportSchema = false)
abstract class PackDatabase : RoomDatabase() {
    abstract fun packDao(): PackDao

    companion object {
        @Volatile private var INSTANCE: PackDatabase? = null

        fun getInstance(context: Context): PackDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }

        private fun buildDatabase(context: Context): PackDatabase {
            return Room.databaseBuilder(context, PackDatabase::class.java, "vachak_packs.db")
                .fallbackToDestructiveMigration()
                // allowMainThreadQueries kept for debug/instrumented tests only (small ≤10 row pack table, indexed).
                // Production path MUST use suspend wrappers via Dispatchers.IO (PackDao.getActiveIO/getAllIO) to avoid ANR.
                // PackManager.getActivePack() delegates via runBlocking(Dispatchers.IO) + suspend DAO; direct getActiveSync() on UI thread is debug-only.
                .allowMainThreadQueries()
                .build()
        }

        /** For tests — allowMainThreadQueries required for instrumented test single-thread */
        fun inMemoryForTest(context: Context): PackDatabase {
            return Room.inMemoryDatabaseBuilder(context, PackDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        }
    }
}
