package com.karthick.partysync.data.local.prefs

import kotlinx.coroutines.flow.StateFlow

interface ChatRelayRepository {
    /** Null until the user configures it - nothing bundled/hardcoded. */
    val config: StateFlow<ChatRelayConfig?>

    fun save(baseUrl: String, apiKey: String)

    fun clear()
}
