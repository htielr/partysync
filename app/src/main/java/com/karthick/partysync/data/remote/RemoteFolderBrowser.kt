package com.karthick.partysync.data.remote

import javax.inject.Inject

sealed class RemoteFolderListResult {
    data class Success(val folders: List<String>) : RemoteFolderListResult()
    data class Failure(val message: String) : RemoteFolderListResult()
}

/**
 * Thin wrapper around [CopyPartyApi.listDirectory] for interactive folder-browsing UIs (the
 * share-upload destination picker and the folder-mapping remote-path picker): lists one
 * directory level and returns just the subfolder names, since browsing is for picking a
 * destination directory, not inspecting files. Shared by both pickers so there's one place to
 * fix bugs in the actual network/parsing logic — see `ui/common/RemoteFolderBrowserState.kt`
 * and `ui/common/FolderBrowserDialog.kt` for the (also shared) UI state shape and dialog.
 */
class RemoteFolderBrowser @Inject constructor(private val copyPartyApi: CopyPartyApi) {

    suspend fun listFolders(serverUrl: String, password: String, path: String): RemoteFolderListResult =
        when (val result = copyPartyApi.listDirectory(serverUrl, password, "", path)) {
            is CopyPartyListResult.Success ->
                RemoteFolderListResult.Success(result.entries.filter(RemoteEntry::isDirectory).map { it.name })
            is CopyPartyListResult.HttpError ->
                RemoteFolderListResult.Failure("Server error (HTTP ${result.code})")
            is CopyPartyListResult.NetworkError ->
                RemoteFolderListResult.Failure(result.exception.message ?: "Network error")
        }
}
