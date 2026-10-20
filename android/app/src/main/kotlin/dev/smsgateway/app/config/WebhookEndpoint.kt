package dev.smsgateway.app.config

import java.net.URI

object WebhookEndpoint {
    const val EVENTS_PATH = "/gateway/events"
    const val HEARTBEAT_PATH = "/gateway/heartbeat"

    /** Base URL → events endpoint. Null unless it is an absolute https URL with a host. */
    fun resolve(baseUrl: String): String? = base(baseUrl)?.plus(EVENTS_PATH)

    fun heartbeat(baseUrl: String): String? = base(baseUrl)?.plus(HEARTBEAT_PATH)

    /** Accepts either the bare host or a URL already ending in the events path. */
    private fun base(baseUrl: String): String? {
        val trimmed = baseUrl.trim().trimEnd('/')
        if (!isValidBase(trimmed)) return null
        return trimmed.removeSuffix(EVENTS_PATH)
    }

    fun isValidBase(baseUrl: String): Boolean {
        val uri = runCatching { URI(baseUrl.trim()) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() &&
            uri.rawQuery == null &&
            uri.rawFragment == null &&
            uri.rawUserInfo == null
    }
}
