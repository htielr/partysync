package com.karthick.partysync.data.local.prefs

import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {
    val settings: StateFlow<AppSyncSettings>

    fun updateGlobalWifiOnly(wifiOnly: Boolean)
    fun updateSyncIntervalMinutes(minutes: Int)
    fun setLastBrowsedServerId(id: Long)
    fun updateThemeMode(mode: AppThemeMode)

    companion object {
        /** WorkManager's platform floor for PeriodicWorkRequest intervals. */
        const val MIN_INTERVAL_MINUTES = 15
        const val DEFAULT_INTERVAL_MINUTES = 60
    }
}
