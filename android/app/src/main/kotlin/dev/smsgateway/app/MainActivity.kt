package dev.smsgateway.app

import dev.smsgateway.app.capture.notification.NotificationAccess
import dev.smsgateway.app.capture.sms.SmsModule
import dev.smsgateway.app.channel.GatewayChannel
import dev.smsgateway.app.delivery.DeliveryScheduler
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

class MainActivity : FlutterActivity() {
    private var gateway: GatewayChannel? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        gateway = GatewayChannel(this, flutterEngine.dartExecutor.binaryMessenger)
        SmsModule.sync(this)
        NotificationAccess.requestRebind(this)
        DeliveryScheduler.ensurePeriodic(this)
    }

    override fun cleanUpFlutterEngine(flutterEngine: FlutterEngine) {
        gateway?.dispose()
        gateway = null
        super.cleanUpFlutterEngine(flutterEngine)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (gateway?.onPermissionResult(requestCode, grantResults) != true) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }
}
