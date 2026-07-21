package com.karthick.partysync.domain.usecase

import com.karthick.partysync.data.local.db.SyncFileStateEntity
import com.karthick.partysync.domain.model.ScannedFile
import javax.inject.Inject

sealed class SyncAction {
    data class Upload(val relativePath: String, val file: ScannedFile) : SyncAction()
    data class Download(val relativePath: String, val file: ScannedFile) : SyncAction()
    data class Conflict(val relativePath: String, val local: ScannedFile, val remote: ScannedFile) : SyncAction()
}

data class TwoWaySyncPlan(val actions: List<SyncAction>)

/**
 * 3-way merge for [com.karthick.partysync.domain.model.SyncMode.TWO_WAY] mappings: compares
 * current local/remote state against the *baseline* recorded at the last successful
 * reconciliation of each path (not against each other directly — that can't distinguish "local
 * changed" from "remote changed" from "both changed"). See the project plan's case table:
 *
 * - Both sides match their baseline -> no-op.
 * - Only one side differs from baseline -> push that side's version to the other, including the
 *   "restore" case where a path is missing on the side that changed but the baseline existed —
 *   that's a local/remote deletion, and per the "never propagate deletes" policy this restores
 *   the file from the side that still has it rather than deleting it there too.
 * - Both sides differ from baseline (a real conflict) -> [SyncAction.Conflict], unless both sides
 *   independently ended up with identical content (e.g. the mapping had pre-existing identical
 *   files on both ends before two-way sync was first turned on, so no baseline exists yet) in
 *   which case there's nothing to reconcile.
 */
class SyncPlanner @Inject constructor() {

    operator fun invoke(
        localFiles: List<ScannedFile>,
        remoteFiles: List<ScannedFile>,
        knownState: List<SyncFileStateEntity>,
    ): TwoWaySyncPlan {
        val localByPath = localFiles.associateBy { it.relativePath }
        val remoteByPath = remoteFiles.associateBy { it.relativePath }
        val knownByPath = knownState.associateBy { it.relativePath }
        val allPaths = localByPath.keys + remoteByPath.keys + knownByPath.keys

        val actions = allPaths.mapNotNull { path ->
            planForPath(path, localByPath[path], remoteByPath[path], knownByPath[path])
        }
        return TwoWaySyncPlan(actions)
    }

    private fun planForPath(
        path: String,
        local: ScannedFile?,
        remote: ScannedFile?,
        known: SyncFileStateEntity?,
    ): SyncAction? {
        val localMatches = matchesBaseline(local, known?.baselineLocalSize, known?.baselineLocalMtime)
        val remoteMatches = matchesBaseline(remote, known?.baselineRemoteSize, known?.baselineRemoteMtime)

        return when {
            localMatches && remoteMatches -> null

            !localMatches && remoteMatches ->
                local?.let { SyncAction.Upload(path, it) } ?: remote?.let { SyncAction.Download(path, it) }

            localMatches && !remoteMatches ->
                remote?.let { SyncAction.Download(path, it) } ?: local?.let { SyncAction.Upload(path, it) }

            else -> when {
                local != null && remote != null ->
                    if (local.size == remote.size && local.lastModified == remote.lastModified) {
                        null
                    } else {
                        SyncAction.Conflict(path, local, remote)
                    }
                local != null -> SyncAction.Upload(path, local)
                remote != null -> SyncAction.Download(path, remote)
                else -> null
            }
        }
    }

    private fun matchesBaseline(current: ScannedFile?, baselineSize: Long?, baselineMtime: Long?): Boolean =
        if (baselineSize == null || baselineMtime == null) {
            current == null
        } else {
            current != null && current.size == baselineSize && current.lastModified == baselineMtime
        }
}
