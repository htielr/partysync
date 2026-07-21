package com.karthick.partysync.sync.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo

/**
 * Foreground-progress + paused/result notifications for [ShareUploadWorker]. Each upload
 * session gets its **own** notification id (derived from the session's Room row id) so
 * concurrent/paused uploads never collide — and so the ongoing and final notifications for the
 * same session never share an id either (that exact collision — a result notification posted
 * under the same id as the just-stopped foreground notification, then immediately deleted when
 * the foreground service stops — was a real bug fixed earlier in this app).
 */
object ShareUploadNotifications {
    const val CHANNEL_ID = "share_upload"
    private const val ID_BASE = 3_000
    // Deliberately a different id range from ID_BASE: when doWork() returns, WorkManager stops
    // the foreground service with STOP_FOREGROUND_REMOVE, which deletes whatever notification
    // currently occupies ID_BASE's id — including a paused/result notification posted there
    // moments earlier. This is the exact bug already fixed once in this app; reusing ID_BASE
    // here silently reintroduced it during the up2k rewrite. Never share these two ranges.
    private const val TERMINAL_ID_BASE = 4_000

    fun notificationId(sessionId: Long): Int = (ID_BASE + sessionId).toInt()

    fun terminalNotificationId(sessionId: Long): Int = (TERMINAL_ID_BASE + sessionId).toInt()

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Shared file uploads", NotificationManager.IMPORTANCE_LOW),
                )
            }
        }
    }

    fun buildProgressForegroundInfo(
        context: Context,
        sessionId: Long,
        fileName: String,
        uploadedChunks: Int,
        totalChunks: Int,
    ): ForegroundInfo {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Uploading $fileName")
            .setContentText(if (totalChunks > 0) "$uploadedChunks / $totalChunks chunks" else "Starting…")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setProgress(totalChunks.coerceAtLeast(1), uploadedChunks, totalChunks <= 0)
            .addAction(0, "Pause", actionIntent(context, UploadControlReceiver.ACTION_PAUSE, sessionId))
            .addAction(0, "Cancel", actionIntent(context, UploadControlReceiver.ACTION_CANCEL, sessionId))
            .build()

        val id = notificationId(sessionId)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, notification)
        }
    }

    /** Shown when the user pauses (or a non-retryable failure stops the upload short of done). */
    fun showPaused(context: Context, sessionId: Long, fileName: String) {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Paused: $fileName")
            .setContentText("Tap Resume to continue uploading")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(false)
            .addAction(0, "Resume", actionIntent(context, UploadControlReceiver.ACTION_RESUME, sessionId))
            .addAction(0, "Cancel", actionIntent(context, UploadControlReceiver.ACTION_CANCEL, sessionId))
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(terminalNotificationId(sessionId), notification)
        // The ongoing/foreground notification for this id may still be mid-teardown from the
        // cancellation that triggered this pause; explicitly clear it so it can't outlive the
        // paused one and look like two notifications for one upload.
        context.getSystemService(NotificationManager::class.java).cancel(notificationId(sessionId))
    }

    /** Final, dismissible summary shown after the worker finishes (success or terminal failure). */
    fun showResult(context: Context, sessionId: Long, contentText: String) {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("PartySync")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(terminalNotificationId(sessionId), notification)
    }

    private fun actionSlot(action: String): Int = when (action) {
        UploadControlReceiver.ACTION_PAUSE -> 1
        UploadControlReceiver.ACTION_RESUME -> 2
        UploadControlReceiver.ACTION_CANCEL -> 3
        else -> 9
    }

    private fun actionIntent(context: Context, action: String, sessionId: Long): PendingIntent {
        val intent = Intent(context, UploadControlReceiver::class.java).apply {
            this.action = action
            putExtra(UploadControlReceiver.EXTRA_SESSION_ID, sessionId)
        }
        // requestCode must be unique per (sessionId, action) or PendingIntents overwrite each other.
        val requestCode = notificationId(sessionId) * 10 + actionSlot(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
