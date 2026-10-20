package dev.smsgateway.app.capture

import dev.smsgateway.app.config.GatewaySettings
import dev.smsgateway.app.delivery.DeliveryEngine
import dev.smsgateway.app.delivery.DeliveryScheduler
import dev.smsgateway.app.queue.EventStore
import dev.smsgateway.app.queue.NewEvent
import dev.smsgateway.app.util.TimeFormat
import android.content.Context
import java.util.UUID
import java.util.concurrent.Executors

enum class IngestResult { STORED, SIMULATED, DUPLICATE, FILTERED }

data class IngestOutcome(val result: IngestResult, val decision: Decision) {
    /** e.g. "stored", "filtered: otp". Shown in the dashboard's testing tab. */
    val label: String
        get() = if (result == IngestResult.FILTERED) "filtered: ${decision.name.lowercase()}" else result.name.lowercase()
}

/**
 * Shared by the notification and SMS modules: filter → persist → schedule.
 * Persisting happens before any network attempt.
 */
object EventIngestor {
    /** Separate thread so an in-flight HTTP call never delays storing the next event. */
    private val sender = Executors.newSingleThreadExecutor()

    fun ingest(
        context: Context,
        event: CapturedEvent,
        receivedAtMs: Long = System.currentTimeMillis(),
        simulated: Boolean = false,
    ): IngestOutcome {
        val settings = GatewaySettings(context)
        val config = CaptureConfig(
            gatewayEnabled = settings.gatewayEnabled,
            monitoredPackages = settings.monitoredPackages,
            smsEnabled = settings.smsEnabled,
            smsAllowlist = settings.smsAllowlist,
        )
        val decision = CapturePolicy.evaluate(event, config, simulated)
        if (!decision.forwards) {
            if (!simulated && decision != Decision.GATEWAY_DISABLED) settings.countFiltered(decision.name.lowercase())
            return IngestOutcome(IngestResult.FILTERED, decision)
        }
        return store(context, event, receivedAtMs, simulated, decision)
    }

    /** Bypasses the filters: for the dashboard's own test webhook only. */
    fun storeTestEvent(context: Context, event: CapturedEvent): IngestOutcome =
        store(context, event, System.currentTimeMillis(), simulated = false, decision = Decision.ACCEPT)

    private fun store(
        context: Context,
        event: CapturedEvent,
        receivedAtMs: Long,
        simulated: Boolean,
        decision: Decision,
    ): IngestOutcome {
        val eventId = UUID.randomUUID().toString()
        val stored = EventStore.get(context).insert(
            NewEvent(
                eventId = eventId,
                dedupeKey = event.dedupeKey ?: eventId,
                sourceType = event.sourceType,
                sourcePackage = event.sourcePackage,
                sender = event.sender,
                title = event.title,
                body = event.body,
                receivedAtMs = receivedAtMs,
                receivedAtIso = TimeFormat.iso(receivedAtMs),
                originTimestampMs = event.originTimestampMs,
                source = event.source,
                moneyHint = MoneyDetector.looksLikeMoney(listOfNotNull(event.title, event.body).joinToString("\n")),
                simulated = simulated,
            )
        )
        if (!stored) return IngestOutcome(IngestResult.DUPLICATE, decision)
        if (simulated) return IngestOutcome(IngestResult.SIMULATED, decision)

        // Durable fallback first, then an immediate attempt in this process.
        DeliveryScheduler.kick(context)
        val appContext = context.applicationContext
        sender.execute { runCatching { DeliveryEngine.drainBlocking(appContext) } }
        return IngestOutcome(IngestResult.STORED, decision)
    }
}
