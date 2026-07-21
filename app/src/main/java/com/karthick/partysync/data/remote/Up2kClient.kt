package com.karthick.partysync.data.remote

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.math.ceil

data class Up2kHandshakeInfo(val wark: String, val neededHashes: List<String>)

sealed class Up2kHandshakeResult {
    data class Success(val info: Up2kHandshakeInfo) : Up2kHandshakeResult()
    data class HttpError(val code: Int, val message: String?) : Up2kHandshakeResult()
    data class NetworkError(val exception: Exception) : Up2kHandshakeResult()
}

private val JSON_MEDIA_TYPE = "application/json".toMediaType()
private val OCTET_STREAM_MEDIA_TYPE = "application/octet-stream".toMediaType()

/**
 * Client for copyparty's up2k chunked resumable upload protocol. Protocol details (chunk-size
 * formula, hash truncation, handshake/chunk-upload wire shape) are verified directly against
 * copyparty's own source (`up2k.py`, `bin/u2c.py`, `httpcli.py`) — see the project plan for the
 * exact quotes. Deliberately separate from [CopyPartyApi] (which stays the simple PUT/GET/`?ls`
 * client) since this protocol is a distinct, more complex wire format.
 */
class Up2kClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {

    /**
     * Ported verbatim from `up2k_chunksize()` in copyparty's `up2k.py`/`bin/u2c.py` — this is an
     * iterative search, not a simple lookup table, and the exact sequence of intermediate
     * chunksize/stepsize values matters for matching the server's own calculation.
     */
    fun chunkSize(fileSize: Long): Int {
        var chunkSize = 1024L * 1024L
        var stepSize = 512L * 1024L
        while (true) {
            for (mul in intArrayOf(1, 2)) {
                val nChunks = ceil(fileSize.toDouble() / chunkSize.toDouble()).toLong()
                if (nChunks <= 256 || (chunkSize >= 32L * 1024L * 1024L && nChunks <= 4096)) {
                    return chunkSize.toInt()
                }
                chunkSize += stepSize
                stepSize *= mul
            }
        }
    }

    /** SHA-512 digest truncated to the first 33 bytes, URL-safe base64, no padding. */
    fun hashChunk(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): String {
        val digest = MessageDigest.getInstance("SHA-512").let { md ->
            md.update(bytes, offset, length)
            md.digest()
        }
        val truncated = digest.copyOf(33)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(truncated)
    }

    /**
     * POSTs the ordered chunk-hash list to the destination directory. The server responds with
     * a `wark` (session id it assigns) and the subset of [chunkHashes] it still needs — an empty
     * needed-list means the file is already fully present (previously uploaded, or dedup against
     * identical content elsewhere on the server).
     */
    suspend fun handshake(
        serverUrl: String,
        password: String,
        remoteDirPath: String,
        fileName: String,
        fileSize: Long,
        lastModifiedSeconds: Long,
        chunkHashes: List<String>,
    ): Up2kHandshakeResult {
        val baseUrl = serverUrl.toHttpUrlOrNull()
            ?: return Up2kHandshakeResult.NetworkError(IOException("Invalid server URL: $serverUrl"))

        val bodyJson = JSONObject()
            .put("hash", JSONArray(chunkHashes))
            .put("name", fileName)
            .put("lmod", lastModifiedSeconds)
            .put("size", fileSize)
            .put("replace", true)
            .toString()

        val request = Request.Builder()
            .url(directoryUrl(baseUrl, remoteDirPath))
            .header("PW", password)
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return executeCancellable(
            request,
            onSuccess = { response ->
                val json = JSONObject(response.body?.string().orEmpty())
                val neededArray = json.getJSONArray("hash")
                val needed = (0 until neededArray.length()).map { neededArray.getString(it) }
                Up2kHandshakeResult.Success(Up2kHandshakeInfo(json.getString("wark"), needed))
            },
            onHttpError = { code, message -> Up2kHandshakeResult.HttpError(code, message) },
            onNetworkError = { e -> Up2kHandshakeResult.NetworkError(e) },
        )
    }

    /**
     * Uploads one or more **consecutive** chunks (in file order) in a single request. Returns
     * the same [CopyPartyResult] shape as [CopyPartyApi]'s plain PUT so callers can reuse
     * [classifyCopyPartyResult] unchanged.
     *
     * **[chunkHashes] must never have exactly 1 entry unless the file has exactly 1 chunk in
     * total.** This was empirically verified against a real copyparty server, not found by
     * reading the source: a request carrying exactly one chunk hash silently fails to decrement
     * the server's "still needed" bookkeeping whenever the file has more than one chunk overall
     * — the bytes get written and the request returns success ("thank"), but the upload never
     * finalizes (it stays a `.PARTIAL` file forever, and a follow-up handshake still reports
     * every chunk as needed). Batches of 2+ chunks per request work reliably, whether sent as
     * one request for the whole file or split across several sequential requests. Callers
     * (`ShareUploadWorker`, and later `SyncEngine`) must group needed chunks into batches of at
     * least 2 before calling this — never dispatch a lone final chunk on its own.
     */
    suspend fun uploadChunkBatch(
        serverUrl: String,
        password: String,
        remoteDirPath: String,
        wark: String,
        chunkHashes: List<String>,
        bytes: ByteArray,
        length: Int,
    ): CopyPartyResult {
        val baseUrl = serverUrl.toHttpUrlOrNull()
            ?: return CopyPartyResult.NetworkError(IOException("Invalid server URL: $serverUrl"))

        val request = Request.Builder()
            .url(directoryUrl(baseUrl, remoteDirPath))
            .header("PW", password)
            .header("X-Up2k-Hash", hashHeaderFor(chunkHashes))
            .header("X-Up2k-Wark", wark)
            .post(bytes.toRequestBody(OCTET_STREAM_MEDIA_TYPE, 0, length))
            .build()

        return executeCancellable(
            request,
            onSuccess = { CopyPartyResult.Success },
            onHttpError = { code, message -> CopyPartyResult.HttpError(code, message) },
            onNetworkError = { e -> CopyPartyResult.NetworkError(e) },
        )
    }

    /**
     * `X-Up2k-Hash` wire format, ported from `bin/u2c.py`'s `upload()`: a single hash is sent
     * as-is; multiple consecutive hashes are compacted as `firstHash,N,siblingsTruncatedToNChars`
     * (N clamped to [2, 9]) to save header space — the server re-expands the truncated sibling
     * prefixes back to full hashes using the file's known ordered hash list from the handshake.
     */
    private fun hashHeaderFor(chunkHashes: List<String>): String {
        if (chunkHashes.size == 1) return chunkHashes[0]
        val n = (192 / chunkHashes.size).coerceIn(2, 9)
        val siblings = chunkHashes.drop(1).joinToString("") { it.take(n) }
        return "${chunkHashes[0]},$n,$siblings"
    }

    private fun directoryUrl(baseUrl: HttpUrl, remoteDirPath: String): HttpUrl {
        val builder = baseUrl.newBuilder()
        remoteDirPath.split('/').filter { it.isNotEmpty() }.forEach { builder.addPathSegment(it) }
        return builder.addPathSegment("").build()
    }

    /**
     * Runs [request] via OkHttp's async API wrapped in [suspendCancellableCoroutine], with
     * `invokeOnCancellation { call.cancel() }` — unlike a plain blocking `.execute()` inside
     * `withContext(Dispatchers.IO)`, this actually aborts the in-flight HTTP request when the
     * coroutine is cancelled (e.g. the user taps Pause), instead of letting it run to completion
     * regardless.
     */
    private suspend fun <T> executeCancellable(
        request: Request,
        onSuccess: (Response) -> T,
        onHttpError: (Int, String?) -> T,
        onNetworkError: (Exception) -> T,
    ): T = suspendCancellableCoroutine { continuation ->
        val call = okHttpClient.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resume(onNetworkError(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val result = if (response.isSuccessful) {
                        try {
                            onSuccess(response)
                        } catch (e: Exception) {
                            onNetworkError(e)
                        }
                    } else {
                        onHttpError(response.code, response.message)
                    }
                    if (continuation.isActive) continuation.resume(result)
                }
            }
        })
    }
}
