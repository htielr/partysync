package com.karthick.partysync.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// org.json.JSONObject/JSONArray are stubbed to no-op defaults under plain (non-Robolectric)
// JVM unit tests — Robolectric supplies the real implementation so listDirectory's JSON
// parsing actually runs instead of silently returning empty results.
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
class CopyPartyApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: CopyPartyApi
    private lateinit var serverUrl: String

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        serverUrl = server.url("/").toString()
        api = CopyPartyApi(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `upload sends PUT with PW header, replace query param, and correct path`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))

        val bytes = "hello world".toByteArray()
        val result = api.uploadFile(
            serverUrl = serverUrl,
            password = "s3cret",
            remoteBasePath = "/backups/phone",
            relativePath = "sub dir/photo.jpg",
            contentLength = bytes.size.toLong(),
        ) { bytes.inputStream() }

        assertTrue(result is CopyPartyResult.Success)

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("s3cret", request.getHeader("PW"))
        assertTrue(request.path?.startsWith("/backups/phone/sub%20dir/photo.jpg") == true)
        assertTrue(request.path?.contains("replace") == true)
        assertEquals("hello world", request.body.readUtf8())
    }

    @Test
    fun `upload classifies a 401 as an HttpError`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))

        val result = api.uploadFile(serverUrl, "s3cret", "/x", "a.txt", 1L) { "a".toByteArray().inputStream() }

        assertTrue(result is CopyPartyResult.HttpError)
        assertEquals(401, (result as CopyPartyResult.HttpError).code)
    }

    @Test
    fun `download sends GET with PW header and streams body to the callback`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("remote content"))

        var received: String? = null
        val result = api.downloadFile(serverUrl, "s3cret", "/backups/phone", "notes.txt") { stream ->
            received = stream.readBytes().toString(Charsets.UTF_8)
        }

        assertTrue(result is CopyPartyResult.Success)
        assertEquals("remote content", received)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("s3cret", request.getHeader("PW"))
    }

    @Test
    fun `listDirectory parses dirs and files from the ls JSON schema`() = runBlocking {
        // Shape confirmed against copyparty's own httpcli.py tx_browser/tx_ls: "name" and "dt"
        // are stripped from the JSON branch, leaving href/sz/ext/ts; dirs' href ends in "/".
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "dirs": [
                    {"lead": "-", "href": "sub%20dir/", "sz": 0, "ext": "---", "ts": 1700000000}
                  ],
                  "files": [
                    {"lead": "-", "href": "photo.jpg", "sz": 12345, "ext": "jpg", "ts": 1700000100}
                  ]
                }
                """.trimIndent(),
            ),
        )

        val result = api.listDirectory(serverUrl, "s3cret", "/backups/phone", "")

        assertTrue(result is CopyPartyListResult.Success)
        val entries = (result as CopyPartyListResult.Success).entries
        assertEquals(2, entries.size)

        val dir = entries.single { it.isDirectory }
        assertEquals("sub dir", dir.name)

        val file = entries.single { !it.isDirectory }
        assertEquals("photo.jpg", file.name)
        assertEquals(12345L, file.size)
        assertEquals(1700000100L * 1000L, file.lastModifiedMillis)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertTrue(request.path?.contains("ls") == true)
    }

    @Test
    fun `listDirectory classifies a 403 as an HttpError`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403))

        val result = api.listDirectory(serverUrl, "s3cret", "/x", "")

        assertTrue(result is CopyPartyListResult.HttpError)
        assertEquals(403, (result as CopyPartyListResult.HttpError).code)
    }

    @Test
    fun `delete sends DELETE with PW header and correct path for a file`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))

        val result = api.delete(serverUrl, "s3cret", "/backups/phone", "notes.txt")

        assertTrue(result is CopyPartyResult.Success)

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("s3cret", request.getHeader("PW"))
        assertTrue(request.path?.startsWith("/backups/phone/notes.txt") == true)
    }

    @Test
    fun `delete sends DELETE with correct path for a folder`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))

        val result = api.delete(serverUrl, "s3cret", "/backups/phone", "old dir")

        assertTrue(result is CopyPartyResult.Success)

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertTrue(request.path?.startsWith("/backups/phone/old%20dir") == true)
    }

    @Test
    fun `delete classifies a 403 as an HttpError`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403))

        val result = api.delete(serverUrl, "s3cret", "/x", "a.txt")

        assertTrue(result is CopyPartyResult.HttpError)
        assertEquals(403, (result as CopyPartyResult.HttpError).code)
    }

    @Test
    fun `move sends POST with move query param containing the destination vpath`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201))

        val result = api.move(serverUrl, "s3cret", "/backups/phone", "old.txt", "new.txt")

        assertTrue(result is CopyPartyResult.Success)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("s3cret", request.getHeader("PW"))
        assertTrue(request.path?.startsWith("/backups/phone/old.txt") == true)
        assertTrue(request.path?.contains("move=%2Fbackups%2Fphone%2Fnew.txt") == true)
    }

    @Test
    fun `move classifies a 500 as an HttpError`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = api.move(serverUrl, "s3cret", "/x", "old.txt", "new.txt")

        assertTrue(result is CopyPartyResult.HttpError)
        assertEquals(500, (result as CopyPartyResult.HttpError).code)
    }

    @Test
    fun `createFolder sends multipart POST with act=mkdir and name fields to the parent path`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201))

        val result = api.createFolder(serverUrl, "s3cret", "/backups/phone", "", "New Folder")

        assertTrue(result is CopyPartyResult.Success)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("s3cret", request.getHeader("PW"))
        assertTrue(request.path?.startsWith("/backups/phone") == true)
        val body = request.body.readUtf8()
        assertTrue(body.contains("name=\"act\""))
        assertTrue(body.contains("mkdir"))
        assertTrue(body.contains("name=\"name\""))
        assertTrue(body.contains("New Folder"))
    }

    @Test
    fun `createFolder classifies a 405 name-collision as an HttpError`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(405))

        val result = api.createFolder(serverUrl, "s3cret", "/x", "", "dup")

        assertTrue(result is CopyPartyResult.HttpError)
        assertEquals(405, (result as CopyPartyResult.HttpError).code)
    }
}
