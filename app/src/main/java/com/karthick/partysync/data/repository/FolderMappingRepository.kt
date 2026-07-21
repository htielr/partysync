package com.karthick.partysync.data.repository

import android.net.Uri
import com.karthick.partysync.data.local.db.FolderMappingEntity
import com.karthick.partysync.domain.model.SyncMode
import kotlinx.coroutines.flow.Flow

interface FolderMappingRepository {
    fun observeAll(): Flow<List<FolderMappingEntity>>

    suspend fun getById(id: Long): FolderMappingEntity?

    /** Takes a persistable URI permission on [treeUri] and stores a new mapping. */
    suspend fun create(
        serverId: Long,
        treeUri: Uri,
        displayName: String,
        remoteBasePath: String,
        syncMode: SyncMode,
        wifiOnlyOverride: Boolean?,
    ): Long

    suspend fun update(mapping: FolderMappingEntity)

    /** Releases the mapping's persisted URI permission and deletes it (cascades file state). */
    suspend fun delete(mapping: FolderMappingEntity)
}
