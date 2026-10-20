package dev.smsgateway.app.delivery

import kotlin.random.Random

sealed interface DeliveryOutcome {
    /** Backend durably accepted the event (2xx), or already had it (409). */
    data object Delivered : DeliveryOutcome
    /** Transient — keep the event and try again later. */
    data class Retry(val reason: String) : DeliveryOutcome
    /** The backend says this payload will never be accepted. */
    data class Reject(val reason: String) : DeliveryOutcome
}

object DeliveryPolicy {
    /**
     * Delay after the Nth failed attempt (index N-1): three quick 1-minute
     * retries, then backing off to every 15 minutes, forever. "No network" is
     * not a failed attempt — it waits for connectivity instead (see DeliveryEngine).
     */
    val RETRY_SCHEDULE_MS = longArrayOf(
        60_000L, 60_000L, 60_000L, 2 * 60_000L, 5 * 60_000L, 10 * 60_000L, 15 * 60_000L,
    )
    val MAX_DELAY_MS = RETRY_SCHEDULE_MS.last()

    /**
     * Only explicit payload rejections are permanent. Auth (401/403) and
     * routing (404) errors are retried: they are fixed by correcting the
     * gateway or backend config, and dropping a payment SMS is worse than
     * retrying it.
     */
    private val PERMANENT = setOf(400, 413, 415, 422)

    fun classify(httpStatus: Int?, transportError: String? = null, detail: String? = null): DeliveryOutcome {
        val reason = "HTTP $httpStatus" + (detail?.let { " ($it)" } ?: "")
        return when {
            httpStatus == null -> DeliveryOutcome.Retry(transportError ?: "network error")
            httpStatus in 200..299 || httpStatus == 409 -> DeliveryOutcome.Delivered
            httpStatus in PERMANENT -> DeliveryOutcome.Reject(reason)
            else -> DeliveryOutcome.Retry(reason)
        }
    }

    /** [attempts] counts the attempt that just failed. ±10% jitter so devices don't sync up. */
    fun backoffDelayMs(attempts: Int, random: Random = Random.Default): Long {
        val base = RETRY_SCHEDULE_MS[(attempts - 1).coerceIn(0, RETRY_SCHEDULE_MS.lastIndex)]
        val jitter = 0.9 + 0.2 * random.nextDouble()
        return (base * jitter).toLong()
    }
}
