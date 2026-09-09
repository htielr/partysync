package com.karthick.partysync.data.remote

import com.karthick.partysync.domain.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

private val OCTET_STREAM = "application/octet-stream".toMediaType()
private val JSON = "application/json".toMediaType()

/**
 * Thin HTTP client for gem's chat-relay webhook routes under `api/webhook` (history, message,
 * upload - see the project memory for the full contract) - bearer-token authed, unlike
 * copyparty's `PW` header. `:room` is always `vault` or `general`
 * ([com.karthick.partysync.domain.model.CHAT_ROOM_VAULT] /
 * [com.karthick.partysync.domain.model.CHAT_ROOM_GENERAL]).
 */
class ChatRelayApi @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {

    suspend fun getHistory(baseUrl: String, apiKey: String, room: String): ChatRelayHistoryResult {
        val url = roomUrl(baseUrl, room, "history")
            ?: return ChatRelayHistoryResult.NetworkError(IOException("Invalid server URL: $baseUrl"))

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()

        return withContext(Dispatchers.IO) {
            try {
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use ChatRelayHistoryResult.HttpError(response.code, response.message)
                    }
                    val body = response.body?.string().orEmpty()
                    ChatRelayHistoryResult.Success(parseHistory(body))
                }
            } catch (e: IOException) {
                ChatRelayHistoryResult.NetworkError(e)
            }
        }
    }

    suspend fun sendMessage(baseUrl: String, apiKey: String, room: String, text: String): ChatRelayResult {
        val url = roomUrl(baseUrl, room, "message")
            ?: return ChatRelayResult.NetworkError(IOException("Invalid server URL: $baseUrl"))

        val body = JSONObject().put("text", text).toString().toRequestBody(JSON)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        return execute(request)
    }

    suspend fun uploadFile(
        baseUrl: String,
        apiKey: String,
        room: String,
        filename: String,
        contentLength: Long,
        mimeType: String,
        openStream: () -> InputStream,
    ): ChatRelayResult {
        val url = roomUrl(baseUrl, room, "upload")
            ?: return ChatRelayResult.NetworkError(IOException("Invalid server URL: $baseUrl"))

        val mediaType = mimeType.toMediaTypeOrDefault()
        val fileBody = object : RequestBody() {
            override fun contentType() = mediaType
            override fun contentLength() = contentLength
            override fun writeTo(sink: BufferedSink) {
                openStream().use { input -> sink.writeAll(input.source()) }
            }
        }

        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", filename, fileBody)
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .post(multipart)
            .build()

        return execute(request)
    }

    private fun roomUrl(baseUrl: String, room: String, action: String) =
        normalizeChatRelayBaseUrl(baseUrl).toHttpUrlOrNull()?.newBuilder()
            ?.addPathSegments("api/webhook")
            ?.addPathSegment(room)
            ?.addPathSegment(action)
            ?.build()

    private fun parseHistory(json: String): List<ChatMessage> {
        val root = JSONObject(json)
        val array = root.optJSONArray("messages") ?: return emptyList()
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            ChatMessage(
                id = obj.getLong("id"),
                room = obj.optString("room"),
                kind = obj.optString("kind", "text"),
                content = obj.optNullableString("content"),
                filename = obj.optNullableString("filename"),
                createdAt = obj.optLong("created_at", 0L),
            )
        }
    }

    private fun String.toMediaTypeOrDefault() = toMediaTypeOrNullSafe() ?: OCTET_STREAM

    private fun String.toMediaTypeOrNullSafe() = try { toMediaType() } catch (e: IllegalArgumentException) { null }

    private suspend fun execute(request: Request): ChatRelayResult = withContext(Dispatchers.IO) {
        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    ChatRelayResult.Success
                } else {
                    ChatRelayResult.HttpError(response.code, response.message)
                }
            }
        } catch (e: IOException) {
            ChatRelayResult.NetworkError(e)
        }
    }
}
