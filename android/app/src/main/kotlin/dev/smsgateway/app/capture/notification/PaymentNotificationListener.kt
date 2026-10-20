package dev.smsgateway.app.capture.notification

import dev.smsgateway.app.capture.EventIngestor
import dev.smsgateway.app.config.GatewaySettings
import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.concurrent.Executors

/**
 * Bound by Android itself once the operator grants Notification Access, so it
 * runs with the UI closed and no Flutter engine. Reads nothing from apps the
 * operator has not approved: the package check happens before any extras are
 * touched. Never logs notification content.
 */
class PaymentNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        GatewaySettings(this).listenerConnected = true
        Log.i(TAG, "Listener connected")
    }

    override fun onListenerDisconnected() {
        GatewaySettings(this).listenerConnected = false
        Log.w(TAG, "Listener disconnected; requesting rebind")
        NotificationAccess.requestRebind(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val settings = GatewaySettings(this)
        if (sbn.packageName !in settings.monitoredPackages) return

        val raw = toRaw(sbn) ?: return
        val appContext = applicationContext
        io.execute {
            try {
                settings.lastNotificationAt = System.currentTimeMillis()
                val event = NotificationExtractor.extract(raw) ?: return@execute
                val outcome = EventIngestor.ingest(appContext, event)
                Log.i(TAG, "Notification from ${raw.packageName}: ${outcome.label}")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to persist notification: ${t.javaClass.simpleName}")
            }
        }
    }

    private fun toRaw(sbn: StatusBarNotification): RawNotification? {
        val n = sbn.notification ?: return null
        val extras: Bundle = n.extras ?: Bundle.EMPTY
        return RawNotification(
            packageName = sbn.packageName,
            key = sbn.key,
            whenMs = n.`when`,
            postTimeMs = sbn.postTime,
            title = extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.map { it.toString() },
            isGroupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            isOngoing = n.flags and Notification.FLAG_ONGOING_EVENT != 0,
        )
    }

    private companion object {
        const val TAG = "NotificationListener"
        val io = Executors.newSingleThreadExecutor()
    }
}
