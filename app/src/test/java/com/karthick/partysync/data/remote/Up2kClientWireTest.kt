package com.karthick.partysync.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// org.json parsing is stubbed to no-ops under plain JVM unit tests — see CopyPartyApiTest.
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
class Up2kClientWireTest {

    private lateinit var server: MockWebServer
    private lateinit var client: Up2kClient
    private lateinit var serverUrl: String

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        serverUrl = server.url("/").toString()
        client = Up2kClient(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `handshake sends a JSON POST to the directory URL with the hash list`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"wark": "abc123", "hash": ["needed-1", "needed-2"]}""",
            ),
        )

        val result = client.handshake(
            serverUrl = serverUrl,
            password = "s3cret",
            remoteDirPath = "/backups/phone",
            fileName = "video.mp4",
            fileSize = 123456L,
            lastModifiedSeconds = 1_700_000_000L,
            chunkHashes = listOf("hash-a", "hash-b"),
        )

        val success = assertIs<Up2kHandshakeResult.Success>(result)
        assertEquals("abc123", success.info.wark)
        assertEquals(listOf("needed-1", "needed-2"), success.info.neededHashes)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("s3cret", request.getHeader("PW"))
        assertTrue(request.getHeader("Content-Type")?.contains("application/json") == true)
        assertTrue(request.path?.startsWith("/backups/phone/") == true)

        val body = JSONObject(request.body.readUtf8())
        assertEquals("video.mp4", body.getString("name"))
        assertEquals(123456L, body.getLong("size"))
        assertEquals(1_700_000_000L, body.getLong("lmod"))
        assertEquals(true, body.getBoolean("replace"))
        assertEquals(2, body.getJSONArray("hash").length())
        assertEquals("hash-a", body.getJSONArray("hash").getString(0))
    }

    @Test
    fun `handshake with empty needed list means the file is already fully present`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"wark": "xyz", "hash": []}"""))

        val result = client.handshake(serverUrl, "pw", "/x", "f.bin", 10L, 0L, listOf("h1"))

        val success = assertIs<Up2kHandshakeResult.Success>(result)
        assertTrue(success.info.neededHashes.isEmpty())
    }

    @Test
    fun `handshake classifies a 401 as HttpError`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))

        val result = client.handshake(serverUrl, "pw", "/x", "f.bin", 10L, 0L, listOf("h1"))

        assertTrue(result is Up2kHandshakeResult.HttpError)
    }

    @Test
    fun `uploadChunkBatch with a single chunk sends the plain hash with no comma suffix`() = runBlocking {
        // Verified against a real server: a lone chunk in a request is only safe when the file
        // has exactly one chunk total — this is the one-total-chunk case.
        server.enqueue(MockResponse().setResponseCode(200).setBody("thank"))

        val bytes = "chunk bytes".toByteArray()
        val result = client.uploadChunkBatch(
            serverUrl = serverUrl,
            password = "s3cret",
            remoteDirPath = "/backups/phone",
            wark = "wark-123",
            chunkHashes = listOf("chunkhash-abc"),
            bytes = bytes,
            length = bytes.size,
        )

        assertIs<CopyPartyResult.Success>(result)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("chunkhash-abc", request.getHeader("X-Up2k-Hash"))
        assertEquals("wark-123", request.getHeader("X-Up2k-Wark"))
        assertEquals("s3cret", request.getHeader("PW"))
        assertTrue(request.getHeader("Content-Type")?.contains("application/octet-stream") == true)
        assertEquals("chunk bytes", request.body.readUtf8())
    }

    @Test
    fun `uploadChunkBatch with multiple chunks uses the compact firstHash,N,siblings header`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("thank"))

        val bytes = "four chunks worth of bytes".toByteArray()
        // 4 hashes -> n = (192/4).coerceIn(2,9) = 9; siblings truncated to first 9 chars each.
        val hashes = listOf(
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB",
            "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC",
            "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD",
        )
        client.uploadChunkBatch(serverUrl, "s3cret", "/x", "wark-abc", hashes, bytes, bytes.size)

        val request = server.takeRequest()
        assertEquals(
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA,9,BBBBBBBBBCCCCCCCCCDDDDDDDDD",
            request.getHeader("X-Up2k-Hash"),
        )
    }

    @Test
    fun `uploadChunkBatch classifies a hash mismatch error`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("your chunk got corrupted"))

        val bytes = "bytes".toByteArray()
        val result = client.uploadChunkBatch(serverUrl, "pw", "/x", "wark", listOf("hash1", "hash2"), bytes, bytes.size)

        assertIs<CopyPartyResult.HttpError>(result)
        assertEquals(400, (result as CopyPartyResult.HttpError).code)
    }
}
