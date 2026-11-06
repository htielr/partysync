package com.karthick.partysync.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FolderMappingEntity::class,
        SyncFileStateEntity::class,
        Up2kSessionEntity::class,
        SyncHistoryEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun folderMappingDao(): FolderMappingDao
    abstract fun syncFileStateDao(): SyncFileStateDao
    abstract fun up2kSessionDao(): Up2kSessionDao
    abstract fun syncHistoryDao(): SyncHistoryDao

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

        /**
         * Adds the per-mapping [FolderMappingEntity.deleteAfterSync] flag. Additive column with a
         * SQL default matching the entity's `@ColumnInfo(defaultValue = "0")`, so existing
         * mappings carry the flag disabled and Room's schema validation passes.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `folder_mappings` ADD COLUMN `deleteAfterSync` INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /**
         * Adds the [SyncHistoryEntity] table (the per-mapping, user-clearable sync history shown
         * in the mapping detail screen). Purely additive (new table + its index); the DDL below
         * must match Room's generated schema for v5 exactly, or the runtime identity check trips
         * the destructive fallback.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sync_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `mappingId` INTEGER NOT NULL,
                        `relativePath` TEXT NOT NULL,
                        `remotePath` TEXT NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `offloaded` INTEGER NOT NULL,
                        `syncedAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_sync_history_mappingId_syncedAt` " +
                        "ON `sync_history` (`mappingId`, `syncedAt`)",
                )
            }
        }

        /**
         * Reconciles the short-lived 0.8.0 test build, whose v5 created a differently-shaped
         * `offload_log` table instead of `sync_history`. Because both tables are disposable UI
         * history (not operational state), we simply drop the old one and ensure the new one
         * exists — `folder_mappings` and `sync_file_states` are left completely untouched, so a
         * device that ran 0.8.0 keeps all its mappings and sync state. Devices that never saw
         * 0.8.0 hit only harmless IF-EXISTS / IF-NOT-EXISTS no-ops here.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `offload_log`")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sync_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `mappingId` INTEGER NOT NULL,
                        `relativePath` TEXT NOT NULL,
                        `remotePath` TEXT NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `offloaded` INTEGER NOT NULL,
                        `syncedAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_sync_history_mappingId_syncedAt` " +
                        "ON `sync_history` (`mappingId`, `syncedAt`)",
                )
            }
        }
    }
}
