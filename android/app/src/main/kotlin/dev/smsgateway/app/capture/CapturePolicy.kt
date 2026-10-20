package dev.smsgateway.app.capture

import dev.smsgateway.app.capture.sms.SenderFilter
import dev.smsgateway.app.queue.SourceType

/** What a capture module saw, before any filtering. */
data class CapturedEvent(
    val sourceType: String,
    val source: String,
    val sourcePackage: String?,
    val sender: String?,
    val title: String?,
    val body: String,
    val originTimestampMs: Long?,
    /** Stable identity for duplicate suppression; null = always unique. */
    val dedupeKey: String?,
)

/** Snapshot of the operator's filter settings. */
data class CaptureConfig(
    val gatewayEnabled: Boolean,
    val monitoredPackages: Set<String>,
    val smsEnabled: Boolean,
    val smsAllowlist: List<String>,
)

enum class Decision(val forwards: Boolean) {
    ACCEPT(true),
    GATEWAY_DISABLED(false),
    SOURCE_DISABLED(false),
    NOT_APPROVED(false),
    EMPTY(false),
    OTP(false),
}

/**
 * The one place that decides whether a captured event may leave the phone.
 * Pure, so the rules are unit-tested. Order matters: the cheapest, most
 * privacy-preserving checks run first.
 */
object CapturePolicy {

    fun evaluate(event: CapturedEvent, config: CaptureConfig, simulated: Boolean = false): Decision {
        // Simulations exercise the filters even before the gateway is switched on.
        if (!simulated && !config.gatewayEnabled) return Decision.GATEWAY_DISABLED

        when (event.sourceType) {
            SourceType.NOTIFICATION ->
                if (event.sourcePackage == null || event.sourcePackage !in config.monitoredPackages) {
                    return Decision.NOT_APPROVED
                }
            SourceType.SMS -> {
                if (!config.smsEnabled) return Decision.SOURCE_DISABLED
                if (event.sender == null || !SenderFilter.allows(event.sender, config.smsAllowlist)) {
                    return Decision.NOT_APPROVED
                }
            }
            else -> return Decision.NOT_APPROVED
        }

        val text = listOfNotNull(event.title, event.body).joinToString("\n")
        if (text.isBlank()) return Decision.EMPTY
        if (OtpFilter.looksLikeOtp(text)) return Decision.OTP
        return Decision.ACCEPT
    }
}
