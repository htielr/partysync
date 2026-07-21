package com.karthick.partysync.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
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

    /**
     * Builds a thumbnail URL for an entry via copyparty's `?th=j` endpoint (verified against the
     * real server and `copyparty/web/browser.js`'s own grid-view thumbnail construction): works
     * uniformly on any path — images get a real thumbnail, videos get a frame-extract thumbnail,
     * everything else gets a generic icon. Callers only invoke this for entries they've already
     * decided are image/video files (see `ui/browse/ThumbnailSupport.kt`). `j` = JPEG, no crop
     * suffix = cropped-square (this server's own configured default, confirmed via `?ls`'s
     * `"dcrop": "y"`). Pure URL construction, no network call — auth (`PW` header) is added by
     * the caller when it actually fetches the image.
     */
    fun thumbnailUrl(serverUrl: String, remoteBasePath: String, relativePath: String): String? {
        val baseUrl = serverUrl.toHttpUrlOrNull() ?: return null
        return filePathUrl(baseUrl, remoteBasePath, relativePath)
            .newBuilder()
            .addQueryParameter("th", "j")
            .build()
            .toString()
    }

    /**
     * Deletes a file or folder (recursively, if a non-empty folder — confirmed against the real
     * server, copyparty does not refuse or require an extra confirmation param). Verified via
     * `HTTP DELETE` on the entry's own vpath, no trailing slash needed either way.
     */
    suspend fun delete(
        serverUrl: String,
        password: String,
        remoteBasePath: String,
        relativePath: String,
    ): CopyPartyResult {
        val baseUrl = serverUrl.toHttpUrlOrNull()
            ?: return CopyPartyResult.NetworkError(IOException("Invalid server URL: $serverUrl"))

        val url = filePathUrl(baseUrl, remoteBasePath, relativePath)
        val request = Request.Builder()
            .url(url)
            .header("PW", password)
            .delete()
            .build()

        return execute(request) { }
    }

    /**
     * Renames or moves a file/folder. copyparty has no separate rename endpoint — a rename is
     * just a move where only the last path segment changes: `POST` to the source vpath with a
     * `move` query param carrying the full destination vpath from server root (verified: literal
     * `/` separators, no percent-encoding needed — OkHttp's [HttpUrl.Builder.addQueryParameter]
     * doesn't encode `/` either, and the real server accepts it as-is).
     */
    suspend fun move(
        serverUrl: String,
        password: String,
        remoteBasePath: String,
        sourceRelativePath: String,
        destRelativePath: String,
    ): CopyPartyResult {
        val baseUrl = serverUrl.toHttpUrlOrNull()
            ?: return CopyPartyResult.NetworkError(IOException("Invalid server URL: $serverUrl"))

        val destVpath = "/" + (remoteBasePath.split('/') + destRelativePath.split('/'))
            .filter { it.isNotEmpty() }
            .joinToString("/")

        val url = filePathUrl(baseUrl, remoteBasePath, sourceRelativePath)
            .newBuilder()
            .addQueryParameter("move", destVpath)
            .build()

        val request = Request.Builder()
            .url(url)
            .header("PW", password)
            .post("".toRequestBody(null))
            .build()

        return execute(request) { }
    }

    /**
     * Creates a new folder inside [parentRelativePath]. Verified against the real server:
     * `POST multipart/form-data` to the parent directory's vpath (no trailing slash needed) with
     * plain text fields `act=mkdir` and `name=<folder name>`.
     */
    suspend fun createFolder(
        serverUrl: String,
        password: String,
        remoteBasePath: String,
        parentRelativePath: String,
        folderName: String,
    ): CopyPartyResult {
        val baseUrl = serverUrl.toHttpUrlOrNull()
            ?: return CopyPartyResult.NetworkError(IOException("Invalid server URL: $serverUrl"))

        val builder = baseUrl.newBuilder()
        (remoteBasePath.split('/') + parentRelativePath.split('/'))
            .filter { it.isNotEmpty() }
            .forEach { segment -> builder.addPathSegment(segment) }
        val url = builder.build()

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("act", "mkdir")
            .addFormDataPart("name", folderName)
            .build()

        val request = Request.Builder()
            .url(url)
            .header("PW", password)
            .post(body)
            .build()

        return execute(request) { }
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
