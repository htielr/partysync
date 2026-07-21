package com.karthick.partysync.data.remote

import com.karthick.partysync.domain.model.FileSyncStatus

/**
 * Classifies a [CopyPartyResult] into a [FileSyncStatus] + optional error message. Shared by
 * every upload/download call site (`SyncEngine`, `ShareUploadWorker`) so retryable-vs-auth-vs-other
 * classification stays consistent across the app instead of being duplicated per caller.
 */
fun classifyCopyPartyResult(result: CopyPartyResult): Pair<FileSyncStatus, String?> = when (result) {
    is CopyPartyResult.Success -> FileSyncStatus.SUCCESS to null
    is CopyPartyResult.HttpError -> when (result.code) {
        401, 403 -> FileSyncStatus.FAILED_AUTH to "HTTP ${result.code}: ${result.message}"
        in 500..599 -> FileSyncStatus.FAILED_RETRYABLE to "HTTP ${result.code}: ${result.message}"
        else -> FileSyncStatus.FAILED_OTHER to "HTTP ${result.code}: ${result.message}"
    }
    is CopyPartyResult.NetworkError -> FileSyncStatus.FAILED_RETRYABLE to result.exception.message
}
