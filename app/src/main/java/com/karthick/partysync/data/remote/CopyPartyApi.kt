package com.karthick.partysync.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.URLDecoder
import javax.inject.Inject

private val OCTET_STREAM = "application/octet-stream".toMediaType()

/**
 * Thin HTTP client for copyparty's "bup" plain-PUT/GET upload path (not the chunked up2k
 * protocol — see the project plan for why that's deferred). Auth is the `PW` header, which
 * copyparty accepts identically over HTTP or HTTPS (simpler than its HTTP/HTTPS-dependent
 * cookie names `cppwd`/`cppws`).
 *
 * Server URL and password are passed per-call rather than read from a single global setting,
 * since a mapping can point at any of the user's configured server profiles (see
 * [com.karthick.partysync.data.local.prefs.ServerRepository]).
 */
class CopyPartyApi @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {

    suspend fun uploadFile(
        serverUrl: String,
        password: String,
        remoteBasePath: String,
        relativePath: String,
        contentLength: Long,
        openStream: () -> InputStream,
    ): CopyPartyResult {
        val baseUrl = serverUrl.toHttpUrlOrNull()
            ?: return CopyPartyResult.NetworkError(IOException("Invalid server URL: $serverUrl"))

        val url = filePathUrl(baseUrl, remoteBasePath, relativePath)
            .newBuilder()
            .addQueryParameter("replace", null)
            .build()

        val body = object : RequestBody() {
            override fun contentType() = OCTET_STREAM
            override fun contentLength() = contentLength
            override fun writeTo(sink: BufferedSink) {
                openStream().use { input -> sink.writeAll(input.source()) }
            }
        }

        val request = Request.Builder()
            .url(url)
            .header("PW", password)
            .put(body)
            .build()

        return execute(request) { }
    }

    suspend fun downloadFile(
        serverUrl: String,
        password: String,
        remoteBasePath: String,
        relativePath: String,
        writeTo: (InputStream) -> Unit,
    ): CopyPartyResult {
        val baseUrl = serverUrl.toHttpUrlOrNull()
            ?: return CopyPartyResult.NetworkError(IOException("Invalid server URL: $serverUrl"))

        val url = filePathUrl(baseUrl, remoteBasePath, relativePath).newBuilder().build()

        val request = Request.Builder()
            .url(url)
            .header("PW", password)
            .get()
            .build()

        return execute(request) { body -> body?.byteStream()?.let(writeTo) }
    }

    /**
     * Lists one directory level via copyparty's `?ls` JSON API (confirmed against copyparty's
     * own httpcli.py: top-level `dirs`/`files` arrays, each entry has `href` (URL-encoded name,
     * directories end in `/`), `sz` (bytes), and `ts` (unix seconds — converted to millis here to
     * match [com.karthick.partysync.domain.model.ScannedFile]'s millis convention). Not
     * recursive — callers walk subdirectories themselves.
     */
    suspend fun listDirectory(
        serverUrl: String,
        password: String,
        remoteBasePath: String,
        relativePath: String,
    ): CopyPartyListResult {
        val baseUrl = serverUrl.toHttpUrlOrNull()
            ?: return CopyPartyListResult.NetworkError(IOException("Invalid server URL: $serverUrl"))

        val builder = baseUrl.newBuilder()
        (remoteBasePath.split('/') + relativePath.split('/'))
            .filter { it.isNotEmpty() }
            .forEach { segment -> builder.addPathSegment(segment) }
        val url = builder.addPathSegment("").addQueryParameter("ls", null).build()

        val request = Request.Builder()
            .url(url)
            .header("PW", password)
            .get()
            .build()

        return withContext(Dispatchers.IO) {
            try {
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use CopyPartyListResult.HttpError(response.code, response.message)
                    }
                    val body = response.body?.string().orEmpty()
                    CopyPartyListResult.Success(parseListing(body))
                }
            } catch (e: IOException) {
                CopyPartyListResult.NetworkError(e)
            }
        }
    }

    private fun parseListing(json: String): List<RemoteEntry> {
        val root = JSONObject(json)
        val entries = mutableListOf<RemoteEntry>()
        fun collect(key: String, isDirectory: Boolean) {
            val array: JSONArray = root.optJSONArray(key) ?: return
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val hrefRaw = obj.getString("href")
                val hrefTrimmed = if (isDirectory) hrefRaw.removeSuffix("/") else hrefRaw
                val name = URLDecoder.decode(hrefTrimmed, "UTF-8")
                entries += RemoteEntry(
                    name = name,
                    isDirectory = isDirectory,
                    size = obj.optLong("sz", 0L),
                    lastModifiedMillis = obj.optLong("ts", 0L) * 1000L,
                )
            }
        }
        collect("dirs", isDirectory = true)
        collect("files", isDirectory = false)
        return entries
    }

    private suspend fun execute(
        request: Request,
        onSuccessBody: (okhttp3.ResponseBody?) -> Unit,
    ): CopyPartyResult = withContext(Dispatchers.IO) {
        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    onSuccessBody(response.body)
                    CopyPartyResult.Success
                } else {
                    CopyPartyResult.HttpError(response.code, response.message)
                }
            }
        } catch (e: IOException) {
            CopyPartyResult.NetworkError(e)
        }
    }

    private fun filePathUrl(baseUrl: HttpUrl, remoteBasePath: String, relativePath: String): HttpUrl {
        val builder = baseUrl.newBuilder()
        (remoteBasePath.split('/') + relativePath.split('/'))
            .filter { it.isNotEmpty() }
            .forEach { segment -> builder.addPathSegment(segment) }
        return builder.build()
    }
}
