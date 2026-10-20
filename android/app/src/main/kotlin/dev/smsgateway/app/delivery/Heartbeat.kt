package dev.smsgateway.app.delivery

import dev.smsgateway.app.queue.EventStore
import dev.smsgateway.app.queue.HeartbeatRecord
import dev.smsgateway.app.capture.notification.NotificationAccess
import dev.smsgateway.app.capture.sms.SmsModule
import dev.smsgateway.app.config.GatewaySettings
import dev.smsgateway.app.config.SecretStore
import dev.smsgateway.app.util.TimeFormat
import android.content.Context
import android.os.PowerManager
import org.json.JSONObject
import java.util.UUID

/**
 * Completeness signal for the backend, sent every 2 min by [HeartbeatScheduler].
 * The backend compares `last_sequence` with the highest sequence it has
 * received for (device_id, install_id): if they differ and `pending_count` is
 * 0, an event is missing. A heartbeat that stops arriving means the device is
 * off, offline, force-stopped or killed — alert on it.
 */
object Heartbeat {

    /** Sends one heartbeat and logs it; null when no backend is configured. [dueAtMs] is when it was scheduled. */
    fun send(context: Context, trigger: String, dueAtMs: Long = System.currentTimeMillis()): WebhookResponse? {
        val ctx = context.applicationContext
        val settings = GatewaySettings(ctx)
        val url = settings.heartbeatUrl ?: return null
        val secret = SecretStore(ctx).get() ?: return null
        val json = build(ctx, settings)
        val sentAt = System.currentTimeMillis()
        val response = WebhookClient().post(url, settings.deviceId, secret, UUID.randomUUID().toString(), json)
        val ok = response.httpStatus in 200..299
        settings.recordHeartbeat(ok, response.httpStatus, System.currentTimeMillis())
        EventStore.get(ctx).logHeartbeat(
            HeartbeatRecord(
                trigger = trigger,
                dueAtMs = dueAtMs,
                sentAtMs = sentAt,
                httpStatus = response.httpStatus,
                error = response.error,
                latencyMs = response.latencyMs,
            )
        )
        return response
    }

    private fun build(ctx: Context, settings: GatewaySettings): String {
        val stats = EventStore.get(ctx).stats()
        fun iso(ms: Any?) = (ms as? Long)?.let { TimeFormat.iso(it) } ?: JSONObject.NULL
        return JSONObject()
            .put("device_id", settings.deviceId)
            .put("install_id", settings.installId)
            .put("sent_at", TimeFormat.iso(System.currentTimeMillis()))
            .put("last_sequence", stats["lastSequence"])
            .put("pending_count", stats["pending"])
            .put("failed_count", stats["failed"])
            .put("money_pending_count", stats["moneyPending"])
            .put("money_failed_count", stats["moneyFailed"])
            .put("oldest_pending_received_at", iso(stats["oldestPendingAt"]))
            .put("last_sms_received_at", iso(settings.lastSmsReceivedAt))
            .put("last_delivered_at", iso(settings.lastDeliveredAt))
            .put("forwarding_enabled", settings.gatewayEnabled)
            .put("notification_access", NotificationAccess.isGranted(ctx))
            .put("notification_listener_connected", settings.listenerConnected)
            .put("monitored_packages", org.json.JSONArray(settings.monitoredPackages.sorted()))
            .put("last_notification_at", iso(settings.lastNotificationAt))
            .put("sms_enabled", settings.smsEnabled)
            .put("sms_permission", SmsModule.hasPermission(ctx))
            .put("filtered_counts", JSONObject(settings.filteredCounts() as Map<*, *>))
            .put(
                "battery_optimization_exempt",
                ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName),
            )
            .put("app_version", runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull())
            .toString()
    }
}
