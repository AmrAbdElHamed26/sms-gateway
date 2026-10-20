package dev.smsgateway.app.capture.notification

import dev.smsgateway.app.capture.CapturedEvent
import dev.smsgateway.app.queue.EventSource
import dev.smsgateway.app.queue.SourceType
import dev.smsgateway.app.util.Hashing

/** The fields read from a StatusBarNotification, as plain values (testable without Android). */
data class RawNotification(
    val packageName: String,
    val key: String,
    /** Notification.when: set by the posting app, stable across updates of one notification. */
    val whenMs: Long,
    val postTimeMs: Long,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val textLines: List<String>?,
    val isGroupSummary: Boolean,
    val isOngoing: Boolean,
)

object NotificationExtractor {

    /**
     * @return the event to ingest, or null when the notification carries nothing
     * forwardable (group summaries, ongoing/progress notifications, empty text).
     */
    fun extract(raw: RawNotification, source: String = EventSource.ANDROID_NOTIFICATION): CapturedEvent? {
        // A summary repeats its children; an ongoing notification is a sync/progress indicator.
        if (raw.isGroupSummary || raw.isOngoing) return null

        val title = raw.title?.trim()?.takeIf { it.isNotEmpty() }
        val body = bodyOf(raw) ?: return null
        val originMs = raw.whenMs.takeIf { it > 0 } ?: raw.postTimeMs

        return CapturedEvent(
            sourceType = SourceType.NOTIFICATION,
            source = source,
            sourcePackage = raw.packageName,
            sender = null,
            title = title,
            body = body,
            originTimestampMs = originMs,
            dedupeKey = dedupeKey(raw.packageName, raw.key, originMs, title, body),
        )
    }

    /** Big text wins (the expanded, complete message); then inbox lines; then the short text. */
    fun bodyOf(raw: RawNotification): String? {
        raw.bigText?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        raw.textLines?.map { it.trim() }?.filter { it.isNotEmpty() }?.takeIf { it.isNotEmpty() }
            ?.let { return it.joinToString("\n") }
        return raw.text?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Apps re-post the same notification to update it (progress, a re-alert, a
     * changed badge). Same package + key + `when` + content = the same event.
     * A second payment with identical text still gets a new `when`, so it is kept.
     */
    fun dedupeKey(packageName: String, key: String, originMs: Long, title: String?, body: String): String =
        Hashing.sha256Hex("notification\u0000$packageName\u0000$key\u0000$originMs\u0000${title.orEmpty()}\u0000$body")
}
