package com.karthick.partysync.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncFileStateDao {
    @Query("SELECT * FROM sync_file_states WHERE mappingId = :mappingId")
    suspend fun getAllForMapping(mappingId: Long): List<SyncFileStateEntity>

    @Query("SELECT * FROM sync_file_states WHERE mappingId = :mappingId ORDER BY relativePath ASC")
    fun observeAllForMapping(mappingId: Long): Flow<List<SyncFileStateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SyncFileStateEntity)

    @Query(
        "SELECT * FROM sync_file_states WHERE mappingId = :mappingId AND lastUploadStatus IN " +
            "('FAILED_RETRYABLE', 'FAILED_AUTH', 'FAILED_OTHER')",
    )
    suspend fun getFailedForMapping(mappingId: Long): List<SyncFileStateEntity>

    @Query(
        "SELECT COUNT(*) FROM sync_file_states WHERE mappingId = :mappingId AND lastUploadStatus = 'PENDING'",
    )
    fun countPendingForMapping(mappingId: Long): Flow<Int>

    @Query(
        "SELECT COUNT(*) FROM sync_file_states WHERE mappingId = :mappingId AND lastUploadStatus = 'CONFLICT_RESOLVED'",
    )
    fun countConflictsForMapping(mappingId: Long): Flow<Int>

    @Query("DELETE FROM sync_file_states WHERE mappingId = :mappingId AND relativePath IN (:relativePaths)")
    suspend fun deleteByPaths(mappingId: Long, relativePaths: List<String>)
}
