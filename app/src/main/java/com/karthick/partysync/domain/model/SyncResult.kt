package com.karthick.partysync.domain.model

data class MappingSyncResult(
    val mappingId: Long,
    val uploaded: Int,
    val failed: Int,
    val hadRetryableFailure: Boolean,
    val hadAuthFailure: Boolean,
    val permissionLost: Boolean,
)

data class SyncResult(val mappingResults: List<MappingSyncResult>) {
    val hasAnyFailure: Boolean get() = mappingResults.any { it.failed > 0 || it.permissionLost }
}
