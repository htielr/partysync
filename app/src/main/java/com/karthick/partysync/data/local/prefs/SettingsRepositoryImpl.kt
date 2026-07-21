package com.karthick.partysync.data.local.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
) : SettingsRepository {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _settings = MutableStateFlow(readFromPrefs())
    override val settings: StateFlow<AppSyncSettings> = _settings.asStateFlow()

    private fun readFromPrefs() = AppSyncSettings(
        globalWifiOnly = prefs.getBoolean(KEY_WIFI_ONLY, true),
        syncIntervalMinutes = prefs.getInt(
            KEY_INTERVAL_MINUTES,
            SettingsRepository.DEFAULT_INTERVAL_MINUTES,
        ),
    )

    override fun updateGlobalWifiOnly(wifiOnly: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, wifiOnly).apply()
        _settings.update { it.copy(globalWifiOnly = wifiOnly) }
    }

    override fun updateSyncIntervalMinutes(minutes: Int) {
        val clamped = minutes.coerceAtLeast(SettingsRepository.MIN_INTERVAL_MINUTES)
        prefs.edit().putInt(KEY_INTERVAL_MINUTES, clamped).apply()
        _settings.update { it.copy(syncIntervalMinutes = clamped) }
    }

    private companion object {
        const val PREFS_FILE_NAME = "partysync_secure_prefs"
        const val KEY_WIFI_ONLY = "global_wifi_only"
        const val KEY_INTERVAL_MINUTES = "sync_interval_minutes"
    }
}
