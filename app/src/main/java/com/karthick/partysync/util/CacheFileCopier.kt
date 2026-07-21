package com.karthick.partysync.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Copies a `content://` Uri into the app's private cache so [Up2kClient]'s
 * `RandomAccessFile`-based reader has a real absolute path to work from — up2k sessions never
 * read directly from a SAF/content Uri (see `ShareUploadWorker`/`Up2kSessionEntity`).
 */
fun copyToCache(context: Context, uri: Uri, displayName: String, subDir: String): File? = try {
    val dir = File(context.cacheDir, subDir).apply { mkdirs() }
    val dest = File(dir, "${UUID.randomUUID()}_$displayName")
    context.contentResolver.openInputStream(uri)?.use { input ->
        dest.outputStream().use { output -> input.copyTo(output) }
    }
    if (dest.exists()) dest else null
} catch (e: IOException) {
    null
}
