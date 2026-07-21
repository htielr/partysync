package com.karthick.partysync.domain.usecase

import com.karthick.partysync.data.local.db.SyncFileStateEntity
import com.karthick.partysync.domain.model.FileSyncStatus
import com.karthick.partysync.domain.model.ScannedFile
import javax.inject.Inject

/** Result of diffing a fresh local scan against the last known Room state for a one-way mapping. */
data class SyncPlan(
    val toUpload: List<ScannedFile>,
    /** Paths tracked in Room that no longer exist locally — dropped, not treated as failures. */
    val toDeleteFromDb: List<String>,
)

/**
 * One-way (upload) change detection: a file is "changed" if its size or last-modified time
 * differs from the last successfully-recorded state, or if it has never been seen before.
 * This is a size+mtime heuristic with no content hashing (that's up2k's job, deferred to a
 * later version) — a file touched without a real content change will be re-uploaded
 * unnecessarily, and a file modified without its mtime changing would be missed. Documented
 * tradeoff, not a bug.
 */
class DiffFilesUseCase @Inject constructor() {

    operator fun invoke(currentLocal: List<ScannedFile>, knownState: List<SyncFileStateEntity>): SyncPlan {
        val knownByPath = knownState.associateBy { it.relativePath }
        val currentPaths = currentLocal.mapTo(mutableSetOf()) { it.relativePath }

        val toUpload = currentLocal.filter { scanned ->
            val known = knownByPath[scanned.relativePath]
            known == null ||
                known.localSize != scanned.size ||
                known.localMtime != scanned.lastModified ||
                known.lastUploadStatus == FileSyncStatus.FAILED_RETRYABLE
        }

        val toDeleteFromDb = knownByPath.keys.filterNot { it in currentPaths }

        return SyncPlan(toUpload, toDeleteFromDb)
    }
}
