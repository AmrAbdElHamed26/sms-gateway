package dev.smsgateway.app.config

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/**
 * Non-secret configuration and health markers. Read natively by the capture
 * modules and the delivery worker, so it works with no Flutter engine running.
 */
class GatewaySettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var backendUrl: String?
        get() = prefs.getString("backend_url", null)
        set(value) = edit { putString("backend_url", value) }

    var deviceId: String
        get() = prefs.getString("device_id", null) ?: defaultDeviceId().also { deviceId = it }
        set(value) = edit { putString("device_id", value) }

    /** Master switch: captured events are stored and forwarded only while this is on. */
    var gatewayEnabled: Boolean
        get() = prefs.getBoolean("forwarding_enabled", false)
        set(value) = edit { putBoolean("forwarding_enabled", value) }

    // ---- Notification module ----

    /** Package names the operator approved. Notifications from anything else are never read further. */
    var monitoredPackages: Set<String>
        get() = prefs.getString("monitored_packages", "")!!.split('\n').filter { it.isNotBlank() }.toSet()
        set(value) = edit { putString("monitored_packages", value.map { it.trim() }.filter { it.isNotEmpty() }.sorted().joinToString("\n")) }

    var listenerConnected: Boolean
        get() = prefs.getBoolean("listener_connected", false)
        set(value) = edit { putBoolean("listener_connected", value); putLong("listener_changed_at", System.currentTimeMillis()) }

    val listenerChangedAt: Long?
        get() = prefs.getLong("listener_changed_at", 0L).takeIf { it > 0 }

    var lastNotificationAt: Long?
        get() = prefs.getLong("last_notification_at", 0L).takeIf { it > 0 }
        set(value) = edit { putLong("last_notification_at", value ?: 0L) }

    // ---- SMS module (optional, off by default) ----

    var smsEnabled: Boolean
        get() = prefs.getBoolean("sms_enabled", false)
        set(value) = edit { putBoolean("sms_enabled", value) }

    /** Approved sender IDs / numbers. Required: an empty list forwards no SMS. */
    var smsAllowlist: List<String>
        get() = prefs.getString("allowlist", "")!!.split('\n').filter { it.isNotBlank() }
        set(value) = edit { putString("allowlist", value.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n")) }

    var lastSmsReceivedAt: Long?
        get() = prefs.getLong("last_sms_received_at", 0L).takeIf { it > 0 }
        set(value) = edit { putLong("last_sms_received_at", value ?: 0L) }

    // ---- Shared ----

    var retentionDays: Int
        get() = prefs.getInt("retention_days", DEFAULT_RETENTION_DAYS)
        set(value) = edit { putInt("retention_days", value) }

    var lastDeliveredAt: Long?
        get() = prefs.getLong("last_delivered_at", 0L).takeIf { it > 0 }
        set(value) = edit { putLong("last_delivered_at", value ?: 0L) }

    /** Counts only — the content of a filtered message is never kept. */
    fun countFiltered(reason: String) = synchronized(LOCK) {
        val key = "filtered_$reason"
        edit { putInt(key, prefs.getInt(key, 0) + 1) }
    }

    fun filteredCounts(): Map<String, Int> = prefs.all
        .filterKeys { it.startsWith("filtered_") }
        .mapKeys { it.key.removePrefix("filtered_") }
        .mapValues { (it.value as? Int) ?: 0 }

    /**
     * Random per-install ID. Sequences restart when app data is wiped, so the
     * backend checks for gaps per (device_id, install_id).
     */
    val installId: String
        get() = prefs.getString("install_id", null)
            ?: UUID.randomUUID().toString().also { id -> edit { putString("install_id", id) } }

    /** Full webhook URL, or null when no valid backend is configured. */
    val webhookUrl: String?
        get() = backendUrl?.let { WebhookEndpoint.resolve(it) }

    val heartbeatUrl: String?
        get() = backendUrl?.let { WebhookEndpoint.heartbeat(it) }

    fun recordBackendResult(ok: Boolean, httpStatus: Int?, message: String?, atMs: Long) = edit {
        putBoolean("backend_ok", ok)
        putInt("backend_http_status", httpStatus ?: -1)
        putString("backend_message", message)
        putLong("backend_checked_at", atMs)
    }

    fun backendStatus(): Map<String, Any?> = mapOf(
        "ok" to prefs.getBoolean("backend_ok", false),
        "httpStatus" to prefs.getInt("backend_http_status", -1).takeIf { it >= 0 },
        "message" to prefs.getString("backend_message", null),
        "checkedAt" to prefs.getLong("backend_checked_at", 0L).takeIf { it > 0 },
    )

    fun recordHeartbeat(ok: Boolean, httpStatus: Int?, atMs: Long) = edit {
        putBoolean("heartbeat_ok", ok)
        putInt("heartbeat_http_status", httpStatus ?: -1)
        putLong("heartbeat_at", atMs)
    }

    /** Wall-clock time the next heartbeat alarm is due; see HeartbeatScheduler. */
    var heartbeatNextAt: Long?
        get() = prefs.getLong("heartbeat_next_at", 0L).takeIf { it > 0 }
        set(value) = edit { putLong("heartbeat_next_at", value ?: 0L) }

    fun heartbeatStatus(): Map<String, Any?> = mapOf(
        "ok" to prefs.getBoolean("heartbeat_ok", false),
        "httpStatus" to prefs.getInt("heartbeat_http_status", -1).takeIf { it >= 0 },
        "checkedAt" to prefs.getLong("heartbeat_at", 0L).takeIf { it > 0 },
    )

    fun toMap(secretConfigured: Boolean): Map<String, Any?> = mapOf(
        "backendUrl" to backendUrl,
        "deviceId" to deviceId,
        "installId" to installId,
        "secretConfigured" to secretConfigured,
        "gatewayEnabled" to gatewayEnabled,
        "monitoredPackages" to monitoredPackages.toList(),
        "smsEnabled" to smsEnabled,
        "smsAllowlist" to smsAllowlist,
        "retentionDays" to retentionDays,
    )

    private fun defaultDeviceId() = "android-gateway-" + UUID.randomUUID().toString().take(8)

    private inline fun edit(block: SharedPreferences.Editor.() -> Unit) {
        // commit(): capture components can be killed right after they return.
        prefs.edit().apply(block).commit()
    }

    companion object {
        private const val PREFS = "gateway_settings"
        private val LOCK = Any()
        const val DEFAULT_RETENTION_DAYS = 30
    }
}
