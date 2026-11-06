package com.karthick.partysync.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.karthick.partysync.domain.model.FileSyncStatus
import com.karthick.partysync.domain.model.MappingSyncStatus
import com.karthick.partysync.domain.model.SyncMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Overrides the manifest's Hilt-generated Application class: these tests only
// exercise Room and have no need for (or access to) the Hilt component graph.
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
class AppDatabaseTest {

    private lateinit var db: AppDatabase
    private lateinit var mappingDao: FolderMappingDao
    private lateinit var fileStateDao: SyncFileStateDao
    private lateinit var historyDao: SyncHistoryDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        mappingDao = db.folderMappingDao()
        fileStateDao = db.syncFileStateDao()
        historyDao = db.syncHistoryDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun newMapping(displayName: String = "Documents") = FolderMappingEntity(
        serverId = 1L,
        treeUri = "content://tree/primary:Documents",
        displayName = displayName,
        remoteBasePath = "/phone/documents",
        syncMode = SyncMode.ONE_WAY_UPLOAD,
        wifiOnlyOverride = null,
        createdAt = 1_000L,
    )

    @Test
    fun `insert and read back a mapping`() = runTest {
        val id = mappingDao.insert(newMapping())

        val loaded = mappingDao.getById(id)

        assertEquals("Documents", loaded?.displayName)
        assertEquals(SyncMode.ONE_WAY_UPLOAD, loaded?.syncMode)
        assertEquals(MappingSyncStatus.IDLE, loaded?.lastSyncStatus)
    }

    @Test
    fun `getAll emits inserted mappings`() = runTest {
        mappingDao.insert(newMapping("A"))
        mappingDao.insert(newMapping("B"))

        val all = mappingDao.getAll().first()

        assertEquals(2, all.size)
    }

    @Test
    fun `updateSyncStatus sets attempt time and preserves previous success time when not superseded`() = runTest {
        val id = mappingDao.insert(newMapping())

        mappingDao.updateSyncStatus(id, MappingSyncStatus.SUCCESS, attemptAt = 100L, successAt = 100L)
        mappingDao.updateSyncStatus(id, MappingSyncStatus.PARTIAL_FAILURE, attemptAt = 200L, successAt = null)

        val loaded = mappingDao.getById(id)
        assertEquals(MappingSyncStatus.PARTIAL_FAILURE, loaded?.lastSyncStatus)
        assertEquals(200L, loaded?.lastSyncAttemptAt)
        assertEquals(100L, loaded?.lastSyncSuccessAt)
    }

    @Test
    fun `deleting a mapping cascades to its file states`() = runTest {
        val id = mappingDao.insert(newMapping())
        fileStateDao.upsert(newFileState(id, "photo.jpg"))

        mappingDao.delete(mappingDao.getById(id)!!)

        assertTrue(fileStateDao.getAllForMapping(id).isEmpty())
    }

    @Test
    fun `countPendingForMapping reflects only PENDING files`() = runTest {
        val id = mappingDao.insert(newMapping())
        fileStateDao.upsert(newFileState(id, "a.jpg", status = FileSyncStatus.PENDING))
        fileStateDao.upsert(newFileState(id, "b.jpg", status = FileSyncStatus.SUCCESS))
        fileStateDao.upsert(newFileState(id, "c.jpg", status = FileSyncStatus.PENDING))

        val pending = fileStateDao.countPendingForMapping(id).first()

        assertEquals(2, pending)
    }

    @Test
    fun `deleteByPaths removes only the requested orphaned paths`() = runTest {
        val id = mappingDao.insert(newMapping())
        fileStateDao.upsert(newFileState(id, "keep.jpg"))
        fileStateDao.upsert(newFileState(id, "gone.jpg"))

        fileStateDao.deleteByPaths(id, listOf("gone.jpg"))

        val remaining = fileStateDao.getAllForMapping(id)
        assertEquals(1, remaining.size)
        assertEquals("keep.jpg", remaining.single().relativePath)
    }

    @Test
    fun `sync history is per-mapping, newest-first, and survives its mapping being deleted`() = runTest {
        val id = mappingDao.insert(newMapping())
        val other = mappingDao.insert(newMapping("Other"))
        historyDao.insert(newHistory(id, "a.jpg", syncedAt = 100L, sizeBytes = 10L, offloaded = false))
        historyDao.insert(newHistory(id, "b.jpg", syncedAt = 300L, sizeBytes = 20L, offloaded = true))
        historyDao.insert(newHistory(id, "c.jpg", syncedAt = 200L, sizeBytes = 30L, offloaded = false))
        historyDao.insert(newHistory(other, "z.jpg", syncedAt = 999L))

        // Deleting the mapping must NOT cascade to the history (no FK) — it's a durable record.
        mappingDao.delete(mappingDao.getById(id)!!)

        val entries = historyDao.observeForMapping(id).first()
        assertEquals(3, entries.size)
        assertEquals(listOf("b.jpg", "c.jpg", "a.jpg"), entries.map { it.relativePath })
        assertEquals(60L, entries.sumOf { it.sizeBytes })
        assertEquals(1, entries.count { it.offloaded })
        // Scoped per mapping: the other mapping's entry is not returned here.
        assertEquals(1, historyDao.observeForMapping(other).first().size)
    }

    @Test
    fun `clearForMapping empties only that mapping's history`() = runTest {
        val id = mappingDao.insert(newMapping())
        val other = mappingDao.insert(newMapping("Other"))
        historyDao.insert(newHistory(id, "a.jpg"))
        historyDao.insert(newHistory(id, "b.jpg"))
        historyDao.insert(newHistory(other, "z.jpg"))

        historyDao.clearForMapping(id)

        assertTrue(historyDao.observeForMapping(id).first().isEmpty())
        assertEquals(1, historyDao.observeForMapping(other).first().size)
    }

    private fun newHistory(
        mappingId: Long,
        relativePath: String,
        syncedAt: Long = 1_700_000_000_000L,
        sizeBytes: Long = 1_024L,
        offloaded: Boolean = false,
    ) = SyncHistoryEntity(
        mappingId = mappingId,
        relativePath = relativePath,
        remotePath = "/phone/documents/$relativePath",
        sizeBytes = sizeBytes,
        offloaded = offloaded,
        syncedAt = syncedAt,
    )

    private fun newFileState(
        mappingId: Long,
        relativePath: String,
        status: FileSyncStatus = FileSyncStatus.PENDING,
    ) = SyncFileStateEntity(
        mappingId = mappingId,
        relativePath = relativePath,
        documentId = "doc:$relativePath",
        localSize = 1_024L,
        localMtime = 1_700_000_000_000L,
        lastUploadStatus = status,
    )
}
