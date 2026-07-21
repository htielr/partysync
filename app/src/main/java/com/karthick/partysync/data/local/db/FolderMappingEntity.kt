package com.karthick.partysync.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.karthick.partysync.domain.model.MappingSyncStatus
import com.karthick.partysync.domain.model.SyncMode

/**
 * One user-configured folder to sync: a local SAF tree paired with a remote
 * copyparty path, plus its own sync mode and per-mapping overrides.
 */
@Entity(tableName = "folder_mappings")
data class FolderMappingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** References a [com.karthick.partysync.data.local.prefs.ServerProfile] id (not a Room FK — server profiles live in encrypted prefs, not this database). */
    val serverId: Long,
    val treeUri: String,
    val displayName: String,
    val remoteBasePath: String,
    val syncMode: SyncMode,
    /** null = inherit the app-wide Wi-Fi-only setting */
    val wifiOnlyOverride: Boolean?,
    val enabled: Boolean = true,
    val createdAt: Long,
    val lastSyncAttemptAt: Long? = null,
    val lastSyncSuccessAt: Long? = null,
    val lastSyncStatus: MappingSyncStatus = MappingSyncStatus.IDLE,
)
