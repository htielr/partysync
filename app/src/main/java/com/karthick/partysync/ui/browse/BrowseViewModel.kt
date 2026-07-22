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
import com.karthick.partysync.data.remote.RemoteFolderBrowser
import com.karthick.partysync.data.remote.RemoteFolderListResult
import com.karthick.partysync.domain.model.UploadSessionStatus
import com.karthick.partysync.sync.RemoteFolderScanner
import com.karthick.partysync.sync.RemoteScanResult
import com.karthick.partysync.sync.worker.ShareUploadWorker
import com.karthick.partysync.sync.worker.UploadControlReceiver
import com.karthick.partysync.ui.common.RemoteFolderBrowserState
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
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject

data class OpenFileRequest(val uri: Uri, val mimeType: String)

data class ShareRequest(val uris: List<Uri>, val mimeType: String)

enum class BrowseViewMode { LIST, GRID }

enum class BrowseSortField { NAME, DATE, SIZE }

enum class ClipboardOperation { CUT, COPY }

/** Snapshot of a cut/copy selection — independent of current navigation so it survives moving
 * to a different folder before pasting. Paste is only valid on the same server it came from. */
data class ClipboardState(
    val entryNames: Set<String>,
    val sourcePath: String,
    val serverId: Long,
    val operation: ClipboardOperation,
)

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
    val renameTarget: RemoteEntry? = null,
    val renameText: String = "",
    val showNewFolderDialog: Boolean = false,
    val newFolderName: String = "",
    val fileToOpen: OpenFileRequest? = null,
    val shareRequest: ShareRequest? = null,
    val viewMode: BrowseViewMode = BrowseViewMode.LIST,
    val mediaViewer: MediaViewerState? = null,
    val sortField: BrowseSortField = BrowseSortField.NAME,
    val sortAscending: Boolean = true,
    val selectedEntries: Set<String> = emptySet(),
    val showDeleteConfirm: Boolean = false,
    val clipboard: ClipboardState? = null,
    val moveTargetBrowser: RemoteFolderBrowserState = RemoteFolderBrowserState(),
    val showZipNameDialog: Boolean = false,
    val zipName: String = "",
    val archiveProgressMessage: String? = null,
) {
    val breadcrumbSegments: List<String>
        get() = currentPath.split('/').filter { it.isNotEmpty() }

    val isSelectionMode: Boolean
        get() = selectedEntries.isNotEmpty()

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
    private val remoteFolderBrowser: RemoteFolderBrowser,
    private val remoteFolderScanner: RemoteFolderScanner,
    private val up2kSessionDao: Up2kSessionDao,
    private val workManager: WorkManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    init {
        // Reactive, not a one-time snapshot — this ViewModel survives tab switches (the nav
        // graph keeps it alive via popUpTo/saveState), so a server added after first visiting
        // Browse must still show up without needing to kill and reopen the app.
        viewModelScope.launch {
            serverRepository.servers.collect { servers ->
                _uiState.update { it.copy(servers = servers) }
            }
        }

        val servers = serverRepository.servers.value
        val lastId = settingsRepository.settings.value.lastBrowsedServerId
        val initialServerId = when {
            servers.any { it.id == lastId } -> lastId
            servers.size == 1 -> servers.single().id
            else -> null
        }
        _uiState.update { it.copy(selectedServerId = initialServerId) }
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

    // --- Selection ---

    /** Used for both long-press (starts a new selection from empty) and tap-while-selecting (extends/shrinks it). */
    fun toggleSelection(entry: RemoteEntry) = _uiState.update {
        val current = it.selectedEntries
        it.copy(selectedEntries = if (entry.name in current) current - entry.name else current + entry.name)
    }

    fun clearSelection() = _uiState.update { it.copy(selectedEntries = emptySet()) }

    private fun selectedEntryNames(): Set<String> = _uiState.value.selectedEntries

    // --- Delete (acts on the current selection — may be 1 or many entries) ---

    fun requestDeleteSelection() = _uiState.update { it.copy(showDeleteConfirm = true) }
    fun dismissDeleteConfirm() = _uiState.update { it.copy(showDeleteConfirm = false) }

    fun confirmDeleteSelection() {
        val server = currentServer() ?: return
        val names = selectedEntryNames()
        val path = _uiState.value.currentPath
        _uiState.update { it.copy(showDeleteConfirm = false, selectedEntries = emptySet()) }
        viewModelScope.launch {
            var lastError: CopyPartyResult? = null
            for (name in names) {
                val result = copyPartyApi.delete(server.serverUrl, server.password, "", joinPath(path, name))
                if (result !is CopyPartyResult.Success) lastError = result
            }
            refresh()
            lastError?.let(::showMutationError)
        }
    }

    // --- Cut / Copy / Paste ---

    fun cutSelection() = snapshotClipboard(ClipboardOperation.CUT)
    fun copySelection() = snapshotClipboard(ClipboardOperation.COPY)

    private fun snapshotClipboard(operation: ClipboardOperation) {
        val server = currentServer() ?: return
        val names = selectedEntryNames()
        if (names.isEmpty()) return
        _uiState.update {
            it.copy(
                clipboard = ClipboardState(names, it.currentPath, server.id, operation),
                selectedEntries = emptySet(),
            )
        }
    }

    fun canPaste(): Boolean {
        val clip = _uiState.value.clipboard ?: return false
        return clip.serverId == _uiState.value.selectedServerId
    }

    fun clearClipboard() = _uiState.update { it.copy(clipboard = null) }

    fun pasteClipboard() {
        val clip = _uiState.value.clipboard ?: return
        val server = currentServer() ?: return
        if (server.id != clip.serverId) return
        val destPath = _uiState.value.currentPath
        _uiState.update { it.copy(clipboard = null) }
        viewModelScope.launch {
            var lastError: CopyPartyResult? = null
            for (name in clip.entryNames) {
                val sourcePath = joinPath(clip.sourcePath, name)
                val destEntryPath = joinPath(destPath, name)
                val result = when (clip.operation) {
                    ClipboardOperation.CUT -> copyPartyApi.move(server.serverUrl, server.password, "", sourcePath, destEntryPath)
                    ClipboardOperation.COPY -> copyPartyApi.copy(server.serverUrl, server.password, "", sourcePath, destEntryPath)
                }
                if (result !is CopyPartyResult.Success) lastError = result
            }
            refresh()
            lastError?.let(::showMutationError)
        }
    }

    // --- Move to... (folder picker, reuses RemoteFolderBrowser like the share-upload/add-mapping pickers) ---

    fun openMoveTargetPicker() {
        if (selectedEntryNames().isEmpty()) return
        _uiState.update { it.copy(moveTargetBrowser = RemoteFolderBrowserState(isOpen = true, currentPath = it.currentPath)) }
        loadMoveTargetContents()
    }

    fun closeMoveTargetPicker() = _uiState.update { it.copy(moveTargetBrowser = RemoteFolderBrowserState()) }

    fun navigateMoveTargetInto(folderName: String) {
        val current = _uiState.value.moveTargetBrowser.currentPath
        val next = if (current.isEmpty()) folderName else "$current/$folderName"
        _uiState.update { it.copy(moveTargetBrowser = it.moveTargetBrowser.copy(currentPath = next)) }
        loadMoveTargetContents()
    }

    fun navigateMoveTargetUp() {
        val current = _uiState.value.moveTargetBrowser.currentPath
        if (current.isEmpty()) return
        val parent = current.substringBeforeLast('/', missingDelimiterValue = "")
        _uiState.update { it.copy(moveTargetBrowser = it.moveTargetBrowser.copy(currentPath = parent)) }
        loadMoveTargetContents()
    }

    fun confirmMoveTarget() {
        val server = currentServer() ?: return
        val destPath = _uiState.value.moveTargetBrowser.currentPath
        val sourcePath = _uiState.value.currentPath
        val names = selectedEntryNames()
        _uiState.update { it.copy(moveTargetBrowser = RemoteFolderBrowserState(), selectedEntries = emptySet()) }
        viewModelScope.launch {
            var lastError: CopyPartyResult? = null
            for (name in names) {
                val result = copyPartyApi.move(
                    server.serverUrl,
                    server.password,
                    "",
                    joinPath(sourcePath, name),
                    joinPath(destPath, name),
                )
                if (result !is CopyPartyResult.Success) lastError = result
            }
            refresh()
            lastError?.let(::showMutationError)
        }
    }

    private fun loadMoveTargetContents() {
        val server = currentServer() ?: return
        val path = _uiState.value.moveTargetBrowser.currentPath
        _uiState.update { it.copy(moveTargetBrowser = it.moveTargetBrowser.copy(isLoading = true, error = null)) }
        viewModelScope.launch {
            val result = remoteFolderBrowser.listFolders(server.serverUrl, server.password, path)
            _uiState.update { state ->
                val browser = when (result) {
                    is RemoteFolderListResult.Success ->
                        state.moveTargetBrowser.copy(isLoading = false, folders = result.folders)
                    is RemoteFolderListResult.Failure ->
                        state.moveTargetBrowser.copy(isLoading = false, error = result.message)
                }
                state.copy(moveTargetBrowser = browser)
            }
        }
    }

    private fun showMutationError(result: CopyPartyResult) {
        val message = when (result) {
            is CopyPartyResult.HttpError -> "Server error (HTTP ${result.code}): ${result.message}"
            is CopyPartyResult.NetworkError -> result.exception.message ?: "Network error"
            is CopyPartyResult.Success -> return
        }
        _uiState.update { it.copy(error = message) }
    }

    // --- Rename (selection top bar action, only enabled for exactly 1 selected entry) ---

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
        _uiState.update { it.copy(renameTarget = null, renameText = "", selectedEntries = emptySet()) }
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
                enqueueUpload(server, cached, displayName, remoteDirPath)
            }
        }
    }

    /** Enqueues an already-local file for up2k upload — shared by regular uploads, zipping, and unzipping. */
    private suspend fun enqueueUpload(server: ServerProfile, localFile: File, displayName: String, remoteDirPath: String) {
        val sessionId = up2kSessionDao.insert(
            Up2kSessionEntity(
                sourceUri = localFile.absolutePath,
                serverId = server.id,
                remoteDirPath = remoteDirPath,
                fileName = displayName,
                fileSize = localFile.length(),
                fileLastModified = localFile.lastModified() / 1000L,
                status = UploadSessionStatus.PENDING,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            ),
        )
        val request = OneTimeWorkRequestBuilder<ShareUploadWorker>()
            .setInputData(ShareUploadWorker.buildInputData(sessionId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniqueWork(UploadControlReceiver.workNameFor(sessionId), ExistingWorkPolicy.REPLACE, request)
    }

    // --- Download and hand off to Android's open-with chooser ---

    fun downloadAndOpen(entry: RemoteEntry) {
        if (entry.isDirectory) return
        val server = currentServer() ?: return
        val path = joinPath(_uiState.value.currentPath, entry.name)
        viewModelScope.launch {
            val (uri, mimeType) = downloadEntryToCache(server, path, entry) ?: return@launch
            _uiState.update { it.copy(fileToOpen = OpenFileRequest(uri, mimeType)) }
        }
    }

    fun onFileOpened() = _uiState.update { it.copy(fileToOpen = null) }

    // --- Share to other apps (selection top bar action) ---

    fun shareSelection() {
        val server = currentServer() ?: return
        val path = _uiState.value.currentPath
        val entries = _uiState.value.entries.filter { it.name in selectedEntryNames() && !it.isDirectory }
        _uiState.update { it.copy(selectedEntries = emptySet()) }
        if (entries.isEmpty()) return
        viewModelScope.launch {
            val downloaded = entries.mapNotNull { entry -> downloadEntryToCache(server, joinPath(path, entry.name), entry) }
            if (downloaded.isEmpty()) return@launch
            val mimeTypes = downloaded.map { it.second }.distinct()
            val mimeType = mimeTypes.singleOrNull() ?: "*/*"
            _uiState.update { it.copy(shareRequest = ShareRequest(downloaded.map { it.first }, mimeType)) }
        }
    }

    fun onShareHandled() = _uiState.update { it.copy(shareRequest = null) }

    /** Downloads one entry into a fresh cache subdirectory, returning its shareable URI + MIME type. */
    private suspend fun downloadEntryToCache(server: ServerProfile, path: String, entry: RemoteEntry): Pair<Uri, String>? {
        val dir = File(context.cacheDir, "browse_downloads/${UUID.randomUUID()}").apply { mkdirs() }
        val dest = File(dir, entry.name)
        val result = try {
            copyPartyApi.downloadFile(server.serverUrl, server.password, "", path) { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: IOException) {
            CopyPartyResult.NetworkError(e)
        }
        return when (result) {
            is CopyPartyResult.Success -> {
                val mimeType = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(entry.name.substringAfterLast('.', ""))
                    ?: "*/*"
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dest)
                uri to mimeType
            }
            else -> {
                showMutationError(result)
                null
            }
        }
    }

    /** Downloads one entry directly to [destFile] (a plain local path, not a shareable URI). */
    private suspend fun downloadToFile(server: ServerProfile, remoteRelativePath: String, destFile: File): Boolean {
        val result = try {
            copyPartyApi.downloadFile(server.serverUrl, server.password, "", remoteRelativePath) { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: IOException) {
            CopyPartyResult.NetworkError(e)
        }
        if (result !is CopyPartyResult.Success) showMutationError(result)
        return result is CopyPartyResult.Success
    }

    // --- Zip selection (recursively, for folders) into a new archive uploaded to the current folder ---

    private data class ZipItem(val sourcePath: String, val archivePath: String)

    fun requestZipSelection() {
        val names = selectedEntryNames()
        if (names.isEmpty()) return
        val suggested = if (names.size == 1) {
            val name = names.single()
            name.substringBeforeLast('.', name) + ".zip"
        } else {
            "Archive.zip"
        }
        _uiState.update { it.copy(showZipNameDialog = true, zipName = suggested) }
    }

    fun dismissZipNameDialog() = _uiState.update { it.copy(showZipNameDialog = false, zipName = "") }
    fun onZipNameChanged(text: String) = _uiState.update { it.copy(zipName = text) }

    fun confirmZip() {
        val server = currentServer() ?: return
        var name = _uiState.value.zipName.trim()
        if (name.isEmpty()) {
            dismissZipNameDialog()
            return
        }
        if (!name.endsWith(".zip", ignoreCase = true)) name += ".zip"
        val selectedNames = selectedEntryNames()
        val entries = _uiState.value.entries.filter { it.name in selectedNames }
        val remoteDirPath = _uiState.value.currentPath
        _uiState.update {
            it.copy(
                showZipNameDialog = false,
                zipName = "",
                selectedEntries = emptySet(),
                archiveProgressMessage = "Zipping…",
            )
        }
        viewModelScope.launch {
            val items = collectZipItems(server, entries)
            if (items.isEmpty()) {
                _uiState.update { it.copy(archiveProgressMessage = null, error = "Nothing to zip") }
                return@launch
            }
            val outDir = File(context.cacheDir, "browse_zips/${UUID.randomUUID()}").apply { mkdirs() }
            val zipFile = File(outDir, name)
            withContext(Dispatchers.IO) {
                ZipOutputStream(zipFile.outputStream()).use { zos ->
                    for (item in items) appendToZip(server, item.sourcePath, item.archivePath, zos)
                }
            }
            enqueueUpload(server, zipFile, name, "/$remoteDirPath")
            _uiState.update { it.copy(archiveProgressMessage = null) }
        }
    }

    /** Expands the selection into individual file entries: files as-is, folders recursively via [RemoteFolderScanner]. */
    private suspend fun collectZipItems(server: ServerProfile, entries: List<RemoteEntry>): List<ZipItem> {
        val items = mutableListOf<ZipItem>()
        val currentPath = _uiState.value.currentPath
        for (entry in entries) {
            if (entry.isDirectory) {
                val folderPath = joinPath(currentPath, entry.name)
                val scan = remoteFolderScanner.scan(server.serverUrl, server.password, folderPath)
                if (scan is RemoteScanResult.Success) {
                    scan.files.forEach { file ->
                        items += ZipItem(
                            sourcePath = joinPath(folderPath, file.relativePath),
                            archivePath = "${entry.name}/${file.relativePath}",
                        )
                    }
                }
            } else {
                items += ZipItem(sourcePath = joinPath(currentPath, entry.name), archivePath = entry.name)
            }
        }
        return items
    }

    /** Streams one file straight from the network response into a zip entry — no in-memory buffering of the whole file. */
    private suspend fun appendToZip(server: ServerProfile, sourcePath: String, archivePath: String, zos: ZipOutputStream) {
        try {
            copyPartyApi.downloadFile(server.serverUrl, server.password, "", sourcePath) { input ->
                zos.putNextEntry(ZipEntry(archivePath))
                input.copyTo(zos)
                zos.closeEntry()
            }
        } catch (e: IOException) {
            // best-effort: skip this file, keep zipping the rest
        }
    }

    // --- Unzip a selected .zip file into a new subfolder named after it ---

    fun canUnzip(entry: RemoteEntry): Boolean = !entry.isDirectory && entry.name.endsWith(".zip", ignoreCase = true)

    fun requestUnzip(entry: RemoteEntry) {
        val server = currentServer() ?: return
        val remoteDirPath = _uiState.value.currentPath
        val baseName = entry.name.dropLast(4) // strip ".zip" (length checked by canUnzip before this is called)
        val destFolderName = uniqueFolderName(baseName)
        _uiState.update { it.copy(selectedEntries = emptySet(), archiveProgressMessage = "Unzipping…") }
        viewModelScope.launch {
            val workDir = File(context.cacheDir, "browse_unzips/${UUID.randomUUID()}").apply { mkdirs() }
            val zipFile = File(workDir, entry.name)
            val downloaded = downloadToFile(server, joinPath(remoteDirPath, entry.name), zipFile)
            if (!downloaded) {
                _uiState.update { it.copy(archiveProgressMessage = null) }
                return@launch
            }

            val mkdirResult = copyPartyApi.createFolder(server.serverUrl, server.password, "", remoteDirPath, destFolderName)
            if (mkdirResult !is CopyPartyResult.Success) {
                _uiState.update { it.copy(archiveProgressMessage = null) }
                showMutationError(mkdirResult)
                return@launch
            }

            val destRemoteBase = joinPath(remoteDirPath, destFolderName)
            val createdDirs = mutableSetOf<String>()
            withContext(Dispatchers.IO) {
                ZipInputStream(zipFile.inputStream()).use { zis ->
                    var zipEntry = zis.nextEntry
                    while (zipEntry != null) {
                        if (!zipEntry.isDirectory) {
                            val entryPath = zipEntry.name.trim('/')
                            val parentDir = entryPath.substringBeforeLast('/', missingDelimiterValue = "")
                            if (parentDir.isNotEmpty() && parentDir !in createdDirs) {
                                ensureRemoteDirs(server, destRemoteBase, parentDir, createdDirs)
                            }
                            val localOut = File(workDir, "extracted/$entryPath").apply { parentFile?.mkdirs() }
                            localOut.outputStream().use { output -> zis.copyTo(output) }
                            val remoteDir = if (parentDir.isEmpty()) destRemoteBase else joinPath(destRemoteBase, parentDir)
                            enqueueUpload(server, localOut, localOut.name, "/$remoteDir")
                        }
                        zis.closeEntry()
                        zipEntry = zis.nextEntry
                    }
                }
            }
            _uiState.update { it.copy(archiveProgressMessage = null) }
            refresh()
        }
    }

    /** Creates each path segment of [relativeDirPath] (parent before child) under [base] if not already done this run. */
    private suspend fun ensureRemoteDirs(server: ServerProfile, base: String, relativeDirPath: String, created: MutableSet<String>) {
        val segments = relativeDirPath.split('/')
        var current = ""
        for (segment in segments) {
            val parent = current
            current = if (current.isEmpty()) segment else "$current/$segment"
            if (current !in created) {
                copyPartyApi.createFolder(server.serverUrl, server.password, "", joinPath(base, parent), segment)
                created += current
            }
        }
    }

    private fun uniqueFolderName(base: String): String {
        val existingNames = _uiState.value.entries.map { it.name }.toSet()
        if (base !in existingNames) return base
        var i = 1
        while ("$base ($i)" in existingNames) i++
        return "$base ($i)"
    }

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
