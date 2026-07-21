package com.karthick.partysync.data.local.prefs

/** A named copyparty server the user has configured (e.g. "Home", "Work"). */
data class ServerProfile(
    val id: Long,
    val displayName: String,
    val serverUrl: String,
    val password: String,
)
