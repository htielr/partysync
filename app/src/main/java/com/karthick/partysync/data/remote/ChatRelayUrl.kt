package com.karthick.partysync.data.remote

import org.json.JSONObject

/**
 * Android's bundled `org.json` (unlike the standalone json.org library) stringifies a JSON
 * `null` value to the literal text `"null"` via `optString(key, fallback)` instead of returning
 * the fallback - `JSONObject.NULL` is a real sentinel object, not a Java null, so
 * `value.toString()` happily returns `"null"`. `isNull(key)` correctly treats both an absent key
 * and an explicit JSON null as "not there", which is what callers actually want.
 */
fun JSONObject.optNullableString(key: String): String? = if (isNull(key)) null else optString(key)

/**
 * Chat Relay's config screen accepts a bare host (e.g. `chat.karthickcloud.app`, matching its
 * own placeholder text) with no scheme - unlike copyparty server URLs, which are always entered
 * with an explicit `http://`/`https://`. Both [ChatRelayApi] and [ChatRelayWebSocketClient] need
 * a real scheme before handing the URL to OkHttp: `HttpUrl.toHttpUrlOrNull` just returns null for
 * a schemeless string (silently producing "Invalid server URL"), while `Request.Builder.url`
 * throws `IllegalArgumentException` instead - either way, a schemeless URL must never reach them
 * as-is.
 */
fun normalizeChatRelayBaseUrl(url: String): String {
    val trimmed = url.trim().trimEnd('/')
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
}
