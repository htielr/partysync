package com.karthick.partysync.data.remote

sealed class CopyPartyResult {
    data object Success : CopyPartyResult()
    data class HttpError(val code: Int, val message: String?) : CopyPartyResult()
    data class NetworkError(val exception: Exception) : CopyPartyResult()
}

/** One entry from a copyparty `?ls` directory listing. */
data class RemoteEntry(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModifiedMillis: Long,
)

sealed class CopyPartyListResult {
    data class Success(val entries: List<RemoteEntry>) : CopyPartyListResult()
    data class HttpError(val code: Int, val message: String?) : CopyPartyListResult()
    data class NetworkError(val exception: Exception) : CopyPartyListResult()
}
