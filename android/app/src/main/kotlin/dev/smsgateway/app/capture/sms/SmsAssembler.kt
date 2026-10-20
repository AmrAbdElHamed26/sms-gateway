package dev.smsgateway.app.capture.sms

/** One PDU-level part as delivered in an SMS_RECEIVED broadcast. */
data class SmsPart(val address: String?, val body: String?, val timestampMs: Long)

/** A complete message: every part from one sender, concatenated in order. */
data class AssembledSms(val sender: String, val body: String, val timestampMs: Long)

object SmsAssembler {
    const val UNKNOWN_SENDER = "unknown"

    /**
     * Android delivers all parts of a concatenated (multipart) SMS in a single
     * broadcast, in sequence order. Parts are grouped by sender and joined
     * without separators — splitting happened mid-text, often mid-word.
     */
    fun assemble(parts: List<SmsPart>): List<AssembledSms> {
        val bySender = LinkedHashMap<String, MutableList<SmsPart>>()
        for (part in parts) {
            val sender = part.address?.trim().takeUnless { it.isNullOrEmpty() } ?: UNKNOWN_SENDER
            bySender.getOrPut(sender) { mutableListOf() }.add(part)
        }
        return bySender.map { (sender, group) ->
            AssembledSms(
                sender = sender,
                body = group.joinToString(separator = "") { it.body.orEmpty() },
                timestampMs = group.first().timestampMs,
            )
        }
    }
}
