package com.karthick.partysync.sync

import com.karthick.partysync.data.remote.CopyPartyApi
import com.karthick.partysync.data.remote.CopyPartyListResult
import com.karthick.partysync.domain.model.ScannedFile
import javax.inject.Inject

sealed class RemoteScanResult {
    data class Success(val files: List<ScannedFile>) : RemoteScanResult()
    data class Failure(val message: String) : RemoteScanResult()
}

/**
 * Recursively walks a mapping's remote copyparty path via [CopyPartyApi.listDirectory] (the
 * `?ls` JSON API, which is single-level only), producing the same [ScannedFile] shape as
 * [SaFolderScanner] so [com.karthick.partysync.domain.usecase.SyncPlanner] can diff both sides
 * symmetrically. [ScannedFile.documentId] has no remote equivalent (copyparty entries are
 * identified by path, not a stable id) — the relative path is reused there for shape
 * consistency; it is not read for remote entries.
 */
class RemoteFolderScanner @Inject constructor(private val copyPartyApi: CopyPartyApi) {

    suspend fun scan(serverUrl: String, password: String, remoteBasePath: String): RemoteScanResult {
        val results = mutableListOf<ScannedFile>()
        return scanDirectory(serverUrl, password, remoteBasePath, relativePathPrefix = "", depth = 0, results)
    }

    private suspend fun scanDirectory(
        serverUrl: String,
        password: String,
        remoteBasePath: String,
        relativePathPrefix: String,
        depth: Int,
        results: MutableList<ScannedFile>,
    ): RemoteScanResult {
        if (depth > MAX_DEPTH) return RemoteScanResult.Success(results)

        val listing = copyPartyApi.listDirectory(serverUrl, password, remoteBasePath, relativePathPrefix)
        return when (listing) {
            is CopyPartyListResult.Success -> {
                for (entry in listing.entries) {
                    val childRelativePath =
                        if (relativePathPrefix.isEmpty()) entry.name else "$relativePathPrefix/${entry.name}"

                    if (entry.isDirectory) {
                        val nested = scanDirectory(
                            serverUrl,
                            password,
                            remoteBasePath,
                            childRelativePath,
                            depth + 1,
                            results,
                        )
                        when (nested) {
                            is RemoteScanResult.Failure -> return nested
                            is RemoteScanResult.Success -> Unit
                        }
                    } else {
                        results += ScannedFile(
                            relativePath = childRelativePath,
                            documentId = childRelativePath,
                            size = entry.size,
                            lastModified = entry.lastModifiedMillis,
                        )
                    }
                }
                RemoteScanResult.Success(results)
            }
            is CopyPartyListResult.HttpError ->
                RemoteScanResult.Failure("HTTP ${listing.code}: ${listing.message}")
            is CopyPartyListResult.NetworkError ->
                RemoteScanResult.Failure(listing.exception.message ?: "network error")
        }
    }

    private companion object {
        const val MAX_DEPTH = 32
    }
}
