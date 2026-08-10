package com.karthick.partysync.util

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File
import java.io.IOException
import java.io.OutputStream

private const val DOWNLOAD_SUBDIR = "PartySync"

/**
 * Saves a stream to the device's public Downloads/PartySync folder, visible in the system
 * Downloads app and any file manager. Uses the MediaStore Downloads collection on API 29+
 * (scoped storage — no permission needed for an app's own inserts); falls back to a direct
 * public-directory write plus a media scan on API 26-28, where the caller must already hold
 * WRITE_EXTERNAL_STORAGE (requested at the call site, since that's a runtime permission).
 */
fun saveToDownloads(context: Context, fileName: String, writeTo: (OutputStream) -> Unit): Boolean {
    val mimeType = MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(fileName.substringAfterLast('.', "").lowercase())
        ?: "application/octet-stream"

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$DOWNLOAD_SUBDIR")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        try {
            resolver.openOutputStream(uri)?.use { writeTo(it) } ?: return false
        } catch (e: IOException) {
            resolver.delete(uri, null, null)
            return false
        }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        true
    } else {
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), DOWNLOAD_SUBDIR)
        dir.mkdirs()
        val dest = File(dir, fileName)
        try {
            dest.outputStream().use { writeTo(it) }
        } catch (e: IOException) {
            return false
        }
        MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), arrayOf(mimeType), null)
        true
    }
}
