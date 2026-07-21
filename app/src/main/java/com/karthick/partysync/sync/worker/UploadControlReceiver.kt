package com.karthick.partysync.sync.worker

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.karthick.partysync.data.local.db.Up2kSessionDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Handles the Pause/Resume/Cancel action buttons on [ShareUploadNotifications]. Pause and
 * Cancel both just cancel the in-flight WorkManager job (which [ShareUploadWorker] observes as
 * coroutine cancellation and reacts to accordingly) — the only difference is Cancel also
 * deletes the session row and its cached temp file, making it terminal.
 */
@AndroidEntryPoint
class UploadControlReceiver : BroadcastReceiver() {

    @Inject lateinit var up2kSessionDao: Up2kSessionDao

    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
        if (sessionId < 0) return
        val action = intent.action ?: return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_PAUSE -> {
                        WorkManager.getInstance(context).cancelUniqueWork(workNameFor(sessionId))
                    }

                    ACTION_CANCEL -> {
                        WorkManager.getInstance(context).cancelUniqueWork(workNameFor(sessionId))
                        up2kSessionDao.getById(sessionId)?.let { session ->
                            runCatching { File(session.sourceUri).delete() }
                        }
                        up2kSessionDao.delete(sessionId)
                        val notificationManager = context.getSystemService(NotificationManager::class.java)
                        notificationManager.cancel(ShareUploadNotifications.notificationId(sessionId))
                        notificationManager.cancel(ShareUploadNotifications.terminalNotificationId(sessionId))
                    }

                    ACTION_RESUME -> {
                        val request = OneTimeWorkRequestBuilder<ShareUploadWorker>()
                            .setInputData(ShareUploadWorker.buildInputData(sessionId))
                            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                            .build()
                        WorkManager.getInstance(context)
                            .enqueueUniqueWork(workNameFor(sessionId), ExistingWorkPolicy.REPLACE, request)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.karthick.partysync.action.PAUSE_UPLOAD"
        const val ACTION_RESUME = "com.karthick.partysync.action.RESUME_UPLOAD"
        const val ACTION_CANCEL = "com.karthick.partysync.action.CANCEL_UPLOAD"
        const val EXTRA_SESSION_ID = "session_id"

        fun workNameFor(sessionId: Long) = "share_upload_$sessionId"
    }
}
