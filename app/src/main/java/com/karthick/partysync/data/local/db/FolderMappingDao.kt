package com.karthick.partysync.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.karthick.partysync.domain.model.MappingSyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderMappingDao {
    @Query("SELECT * FROM folder_mappings ORDER BY createdAt ASC")
    fun getAll(): Flow<List<FolderMappingEntity>>

    @Query("SELECT * FROM folder_mappings WHERE id = :id")
    suspend fun getById(id: Long): FolderMappingEntity?

    @Query("SELECT * FROM folder_mappings WHERE enabled = 1")
    suspend fun getAllEnabled(): List<FolderMappingEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(mapping: FolderMappingEntity): Long

    @Update
    suspend fun update(mapping: FolderMappingEntity)

    @Delete
    suspend fun delete(mapping: FolderMappingEntity)

    @Query(
        """
        UPDATE folder_mappings
        SET lastSyncStatus = :status,
            lastSyncAttemptAt = :attemptAt,
            lastSyncSuccessAt = COALESCE(:successAt, lastSyncSuccessAt)
        WHERE id = :id
        """,
    )
    suspend fun updateSyncStatus(
        id: Long,
        status: MappingSyncStatus,
        attemptAt: Long,
        successAt: Long?,
    )
}
