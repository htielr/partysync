package com.karthick.partysync.sync.worker

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enqueues manual and periodic sync under separate WorkManager unique-work names (a one-time
 * expedited request can't share a name with a periodic one without WorkManager cancelling the
 * periodic registration every time manual sync runs). Concurrency safety against shared Room
 * state is instead enforced by [com.karthick.partysync.sync.SyncEngine]'s own internal mutex.
 */
@Singleton
class SyncWorkScheduler @Inject constructor(private val workManager: WorkManager) {

    fun enqueueManualSync() {
        val request = OneTimeWorkRequestBuilder<ManualSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager.enqueueUniqueWork(MANUAL_SYNC_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun schedulePeriodicSync(intervalMinutes: Int, wifiOnly: Boolean) {
        val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request = PeriodicWorkRequestBuilder<PeriodicSyncWorker>(intervalMinutes.toLong(), TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(networkType).build())
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_SYNC_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    companion object {
        const val MANUAL_SYNC_WORK_NAME = "manual_sync"
        const val PERIODIC_SYNC_WORK_NAME = "periodic_sync"
    }
}
