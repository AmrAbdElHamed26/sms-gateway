package dev.smsgateway.app.delivery

import dev.smsgateway.app.config.GatewaySettings
import dev.smsgateway.app.queue.HeartbeatRecord
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.Executors

/**
 * Heartbeat every [INTERVAL_MS], on an exact allow-while-idle alarm.
 *
 * WorkManager can't hold a 2-minute cadence: with the screen off and the phone
 * on battery, Doze defers jobs to its maintenance windows (5-6 min in light
 * Doze, 10-30+ min in deep Doze), even for battery-optimisation-exempt apps.
 * `setExactAndAllowWhileIdle` alarms fire inside Doze and wake the CPU; Android
 * allows up to 72 per hour, and we use 30.
 *
 * Each firing arms the next alarm before sending, so a failed or slow request
 * never breaks the chain. [ensure] re-arms it from app start, boot, update and
 * the 15-minute maintenance job, in case Android dropped it.
 */
object HeartbeatScheduler {
    internal const val TAG = "HeartbeatScheduler"
    const val INTERVAL_MS = 2 * 60_000L
    private const val EXTRA_DUE_AT = "due_at"

    /** Arms the alarm unless one is already due within the next interval. Idempotent. */
    fun ensure(context: Context) {
        val now = System.currentTimeMillis()
        val next = GatewaySettings(context).heartbeatNextAt
        val delay = if (next != null && next > now && next <= now + INTERVAL_MS) next - now else 0L
        arm(context, delay)
    }

    /** Called when an alarm fires: the next beat is one interval from now. */
    fun scheduleNext(context: Context) = arm(context, INTERVAL_MS)

    /** On Android 12 the operator can revoke exact alarms; we then fall back to inexact allow-while-idle. */
    fun exactAlarmsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun arm(context: Context, delayMs: Long) {
        val ctx = context.applicationContext
        val dueAt = System.currentTimeMillis() + delayMs
        val pi = PendingIntent.getBroadcast(
            ctx,
            0,
            Intent(ctx, HeartbeatAlarmReceiver::class.java).putExtra(EXTRA_DUE_AT, dueAt),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val am = ctx.getSystemService(AlarmManager::class.java)
        // Elapsed time: a wall-clock change must not stall or burst the chain.
        val triggerAt = SystemClock.elapsedRealtime() + delayMs
        if (exactAlarmsAllowed(ctx)) {
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
        }
        GatewaySettings(ctx).heartbeatNextAt = dueAt
    }

    internal fun dueAt(intent: Intent): Long =
        intent.getLongExtra(EXTRA_DUE_AT, 0L).takeIf { it > 0 } ?: System.currentTimeMillis()
}

class HeartbeatAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val dueAt = HeartbeatScheduler.dueAt(intent)
        HeartbeatScheduler.scheduleNext(appContext)
        // The alarm's wake lock is held until finish(): the request runs with the CPU awake.
        val pending = goAsync()
        io.execute {
            try {
                Heartbeat.send(appContext, HeartbeatRecord.TRIGGER_ALARM, dueAt)
            } catch (t: Throwable) {
                Log.w(HeartbeatScheduler.TAG, "Heartbeat failed: ${t.javaClass.simpleName}")
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val io = Executors.newSingleThreadExecutor()
    }
}
