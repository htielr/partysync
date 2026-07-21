package com.karthick.partysync.sync

import com.karthick.partysync.data.remote.CopyPartyApi
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

// See CopyPartyApiTest for why Robolectric is required here: org.json parsing is stubbed
// to no-ops under plain JVM unit tests.
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
class RemoteFolderScannerTest {

    private lateinit var server: MockWebServer
    private lateinit var scanner: RemoteFolderScanner
    private lateinit var serverUrl: String

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        serverUrl = server.url("/").toString()
        scanner = RemoteFolderScanner(CopyPartyApi(OkHttpClient()))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `recurses into subdirectories and collects files with accumulated relative paths`() = runBlocking {
        // Root listing: one file + one subdirectory.
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"dirs": [{"href": "sub/", "sz": 0, "ts": 1000}], "files": [{"href": "root.txt", "sz": 10, "ts": 2000}]}""",
            ),
        )
        // Subdirectory listing: one file.
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"dirs": [], "files": [{"href": "nested.txt", "sz": 20, "ts": 3000}]}""",
            ),
        )

        val result = scanner.scan(serverUrl, "pw", "/backups/phone")

        val success = assertIs<RemoteScanResult.Success>(result)
        val byPath = success.files.associateBy { it.relativePath }
        assertEquals(2, success.files.size)
        assertEquals(10L, byPath.getValue("root.txt").size)
        assertEquals(20L, byPath.getValue("sub/nested.txt").size)
        assertEquals(3000L * 1000L, byPath.getValue("sub/nested.txt").lastModified)
    }

    @Test
    fun `propagates a listing failure instead of returning partial results`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))

        val result = scanner.scan(serverUrl, "pw", "/backups/phone")

        assertIs<RemoteScanResult.Failure>(result)
        assertTrue((result as RemoteScanResult.Failure).message.contains("401"))
    }
}
