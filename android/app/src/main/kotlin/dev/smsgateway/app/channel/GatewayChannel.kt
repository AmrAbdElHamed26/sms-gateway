package dev.smsgateway.app.channel

import dev.smsgateway.app.capture.CapturedEvent
import dev.smsgateway.app.capture.EventIngestor
import dev.smsgateway.app.capture.notification.InstalledApps
import dev.smsgateway.app.capture.notification.NotificationAccess
import dev.smsgateway.app.capture.notification.NotificationExtractor
import dev.smsgateway.app.capture.notification.RawNotification
import dev.smsgateway.app.capture.sms.AssembledSms
import dev.smsgateway.app.capture.sms.SmsModule
import dev.smsgateway.app.capture.sms.SmsReceiver
import dev.smsgateway.app.config.GatewaySettings
import dev.smsgateway.app.config.SecretStore
import dev.smsgateway.app.config.WebhookEndpoint
import dev.smsgateway.app.delivery.DeliveryOutcome
import dev.smsgateway.app.delivery.DeliveryPolicy
import dev.smsgateway.app.delivery.DeliveryScheduler
import dev.smsgateway.app.delivery.Heartbeat
import dev.smsgateway.app.delivery.HeartbeatScheduler
import dev.smsgateway.app.delivery.WebhookClient
import dev.smsgateway.app.delivery.WebhookPayload
import dev.smsgateway.app.queue.EventSource
import dev.smsgateway.app.queue.EventStatus
import dev.smsgateway.app.queue.EventStore
import dev.smsgateway.app.queue.HeartbeatRecord
import dev.smsgateway.app.queue.SourceType
import dev.smsgateway.app.util.NetworkState
import dev.smsgateway.app.util.TimeFormat
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * The dashboard's only window into the gateway. The Flutter UI is optional:
 * nothing here is on the capture or delivery path.
 */
class GatewayChannel(private val activity: Activity, messenger: BinaryMessenger) :
    MethodChannel.MethodCallHandler {

    private val channel = MethodChannel(messenger, CHANNEL).also { it.setMethodCallHandler(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val context get() = activity.applicationContext
    private val settings get() = GatewaySettings(context)
    private val secrets get() = SecretStore(context)
    private val store get() = EventStore.get(context)
    private var pendingPermission: MethodChannel.Result? = null

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "requestSmsPermission" -> requestSmsPermission(result)
            "openBatteryOptimizationSettings" -> {
                openBatteryOptimizationSettings()
                result.success(null)
            }
            "openNotificationAccessSettings" -> {
                NotificationAccess.openSettings(activity)
                result.success(null)
            }
            else -> scope.launch {
                try {
                    result.success(withContext(Dispatchers.IO) { handle(call) })
                } catch (e: InvalidArgument) {
                    result.error("INVALID_ARGUMENT", e.message, null)
                } catch (e: NotImplementedError) {
                    result.notImplemented()
                } catch (e: Exception) {
                    result.error("GATEWAY_ERROR", e.javaClass.simpleName, null)
                }
            }
        }
    }

    private fun handle(call: MethodCall): Any? = when (call.method) {
        "getStatus" -> status()
        "getStats" -> store.stats()
        "getEvents" -> store.list(
            limit = call.argument<Int>("limit") ?: 50,
            offset = call.argument<Int>("offset") ?: 0,
            status = call.argument<String>("status")?.let { EventStatus.valueOf(it.uppercase()) },
            moneyOnly = call.argument<Boolean>("moneyOnly") ?: false,
        ).map { it.toMap() }
        "getSettings" -> settings.toMap(secrets.isConfigured())
        "saveSettings" -> saveSettings(call)
        "clearSecret" -> {
            secrets.clear()
            settings.gatewayEnabled = false
            null
        }
        "getInstalledApps" -> installedApps()
        "setMonitoredPackages" -> {
            settings.monitoredPackages = call.argument<List<String>>("packages").orEmpty().toSet()
            settings.monitoredPackages.toList()
        }
        "testConnection" -> testConnection()
        "sendTestWebhook" -> EventIngestor.storeTestEvent(
            context,
            CapturedEvent(
                sourceType = SourceType.SMS,
                source = EventSource.GATEWAY_TEST,
                sourcePackage = null,
                sender = "SMS-GATEWAY",
                title = null,
                body = "Test webhook from ${settings.deviceId}",
                originTimestampMs = null,
                dedupeKey = null,
            ),
        ).label
        "simulateEvent" -> simulate(call)
        "getHeartbeats" -> mapOf(
            "intervalMs" to HeartbeatScheduler.INTERVAL_MS,
            "nextAt" to settings.heartbeatNextAt,
            "exactAlarms" to HeartbeatScheduler.exactAlarmsAllowed(context),
            "records" to store.heartbeats(call.argument<Int>("limit") ?: 100).map { it.toMap() },
        )
        "sendHeartbeat" -> {
            val response = Heartbeat.send(context, HeartbeatRecord.TRIGGER_MANUAL)
                ?: throw InvalidArgument("Backend not configured")
            mapOf(
                "ok" to (response.httpStatus in 200..299),
                "httpStatus" to response.httpStatus,
                "latencyMs" to response.latencyMs,
                "message" to (response.httpStatus?.let { "HTTP $it" } ?: response.error),
            )
        }
        "retryPending" -> store.retryAll(System.currentTimeMillis()).also { DeliveryScheduler.kick(context) }
        else -> throw NotImplementedError()
    }

    private fun status(): Map<String, Any?> {
        val s = settings
        return mapOf(
            "gatewayEnabled" to s.gatewayEnabled,
            "backendConfigured" to (s.webhookUrl != null && secrets.isConfigured()),
            "notificationAccess" to NotificationAccess.isGranted(context),
            "listenerConnected" to s.listenerConnected,
            "listenerChangedAt" to s.listenerChangedAt,
            "monitoredCount" to s.monitoredPackages.size,
            "lastNotificationAt" to s.lastNotificationAt,
            "smsEnabled" to s.smsEnabled,
            "smsPermission" to SmsModule.hasPermission(context),
            "smsReceiverEnabled" to SmsModule.isReceiverEnabled(context),
            "smsAllowlistCount" to s.smsAllowlist.size,
            "lastSmsReceivedAt" to s.lastSmsReceivedAt,
            "ignoringBatteryOptimizations" to isIgnoringBatteryOptimizations(),
            "networkOnline" to NetworkState.isOnline(context),
            "lastDeliveredAt" to s.lastDeliveredAt,
            "filteredCounts" to s.filteredCounts(),
            "backend" to s.backendStatus(),
            "heartbeat" to s.heartbeatStatus(),
        )
    }

    /** Launchable apps, plus monitored packages that are no longer installed (so they can be removed). */
    private fun installedApps(): List<Map<String, Any?>> {
        val monitored = settings.monitoredPackages
        val apps = InstalledApps.list(context)
        val installed = apps.map { it["packageName"] as String }.toSet()
        val missing = (monitored - installed).map {
            mapOf("packageName" to it, "label" to it, "icon" to null, "installed" to false)
        }
        return (apps + missing).map { it + ("monitored" to (it["packageName"] in monitored)) }
    }

    private fun saveSettings(call: MethodCall): Map<String, Any?> {
        val s = settings
        val url = call.argument<String>("backendUrl")?.trim().orEmpty()
        val deviceId = call.argument<String>("deviceId")?.trim().orEmpty()
        val secret = call.argument<String>("secret")
        val gatewayEnabled = call.argument<Boolean>("gatewayEnabled") ?: false
        val smsEnabled = call.argument<Boolean>("smsEnabled") ?: false
        val allowlist = call.argument<List<String>>("smsAllowlist").orEmpty()
        val retention = call.argument<Int>("retentionDays") ?: GatewaySettings.DEFAULT_RETENTION_DAYS

        if (url.isNotEmpty() && !WebhookEndpoint.isValidBase(url)) {
            throw InvalidArgument("Backend URL must be an absolute https:// URL")
        }
        if (!DEVICE_ID.matches(deviceId)) {
            throw InvalidArgument("Device ID: 3-64 chars, letters, digits, '.', '_' or '-'")
        }
        if (retention !in 1..365) throw InvalidArgument("Retention must be 1-365 days")
        val secretWillBeSet = !secret.isNullOrEmpty() || secrets.isConfigured()
        if (gatewayEnabled && (url.isEmpty() || !secretWillBeSet)) {
            throw InvalidArgument("Configure backend URL and webhook secret before enabling the gateway")
        }
        if (smsEnabled && allowlist.none { it.isNotBlank() }) {
            throw InvalidArgument("Add at least one approved SMS sender before enabling SMS monitoring")
        }
        if (!secret.isNullOrEmpty()) {
            if (secret.length < 16) throw InvalidArgument("Webhook secret must be at least 16 characters")
            secrets.set(secret)
        }

        s.backendUrl = url.ifEmpty { null }
        s.deviceId = deviceId
        s.gatewayEnabled = gatewayEnabled
        s.smsEnabled = smsEnabled
        s.smsAllowlist = allowlist
        s.retentionDays = retention

        SmsModule.sync(context)
        NotificationAccess.requestRebind(context)
        DeliveryScheduler.ensurePeriodic(context)
        DeliveryScheduler.kick(context)
        return s.toMap(secrets.isConfigured())
    }

    /**
     * Local test mode: runs a synthetic event through the real capture filters,
     * dedupe and queue. `localOnly` stores it as SIMULATED, never sent;
     * otherwise it is queued and sent with a `simulated_*` source.
     */
    private fun simulate(call: MethodCall): String {
        val type = call.argument<String>("sourceType") ?: throw InvalidArgument("sourceType is required")
        val body = call.argument<String>("body").orEmpty()
        val localOnly = call.argument<Boolean>("localOnly") ?: true
        val now = System.currentTimeMillis()
        val event = when (type) {
            SourceType.NOTIFICATION -> {
                val pkg = call.argument<String>("packageName")?.trim().orEmpty()
                if (pkg.isEmpty()) throw InvalidArgument("Pick the app to simulate")
                NotificationExtractor.extract(
                    RawNotification(
                        packageName = pkg,
                        key = "simulated|$pkg|${UUID.randomUUID()}",
                        whenMs = now,
                        postTimeMs = now,
                        title = call.argument<String>("title"),
                        text = body,
                        bigText = null,
                        textLines = null,
                        isGroupSummary = false,
                        isOngoing = false,
                    ),
                    source = EventSource.SIMULATED_NOTIFICATION,
                ) ?: throw InvalidArgument("Simulated notification has no text")
            }
            SourceType.SMS -> {
                val sender = call.argument<String>("sender")?.trim().orEmpty()
                if (sender.isEmpty() || body.isBlank()) throw InvalidArgument("Sender and body are required")
                SmsReceiver.capturedEvent(AssembledSms(sender, body, now), EventSource.SIMULATED_SMS)
            }
            else -> throw InvalidArgument("Unknown sourceType $type")
        }
        return EventIngestor.ingest(context, event, now, simulated = localOnly).label
    }

    /** Signed ping straight to the backend; not stored, not queued. */
    private fun testConnection(): Map<String, Any?> {
        val s = settings
        val url = s.webhookUrl ?: return mapOf("ok" to false, "message" to "Backend URL not configured")
        val secret = secrets.get() ?: return mapOf("ok" to false, "message" to "Webhook secret not configured")
        val eventId = UUID.randomUUID().toString()
        val json = WebhookPayload.build(
            eventId = eventId,
            deviceId = s.deviceId,
            sourceType = SourceType.SMS,
            sourcePackage = null,
            sender = "SMS-GATEWAY",
            title = null,
            body = "ping",
            receivedAtIso = TimeFormat.iso(System.currentTimeMillis()),
            source = EventSource.GATEWAY_PING,
        )
        val response = WebhookClient().post(url, s.deviceId, secret, eventId, json)
        val outcome = DeliveryPolicy.classify(response.httpStatus, response.error, response.detail)
        val ok = outcome == DeliveryOutcome.Delivered
        val message = when (outcome) {
            DeliveryOutcome.Delivered -> "Connected (HTTP ${response.httpStatus})"
            is DeliveryOutcome.Retry -> outcome.reason
            is DeliveryOutcome.Reject -> outcome.reason
        }
        s.recordBackendResult(ok, response.httpStatus, message, System.currentTimeMillis())
        return mapOf("ok" to ok, "httpStatus" to response.httpStatus, "latencyMs" to response.latencyMs, "message" to message)
    }

    /** Only called when the operator enables the SMS module. */
    private fun requestSmsPermission(result: MethodChannel.Result) {
        if (SmsModule.hasPermission(context)) return result.success(true)
        if (pendingPermission != null) return result.error("BUSY", "Permission request in progress", null)
        pendingPermission = result
        activity.requestPermissions(arrayOf(Manifest.permission.RECEIVE_SMS), PERMISSION_REQUEST)
    }

    /** Forwarded from [dev.smsgateway.app.MainActivity.onRequestPermissionsResult]. */
    fun onPermissionResult(requestCode: Int, grantResults: IntArray): Boolean {
        if (requestCode != PERMISSION_REQUEST) return false
        pendingPermission?.success(grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
        pendingPermission = null
        return true
    }

    private fun isIgnoringBatteryOptimizations(): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    /** Sends the operator to the system dialog; the app never changes the setting itself. */
    @SuppressLint("BatteryLife") // Sideloaded gateway, not a Play Store app.
    private fun openBatteryOptimizationSettings() {
        val intent = if (isIgnoringBatteryOptimizations()) {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        } else {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
        }
        runCatching { activity.startActivity(intent) }
    }

    fun dispose() {
        channel.setMethodCallHandler(null)
        pendingPermission?.success(false)
        pendingPermission = null
        scope.cancel()
    }

    private class InvalidArgument(message: String) : Exception(message)

    private companion object {
        const val CHANNEL = "dev.smsgateway.app/gateway"
        const val PERMISSION_REQUEST = 4201
        val DEVICE_ID = Regex("^[A-Za-z0-9._-]{3,64}$")
    }
}
