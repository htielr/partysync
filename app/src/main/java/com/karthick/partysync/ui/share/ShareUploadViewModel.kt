package com.karthick.partysync.ui.share

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.karthick.partysync.data.local.db.Up2kSessionDao
import com.karthick.partysync.data.local.db.Up2kSessionEntity
import com.karthick.partysync.data.local.prefs.ChatRelayRepository
import com.karthick.partysync.data.local.prefs.ServerProfile
import com.karthick.partysync.data.local.prefs.ServerRepository
import com.karthick.partysync.data.remote.ChatRelayApi
import com.karthick.partysync.data.remote.ChatRelayResult
import com.karthick.partysync.data.remote.RemoteFolderBrowser
import com.karthick.partysync.data.remote.RemoteFolderListResult
import com.karthick.partysync.data.repository.FolderMappingRepository
import com.karthick.partysync.domain.model.CHAT_ROOM_GENERAL
import com.karthick.partysync.domain.model.CHAT_ROOM_VAULT
import com.karthick.partysync.domain.model.UploadSessionStatus
import com.karthick.partysync.sync.worker.ChatRelayUploadWorker
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class SharedFileInfo(val uri: Uri, val displayName: String, val size: Long)

enum class ShareDestination { SYNC_FOLDER, CHAT_RELAY }

data class ShareUploadUiState(
    val files: List<SharedFileInfo> = emptyList(),
    val sharedText: String? = null,
    val destination: ShareDestination = ShareDestination.SYNC_FOLDER,
    val chatRelayConfigured: Boolean = false,
    val chatRoom: String = CHAT_ROOM_VAULT,
    val servers: List<ServerProfile> = emptyList(),
    val selectedServerId: Long? = null,
    val remotePath: String = "",
    val pathSuggestions: List<String> = emptyList(),
    val isUploading: Boolean = false,
    val isDone: Boolean = false,
    val folderBrowser: RemoteFolderBrowserState = RemoteFolderBrowserState(),
) {
    // Bare shared text has nowhere to go in a sync folder - and Chat Relay's General room
    // takes files only, so text is Vault-or-nothing.
    val canUpload: Boolean
        get() = when {
            isUploading -> false
            sharedText != null -> destination == ShareDestination.CHAT_RELAY && chatRoom == CHAT_ROOM_VAULT
            destination == ShareDestination.CHAT_RELAY -> chatRelayConfigured && files.isNotEmpty()
            else -> files.isNotEmpty() && selectedServerId != null && remotePath.startsWith("/")
        }
}

@HiltViewModel
class ShareUploadViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serverRepository: ServerRepository,
    private val folderMappingRepository: FolderMappingRepository,
    private val up2kSessionDao: Up2kSessionDao,
    private val remoteFolderBrowser: RemoteFolderBrowser,
    private val workManager: WorkManager,
    private val chatRelayRepository: ChatRelayRepository,
    private val chatRelayApi: ChatRelayApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShareUploadUiState())
    val uiState: StateFlow<ShareUploadUiState> = _uiState.asStateFlow()

    init {
        val servers = serverRepository.servers.value
        _uiState.update {
            it.copy(servers = servers, selectedServerId = if (servers.size == 1) servers.single().id else null)
        }
        viewModelScope.launch { refreshPathSuggestions() }
        viewModelScope.launch {
            chatRelayRepository.config.collect { config ->
                _uiState.update { it.copy(chatRelayConfigured = config != null) }
            }
        }
    }

    /** Called once by [ShareUploadActivity] right after the ViewModel is created. */
    fun setSharedUris(uris: List<Uri>) {
        val resolved = uris.map(::resolveFileInfo)
        _uiState.update { it.copy(files = resolved) }
    }

    /** Called once by [ShareUploadActivity] for a text/link share (no destination but Chat Relay). */
    fun setSharedText(text: String) {
        _uiState.update { it.copy(sharedText = text, destination = ShareDestination.CHAT_RELAY) }
    }

    fun onDestinationSelected(destination: ShareDestination) {
        _uiState.update { it.copy(destination = destination) }
    }

    fun onChatRoomSelected(room: String) {
        _uiState.update { it.copy(chatRoom = room) }
    }

    private fun resolveFileInfo(uri: Uri): SharedFileInfo {
        var name = uri.lastPathSegment ?: "shared_file"
        var size = -1L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx >= 0) cursor.getString(nameIdx)?.let { name = it }
                if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
            }
        }
        return SharedFileInfo(uri, name, size)
    }

    fun onServerSelected(serverId: Long) {
        _uiState.update { it.copy(selectedServerId = serverId) }
        viewModelScope.launch { refreshPathSuggestions() }
    }

    private suspend fun refreshPathSuggestions() {
        val serverId = _uiState.value.selectedServerId ?: return
        val suggestions = folderMappingRepository.observeAll().first()
            .filter { it.serverId == serverId }
            .map { it.remoteBasePath }
            .distinct()
        _uiState.update { it.copy(pathSuggestions = suggestions) }
    }

    fun onRemotePathChanged(path: String) = _uiState.update { it.copy(remotePath = path) }

    /** Opens the live folder browser at the server root. Requires a server to already be selected. */
    fun openFolderBrowser() {
        if (_uiState.value.selectedServerId == null) return
        _uiState.update { it.copy(folderBrowser = RemoteFolderBrowserState(isOpen = true, currentPath = "")) }
        loadFolderBrowserContents()
    }

    fun closeFolderBrowser() {
        _uiState.update { it.copy(folderBrowser = RemoteFolderBrowserState()) }
    }

    fun navigateFolderBrowserInto(folderName: String) {
        val current = _uiState.value.folderBrowser.currentPath
        val next = if (current.isEmpty()) folderName else "$current/$folderName"
        _uiState.update { it.copy(folderBrowser = it.folderBrowser.copy(currentPath = next)) }
        loadFolderBrowserContents()
    }

    fun navigateFolderBrowserUp() {
        val current = _uiState.value.folderBrowser.currentPath
        if (current.isEmpty()) return
        val parent = current.substringBeforeLast('/', missingDelimiterValue = "")
        _uiState.update { it.copy(folderBrowser = it.folderBrowser.copy(currentPath = parent)) }
        loadFolderBrowserContents()
    }

    fun selectCurrentFolderBrowserPath() {
        val path = "/${_uiState.value.folderBrowser.currentPath}"
        _uiState.update { it.copy(remotePath = path, folderBrowser = RemoteFolderBrowserState()) }
    }

    private fun loadFolderBrowserContents() {
        val server = _uiState.value.servers.find { it.id == _uiState.value.selectedServerId } ?: return
        val path = _uiState.value.folderBrowser.currentPath
        _uiState.update { it.copy(folderBrowser = it.folderBrowser.copy(isLoading = true, error = null)) }
        viewModelScope.launch {
            val result = remoteFolderBrowser.listFolders(server.serverUrl, server.password, path)
            _uiState.update { state ->
                val browser = when (result) {
                    is RemoteFolderListResult.Success ->
                        state.folderBrowser.copy(isLoading = false, folders = result.folders)
                    is RemoteFolderListResult.Failure ->
                        state.folderBrowser.copy(isLoading = false, error = result.message)
                }
                state.copy(folderBrowser = browser)
            }
        }
    }

    fun upload() {
        when (_uiState.value.destination) {
            ShareDestination.SYNC_FOLDER -> uploadToSyncFolder()
            ShareDestination.CHAT_RELAY -> uploadToChatRelay()
        }
    }

    /**
     * Copies each shared file into the app's private cache (while this activity is still
     * alive — see the project plan for why that's required for the upload to survive the
     * activity closing), creates one [Up2kSessionEntity] per file, and enqueues one
     * [ShareUploadWorker] per session under a session-scoped unique work name so
     * [UploadControlReceiver]'s Pause action can target exactly one upload.
     */
    private fun uploadToSyncFolder() {
        val state = _uiState.value
        val serverId = state.selectedServerId ?: return
        if (state.files.isEmpty()) return

        _uiState.update { it.copy(isUploading = true) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                for (file in state.files) {
                    val cached = copyToCache(file) ?: continue
                    val sessionId = up2kSessionDao.insert(
                        Up2kSessionEntity(
                            sourceUri = cached.absolutePath,
                            serverId = serverId,
                            remoteDirPath = state.remotePath,
                            fileName = file.displayName,
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
            _uiState.update { it.copy(isUploading = false, isDone = true) }
        }
    }

    /**
     * Chat Relay's upload API has no chunking/resume support, so unlike [uploadToSyncFolder]
     * there's no [Up2kSessionEntity] per file - just a cached copy + a one-shot
     * [ChatRelayUploadWorker] each. Shared text (Vault only, see [ShareUploadUiState.canUpload])
     * is small enough to send directly rather than via a worker.
     */
    private fun uploadToChatRelay() {
        val state = _uiState.value

        val text = state.sharedText
        if (text != null) {
            _uiState.update { it.copy(isUploading = true) }
            viewModelScope.launch {
                val config = chatRelayRepository.config.value
                if (config == null) {
                    _uiState.update { it.copy(isUploading = false) }
                    return@launch
                }
                when (chatRelayApi.sendMessage(config.baseUrl, config.apiKey, state.chatRoom, text)) {
                    is ChatRelayResult.Success -> _uiState.update { it.copy(isUploading = false, isDone = true) }
                    else -> _uiState.update { it.copy(isUploading = false) }
                }
            }
            return
        }

        if (state.files.isEmpty()) return
        _uiState.update { it.copy(isUploading = true) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                for (file in state.files) {
                    val cached = copyToCache(file) ?: continue
                    val mimeType = context.contentResolver.getType(file.uri) ?: "application/octet-stream"
                    val request = OneTimeWorkRequestBuilder<ChatRelayUploadWorker>()
                        .setInputData(
                            ChatRelayUploadWorker.buildInputData(
                                room = state.chatRoom,
                                filePath = cached.absolutePath,
                                filename = file.displayName,
                                mimeType = mimeType,
                            ),
                        )
                        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .build()
                    workManager.enqueue(request)
                }
            }
            _uiState.update { it.copy(isUploading = false, isDone = true) }
        }
    }

    private fun copyToCache(file: SharedFileInfo): File? =
        copyToCache(context, file.uri, file.displayName, "share_uploads")
}
