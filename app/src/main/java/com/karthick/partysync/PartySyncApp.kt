package com.karthick.partysync

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.karthick.partysync.data.local.prefs.SettingsRepository
import com.karthick.partysync.sync.worker.SyncWorkScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PartySyncApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var syncWorkScheduler: SyncWorkScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Re-registers the periodic auto-sync on every process start (not just first install)
        // so it stays consistent with whatever interval/Wi-Fi settings are currently persisted.
        val settings = settingsRepository.settings.value
        syncWorkScheduler.schedulePeriodicSync(settings.syncIntervalMinutes, settings.globalWifiOnly)
    }
}
