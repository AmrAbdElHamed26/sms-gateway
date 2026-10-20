package dev.smsgateway.app.delivery

import dev.smsgateway.app.capture.notification.NotificationAccess
import dev.smsgateway.app.queue.EventStore
import dev.smsgateway.app.config.GatewaySettings
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class DeliveryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val maintenance = inputData.getBoolean(KEY_MAINTENANCE, false)
        if (maintenance) {
            purgeExpired()
            // Reconnection: if Android dropped the listener binding, ask for it back.
            if (!GatewaySettings(applicationContext).listenerConnected) NotificationAccess.requestRebind(applicationContext)
            HeartbeatScheduler.ensure(applicationContext)
        }
        DeliveryEngine.drain(applicationContext) { isStopped }
        Result.success()
    }

    /**
     * Only used for expedited work on Android 11 and below, where WorkManager
     * runs it as a short foreground service. Android 12+ uses expedited jobs
     * and never shows this.
     */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Event delivery", NotificationManager.IMPORTANCE_MIN)
        )
        val notification = Notification.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Forwarding payment events")
            .setOngoing(true)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private fun purgeExpired() {
        val days = GatewaySettings(applicationContext).retentionDays
        if (days <= 0) return
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
        val purged = EventStore.get(applicationContext).purgeFinishedBefore(cutoff)
        if (purged > 0) Log.i(TAG, "Retention: purged $purged event(s) older than $days day(s)")
    }

    companion object {
        const val KEY_MAINTENANCE = "maintenance"
        private const val TAG = "DeliveryWorker"
        private const val CHANNEL_ID = "sms_delivery"
        private const val NOTIFICATION_ID = 4202
    }
}
