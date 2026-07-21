package com.karthick.partysync.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.karthick.partysync.data.local.db.FolderMappingDao
import com.karthick.partysync.data.local.db.FolderMappingEntity
import com.karthick.partysync.domain.model.SyncMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

private const val PERSISTABLE_PERMISSION_FLAGS =
    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

@Singleton
class FolderMappingRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: FolderMappingDao,
) : FolderMappingRepository {

    override fun observeAll(): Flow<List<FolderMappingEntity>> = dao.getAll()

    override suspend fun getById(id: Long): FolderMappingEntity? = dao.getById(id)

    override suspend fun create(
        serverId: Long,
        treeUri: Uri,
        displayName: String,
        remoteBasePath: String,
        syncMode: SyncMode,
        wifiOnlyOverride: Boolean?,
    ): Long {
        context.contentResolver.takePersistableUriPermission(treeUri, PERSISTABLE_PERMISSION_FLAGS)
        return dao.insert(
            FolderMappingEntity(
                serverId = serverId,
                treeUri = treeUri.toString(),
                displayName = displayName,
                remoteBasePath = remoteBasePath,
                syncMode = syncMode,
                wifiOnlyOverride = wifiOnlyOverride,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun update(mapping: FolderMappingEntity) = dao.update(mapping)

    override suspend fun delete(mapping: FolderMappingEntity) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(mapping.treeUri),
                PERSISTABLE_PERMISSION_FLAGS,
            )
        }
        dao.delete(mapping)
    }
}
