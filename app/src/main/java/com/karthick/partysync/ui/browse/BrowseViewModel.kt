package com.karthick.partysync.ui.browse

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.core.content.FileProvider
import com.karthick.partysync.data.local.db.Up2kSessionDao
import com.karthick.partysync.data.local.db.Up2kSessionEntity
import com.karthick.partysync.data.local.prefs.ServerProfile
import com.karthick.partysync.data.local.prefs.ServerRepository
import com.karthick.partysync.data.local.prefs.SettingsRepository
import com.karthick.partysync.data.remote.CopyPartyApi
import com.karthick.partysync.data.remote.CopyPartyListResult
import com.karthick.partysync.data.remote.CopyPartyResult
import com.karthick.partysync.data.remote.RemoteEntry
import com.karthick.partysync.domain.model.UploadSessionStatus
import com.karthick.partysync.sync.worker.ShareUploadWorker
import com.karthick.partysync.sync.worker.UploadControlReceiver
import com.karthick.partysync.util.copyToCache
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

data class OpenFileRequest(val uri: Uri, val mimeType: String)

enum class BrowseViewMode { LIST, GRID }

enum class BrowseSortField { NAME, DATE, SIZE }

/** Snapshot needed to page through the current folder's images/videos in the in-app viewer. */
data class MediaViewerState(
    val entries: List<RemoteEntry>,
    val startIndex: Int,
    val serverUrl: String,
    val password: String,
    val remoteBasePath: String,
)

data class BrowseUiState(
    val servers: List<ServerProfile> = emptyList(),
    val selectedServerId: Long? = null,
    val currentPath: String = "",
    val entries: List<RemoteEntry> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val pendingDelete: RemoteEntry? = null,
    val renameTarget: RemoteEntry? = null,
    val renameText: String = "",
    val showNewFolderDialog: Boolean = false,
    val newFolderName: String = "",
    val fileToOpen: OpenFileRequest? = null,
    val viewMode: BrowseViewMode = BrowseViewMode.LIST,
    val mediaViewer: MediaViewerState? = null,
    val sortField: BrowseSortField = BrowseSortField.NAME,
    val sortAscending: Boolean = true,
) {
    val breadcrumbSegments: List<String>
        get() = currentPath.split('/').filter { it.isNotEmpty() }

    /** Folders first, then files — each group sorted independently by [sortField]/[sortAscending]. */
    val sortedEntries: List<RemoteEntry>
        get() {
            val comparator = when (sortField) {
                BrowseSortField.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it: RemoteEntry -> it.name }
                BrowseSortField.DATE -> compareBy { it: RemoteEntry -> it.lastModifiedMillis }
                BrowseSortField.SIZE -> compareBy { it: RemoteEntry -> it.size }
            }.let { if (sortAscending) it else it.reversed() }
            val (dirs, files) = entries.partition { it.isDirectory }
            return dirs.sortedWith(comparator) + files.sortedWith(comparator)
        }
}

@HiltViewModel
class BrowseViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serverRepository: ServerRepository,
    private val settingsRepository: SettingsRepository,
    private val copyPartyApi: CopyPartyApi,
    private val up2kSessionDao: Up2kSessionDao,
    private val workManager: WorkManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    init {
        val servers = serverRepository.servers.value
        val lastId = settingsRepository.settings.value.lastBrowsedServerId
        val initialServerId = when {
            servers.any { it.id == lastId } -> lastId
            servers.size == 1 -> servers.single().id
            else -> null
        }
        _uiState.update { it.copy(servers = servers, selectedServerId = initialServerId) }
        if (initialServerId != null) refresh()
    }

    fun onServerSelected(serverId: Long) {
        settingsRepository.setLastBrowsedServerId(serverId)
        _uiState.update { it.copy(selectedServerId = serverId, currentPath = "", entries = emptyList()) }
        refresh()
    }

    fun navigateInto(entry: RemoteEntry) {
        if (!entry.isDirectory) return
        val current = _uiState.value.currentPath
        val next = if (current.isEmpty()) entry.name else "$current/${entry.name}"
        _uiState.update { it.copy(currentPath = next) }
        refresh()
    }

    fun navigateUp() {
        val current = _uiState.value.currentPath
        if (current.isEmpty()) return
        _uiState.update { it.copy(currentPath = current.substringBeforeLast('/', missingDelimiterValue = "")) }
        refresh()
    }

    fun navigateToBreadcrumb(index: Int) {
        val segments = _uiState.value.breadcrumbSegments
        if (index < 0) {
            _uiState.update { it.copy(currentPath = "") }
        } else {
            _uiState.update { it.copy(currentPath = segments.take(index + 1).joinToString("/")) }
        }
        refresh()
    }

    fun refresh() {
        val server = currentServer() ?: return
        val path = _uiState.value.currentPath
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = copyPartyApi.listDirectory(server.serverUrl, server.password, "", path)) {
                is CopyPartyListResult.Success ->
                    _uiState.update { it.copy(isLoading = false, entries = result.entries) }
                is CopyPartyListResult.HttpError ->
                    _uiState.update { it.copy(isLoading = false, error = "Server error (HTTP ${result.code})") }
                is CopyPartyListResult.NetworkError ->
                    _uiState.update { it.copy(isLoading = false, error = result.exception.message ?: "Network error") }
            }
        }
    }

    // --- Delete ---

    fun requestDelete(entry: RemoteEntry) = _uiState.update { it.copy(pendingDelete = entry) }
    fun dismissDeleteConfirm() = _uiState.update { it.copy(pendingDelete = null) }

    fun confirmDelete() {
        val entry = _uiState.value.pendingDelete ?: return
        val server = currentServer() ?: return
        val path = joinPath(_uiState.value.currentPath, entry.name)
        _uiState.update { it.copy(pendingDelete = null) }
        viewModelScope.launch {
            val result = copyPartyApi.delete(server.serverUrl, server.password, "", path)
            handleMutationResult(result)
        }
    }

    // --- Rename ---

    fun requestRename(entry: RemoteEntry) =
        _uiState.update { it.copy(renameTarget = entry, renameText = entry.name) }

    fun dismissRenameDialog() = _uiState.update { it.copy(renameTarget = null, renameText = "") }
    fun onRenameTextChanged(text: String) = _uiState.update { it.copy(renameText = text) }

    fun confirmRename() {
        val entry = _uiState.value.renameTarget ?: return
        val newName = _uiState.value.renameText.trim()
        if (newName.isEmpty() || newName == entry.name) {
            dismissRenameDialog()
            return
        }
        val server = currentServer() ?: return
        val sourcePath = joinPath(_uiState.value.currentPath, entry.name)
        val destPath = joinPath(_uiState.value.currentPath, newName)
        _uiState.update { it.copy(renameTarget = null, renameText = "") }
        viewModelScope.launch {
            val result = copyPartyApi.move(server.serverUrl, server.password, "", sourcePath, destPath)
            handleMutationResult(result)
        }
    }

    // --- New folder ---

    fun openNewFolderDialog() = _uiState.update { it.copy(showNewFolderDialog = true, newFolderName = "") }
    fun dismissNewFolderDialog() = _uiState.update { it.copy(showNewFolderDialog = false, newFolderName = "") }
    fun onNewFolderNameChanged(text: String) = _uiState.update { it.copy(newFolderName = text) }

    fun confirmNewFolder() {
        val name = _uiState.value.newFolderName.trim()
        if (name.isEmpty()) {
            dismissNewFolderDialog()
            return
        }
        val server = currentServer() ?: return
        val parentPath = _uiState.value.currentPath
        _uiState.update { it.copy(showNewFolderDialog = false, newFolderName = "") }
        viewModelScope.launch {
            val result = copyPartyApi.createFolder(server.serverUrl, server.password, "", parentPath, name)
            handleMutationResult(result)
        }
    }

    private fun handleMutationResult(result: CopyPartyResult) {
        when (result) {
            is CopyPartyResult.Success -> refresh()
            is CopyPartyResult.HttpError ->
                _uiState.update { it.copy(error = "Server error (HTTP ${result.code}): ${result.message}") }
            is CopyPartyResult.NetworkError ->
                _uiState.update { it.copy(error = result.exception.message ?: "Network error") }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    fun toggleViewMode() = _uiState.update {
        it.copy(viewMode = if (it.viewMode == BrowseViewMode.LIST) BrowseViewMode.GRID else BrowseViewMode.LIST)
    }

    /** Selecting the already-active field flips direction; selecting a new field resets to ascending. */
    fun setSortField(field: BrowseSortField) = _uiState.update {
        if (it.sortField == field) it.copy(sortAscending = !it.sortAscending)
        else it.copy(sortField = field, sortAscending = true)
    }

    /** URL + password pair for fetching a thumbnail, or null if no server is selected. */
    fun thumbnailRequest(entry: RemoteEntry): Pair<String, String>? {
        val server = currentServer() ?: return null
        val path = joinPath(_uiState.value.currentPath, entry.name)
        val url = copyPartyApi.thumbnailUrl(server.serverUrl, "", path) ?: return null
        return url to server.password
    }

    // --- Upload into current folder (reuses ShareUploadWorker as-is) ---

    fun uploadFile(uri: Uri, displayName: String) {
        val server = currentServer() ?: return
        val remoteDirPath = "/" + _uiState.value.currentPath
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val cached = copyToCache(context, uri, displayName, "browse_uploads") ?: return@withContext
                val sessionId = up2kSessionDao.insert(
                    Up2kSessionEntity(
                        sourceUri = cached.absolutePath,
                        serverId = server.id,
                        remoteDirPath = remoteDirPath,
                        fileName = displayName,
                        fileSize = cached.length(),
                        fileLastModified = cached.lastModified() / 1000L,
                        status = UploadSessionStatus.PENDING,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                val request = OneTimeWorkRequestBuilder<ShareUploadWorker>()
                    .setInputData(ShareUploadWorker.buildInputData(sessionId))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
                workManager.enqueueUniqueWork(
                    UploadControlReceiver.workNameFor(sessionId),
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
            }
        }
    }

    // --- Download and hand off to Android's open-with chooser ---

    fun downloadAndOpen(entry: RemoteEntry) {
        if (entry.isDirectory) return
        val server = currentServer() ?: return
        val path = joinPath(_uiState.value.currentPath, entry.name)
        viewModelScope.launch {
            val dir = File(context.cacheDir, "browse_downloads/${UUID.randomUUID()}").apply { mkdirs() }
            val dest = File(dir, entry.name)
            val result = try {
                copyPartyApi.downloadFile(server.serverUrl, server.password, "", path) { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (e: IOException) {
                CopyPartyResult.NetworkError(e)
            }
            when (result) {
                is CopyPartyResult.Success -> {
                    val mimeType = MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(entry.name.substringAfterLast('.', ""))
                        ?: "*/*"
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dest)
                    _uiState.update { it.copy(fileToOpen = OpenFileRequest(uri, mimeType)) }
                }
                is CopyPartyResult.HttpError ->
                    _uiState.update { it.copy(error = "Download failed (HTTP ${result.code})") }
                is CopyPartyResult.NetworkError ->
                    _uiState.update { it.copy(error = result.exception.message ?: "Network error") }
            }
        }
    }

    fun onFileOpened() = _uiState.update { it.copy(fileToOpen = null) }

    // --- In-app media viewer ---

    fun openMediaViewer(entry: RemoteEntry) {
        val server = currentServer() ?: return
        // Use the currently displayed (sorted) order so swiping matches what's on screen,
        // not the server's raw listing order.
        val mediaEntries = _uiState.value.sortedEntries.filter { it.isThumbnailable() }
        val startIndex = mediaEntries.indexOf(entry)
        if (startIndex < 0) return
        _uiState.update {
            it.copy(
                mediaViewer = MediaViewerState(
                    entries = mediaEntries,
                    startIndex = startIndex,
                    serverUrl = server.serverUrl,
                    password = server.password,
                    remoteBasePath = it.currentPath,
                ),
            )
        }
    }

    fun closeMediaViewer() = _uiState.update { it.copy(mediaViewer = null) }

    /** Builds a media entry's raw file URL, given the snapshot a [MediaViewerState] was opened with. */
    fun mediaFileUrl(serverUrl: String, remoteBasePath: String, entryName: String): String? =
        copyPartyApi.fileUrl(serverUrl, "", joinPath(remoteBasePath, entryName))

    private fun currentServer(): ServerProfile? =
        _uiState.value.servers.find { it.id == _uiState.value.selectedServerId }

    private fun joinPath(base: String, name: String) = if (base.isEmpty()) name else "$base/$name"
}
