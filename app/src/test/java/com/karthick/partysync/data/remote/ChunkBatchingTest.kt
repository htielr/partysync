package com.karthick.partysync.data.remote

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChunkBatchingTest {

    private fun hashes(n: Int) = (0 until n).map { "hash$it" }

    @Test
    fun `no needed chunks produces no batches`() {
        assertTrue(ChunkBatching.plan(hashes(4), emptyList()).isEmpty())
    }

    @Test
    fun `single-chunk file allows a batch of size 1`() {
        val full = hashes(1)
        val batches = ChunkBatching.plan(full, full)
        assertEquals(listOf(full), batches)
    }

    @Test
    fun `every needed chunk in a small multi-chunk file becomes one batch of 2+`() {
        val full = hashes(4)
        val batches = ChunkBatching.plan(full, full)
        assertEquals(1, batches.size)
        assertEquals(4, batches[0].size)
    }

    @Test
    fun `large needed list splits into multiple batches never leaving a trailing size-1 batch`() {
        val full = hashes(20)
        val batches = ChunkBatching.plan(full, full)

        assertTrue(batches.size > 1, "expected multiple batches for 20 chunks")
        for (batch in batches) {
            assertTrue(batch.size >= 2, "found a batch of size ${batch.size}: $batch")
        }
        assertEquals(full, batches.flatten())
    }

    @Test
    fun `a lone needed chunk in a multi-chunk file is padded with the preceding chunk`() {
        val full = hashes(5)
        val batches = ChunkBatching.plan(full, listOf("hash3"))

        assertEquals(1, batches.size)
        assertEquals(listOf("hash2", "hash3"), batches[0])
    }

    @Test
    fun `a lone needed first chunk is padded with the following chunk`() {
        val full = hashes(5)
        val batches = ChunkBatching.plan(full, listOf("hash0"))

        assertEquals(1, batches.size)
        assertEquals(listOf("hash0", "hash1"), batches[0])
    }
}
