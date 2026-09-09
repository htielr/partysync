package com.karthick.partysync.data.remote

import android.os.Handler
import android.os.Looper
import com.karthick.partysync.domain.model.ChatEvent
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import javax.inject.Inject

/**
 * Android can kill a backgrounded app's live socket at any time (confirmed via
 * `SocketException: Software caused connection abort` while the system share/file picker was
 * foregrounded - reconnect attempts fail outright until the app returns to foreground, this is
 * not a one-off dead-socket bug). This client reconnects with backoff on its own, and calls
 * [onReconnected] after any reconnect that follows a real drop so the caller can re-fetch
 * history via [ChatRelayApi.getHistory] - chat-relay does not queue undelivered broadcasts per
 * client, so anything sent during the outage is otherwise silently lost.
 */
class ChatRelayWebSocketClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {

    fun connect(
        baseUrl: String,
        apiKey: String,
        room: String,
        onEvent: (ChatEvent) -> Unit,
        onReconnected: () -> Unit = {},
    ): AutoCloseable {
        val wsUrl = normalizeChatRelayBaseUrl(baseUrl)
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://") + "/api/webhook/ws?room=$room"

        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        val mainHandler = Handler(Looper.getMainLooper())
        var userClosed = false
        var currentSocket: WebSocket? = null
        var reconnectDelayMs = 1000L
        var hasConnectedBefore = false

        lateinit var open: () -> Unit

        val scheduleReconnect: () -> Unit = {
            mainHandler.postDelayed({
                if (!userClosed) {
                    open()
                    reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(15_000L)
                }
            }, reconnectDelayMs)
        }

        open = {
            currentSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    reconnectDelayMs = 1000L
                    if (hasConnectedBefore) onReconnected()
                    hasConnectedBefore = true
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    parseEvent(text)?.let(onEvent)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (!userClosed) scheduleReconnect()
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (!userClosed && code != 1000) scheduleReconnect()
                }
            })
        }

        open()

        return AutoCloseable {
            userClosed = true
            currentSocket?.close(1000, "leaving screen")
        }
    }

    private fun parseEvent(text: String): ChatEvent? = try {
        val obj = JSONObject(text)
        when (obj.optString("type")) {
            "message" -> ChatEvent.NewMessage(
                id = obj.optLong("id", 0L),
                kind = obj.optString("kind", "text"),
                content = obj.optNullableString("content"),
                filename = obj.optNullableString("filename"),
            )
            "status" -> ChatEvent.Status(obj.optString("text", ""))
            "cleared" -> ChatEvent.Cleared
            else -> null
        }
    } catch (e: org.json.JSONException) {
        null
    }
}
