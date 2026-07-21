package com.karthick.partysync.domain.model

/**
 * One file discovered by a scan (local via [com.karthick.partysync.sync.SaFolderScanner] or
 * remote via the future RemoteFolderScanner), expressed relative to a mapping's root so both
 * sides can be diffed symmetrically.
 */
data class ScannedFile(
    val relativePath: String,
    val documentId: String,
    val size: Long,
    val lastModified: Long,
)
