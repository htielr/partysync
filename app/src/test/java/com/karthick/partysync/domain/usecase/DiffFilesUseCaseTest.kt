package com.karthick.partysync.domain.usecase

import com.karthick.partysync.data.local.db.SyncFileStateEntity
import com.karthick.partysync.domain.model.FileSyncStatus
import com.karthick.partysync.domain.model.ScannedFile
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiffFilesUseCaseTest {

    private val useCase = DiffFilesUseCase()

    private fun state(
        path: String,
        size: Long = 100L,
        mtime: Long = 1_000L,
        status: FileSyncStatus = FileSyncStatus.SUCCESS,
    ) = SyncFileStateEntity(
        mappingId = 1L,
        relativePath = path,
        documentId = "doc:$path",
        localSize = size,
        localMtime = mtime,
        lastUploadStatus = status,
    )

    private fun scanned(path: String, size: Long = 100L, mtime: Long = 1_000L) =
        ScannedFile(relativePath = path, documentId = "doc:$path", size = size, lastModified = mtime)

    @Test
    fun `new file with no known state is uploaded`() {
        val plan = useCase(currentLocal = listOf(scanned("a.jpg")), knownState = emptyList())

        assertEquals(listOf("a.jpg"), plan.toUpload.map { it.relativePath })
        assertTrue(plan.toDeleteFromDb.isEmpty())
    }

    @Test
    fun `unchanged file is not re-uploaded`() {
        val plan = useCase(
            currentLocal = listOf(scanned("a.jpg", size = 100L, mtime = 1_000L)),
            knownState = listOf(state("a.jpg", size = 100L, mtime = 1_000L)),
        )

        assertTrue(plan.toUpload.isEmpty())
        assertTrue(plan.toDeleteFromDb.isEmpty())
    }

    @Test
    fun `file with changed size is re-uploaded`() {
        val plan = useCase(
            currentLocal = listOf(scanned("a.jpg", size = 200L, mtime = 1_000L)),
            knownState = listOf(state("a.jpg", size = 100L, mtime = 1_000L)),
        )

        assertEquals(listOf("a.jpg"), plan.toUpload.map { it.relativePath })
    }

    @Test
    fun `file with changed mtime only is re-uploaded`() {
        val plan = useCase(
            currentLocal = listOf(scanned("a.jpg", size = 100L, mtime = 2_000L)),
            knownState = listOf(state("a.jpg", size = 100L, mtime = 1_000L)),
        )

        assertEquals(listOf("a.jpg"), plan.toUpload.map { it.relativePath })
    }

    @Test
    fun `previously retryable-failed file is re-attempted even if unchanged`() {
        val plan = useCase(
            currentLocal = listOf(scanned("a.jpg", size = 100L, mtime = 1_000L)),
            knownState = listOf(state("a.jpg", size = 100L, mtime = 1_000L, status = FileSyncStatus.FAILED_RETRYABLE)),
        )

        assertEquals(listOf("a.jpg"), plan.toUpload.map { it.relativePath })
    }

    @Test
    fun `previously auth-failed file is not auto-retried without a real change`() {
        val plan = useCase(
            currentLocal = listOf(scanned("a.jpg", size = 100L, mtime = 1_000L)),
            knownState = listOf(state("a.jpg", size = 100L, mtime = 1_000L, status = FileSyncStatus.FAILED_AUTH)),
        )

        assertTrue(plan.toUpload.isEmpty())
    }

    @Test
    fun `file deleted locally is dropped from tracked state and not uploaded`() {
        val plan = useCase(
            currentLocal = emptyList(),
            knownState = listOf(state("gone.jpg")),
        )

        assertTrue(plan.toUpload.isEmpty())
        assertEquals(listOf("gone.jpg"), plan.toDeleteFromDb)
    }

    @Test
    fun `mixed batch classifies each file independently`() {
        val plan = useCase(
            currentLocal = listOf(
                scanned("new.jpg"),
                scanned("unchanged.jpg", size = 50L, mtime = 500L),
                scanned("changed.jpg", size = 999L, mtime = 500L),
            ),
            knownState = listOf(
                state("unchanged.jpg", size = 50L, mtime = 500L),
                state("changed.jpg", size = 50L, mtime = 500L),
                state("gone.jpg"),
            ),
        )

        assertEquals(setOf("new.jpg", "changed.jpg"), plan.toUpload.map { it.relativePath }.toSet())
        assertEquals(listOf("gone.jpg"), plan.toDeleteFromDb)
    }
}
