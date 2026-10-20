package dev.smsgateway.app.capture.sms

import dev.smsgateway.app.config.GatewaySettings
import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** Turns the optional SMS module on and off at the component level. */
object SmsModule {

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

    fun isReceiverEnabled(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(component(context)) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    /** Enables the receiver only when the module is on. Call after settings change, at launch and at boot. */
    fun sync(context: Context) {
        val enabled = GatewaySettings(context).smsEnabled
        val wanted = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        val pm = context.packageManager
        if (pm.getComponentEnabledSetting(component(context)) != wanted) {
            pm.setComponentEnabledSetting(component(context), wanted, PackageManager.DONT_KILL_APP)
        }
    }

    private fun component(context: Context) = ComponentName(context, SmsReceiver::class.java)
}
