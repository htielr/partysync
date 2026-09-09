package com.karthick.partysync.data.remote

import com.karthick.partysync.domain.model.ChatMessage

sealed class ChatRelayResult {
    data object Success : ChatRelayResult()
    data class HttpError(val code: Int, val message: String?) : ChatRelayResult()
    data class NetworkError(val exception: Exception) : ChatRelayResult()
}

sealed class ChatRelayHistoryResult {
    data class Success(val messages: List<ChatMessage>) : ChatRelayHistoryResult()
    data class HttpError(val code: Int, val message: String?) : ChatRelayHistoryResult()
    data class NetworkError(val exception: Exception) : ChatRelayHistoryResult()
}
