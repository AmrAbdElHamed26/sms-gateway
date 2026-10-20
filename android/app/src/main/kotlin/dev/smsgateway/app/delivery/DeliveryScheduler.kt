package dev.smsgateway.app.delivery

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Three unique work chains running [DeliveryWorker], plus the heartbeat:
 *  - NOW:      a new event arrived (or the network dropped); expedited, so it
 *              runs as soon as the network is available, even under Doze
 *              while the expedited quota lasts. APPEND so an event stored
 *              during a running drain still gets its own pass.
 *  - RETRY:    the earliest pending event's backoff deadline; REPLACE keeps one.
 *  - PERIODIC: 15-minute safety net + retention purge.
 * WorkManager persists all of them across process death and reboot. The
 * heartbeat runs on an exact alarm instead ([HeartbeatScheduler]): WorkManager
 * can't keep a 2-minute cadence through Doze.
 */
object DeliveryScheduler {
    private const val WORK_NOW = "sms-delivery-now"
    private const val WORK_RETRY = "sms-delivery-retry"
    private const val WORK_PERIODIC = "sms-delivery-periodic"
    /** Pre-alarm heartbeat chain; cancelled so installs that had it don't beat twice. */
    private const val LEGACY_WORK_HEARTBEAT = "sms-heartbeat"

    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun kick(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NOW,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<DeliveryWorker>()
                .setConstraints(online)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build(),
        )
    }

    fun scheduleRetry(context: Context, atMs: Long?) {
        val wm = WorkManager.getInstance(context)
        if (atMs == null) return
        val delay = (atMs - System.currentTimeMillis()).coerceAtLeast(0L)
        wm.enqueueUniqueWork(
            WORK_RETRY,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<DeliveryWorker>()
                .setConstraints(online)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build(),
        )
    }

    fun ensurePeriodic(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DeliveryWorker>(15, TimeUnit.MINUTES)
                .setConstraints(online)
                .setInputData(Data.Builder().putBoolean(DeliveryWorker.KEY_MAINTENANCE, true).build())
                .build(),
        )
        WorkManager.getInstance(context).cancelUniqueWork(LEGACY_WORK_HEARTBEAT)
        HeartbeatScheduler.ensure(context)
    }
}
