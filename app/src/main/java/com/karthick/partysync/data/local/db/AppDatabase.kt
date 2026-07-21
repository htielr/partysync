package com.karthick.partysync.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FolderMappingEntity::class, SyncFileStateEntity::class, Up2kSessionEntity::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun folderMappingDao(): FolderMappingDao
    abstract fun syncFileStateDao(): SyncFileStateDao
    abstract fun up2kSessionDao(): Up2kSessionDao

    companion object {
        const val DATABASE_NAME = "partysync.db"

        /** Purely additive (new table only) — no destructive fallback needed for this jump. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `up2k_sessions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sourceUri` TEXT NOT NULL,
                        `serverId` INTEGER NOT NULL,
                        `remoteDirPath` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `fileSize` INTEGER NOT NULL,
                        `fileLastModified` INTEGER NOT NULL,
                        `chunkHashesJson` TEXT,
                        `wark` TEXT,
                        `status` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
