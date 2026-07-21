package com.karthick.partysync.sync.worker

import androidx.work.ListenableWorker
import com.karthick.partysync.domain.model.SyncResult
import com.karthick.partysync.sync.SyncEngine

/** Shared by [ManualSyncWorker] and [PeriodicSyncWorker]: run the engine, map the outcome. */
internal suspend fun runSyncEngineAndMapResult(syncEngine: SyncEngine): ListenableWorker.Result {
    val result: SyncResult = syncEngine.run()
    val anyRetryable = result.mappingResults.any { it.hadRetryableFailure }
    return if (anyRetryable) ListenableWorker.Result.retry() else ListenableWorker.Result.success()
}
