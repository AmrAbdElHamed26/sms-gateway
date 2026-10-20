package dev.smsgateway.app.capture.notification

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService

/** Notification Access is granted by the operator in system settings; the app can only check and link to it. */
object NotificationAccess {

    fun component(context: Context) = ComponentName(context, PaymentNotificationListener::class.java)

    fun isGranted(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            ?: return false
        val mine = component(context)
        return enabled.split(':').mapNotNull { ComponentName.unflattenFromString(it) }.any { it == mine }
    }

    /** Opens our own toggle where supported (Android 11+), else the full list. */
    fun openSettings(activity: Activity) {
        val detail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component(activity).flattenToString())
        } else null
        val list = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        runCatching { activity.startActivity(detail ?: list) }.onFailure { runCatching { activity.startActivity(list) } }
    }

    /**
     * Asks Android to bind the listener again, e.g. after the process was
     * killed. A no-op when access is not granted. Android may still refuse.
     */
    fun requestRebind(context: Context) {
        if (!isGranted(context)) return
        runCatching { NotificationListenerService.requestRebind(component(context)) }
    }
}
