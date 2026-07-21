package com.karthick.partysync.data.local.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
) : ServerRepository {

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

    private val _servers = MutableStateFlow(readFromPrefs())
    override val servers: StateFlow<List<ServerProfile>> = _servers.asStateFlow()

    override suspend fun getById(id: Long): ServerProfile? = _servers.value.find { it.id == id }

    override fun add(displayName: String, serverUrl: String, password: String): Long {
        val id = nextId()
        persist(_servers.value + ServerProfile(id, displayName, serverUrl, password))
        return id
    }

    override fun update(id: Long, displayName: String, serverUrl: String, password: String) {
        persist(
            _servers.value.map {
                if (it.id == id) it.copy(displayName = displayName, serverUrl = serverUrl, password = password) else it
            },
        )
    }

    override fun delete(id: Long) {
        persist(_servers.value.filterNot { it.id == id })
    }

    private fun nextId(): Long {
        val next = prefs.getLong(KEY_NEXT_ID, 1L)
        prefs.edit().putLong(KEY_NEXT_ID, next + 1).apply()
        return next
    }

    private fun persist(servers: List<ServerProfile>) {
        prefs.edit().putString(KEY_SERVERS, serialize(servers)).apply()
        _servers.value = servers
    }

    private fun readFromPrefs(): List<ServerProfile> =
        prefs.getString(KEY_SERVERS, null)?.let(::deserialize) ?: emptyList()

    private fun serialize(servers: List<ServerProfile>): String {
        val array = JSONArray()
        servers.forEach { server ->
            array.put(
                JSONObject()
                    .put("id", server.id)
                    .put("displayName", server.displayName)
                    .put("serverUrl", server.serverUrl)
                    .put("password", server.password),
            )
        }
        return array.toString()
    }

    private fun deserialize(json: String): List<ServerProfile> {
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            ServerProfile(
                id = obj.getLong("id"),
                displayName = obj.getString("displayName"),
                serverUrl = obj.getString("serverUrl"),
                password = obj.getString("password"),
            )
        }
    }

    private companion object {
        const val PREFS_FILE_NAME = "partysync_servers_prefs"
        const val KEY_SERVERS = "servers_json"
        const val KEY_NEXT_ID = "servers_next_id"
    }
}
