package com.karthick.partysync.ui.addmapping

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karthick.partysync.data.local.prefs.ServerProfile
import com.karthick.partysync.data.local.prefs.ServerRepository
import com.karthick.partysync.data.remote.RemoteFolderBrowser
import com.karthick.partysync.data.remote.RemoteFolderListResult
import com.karthick.partysync.data.repository.FolderMappingRepository
import com.karthick.partysync.domain.model.SyncMode
import com.karthick.partysync.ui.common.RemoteFolderBrowserState
import com.karthick.partysync.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Tri-state Wi-Fi override for a mapping: null means "inherit the app-wide setting." */
enum class WifiOverrideChoice { INHERIT, WIFI_ONLY, ALLOW_CELLULAR }

data class AddEditMappingUiState(
    val isEditing: Boolean = false,
    val selectedServerId: Long? = null,
    val treeUri: Uri? = null,
    val folderDisplayName: String = "",
    val remoteBasePath: String = "",
    val syncMode: SyncMode = SyncMode.ONE_WAY_UPLOAD,
    val wifiOverride: WifiOverrideChoice = WifiOverrideChoice.INHERIT,
    val enabled: Boolean = true,
    val isSaved: Boolean = false,
    val folderBrowser: RemoteFolderBrowserState = RemoteFolderBrowserState(),
) {
    val canSave: Boolean get() = selectedServerId != null && treeUri != null && remoteBasePath.startsWith("/")
}

@HiltViewModel
class AddEditMappingViewModel @Inject constructor(
    private val repository: FolderMappingRepository,
    private val serverRepository: ServerRepository,
    private val remoteFolderBrowser: RemoteFolderBrowser,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val mappingId: Long =
        savedStateHandle.get<String>(Screen.AddEditMapping.ARG_MAPPING_ID)?.toLongOrNull()
            ?: Screen.AddEditMapping.NEW_MAPPING_ID

    val servers: StateFlow<List<ServerProfile>> = serverRepository.servers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(AddEditMappingUiState())
    val uiState: StateFlow<AddEditMappingUiState> = _uiState.asStateFlow()

    init {
        if (mappingId != Screen.AddEditMapping.NEW_MAPPING_ID) {
            viewModelScope.launch {
                repository.getById(mappingId)?.let { mapping ->
                    _uiState.update {
                        it.copy(
                            isEditing = true,
                            selectedServerId = mapping.serverId,
                            treeUri = Uri.parse(mapping.treeUri),
                            folderDisplayName = mapping.displayName,
                            remoteBasePath = mapping.remoteBasePath,
                            syncMode = mapping.syncMode,
                            wifiOverride = when (mapping.wifiOnlyOverride) {
                                null -> WifiOverrideChoice.INHERIT
                                true -> WifiOverrideChoice.WIFI_ONLY
                                false -> WifiOverrideChoice.ALLOW_CELLULAR
                            },
                            enabled = mapping.enabled,
                        )
                    }
                }
            }
        } else {
            // Nice default for the common case of a single configured server.
            viewModelScope.launch {
                val servers = serverRepository.servers.value
                if (servers.size == 1) {
                    _uiState.update { it.copy(selectedServerId = servers.single().id) }
                }
            }
        }
    }

    fun onServerSelected(serverId: Long) = _uiState.update { it.copy(selectedServerId = serverId) }

    fun onFolderPicked(uri: Uri, documentFile: DocumentFile?) {
        _uiState.update {
            it.copy(
                treeUri = uri,
                folderDisplayName = documentFile?.name ?: it.folderDisplayName,
            )
        }
    }

    fun onRemoteBasePathChanged(path: String) = _uiState.update { it.copy(remoteBasePath = path) }

    fun onSyncModeChanged(mode: SyncMode) = _uiState.update { it.copy(syncMode = mode) }

    fun onWifiOverrideChanged(choice: WifiOverrideChoice) = _uiState.update { it.copy(wifiOverride = choice) }

    fun onEnabledChanged(enabled: Boolean) = _uiState.update { it.copy(enabled = enabled) }

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
        _uiState.update { it.copy(remoteBasePath = path, folderBrowser = RemoteFolderBrowserState()) }
    }

    private fun loadFolderBrowserContents() {
        val server = servers.value.find { it.id == _uiState.value.selectedServerId } ?: return
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

    fun save() {
        val state = _uiState.value
        val uri = state.treeUri ?: return
        val serverId = state.selectedServerId ?: return
        viewModelScope.launch {
            val wifiOverride = when (state.wifiOverride) {
                WifiOverrideChoice.INHERIT -> null
                WifiOverrideChoice.WIFI_ONLY -> true
                WifiOverrideChoice.ALLOW_CELLULAR -> false
            }
            if (state.isEditing) {
                val existing = repository.getById(mappingId) ?: return@launch
                repository.update(
                    existing.copy(
                        serverId = serverId,
                        remoteBasePath = state.remoteBasePath,
                        syncMode = state.syncMode,
                        wifiOnlyOverride = wifiOverride,
                        enabled = state.enabled,
                    ),
                )
            } else {
                repository.create(
                    serverId = serverId,
                    treeUri = uri,
                    displayName = state.folderDisplayName.ifBlank { "Untitled folder" },
                    remoteBasePath = state.remoteBasePath,
                    syncMode = state.syncMode,
                    wifiOnlyOverride = wifiOverride,
                )
            }
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    fun delete() {
        viewModelScope.launch {
            repository.getById(mappingId)?.let { repository.delete(it) }
            _uiState.update { it.copy(isSaved = true) }
        }
    }
}
