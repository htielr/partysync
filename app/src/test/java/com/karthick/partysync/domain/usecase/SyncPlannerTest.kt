package com.karthick.partysync.domain.usecase

import com.karthick.partysync.data.local.db.SyncFileStateEntity
import com.karthick.partysync.domain.model.FileSyncStatus
import com.karthick.partysync.domain.model.ScannedFile
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SyncPlannerTest {

    private val planner = SyncPlanner()

    private fun file(path: String, size: Long = 100L, mtime: Long = 1_000L) =
        ScannedFile(relativePath = path, documentId = path, size = size, lastModified = mtime)

    private fun known(
        path: String,
        baselineLocalSize: Long? = 100L,
        baselineLocalMtime: Long? = 1_000L,
        baselineRemoteSize: Long? = 100L,
        baselineRemoteMtime: Long? = 1_000L,
    ) = SyncFileStateEntity(
        mappingId = 1L,
        relativePath = path,
        documentId = path,
        localSize = baselineLocalSize ?: 0L,
        localMtime = baselineLocalMtime ?: 0L,
        baselineLocalSize = baselineLocalSize,
        baselineLocalMtime = baselineLocalMtime,
        baselineRemoteSize = baselineRemoteSize,
        baselineRemoteMtime = baselineRemoteMtime,
        lastUploadStatus = FileSyncStatus.SUCCESS,
    )

    @Test
    fun `both sides match baseline is a no-op`() {
        val plan = planner(
            localFiles = listOf(file("a.txt")),
            remoteFiles = listOf(file("a.txt")),
            knownState = listOf(known("a.txt")),
        )
        assertTrue(plan.actions.isEmpty())
    }

    @Test
    fun `local-only change uploads`() {
        val plan = planner(
            localFiles = listOf(file("a.txt", size = 200L)),
            remoteFiles = listOf(file("a.txt")),
            knownState = listOf(known("a.txt")),
        )
        assertEquals(1, plan.actions.size)
        val action = assertIs<SyncAction.Upload>(plan.actions.single())
        assertEquals("a.txt", action.relativePath)
        assertEquals(200L, action.file.size)
    }

    @Test
    fun `remote-only change downloads`() {
        val plan = planner(
            localFiles = listOf(file("a.txt")),
            remoteFiles = listOf(file("a.txt", mtime = 2_000L)),
            knownState = listOf(known("a.txt")),
        )
        val action = assertIs<SyncAction.Download>(plan.actions.single())
        assertEquals(2_000L, action.file.lastModified)
    }

    @Test
    fun `both sides changed differently is a conflict`() {
        val plan = planner(
            localFiles = listOf(file("a.txt", size = 150L)),
            remoteFiles = listOf(file("a.txt", size = 300L)),
            knownState = listOf(known("a.txt")),
        )
        val action = assertIs<SyncAction.Conflict>(plan.actions.single())
        assertEquals(150L, action.local.size)
        assertEquals(300L, action.remote.size)
    }

    @Test
    fun `both sides changed to the identical result is a no-op`() {
        val plan = planner(
            localFiles = listOf(file("a.txt", size = 500L, mtime = 9_000L)),
            remoteFiles = listOf(file("a.txt", size = 500L, mtime = 9_000L)),
            knownState = listOf(known("a.txt")),
        )
        assertTrue(plan.actions.isEmpty())
    }

    @Test
    fun `local deletion with unchanged remote restores from remote`() {
        val plan = planner(
            localFiles = emptyList(),
            remoteFiles = listOf(file("a.txt")),
            knownState = listOf(known("a.txt")),
        )
        val action = assertIs<SyncAction.Download>(plan.actions.single())
        assertEquals("a.txt", action.relativePath)
    }

    @Test
    fun `remote deletion with unchanged local restores to remote`() {
        val plan = planner(
            localFiles = listOf(file("a.txt")),
            remoteFiles = emptyList(),
            knownState = listOf(known("a.txt")),
        )
        val action = assertIs<SyncAction.Upload>(plan.actions.single())
        assertEquals("a.txt", action.relativePath)
    }

    @Test
    fun `new file present locally only with no baseline uploads`() {
        val plan = planner(
            localFiles = listOf(file("new.txt")),
            remoteFiles = emptyList(),
            knownState = emptyList(),
        )
        assertIs<SyncAction.Upload>(plan.actions.single())
    }

    @Test
    fun `new file present remotely only with no baseline downloads`() {
        val plan = planner(
            localFiles = emptyList(),
            remoteFiles = listOf(file("new.txt")),
            knownState = emptyList(),
        )
        assertIs<SyncAction.Download>(plan.actions.single())
    }

    @Test
    fun `new file present on both sides with no baseline and identical content is a no-op`() {
        val plan = planner(
            localFiles = listOf(file("seed.txt", size = 42L, mtime = 5_000L)),
            remoteFiles = listOf(file("seed.txt", size = 42L, mtime = 5_000L)),
            knownState = emptyList(),
        )
        assertTrue(plan.actions.isEmpty())
    }

    @Test
    fun `new file present on both sides with no baseline and differing content is a conflict`() {
        val plan = planner(
            localFiles = listOf(file("seed.txt", size = 42L)),
            remoteFiles = listOf(file("seed.txt", size = 99L)),
            knownState = emptyList(),
        )
        assertIs<SyncAction.Conflict>(plan.actions.single())
    }

    @Test
    fun `deleted on both sides independently is a no-op`() {
        val plan = planner(
            localFiles = emptyList(),
            remoteFiles = emptyList(),
            knownState = listOf(known("gone.txt")),
        )
        assertTrue(plan.actions.isEmpty())
    }
}
