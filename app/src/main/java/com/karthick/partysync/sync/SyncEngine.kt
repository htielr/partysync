package com.karthick.partysync.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.karthick.partysync.data.local.db.FolderMappingDao
import com.karthick.partysync.data.local.db.FolderMappingEntity
import com.karthick.partysync.data.local.db.SyncFileStateDao
import com.karthick.partysync.data.local.db.SyncFileStateEntity
import com.karthick.partysync.data.local.prefs.ServerProfile
import com.karthick.partysync.data.local.prefs.ServerRepository
import com.karthick.partysync.data.local.prefs.SettingsRepository
import com.karthick.partysync.data.remote.CopyPartyApi
import com.karthick.partysync.data.remote.CopyPartyResult
import com.karthick.partysync.data.remote.classifyCopyPartyResult
import com.karthick.partysync.domain.model.FileSyncStatus
import com.karthick.partysync.domain.model.MappingSyncResult
import com.karthick.partysync.domain.model.MappingSyncStatus
import com.karthick.partysync.domain.model.ScannedFile
import com.karthick.partysync.domain.model.SyncMode
import com.karthick.partysync.domain.model.SyncResult
import com.karthick.partysync.domain.usecase.DiffFilesUseCase
import com.karthick.partysync.domain.usecase.SyncAction
import com.karthick.partysync.domain.usecase.SyncPlanner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates one full sync pass: for every enabled mapping, scan -> diff/plan -> transfer ->
 * record Room state. Framework-agnostic (a plain suspend function) so it's callable identically
 * from a WorkManager Worker or a standalone test, per the project plan.
 *
 * [Singleton] + an internal [Mutex] so the manual "sync now" trigger and the periodic
 * auto-sync worker — which run as separate WorkManager unique-work chains — can never
 * execute concurrently and race on shared Room state.
 */
@Singleton
class SyncEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val folderMappingDao: FolderMappingDao,
    private val syncFileStateDao: SyncFileStateDao,
    private val serverRepository: ServerRepository,
    private val saFolderScanner: SaFolderScanner,
    private val remoteFolderScanner: RemoteFolderScanner,
    private val diffFilesUseCase: DiffFilesUseCase,
    private val syncPlanner: SyncPlanner,
    private val copyPartyApi: CopyPartyApi,
    private val settingsRepository: SettingsRepository,
) {
    private val runMutex = Mutex()

    suspend fun run(): SyncResult = runMutex.withLock {
        val mappings = folderMappingDao.getAllEnabled()
        val results = mappings.map { mapping -> runForMapping(mapping) }
        SyncResult(results)
    }

    private suspend fun runForMapping(mapping: FolderMappingEntity): MappingSyncResult {
        val wifiOnly = mapping.wifiOnlyOverride ?: settingsRepository.settings.value.globalWifiOnly
        if (wifiOnly && isActiveNetworkMetered()) {
            return MappingSyncResult(mapping.id, uploaded = 0, failed = 0, false, false, false)
        }

        val server = serverRepository.getById(mapping.serverId) ?: return finishWithServerMissing(mapping)

        folderMappingDao.updateSyncStatus(
            mapping.id,
            MappingSyncStatus.RUNNING,
            attemptAt = System.currentTimeMillis(),
            successAt = null,
        )

        return when (mapping.syncMode) {
            SyncMode.ONE_WAY_UPLOAD -> runOneWay(mapping, server)
            SyncMode.TWO_WAY -> runTwoWay(mapping, server)
        }
    }

    // ---- One-way upload -----------------------------------------------------------------

    private suspend fun runOneWay(mapping: FolderMappingEntity, server: ServerProfile): MappingSyncResult {
        val treeUri = Uri.parse(mapping.treeUri)
        val scanned = try {
            saFolderScanner.scan(treeUri)
        } catch (e: SecurityException) {
            return finishWithPermissionLost(mapping)
        }

        val knownState = syncFileStateDao.getAllForMapping(mapping.id)
        val knownByPath = knownState.associateBy { it.relativePath }
        val plan = diffFilesUseCase(scanned, knownState)

        if (plan.toDeleteFromDb.isNotEmpty()) {
            syncFileStateDao.deleteByPaths(mapping.id, plan.toDeleteFromDb)
        }

        var uploaded = 0
        var failed = 0
        var hadRetryable = false
        var hadAuth = false

        for (file in plan.toUpload) {
            val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, file.documentId)

            if (!existsLocally(documentUri)) {
                // Vanished between scan and upload — treat like any other locally-deleted file,
                // not a failure (see plan's error-classification table).
                syncFileStateDao.deleteByPaths(mapping.id, listOf(file.relativePath))
                continue
            }

            val result = uploadLocalFile(mapping, server, documentUri, file)
            val (status, errorMessage) = classifyCopyPartyResult(result)
            when (status) {
                FileSyncStatus.SUCCESS -> uploaded++
                FileSyncStatus.FAILED_RETRYABLE -> { failed++; hadRetryable = true }
                FileSyncStatus.FAILED_AUTH -> { failed++; hadAuth = true }
                else -> failed++
            }

            val previousAttempts = knownByPath[file.relativePath]?.attemptCount ?: 0
            val succeeded = status == FileSyncStatus.SUCCESS
            syncFileStateDao.upsert(
                SyncFileStateEntity(
                    mappingId = mapping.id,
                    relativePath = file.relativePath,
                    documentId = file.documentId,
                    localSize = file.size,
                    localMtime = file.lastModified,
                    baselineLocalSize = if (succeeded) file.size else knownByPath[file.relativePath]?.baselineLocalSize,
                    baselineLocalMtime = if (succeeded) file.lastModified else knownByPath[file.relativePath]?.baselineLocalMtime,
                    lastUploadedAt = if (succeeded) System.currentTimeMillis() else null,
                    lastUploadStatus = status,
                    lastErrorMessage = errorMessage,
                    attemptCount = previousAttempts + 1,
                ),
            )
        }

        return finish(mapping, uploaded, failed, hadRetryable, hadAuth)
    }

    // ---- Two-way sync --------------------------------------------------------------------

    private suspend fun runTwoWay(mapping: FolderMappingEntity, server: ServerProfile): MappingSyncResult {
        val treeUri = Uri.parse(mapping.treeUri)
        val localFiles = try {
            saFolderScanner.scan(treeUri)
        } catch (e: SecurityException) {
            return finishWithPermissionLost(mapping)
        }

        val remoteScan = remoteFolderScanner.scan(server.serverUrl, server.password, mapping.remoteBasePath)
        val remoteFiles = when (remoteScan) {
            is RemoteScanResult.Success -> remoteScan.files
            is RemoteScanResult.Failure -> {
                val hadAuth = remoteScan.message.contains("401") || remoteScan.message.contains("403")
                return finish(mapping, uploaded = 0, failed = 1, hadRetryable = !hadAuth, hadAuth = hadAuth)
            }
        }

        val knownState = syncFileStateDao.getAllForMapping(mapping.id)
        val knownByPath = knownState.associateBy { it.relativePath }
        val plan = syncPlanner(localFiles, remoteFiles, knownState)

        var succeededCount = 0
        var failed = 0
        var hadRetryable = false
        var hadAuth = false

        for (action in plan.actions) {
            val outcome = when (action) {
                is SyncAction.Upload -> executeUpload(mapping, server, treeUri, action, knownByPath)
                is SyncAction.Download ->
                    executeDownload(mapping, server, treeUri, action.relativePath, action.file, knownByPath)
                is SyncAction.Conflict -> executeConflict(mapping, server, treeUri, action, knownByPath)
            }
            when (outcome) {
                ActionOutcome.SUCCESS -> succeededCount++
                ActionOutcome.RETRYABLE_FAILURE -> { failed++; hadRetryable = true }
                ActionOutcome.AUTH_FAILURE -> { failed++; hadAuth = true }
                ActionOutcome.OTHER_FAILURE -> failed++
            }
        }

        return finish(mapping, succeededCount, failed, hadRetryable, hadAuth)
    }

    private enum class ActionOutcome { SUCCESS, RETRYABLE_FAILURE, AUTH_FAILURE, OTHER_FAILURE }

    private suspend fun executeUpload(
        mapping: FolderMappingEntity,
        server: ServerProfile,
        treeUri: Uri,
        action: SyncAction.Upload,
        knownByPath: Map<String, SyncFileStateEntity>,
    ): ActionOutcome {
        val file = action.file
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, file.documentId)
        if (!existsLocally(documentUri)) return ActionOutcome.OTHER_FAILURE

        val result = uploadLocalFile(mapping, server, documentUri, file)
        val (status, errorMessage) = classifyCopyPartyResult(result)
        recordTwoWayState(mapping.id, action.relativePath, file, remoteMirror = file, status, errorMessage, knownByPath)
        return status.toOutcome()
    }

    private suspend fun executeDownload(
        mapping: FolderMappingEntity,
        server: ServerProfile,
        treeUri: Uri,
        relativePath: String,
        remoteFile: ScannedFile,
        knownByPath: Map<String, SyncFileStateEntity>,
    ): ActionOutcome {
        val result = copyPartyApi.downloadFile(
            server.serverUrl,
            server.password,
            mapping.remoteBasePath,
            relativePath,
        ) { input -> writeToLocalFile(treeUri, relativePath, input) }
        val (status, errorMessage) = classifyCopyPartyResult(result)
        recordTwoWayState(mapping.id, relativePath, localMirror = remoteFile, remoteFile, status, errorMessage, knownByPath)
        return status.toOutcome()
    }

    private suspend fun executeConflict(
        mapping: FolderMappingEntity,
        server: ServerProfile,
        treeUri: Uri,
        action: SyncAction.Conflict,
        knownByPath: Map<String, SyncFileStateEntity>,
    ): ActionOutcome {
        val conflictPath = conflictCopyPath(action.relativePath)

        val downloadResult = copyPartyApi.downloadFile(
            server.serverUrl,
            server.password,
            mapping.remoteBasePath,
            action.relativePath,
        ) { input -> writeToLocalFile(treeUri, conflictPath, input) }
        val (downloadStatus, downloadError) = classifyCopyPartyResult(downloadResult)
        // The conflict copy is a brand-new tracked path with no baseline yet — it will
        // propagate to the other side normally on the next sync pass.
        syncFileStateDao.upsert(
            SyncFileStateEntity(
                mappingId = mapping.id,
                relativePath = conflictPath,
                documentId = conflictPath,
                localSize = action.remote.size,
                localMtime = action.remote.lastModified,
                lastUploadStatus = if (downloadStatus == FileSyncStatus.SUCCESS) {
                    FileSyncStatus.CONFLICT_RESOLVED
                } else {
                    downloadStatus
                },
                lastErrorMessage = downloadError,
                lastUploadedAt = if (downloadStatus == FileSyncStatus.SUCCESS) System.currentTimeMillis() else null,
                attemptCount = 1,
            ),
        )

        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, action.local.documentId)
        val uploadResult = if (existsLocally(documentUri)) {
            uploadLocalFile(mapping, server, documentUri, action.local)
        } else {
            null
        }
        val (uploadStatus, uploadError) = uploadResult?.let(::classifyCopyPartyResult)
            ?: (FileSyncStatus.FAILED_OTHER to "Local file vanished before conflict upload")

        recordTwoWayState(
            mapping.id,
            action.relativePath,
            localMirror = action.local,
            remoteMirror = action.local,
            status = if (uploadStatus == FileSyncStatus.SUCCESS) FileSyncStatus.CONFLICT_RESOLVED else uploadStatus,
            errorMessage = uploadError,
            knownByPath = knownByPath,
        )

        val worst = listOf(downloadStatus, uploadStatus).maxByOrNull { it.severityRank() } ?: uploadStatus
        return worst.toOutcome()
    }

    /**
     * Records the post-transfer state for a two-way path. On success, sets *both* baseline
     * pairs from the values we just observed on each side, so the next 3-way diff treats this
     * path as reconciled. Where one side's post-write value can't be directly observed (e.g. a
     * download can't tell us what mtime the SAF provider will actually assign to the local
     * file), we assume it mirrors the other side; if the assumption is wrong, the next scan
     * will surface a one-time, self-correcting extra transfer rather than a persistent loop.
     */
    private suspend fun recordTwoWayState(
        mappingId: Long,
        relativePath: String,
        localMirror: ScannedFile,
        remoteMirror: ScannedFile,
        status: FileSyncStatus,
        errorMessage: String?,
        knownByPath: Map<String, SyncFileStateEntity>,
    ) {
        val previous = knownByPath[relativePath]
        val succeeded = status == FileSyncStatus.SUCCESS || status == FileSyncStatus.CONFLICT_RESOLVED
        syncFileStateDao.upsert(
            SyncFileStateEntity(
                mappingId = mappingId,
                relativePath = relativePath,
                documentId = localMirror.documentId,
                localSize = localMirror.size,
                localMtime = localMirror.lastModified,
                remoteSize = remoteMirror.size,
                remoteMtime = remoteMirror.lastModified,
                baselineLocalSize = if (succeeded) localMirror.size else previous?.baselineLocalSize,
                baselineLocalMtime = if (succeeded) localMirror.lastModified else previous?.baselineLocalMtime,
                baselineRemoteSize = if (succeeded) remoteMirror.size else previous?.baselineRemoteSize,
                baselineRemoteMtime = if (succeeded) remoteMirror.lastModified else previous?.baselineRemoteMtime,
                lastUploadedAt = if (succeeded) System.currentTimeMillis() else null,
                lastUploadStatus = status,
                lastErrorMessage = errorMessage,
                attemptCount = (previous?.attemptCount ?: 0) + 1,
            ),
        )
    }

    private suspend fun uploadLocalFile(
        mapping: FolderMappingEntity,
        server: ServerProfile,
        documentUri: Uri,
        file: ScannedFile,
    ): CopyPartyResult = copyPartyApi.uploadFile(
        serverUrl = server.serverUrl,
        password = server.password,
        remoteBasePath = mapping.remoteBasePath,
        relativePath = file.relativePath,
        contentLength = file.size,
    ) {
        context.contentResolver.openInputStream(documentUri)
            ?: throw IOException("Unable to open $documentUri")
    }

    private fun writeToLocalFile(treeUri: Uri, relativePath: String, input: java.io.InputStream) {
        val segments = relativePath.split('/').filter { it.isNotEmpty() }
        val dirSegments = segments.dropLast(1)
        val fileName = segments.last()

        var parent = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IOException("Cannot resolve tree $treeUri")
        for (dirSegment in dirSegments) {
            parent = parent.findFile(dirSegment) ?: parent.createDirectory(dirSegment)
                ?: throw IOException("Cannot create directory $dirSegment")
        }

        val mimeType = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(fileName.substringAfterLast('.', ""))
            ?: "application/octet-stream"

        val targetFile = parent.findFile(fileName) ?: parent.createFile(mimeType, fileName)
            ?: throw IOException("Cannot create file $fileName")

        context.contentResolver.openOutputStream(targetFile.uri, "wt")?.use { output ->
            input.copyTo(output)
        } ?: throw IOException("Cannot open output stream for ${targetFile.uri}")
    }

    private fun conflictCopyPath(relativePath: String): String {
        val lastSlash = relativePath.lastIndexOf('/')
        val dir = if (lastSlash >= 0) relativePath.substring(0, lastSlash + 1) else ""
        val fileName = if (lastSlash >= 0) relativePath.substring(lastSlash + 1) else relativePath
        val dot = fileName.lastIndexOf('.')
        val stamp = SimpleDateFormat("yyyy-MM-dd HHmmss", Locale.US).format(Date())
        return if (dot > 0) {
            "$dir${fileName.substring(0, dot)} (conflict-copy $stamp)${fileName.substring(dot)}"
        } else {
            "$dir$fileName (conflict-copy $stamp)"
        }
    }

    // ---- Shared helpers --------------------------------------------------------------------

    private suspend fun finish(
        mapping: FolderMappingEntity,
        uploaded: Int,
        failed: Int,
        hadRetryable: Boolean,
        hadAuth: Boolean,
    ): MappingSyncResult {
        val mappingStatus = when {
            hadAuth -> MappingSyncStatus.AUTH_ERROR
            failed > 0 -> MappingSyncStatus.PARTIAL_FAILURE
            else -> MappingSyncStatus.SUCCESS
        }
        val now = System.currentTimeMillis()
        folderMappingDao.updateSyncStatus(
            mapping.id,
            mappingStatus,
            attemptAt = now,
            successAt = if (mappingStatus == MappingSyncStatus.SUCCESS) now else null,
        )
        return MappingSyncResult(mapping.id, uploaded, failed, hadRetryable, hadAuth, permissionLost = false)
    }

    private suspend fun finishWithPermissionLost(mapping: FolderMappingEntity): MappingSyncResult {
        folderMappingDao.updateSyncStatus(
            mapping.id,
            MappingSyncStatus.PERMISSION_LOST,
            attemptAt = System.currentTimeMillis(),
            successAt = null,
        )
        return MappingSyncResult(mapping.id, uploaded = 0, failed = 0, false, false, permissionLost = true)
    }

    private suspend fun finishWithServerMissing(mapping: FolderMappingEntity): MappingSyncResult {
        folderMappingDao.updateSyncStatus(
            mapping.id,
            MappingSyncStatus.SERVER_MISSING,
            attemptAt = System.currentTimeMillis(),
            successAt = null,
        )
        return MappingSyncResult(mapping.id, uploaded = 0, failed = 0, false, false, permissionLost = false)
    }

    private fun existsLocally(documentUri: Uri): Boolean = try {
        context.contentResolver.openInputStream(documentUri)?.use { } != null
    } catch (e: IOException) {
        false
    }

    private fun FileSyncStatus.toOutcome(): ActionOutcome = when (this) {
        FileSyncStatus.SUCCESS, FileSyncStatus.CONFLICT_RESOLVED -> ActionOutcome.SUCCESS
        FileSyncStatus.FAILED_RETRYABLE -> ActionOutcome.RETRYABLE_FAILURE
        FileSyncStatus.FAILED_AUTH -> ActionOutcome.AUTH_FAILURE
        else -> ActionOutcome.OTHER_FAILURE
    }

    private fun FileSyncStatus.severityRank(): Int = when (this) {
        FileSyncStatus.FAILED_AUTH -> 3
        FileSyncStatus.FAILED_RETRYABLE -> 2
        FileSyncStatus.FAILED_OTHER -> 1
        else -> 0
    }

    private fun isActiveNetworkMetered(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        return connectivityManager?.isActiveNetworkMetered ?: false
    }
}
