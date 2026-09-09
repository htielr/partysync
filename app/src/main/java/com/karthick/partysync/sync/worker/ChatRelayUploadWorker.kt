package com.karthick.partysync.sync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.karthick.partysync.data.local.prefs.ChatRelayRepository
import com.karthick.partysync.data.remote.ChatRelayApi
import com.karthick.partysync.data.remote.ChatRelayResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

/**
 * Uploads one shared file to a chat-relay room. A single multipart POST, not chunked - the
 * chat-relay upload API has no resume support, unlike copyparty's up2k
 * ([ShareUploadWorker]) - so there's no per-chunk session state to persist, just WorkManager's
 * built-in retry for transient failures.
 */
@HiltWorker
class ChatRelayUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val chatRelayRepository: ChatRelayRepository,
    private val chatRelayApi: ChatRelayApi,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val room = inputData.getString(KEY_ROOM) ?: return Result.failure()
        val filePath = inputData.getString(KEY_FILE_PATH) ?: return Result.failure()
        val filename = inputData.getString(KEY_FILENAME) ?: return Result.failure()
        val mimeType = inputData.getString(KEY_MIME_TYPE) ?: "application/octet-stream"
        val notificationId = id.hashCode()

        val file = File(filePath)
        if (!file.exists()) return Result.failure()

        val config = chatRelayRepository.config.value
        if (config == null) {
            ChatRelayUploadNotifications.showResult(applicationContext, notificationId, "Chat Relay isn't configured")
            return Result.failure()
        }

        setForeground(
            ChatRelayUploadNotifications.buildSendingForegroundInfo(applicationContext, notificationId, filename, room),
        )

        val result = chatRelayApi.uploadFile(
            baseUrl = config.baseUrl,
            apiKey = config.apiKey,
            room = room,
            filename = filename,
            contentLength = file.length(),
            mimeType = mimeType,
            openStream = { file.inputStream() },
        )

        return when (result) {
            is ChatRelayResult.Success -> {
                ChatRelayUploadNotifications.showResult(applicationContext, notificationId, "$filename sent to $room")
                runCatching { file.delete() }
                Result.success()
            }
            is ChatRelayResult.HttpError -> {
                ChatRelayUploadNotifications.showResult(applicationContext, notificationId, "Send failed: HTTP ${result.code}")
                if (result.code in 500..599) Result.retry() else Result.failure()
            }
            is ChatRelayResult.NetworkError -> {
                ChatRelayUploadNotifications.showResult(applicationContext, notificationId, "Send failed: ${result.exception.message}")
                Result.retry()
            }
        }
    }

    companion object {
        private const val KEY_ROOM = "room"
        private const val KEY_FILE_PATH = "file_path"
        private const val KEY_FILENAME = "filename"
        private const val KEY_MIME_TYPE = "mime_type"

        fun buildInputData(room: String, filePath: String, filename: String, mimeType: String): Data =
            Data.Builder()
                .putString(KEY_ROOM, room)
                .putString(KEY_FILE_PATH, filePath)
                .putString(KEY_FILENAME, filename)
                .putString(KEY_MIME_TYPE, mimeType)
                .build()
    }
}
