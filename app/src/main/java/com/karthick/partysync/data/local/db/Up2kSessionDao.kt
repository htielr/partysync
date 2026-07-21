package com.karthick.partysync.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.karthick.partysync.domain.model.UploadSessionStatus

@Dao
interface Up2kSessionDao {
    @Insert
    suspend fun insert(session: Up2kSessionEntity): Long

    @Update
    suspend fun update(session: Up2kSessionEntity)

    @Query("SELECT * FROM up2k_sessions WHERE id = :id")
    suspend fun getById(id: Long): Up2kSessionEntity?

    @Query("SELECT * FROM up2k_sessions WHERE status = :status")
    suspend fun getAllWithStatus(status: UploadSessionStatus): List<Up2kSessionEntity>

    @Query("DELETE FROM up2k_sessions WHERE id = :id")
    suspend fun delete(id: Long)
}
