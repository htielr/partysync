package com.karthick.partysync.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.karthick.partysync.domain.model.UploadSessionStatus

/**
 * Tracks one in-progress/paused/completed up2k upload so it can be resumed after the app is
 * killed, backgrounded, or the network drops — without re-hashing the whole file. Resuming is
 * still correct even if [chunkHashesJson] were lost (the up2k handshake is stateless: resend
 * the hash list, the server reports which chunks are still needed), but caching it here avoids
 * an O(filesize) SHA-512 pass on every resume.
 */
@Entity(tableName = "up2k_sessions")
data class Up2kSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** SAF document URI for folder-synced files, or a cached-file path for shared files. */
    val sourceUri: String,
    val serverId: Long,
    val remoteDirPath: String,
    val fileName: String,
    val fileSize: Long,
    val fileLastModified: Long,
    /** Ordered JSON array of this file's chunk hashes, computed once and cached. */
    val chunkHashesJson: String? = null,
    /** Server-issued session identifier from the last successful handshake. */
    val wark: String? = null,
    val status: UploadSessionStatus = UploadSessionStatus.PENDING,
    val createdAt: Long,
    val updatedAt: Long,
)
