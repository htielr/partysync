package com.karthick.partysync.data.local.prefs

import kotlinx.coroutines.flow.StateFlow

interface ServerRepository {
    val servers: StateFlow<List<ServerProfile>>

    suspend fun getById(id: Long): ServerProfile?

    /** Adds a new server profile and returns its generated id. */
    fun add(displayName: String, serverUrl: String, password: String): Long

    fun update(id: Long, displayName: String, serverUrl: String, password: String)

    fun delete(id: Long)
}
