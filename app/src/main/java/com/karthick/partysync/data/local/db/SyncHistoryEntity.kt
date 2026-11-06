package com.karthick.partysync.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One entry in a mapping's human-facing **sync history** — a record that a file was successfully
 * transferred. [offloaded] marks the entries whose local copy was then deleted from the phone
 * (see [FolderMappingEntity.deleteAfterSync]); the rest were uploaded and kept.
 *
 * This is display-only and deliberately separate from [SyncFileStateEntity], which is the sync
 * engine's operational state. Keeping them apart is what lets the user clear the history (per
 * mapping) without disturbing the diff baselines, and lets an offloaded file stay in the history
 * even though its operational row is intentionally dropped so it's never re-synced. [remotePath]
 * and [sizeBytes] are snapshotted at transfer time so the entry survives the local file going away.
 */
@Entity(tableName = "sync_history", indices = [Index("mappingId", "syncedAt")])
data class SyncHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mappingId: Long,
    /** Path within the mapped folder, e.g. "trip/IMG_0001.jpg". */
    val relativePath: String,
    /** Full server path the file was transferred to, e.g. "/phone/photos/trip/IMG_0001.jpg". */
    val remotePath: String,
    val sizeBytes: Long,
    /** True when the local copy was deleted after upload (an offload); false when kept. */
    val offloaded: Boolean,
    val syncedAt: Long,
)
