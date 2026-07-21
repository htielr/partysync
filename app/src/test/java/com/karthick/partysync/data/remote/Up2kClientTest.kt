package com.karthick.partysync.data.remote

import okhttp3.OkHttpClient
import org.junit.Test
import kotlin.math.ceil
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Up2kClientTest {

    private val client = Up2kClient(OkHttpClient())

    // Expected values hand-traced against the exact ported algorithm (see Up2kClient.chunkSize
    // kdoc) — each one exercises a different number of while-loop iterations and both mul
    // branches, to catch a subtly wrong port rather than just re-deriving the same formula.

    @Test
    fun `tiny file uses the 1 MiB floor`() {
        assertEquals(1024 * 1024, client.chunkSize(1L))
    }

    @Test
    fun `300 MiB resolves to 1_5 MiB after one step`() {
        assertEquals((1.5 * 1024 * 1024).toInt(), client.chunkSize(300L * 1024 * 1024))
    }

    @Test
    fun `1 GiB resolves to 4 MiB after multiple loop iterations`() {
        assertEquals(4 * 1024 * 1024, client.chunkSize(1024L * 1024 * 1024))
    }

    @Test
    fun `chunk count never exceeds the algorithm's own termination guarantee`() {
        val sizes = listOf(0L, 1L, 1_000L, 1_000_000L, 1_000_000_000L, 1_000_000_000_000L, 10_000_000_000_000L)
        for (size in sizes) {
            val chunkSize = client.chunkSize(size.coerceAtLeast(1))
            val nChunks = ceil(size.coerceAtLeast(1).toDouble() / chunkSize).toLong()
            assertTrue(
                nChunks <= 4096,
                "size=$size chunkSize=$chunkSize nChunks=$nChunks exceeds the 4096 cap",
            )
        }
    }

    @Test
    fun `hashChunk produces a 33-byte SHA-512 prefix, url-safe base64, no padding`() {
        val hash = client.hashChunk("hello world".toByteArray())

        // 33 raw bytes -> 44 base64 chars with padding, 44 without trailing '=' since 33*8/6 = 44 exactly
        assertEquals(44, hash.length)
        assertTrue(hash.none { it == '+' || it == '/' || it == '=' }, "expected URL-safe, unpadded base64: $hash")
    }

    @Test
    fun `hashChunk is deterministic for identical input`() {
        val a = client.hashChunk("same content".toByteArray())
        val b = client.hashChunk("same content".toByteArray())
        assertEquals(a, b)
    }

    @Test
    fun `hashChunk differs for different input`() {
        val a = client.hashChunk("content A".toByteArray())
        val b = client.hashChunk("content B".toByteArray())
        assertTrue(a != b)
    }
}
