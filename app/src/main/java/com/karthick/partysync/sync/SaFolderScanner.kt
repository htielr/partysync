package com.karthick.partysync.sync

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.karthick.partysync.domain.model.ScannedFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Recursively walks a SAF tree URI using [DocumentsContract] + [android.content.ContentResolver.query]
 * directly, rather than [androidx.documentfile.provider.DocumentFile.listFiles], which issues an
 * extra query per child to resolve metadata lazily. For a folder with hundreds/thousands of files
 * (e.g. a camera folder), that's roughly 2x the binder round-trips per scan.
 */
class SaFolderScanner @Inject constructor(@ApplicationContext private val context: Context) {

    suspend fun scan(treeUri: Uri): List<ScannedFile> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ScannedFile>()
        val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        scanDirectory(treeUri, rootDocumentId, relativePathPrefix = "", depth = 0, results)
        results
    }

    private fun scanDirectory(
        treeUri: Uri,
        documentId: String,
        relativePathPrefix: String,
        depth: Int,
        results: MutableList<ScannedFile>,
    ) {
        if (depth > MAX_DEPTH) return

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        context.contentResolver.query(childrenUri, PROJECTION, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            val mtimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

            while (cursor.moveToNext()) {
                val childId = cursor.getString(idIdx)
                val name = cursor.getString(nameIdx) ?: continue
                val mime = cursor.getString(mimeIdx)
                val relativePath = if (relativePathPrefix.isEmpty()) name else "$relativePathPrefix/$name"

                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    scanDirectory(treeUri, childId, relativePath, depth + 1, results)
                } else {
                    results += ScannedFile(
                        relativePath = relativePath,
                        documentId = childId,
                        size = cursor.getLong(sizeIdx),
                        lastModified = cursor.getLong(mtimeIdx),
                    )
                }
            }
        }
    }

    private companion object {
        const val MAX_DEPTH = 32

        val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
    }
}
