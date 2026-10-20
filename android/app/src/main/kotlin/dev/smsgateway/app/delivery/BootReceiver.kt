package dev.smsgateway.app.delivery

import dev.smsgateway.app.capture.notification.NotificationAccess
import dev.smsgateway.app.capture.sms.SmsModule
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * WorkManager already reschedules persisted work after reboot; this re-asserts
 * the periodic safety net, drains anything queued before shutdown, re-syncs the
 * SMS module and asks Android to rebind the notification listener.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                SmsModule.sync(context)
                NotificationAccess.requestRebind(context)
                DeliveryScheduler.ensurePeriodic(context)
                DeliveryScheduler.kick(context)
            }
        }
    }
}
