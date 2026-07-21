package com.karthick.partysync.data.local.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.karthick.partysync.domain.model.FileSyncStatus

/**
 * Per-file sync state for one path within one [FolderMappingEntity].
 *
 * The baseline* fields are the reference point for the two-way 3-way merge:
 * they capture what local and remote each looked like the last time this path
 * was successfully reconciled. Comparing current local/remote state against
 * these baselines (rather than against each other directly) is what lets the
 * planner tell "local changed" apart from "remote changed" apart from "both
 * changed" (see SyncPlanner). One-way mappings only ever populate the local
 * baseline fields.
 */
@Entity(
    tableName = "sync_file_states",
    primaryKeys = ["mappingId", "relativePath"],
    foreignKeys = [
        ForeignKey(
            entity = FolderMappingEntity::class,
            parentColumns = ["id"],
            childColumns = ["mappingId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("mappingId"), Index("lastUploadStatus")],
)
data class SyncFileStateEntity(
    val mappingId: Long,
    val relativePath: String,
    val documentId: String,
    val localSize: Long,
    val localMtime: Long,
    val remoteSize: Long? = null,
    val remoteMtime: Long? = null,
    val baselineLocalSize: Long? = null,
    val baselineLocalMtime: Long? = null,
    val baselineRemoteSize: Long? = null,
    val baselineRemoteMtime: Long? = null,
    val lastUploadedAt: Long? = null,
    val lastUploadStatus: FileSyncStatus = FileSyncStatus.PENDING,
    val lastErrorMessage: String? = null,
    val attemptCount: Int = 0,
)
