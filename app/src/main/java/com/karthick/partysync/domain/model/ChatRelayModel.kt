package com.karthick.partysync.domain.model

const val CHAT_ROOM_VAULT = "vault"
const val CHAT_ROOM_GENERAL = "general"

data class ChatMessage(
    val id: Long,
    val room: String,
    val kind: String, // text | file | status
    val content: String? = null,
    val filename: String? = null,
    val createdAt: Long = 0L,
)

/** Parsed form of the WebSocket events chat-relay broadcasts. */
sealed class ChatEvent {
    data class NewMessage(
        val id: Long,
        val kind: String,
        val content: String? = null,
        val filename: String? = null,
    ) : ChatEvent()

    data class Status(val text: String) : ChatEvent()

    /** Someone (any client) cleared this room's history - broadcast so every live viewer clears too. */
    data object Cleared : ChatEvent()
}
