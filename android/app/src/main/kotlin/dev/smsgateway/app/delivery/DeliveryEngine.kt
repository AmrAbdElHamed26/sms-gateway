package dev.smsgateway.app.delivery

import dev.smsgateway.app.queue.EventStore
import dev.smsgateway.app.config.GatewaySettings
import dev.smsgateway.app.config.SecretStore
import dev.smsgateway.app.util.NetworkState
import android.content.Context
import android.util.Log
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Drains due events to the backend. Shared by [DeliveryWorker] and the
 * immediate attempt in SmsReceiver; the lock guarantees one drain at a time.
 *
 * Failure handling:
 *  - No network        → stop, don't count an attempt; network-constrained work resumes on reconnect.
 *  - Request failed    → retry per [DeliveryPolicy.RETRY_SCHEDULE_MS] (1 min, 1 min, 1 min, 2, 5, 10, 15…).
 *  - Payload rejected  → FAILED (visible, manually retryable).
 *  - Delivered / 409   → DELIVERED.
 * Each event keeps its event_id forever, so resending after a lost response is idempotent.
 */
object DeliveryEngine {
    private const val TAG = "DeliveryEngine"
    private const val BATCH_SIZE = 25
    private val lock = Mutex()

    suspend fun drain(context: Context, isStopped: () -> Boolean = { false }) {
        lock.withLock { drainLocked(context.applicationContext, isStopped) }
    }

    /** For plain threads (the receiver's immediate attempt). */
    fun drainBlocking(context: Context) = runBlocking { drain(context) }

    private fun drainLocked(ctx: Context, isStopped: () -> Boolean) {
        val settings = GatewaySettings(ctx)
        val store = EventStore.get(ctx)
        val url = settings.webhookUrl
        val secret = SecretStore(ctx).get()
        if (url == null || secret == null) {
            Log.w(TAG, "Backend not configured; events stay queued")
            return
        }
        val client = WebhookClient()
        val deviceId = settings.deviceId
        val installId = settings.installId

        drain@ while (!isStopped()) {
            val batch = store.due(System.currentTimeMillis(), BATCH_SIZE)
            if (batch.isEmpty()) break
            for (event in batch) {
                if (isStopped()) break@drain
                if (!NetworkState.isOnline(ctx)) {
                    Log.i(TAG, "Offline; waiting for network")
                    DeliveryScheduler.kick(ctx) // network-constrained: runs on reconnect
                    break@drain
                }
                val response = client.post(url, deviceId, secret, event.eventId, WebhookPayload.of(event, deviceId, installId))
                val now = System.currentTimeMillis()
                val attempts = event.attempts + 1
                when (val outcome = DeliveryPolicy.classify(response.httpStatus, response.error, response.detail)) {
                    DeliveryOutcome.Delivered -> {
                        store.markDelivered(event.id, attempts, response.httpStatus, now)
                        settings.lastDeliveredAt = now
                        settings.recordBackendResult(true, response.httpStatus, "delivered", now)
                    }
                    is DeliveryOutcome.Reject -> {
                        store.markFailed(event.id, attempts, outcome.reason, response.httpStatus)
                        settings.recordBackendResult(false, response.httpStatus, outcome.reason, now)
                        Log.w(TAG, "Event #${event.sequence} rejected: ${outcome.reason}")
                    }
                    is DeliveryOutcome.Retry -> {
                        if (response.httpStatus == null && !NetworkState.isOnline(ctx)) {
                            // Network dropped mid-request: not the backend's fault, don't back off.
                            DeliveryScheduler.kick(ctx)
                            break@drain
                        }
                        val nextAt = now + DeliveryPolicy.backoffDelayMs(attempts)
                        store.markRetry(event.id, attempts, nextAt, outcome.reason, response.httpStatus)
                        // Backend unhealthy: hold the rest of the queue too instead of burning a timeout each.
                        store.deferDue(now, nextAt)
                        settings.recordBackendResult(false, response.httpStatus, outcome.reason, now)
                        Log.w(TAG, "Event #${event.sequence} failed (${outcome.reason}); retry in ${(nextAt - now) / 1000}s")
                        break@drain
                    }
                }
            }
        }
        DeliveryScheduler.scheduleRetry(ctx, store.nextPendingAt())
    }
}
