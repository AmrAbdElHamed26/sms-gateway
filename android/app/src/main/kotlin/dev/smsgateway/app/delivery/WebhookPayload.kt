package dev.smsgateway.app.delivery

import dev.smsgateway.app.queue.GatewayEvent
import dev.smsgateway.app.queue.SourceType
import org.json.JSONObject

object WebhookPayload {
    /** Last line of a forwarded notification's body; the backend can match on it. */
    const val NOTIFICATION_NOTE_PREFIX = "[via notification: "

    /**
     * Built from stored fields only, so every retry of an event sends the same body.
     *
     * SMS and notifications share one set of fields, so the backend needs a
     * single schema. A notification is mapped onto it:
     *  - `sender` = the posting app's package name (stable, like an SMS sender ID)
     *  - `body`   = title + text + a last line "[via notification: <package>]"
     *  - `source` = the true origin, e.g. `android_notification` (see `EventSource`)
     */
    fun build(
        eventId: String,
        deviceId: String,
        sourceType: String,
        sourcePackage: String?,
        sender: String?,
        title: String?,
        body: String,
        receivedAtIso: String,
        source: String,
        sequence: Long? = null,
        installId: String? = null,
        moneyHint: Boolean = false,
    ): String {
        val isNotification = sourceType == SourceType.NOTIFICATION
        return JSONObject()
            .put("event_id", eventId)
            .put("device_id", deviceId)
            .put("sender", if (isNotification) sourcePackage.orEmpty() else sender.orEmpty())
            .put("body", if (isNotification) notificationBody(title, body, sourcePackage) else body)
            .put("received_at", receivedAtIso)
            .put("source", source)
            .apply {
                if (sequence != null) put("sequence", sequence)
                if (installId != null) put("install_id", installId)
            }
            .put("money_hint", moneyHint)
            .toString()
    }

    fun notificationBody(title: String?, text: String, packageName: String?): String =
        listOfNotNull(
            title?.takeIf { it.isNotBlank() && !text.startsWith(it) },
            text,
            "$NOTIFICATION_NOTE_PREFIX${packageName.orEmpty()}]",
        ).joinToString("\n")

    fun of(event: GatewayEvent, deviceId: String, installId: String): String = build(
        eventId = event.eventId,
        deviceId = deviceId,
        sourceType = event.sourceType,
        sourcePackage = event.sourcePackage,
        sender = event.sender,
        title = event.title,
        body = event.body,
        receivedAtIso = event.receivedAtIso,
        source = event.source,
        sequence = event.sequence,
        installId = installId,
        moneyHint = event.moneyHint,
    )
}
