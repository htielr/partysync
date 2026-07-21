package com.karthick.partysync.sync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.karthick.partysync.data.local.db.Up2kSessionDao
import com.karthick.partysync.data.local.db.Up2kSessionEntity
import com.karthick.partysync.data.local.prefs.ServerRepository
import com.karthick.partysync.data.remote.ChunkBatching
import com.karthick.partysync.data.remote.Up2kClient
import com.karthick.partysync.data.remote.Up2kHandshakeResult
import com.karthick.partysync.data.remote.classifyCopyPartyResult
import com.karthick.partysync.domain.model.FileSyncStatus
import com.karthick.partysync.domain.model.UploadSessionStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.RandomAccessFile

/**
 * Uploads one [Up2kSessionEntity] (one file) via the up2k chunked protocol, reporting
 * chunk-count progress and reacting to Pause/Cancel via ordinary coroutine cancellation (see
 * [UploadControlReceiver]): WorkManager cancelling this work cancels the underlying coroutine,
 * which we catch to mark the session `PAUSED` rather than `FAILED` before letting the
 * cancellation propagate (the standard "cleanup on cancellation" coroutine pattern).
 *
 * A "resume" is just enqueueing this worker again for the same session id — the handshake
 * (cheap: the ordered chunk-hash list is cached on the session row, no re-hashing) tells us
 * exactly which chunks the server still needs, so only the remainder gets uploaded.
 */
@HiltWorker
class ShareUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val up2kSessionDao: Up2kSessionDao,
    private val serverRepository: ServerRepository,
    private val up2kClient: Up2kClient,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val sessionId = inputData.getLong(KEY_SESSION_ID, -1L)
        if (sessionId < 0) return Result.failure()

        var session = up2kSessionDao.getById(sessionId) ?: return Result.failure()
        val file = File(session.sourceUri)
        if (!file.exists()) {
            failSession(session, "Source file no longer exists")
            return Result.failure()
        }

        val server = serverRepository.getById(session.serverId)
        if (server == null) {
            failSession(session, "Server no longer exists")
            return Result.failure()
        }

        setForeground(
            ShareUploadNotifications.buildProgressForegroundInfo(applicationContext, sessionId, session.fileName, 0, 0),
        )

        try {
            val fullHashes = session.chunkHashesJson?.let(::parseHashList) ?: run {
                session = session.copy(status = UploadSessionStatus.HASHING, updatedAt = System.currentTimeMillis())
                up2kSessionDao.update(session)
                val computed = hashFile(file)
                session = session.copy(chunkHashesJson = serializeHashList(computed), updatedAt = System.currentTimeMillis())
                up2kSessionDao.update(session)
                computed
            }

            val handshake = up2kClient.handshake(
                serverUrl = server.serverUrl,
                password = server.password,
                remoteDirPath = session.remoteDirPath,
                fileName = session.fileName,
                fileSize = session.fileSize,
                lastModifiedSeconds = session.fileLastModified,
                chunkHashes = fullHashes,
            )

            val info = when (handshake) {
                is Up2kHandshakeResult.Success -> handshake.info
                is Up2kHandshakeResult.HttpError -> {
                    failSession(session, "Handshake failed: HTTP ${handshake.code}")
                    return if (handshake.code in 500..599) Result.retry() else Result.failure()
                }
                is Up2kHandshakeResult.NetworkError -> {
                    failSession(session, "Handshake failed: ${handshake.exception.message}")
                    return Result.retry()
                }
            }

            session = session.copy(
                wark = info.wark,
                status = UploadSessionStatus.UPLOADING,
                updatedAt = System.currentTimeMillis(),
            )
            up2kSessionDao.update(session)

            val totalChunks = fullHashes.size
            var uploadedChunks = totalChunks - info.neededHashes.size

            if (info.neededHashes.isEmpty()) {
                completeSession(session)
                return Result.success()
            }

            val chunkSize = up2kClient.chunkSize(session.fileSize)
            val batches = ChunkBatching.plan(fullHashes, info.neededHashes)

            RandomAccessFile(file, "r").use { raf ->
                for (batch in batches) {
                    val firstIdx = fullHashes.indexOf(batch.first())
                    val lastIdx = fullHashes.indexOf(batch.last())
                    val startOffset = firstIdx.toLong() * chunkSize
                    val endOffset = minOf(session.fileSize, (lastIdx + 1).toLong() * chunkSize)
                    val length = (endOffset - startOffset).toInt()
                    val buffer = ByteArray(length)
                    raf.seek(startOffset)
                    raf.readFully(buffer)

                    val result = up2kClient.uploadChunkBatch(
                        serverUrl = server.serverUrl,
                        password = server.password,
                        remoteDirPath = session.remoteDirPath,
                        wark = info.wark,
                        chunkHashes = batch,
                        bytes = buffer,
                        length = length,
                    )

                    val (status, errorMessage) = classifyCopyPartyResult(result)
                    if (status != FileSyncStatus.SUCCESS) {
                        failSession(session, errorMessage ?: "Chunk upload failed")
                        return if (status == FileSyncStatus.FAILED_RETRYABLE) Result.retry() else Result.failure()
                    }

                    uploadedChunks += batch.size
                    setForeground(
                        ShareUploadNotifications.buildProgressForegroundInfo(
                            applicationContext,
                            sessionId,
                            session.fileName,
                            uploadedChunks,
                            totalChunks,
                        ),
                    )
                }
            }

            completeSession(session)
            return Result.success()
        } catch (e: CancellationException) {
            withContext(NonCancellable) { pauseSession(session) }
            throw e
        }
    }

    private suspend fun pauseSession(session: Up2kSessionEntity) {
        up2kSessionDao.update(session.copy(status = UploadSessionStatus.PAUSED, updatedAt = System.currentTimeMillis()))
        ShareUploadNotifications.showPaused(applicationContext, session.id, session.fileName)
    }

    private suspend fun failSession(session: Up2kSessionEntity, message: String) {
        up2kSessionDao.update(session.copy(status = UploadSessionStatus.FAILED, updatedAt = System.currentTimeMillis()))
        ShareUploadNotifications.showResult(applicationContext, session.id, message)
    }

    private suspend fun completeSession(session: Up2kSessionEntity) {
        up2kSessionDao.update(session.copy(status = UploadSessionStatus.COMPLETED, updatedAt = System.currentTimeMillis()))
        ShareUploadNotifications.showResult(applicationContext, session.id, "${session.fileName} uploaded")
        runCatching { File(session.sourceUri).delete() }
    }

    private fun hashFile(file: File): List<String> {
        val fileSize = file.length()
        val chunkSize = up2kClient.chunkSize(fileSize)
        val hashes = mutableListOf<String>()
        RandomAccessFile(file, "r").use { raf ->
            var offset = 0L
            while (offset < fileSize) {
                val length = minOf(chunkSize.toLong(), fileSize - offset).toInt()
                val buffer = ByteArray(length)
                raf.seek(offset)
                raf.readFully(buffer)
                hashes += up2kClient.hashChunk(buffer, 0, length)
                offset += length
            }
        }
        return hashes
    }

    private fun parseHashList(json: String): List<String> {
        val array = JSONArray(json)
        return (0 until array.length()).map { array.getString(it) }
    }

    private fun serializeHashList(hashes: List<String>): String = JSONArray(hashes).toString()

    companion object {
        private const val KEY_SESSION_ID = "session_id"

        fun buildInputData(sessionId: Long): Data =
            Data.Builder().putLong(KEY_SESSION_ID, sessionId).build()
    }
}
