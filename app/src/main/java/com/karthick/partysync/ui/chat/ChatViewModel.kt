package com.karthick.partysync.ui.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.karthick.partysync.data.local.prefs.ChatRelayConfig
import com.karthick.partysync.data.local.prefs.ChatRelayRepository
import com.karthick.partysync.data.remote.ChatRelayApi
import com.karthick.partysync.data.remote.ChatRelayHistoryResult
import com.karthick.partysync.data.remote.ChatRelayResult
import com.karthick.partysync.data.remote.ChatRelayWebSocketClient
import com.karthick.partysync.domain.model.CHAT_ROOM_VAULT
import com.karthick.partysync.domain.model.ChatEvent
import com.karthick.partysync.domain.model.ChatMessage
import com.karthick.partysync.sync.worker.ChatRelayUploadWorker
import com.karthick.partysync.util.copyToCache
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val config: ChatRelayConfig? = null,
    val selectedRoom: String = CHAT_ROOM_VAULT,
    val messages: List<ChatMessage> = emptyList(),
    val isSending: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatRelayRepository: ChatRelayRepository,
    private val chatRelayApi: ChatRelayApi,
    private val chatRelayWebSocketClient: ChatRelayWebSocketClient,
    private val workManager: WorkManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var socket: AutoCloseable? = null

    init {
        viewModelScope.launch {
            chatRelayRepository.config.collect { config ->
                _uiState.update { it.copy(config = config) }
                reconnect()
            }
        }
    }

    fun saveConfig(baseUrl: String, apiKey: String) {
        chatRelayRepository.save(baseUrl.trim(), apiKey.trim())
    }

    fun selectRoom(room: String) {
        if (room == _uiState.value.selectedRoom) return
        _uiState.update { it.copy(selectedRoom = room, messages = emptyList()) }
        reconnect()
    }

    private fun reconnect() {
        socket?.close()
        socket = null
        val config = _uiState.value.config ?: return
        val room = _uiState.value.selectedRoom

        viewModelScope.launch { refreshHistory(config, room) }

        socket = chatRelayWebSocketClient.connect(
            baseUrl = config.baseUrl,
            apiKey = config.apiKey,
            room = room,
            onEvent = { event -> appendLive(room, event) },
            onReconnected = { viewModelScope.launch { refreshHistory(config, room) } },
        )
    }

    private suspend fun refreshHistory(config: ChatRelayConfig, room: String) {
        // A room switch or reconnect may have raced this call - only apply the result if we're
        // still looking at the room it was fetched for.
        when (val result = chatRelayApi.getHistory(config.baseUrl, config.apiKey, room)) {
            is ChatRelayHistoryResult.Success -> {
                _uiState.update { if (it.selectedRoom == room) it.copy(messages = result.messages) else it }
            }
            is ChatRelayHistoryResult.HttpError ->
                _uiState.update { it.copy(errorMessage = "Failed to load history: HTTP ${result.code}") }
            is ChatRelayHistoryResult.NetworkError ->
                _uiState.update { it.copy(errorMessage = "Failed to load history: ${result.exception.message}") }
        }
    }

    // Called from ChatRelayWebSocketClient's listener, which fires on OkHttp's dispatcher
    // thread - MutableStateFlow.update {} does an atomic compare-and-set retry, safe to call
    // from any thread, unlike a plain read-modify-write of `.value`.
    private fun appendLive(room: String, event: ChatEvent) {
        val newMessage = when (event) {
            is ChatEvent.NewMessage -> ChatMessage(
                id = event.id,
                room = room,
                kind = event.kind,
                content = event.content,
                filename = event.filename,
            )
            is ChatEvent.Status -> ChatMessage(
                id = -System.nanoTime(),
                room = room,
                kind = "status",
                content = event.text,
            )
        }
        _uiState.update { if (it.selectedRoom == room) it.copy(messages = it.messages + newMessage) else it }
    }

    fun sendText(text: String) {
        val config = _uiState.value.config ?: return
        if (text.isBlank()) return
        _uiState.update { it.copy(isSending = true) }
        viewModelScope.launch {
            when (val result = chatRelayApi.sendMessage(config.baseUrl, config.apiKey, _uiState.value.selectedRoom, text)) {
                is ChatRelayResult.Success -> _uiState.update { it.copy(isSending = false) }
                is ChatRelayResult.HttpError -> _uiState.update {
                    it.copy(isSending = false, errorMessage = result.message ?: "Send failed (HTTP ${result.code})")
                }
                is ChatRelayResult.NetworkError -> _uiState.update {
                    it.copy(isSending = false, errorMessage = result.exception.message)
                }
            }
        }
    }

    fun uploadFile(uri: Uri) {
        val room = _uiState.value.selectedRoom
        viewModelScope.launch {
            val displayName = queryDisplayName(uri) ?: "shared-file"
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val cached = copyToCache(context, uri, displayName, "chat_relay_uploads")
            if (cached == null) {
                _uiState.update { it.copy(errorMessage = "Could not read file") }
                return@launch
            }
            val request = OneTimeWorkRequestBuilder<ChatRelayUploadWorker>()
                .setInputData(ChatRelayUploadWorker.buildInputData(room, cached.absolutePath, displayName, mimeType))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            workManager.enqueue(request)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    } catch (e: Exception) {
        null
    }

    override fun onCleared() {
        socket?.close()
    }
}
