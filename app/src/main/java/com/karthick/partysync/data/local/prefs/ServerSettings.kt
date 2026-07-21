package com.karthick.partysync.data.local.prefs

/** App-wide sync scheduling settings (per-server credentials live in [ServerRepository]). */
data class AppSyncSettings(
    val globalWifiOnly: Boolean = true,
    val syncIntervalMinutes: Int = SettingsRepository.DEFAULT_INTERVAL_MINUTES,
)
