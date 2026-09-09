package com.karthick.partysync.data.local.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRelayRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
) : ChatRelayRepository {

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

    private val _config = MutableStateFlow(readFromPrefs())
    override val config: StateFlow<ChatRelayConfig?> = _config.asStateFlow()

    override fun save(baseUrl: String, apiKey: String) {
        val config = ChatRelayConfig(baseUrl, apiKey)
        prefs.edit()
            .putString(KEY_BASE_URL, config.baseUrl)
            .putString(KEY_API_KEY, config.apiKey)
            .apply()
        _config.value = config
    }

    override fun clear() {
        prefs.edit().remove(KEY_BASE_URL).remove(KEY_API_KEY).apply()
        _config.value = null
    }

    private fun readFromPrefs(): ChatRelayConfig? {
        val baseUrl = prefs.getString(KEY_BASE_URL, null) ?: return null
        val apiKey = prefs.getString(KEY_API_KEY, null) ?: return null
        return ChatRelayConfig(baseUrl, apiKey)
    }

    private companion object {
        const val PREFS_FILE_NAME = "partysync_chat_relay_prefs"
        const val KEY_BASE_URL = "base_url"
        const val KEY_API_KEY = "api_key"
    }
}
