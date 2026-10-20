package dev.smsgateway.app.queue

enum class EventStatus {
    PENDING,
    DELIVERED,
    FAILED,

    /** Local test-mode event: went through capture filters and the queue, never sent. */
    SIMULATED,
}

/** `source_type` on the wire. */
object SourceType {
    const val SMS = "sms"
    const val NOTIFICATION = "notification"
}

/** `source` on the wire. Only [isReal] sources may ever activate anything on the backend. */
object EventSource {
    const val ANDROID_SMS = "android_sms"
    const val ANDROID_NOTIFICATION = "android_notification"
    const val SIMULATED_SMS = "simulated_sms"
    const val SIMULATED_NOTIFICATION = "simulated_notification"
    const val GATEWAY_TEST = "gateway_test"
    const val GATEWAY_PING = "gateway_ping"

    fun isReal(source: String) = source == ANDROID_SMS || source == ANDROID_NOTIFICATION
}

/**
 * One queued event. [sequence] is the per-install, gap-free number sent to the
 * backend; it is assigned only when an event that will be sent is stored, so a
 * gap on the backend means a missing event. Simulated events have none.
 *
 * [sender] is set for SMS only, [sourcePackage] and [title] for notifications only.
 * [title] and [body] are wiped once the backend has acknowledged the event.
 */
data class GatewayEvent(
    val id: Long,
    val sequence: Long?,
    val eventId: String,
    val sourceType: String,
    val sourcePackage: String?,
    val sender: String?,
    val title: String?,
    val body: String,
    val receivedAtMs: Long,
    val receivedAtIso: String,
    val source: String,
    val moneyHint: Boolean,
    val status: EventStatus,
    val attempts: Int,
    val nextAttemptAtMs: Long,
    val lastError: String?,
    val lastHttpStatus: Int?,
    val deliveredAtMs: Long?,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "sequence" to sequence,
        "eventId" to eventId,
        "sourceType" to sourceType,
        "sourcePackage" to sourcePackage,
        "sender" to sender,
        "title" to title,
        "body" to body,
        "receivedAt" to receivedAtMs,
        "receivedAtIso" to receivedAtIso,
        "source" to source,
        "moneyHint" to moneyHint,
        "status" to status.name.lowercase(),
        "attempts" to attempts,
        "nextAttemptAt" to nextAttemptAtMs,
        "lastError" to lastError,
        "lastHttpStatus" to lastHttpStatus,
        "deliveredAt" to deliveredAtMs,
    )
}

/** An event about to be persisted. [dedupeKey] collapses duplicate broadcasts and notification updates. */
data class NewEvent(
    val eventId: String,
    val dedupeKey: String,
    val sourceType: String,
    val sourcePackage: String?,
    val sender: String?,
    val title: String?,
    val body: String,
    val receivedAtMs: Long,
    val receivedAtIso: String,
    val originTimestampMs: Long?,
    val source: String,
    val moneyHint: Boolean,
    val simulated: Boolean = false,
)
