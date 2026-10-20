package dev.smsgateway.app.capture.sms

/** Approved SMS senders. Required: an empty allowlist forwards nothing. */
object SenderFilter {
    fun allows(sender: String, allowlist: Collection<String>): Boolean {
        val key = normalize(sender)
        return key.isNotEmpty() && allowlist.any { normalize(it) == key }
    }

    /**
     * Case-, whitespace- and dash-insensitive, and phone numbers match with or
     * without the Egyptian country code: "VF-Cash" = "vf cash", "+201012345678" = "01012345678".
     */
    fun normalize(sender: String): String {
        val compact = sender.lowercase().filterNot { it.isWhitespace() || it == '-' || it == '_' }
        if (compact.isNotEmpty() && compact.all { it.isDigit() || it == '+' }) {
            val digits = compact.filter { it.isDigit() }
            return when {
                digits.startsWith("0020") -> "0" + digits.removePrefix("0020")
                digits.startsWith("20") && digits.length == 12 -> "0" + digits.removePrefix("20")
                else -> digits
            }
        }
        return compact
    }
}
