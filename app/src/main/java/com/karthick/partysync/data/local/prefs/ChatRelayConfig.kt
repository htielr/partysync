package com.karthick.partysync.data.local.prefs

/** Chat Relay is a single fixed service (unlike copyparty's multi-server profiles). */
data class ChatRelayConfig(
    val baseUrl: String,
    val apiKey: String,
)
