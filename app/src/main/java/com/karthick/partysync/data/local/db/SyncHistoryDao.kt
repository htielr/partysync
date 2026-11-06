package com.karthick.partysync.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncHistoryDao {
    @Insert
    suspend fun insert(entry: SyncHistoryEntity)

    /** Newest first; ties broken by insertion order so same-millisecond transfers stay stable. */
    @Query("SELECT * FROM sync_history WHERE mappingId = :mappingId ORDER BY syncedAt DESC, id DESC")
    fun observeForMapping(mappingId: Long): Flow<List<SyncHistoryEntity>>

    @Query("DELETE FROM sync_history WHERE mappingId = :mappingId")
    suspend fun clearForMapping(mappingId: Long)
}
