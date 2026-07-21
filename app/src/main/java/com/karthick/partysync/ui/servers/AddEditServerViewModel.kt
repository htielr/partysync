package com.karthick.partysync.ui.servers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karthick.partysync.data.local.prefs.ServerRepository
import com.karthick.partysync.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddEditServerUiState(
    val isEditing: Boolean = false,
    val displayName: String = "",
    val serverUrl: String = "",
    val password: String = "",
    val isSaved: Boolean = false,
) {
    val canSave: Boolean get() = displayName.isNotBlank() && serverUrl.startsWith("http")
}

@HiltViewModel
class AddEditServerViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val serverId: Long =
        savedStateHandle.get<String>(Screen.AddEditServer.ARG_SERVER_ID)?.toLongOrNull()
            ?: Screen.AddEditServer.NEW_SERVER_ID

    private val _uiState = MutableStateFlow(AddEditServerUiState())
    val uiState: StateFlow<AddEditServerUiState> = _uiState.asStateFlow()

    init {
        if (serverId != Screen.AddEditServer.NEW_SERVER_ID) {
            viewModelScope.launch {
                serverRepository.getById(serverId)?.let { server ->
                    _uiState.update {
                        it.copy(
                            isEditing = true,
                            displayName = server.displayName,
                            serverUrl = server.serverUrl,
                            password = server.password,
                        )
                    }
                }
            }
        }
    }

    fun onDisplayNameChanged(name: String) = _uiState.update { it.copy(displayName = name) }

    fun onServerUrlChanged(url: String) = _uiState.update { it.copy(serverUrl = url) }

    fun onPasswordChanged(password: String) = _uiState.update { it.copy(password = password) }

    fun save() {
        val state = _uiState.value
        if (state.isEditing) {
            serverRepository.update(serverId, state.displayName, state.serverUrl, state.password)
        } else {
            serverRepository.add(state.displayName, state.serverUrl, state.password)
        }
        _uiState.update { it.copy(isSaved = true) }
    }

    fun delete() {
        serverRepository.delete(serverId)
        _uiState.update { it.copy(isSaved = true) }
    }
}
