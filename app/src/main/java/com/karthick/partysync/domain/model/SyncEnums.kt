package com.karthick.partysync.domain.model

enum class SyncMode {
    ONE_WAY_UPLOAD,
    TWO_WAY,
}

enum class MappingSyncStatus {
    IDLE,
    RUNNING,
    SUCCESS,
    PARTIAL_FAILURE,
    AUTH_ERROR,
    PERMISSION_LOST,
    /** The mapping's server profile no longer exists (deleted from the Servers screen). */
    SERVER_MISSING,
}

enum class FileSyncStatus {
    PENDING,
    UPLOADING,
    DOWNLOADING,
    SUCCESS,
    CONFLICT_RESOLVED,
    FAILED_RETRYABLE,
    FAILED_AUTH,
    FAILED_OTHER,
}

enum class UploadSessionStatus {
    PENDING,
    HASHING,
    UPLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED,
}
