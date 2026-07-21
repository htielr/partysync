package com.karthick.partysync.ui.settings

import androidx.lifecycle.ViewModel
import com.karthick.partysync.data.local.prefs.AppSyncSettings
import com.karthick.partysync.data.local.prefs.SettingsRepository
import com.karthick.partysync.sync.worker.SyncWorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val syncWorkScheduler: SyncWorkScheduler,
) : ViewModel() {

    val settings: StateFlow<AppSyncSettings> = settingsRepository.settings

    fun onGlobalWifiOnlyChanged(wifiOnly: Boolean) {
        settingsRepository.updateGlobalWifiOnly(wifiOnly)
        reschedulePeriodicSync()
    }

    fun onSyncIntervalChanged(minutes: Int) {
        settingsRepository.updateSyncIntervalMinutes(minutes)
        reschedulePeriodicSync()
    }

    private fun reschedulePeriodicSync() {
        val current = settingsRepository.settings.value
        syncWorkScheduler.schedulePeriodicSync(current.syncIntervalMinutes, current.globalWifiOnly)
    }
}
