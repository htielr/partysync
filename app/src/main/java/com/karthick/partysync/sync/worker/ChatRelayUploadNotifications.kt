package com.karthick.partysync.sync.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo

/**
 * Notifications for [ChatRelayUploadWorker]. Unlike [ShareUploadWorker]'s up2k uploads, there's
 * no chunk progress or pause/resume to show - chat-relay's upload API is a single POST with no
 * resume support - so this is deliberately much simpler: one foreground "sending" notification
 * while the POST is in flight, one dismissible result notification after.
 */
object ChatRelayUploadNotifications {
    private const val CHANNEL_ID = "chat_relay_upload"

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Chat Relay uploads", NotificationManager.IMPORTANCE_LOW),
                )
            }
        }
    }

    fun buildSendingForegroundInfo(context: Context, notificationId: Int, filename: String, room: String): ForegroundInfo {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Sending to $room")
            .setContentText(filename)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    fun showResult(context: Context, notificationId: Int, contentText: String) {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("PartySync")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(notificationId, notification)
    }
}
