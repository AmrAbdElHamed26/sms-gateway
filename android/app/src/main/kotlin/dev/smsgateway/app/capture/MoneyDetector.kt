package dev.smsgateway.app.capture

/**
 * Generic "does this SMS talk about money?" hint. Not provider-specific and
 * not a payment decision — the backend parses and validates every message.
 * Used to (a) never let the allowlist drop a money SMS, (b) flag events so the
 * backend and dashboard can watch money SMS completeness closely.
 *
 * Deliberately over-inclusive: a false positive costs nothing (every SMS is
 * forwarded anyway); a false negative only loses the flag, never the SMS.
 */
object MoneyDetector {
    private val number = Regex("""\d[\d,]*(?:\.\d+)?""")

    private val currencyWord = Regex(
        """(?i)\b(egp|le|l\.e|usd|eur|gbp|sar|aed|kwd|qar|jod)\b|\$|€|£"""
    )

    private val arabicKeywords = listOf(
        "جنيه", "ج.م", "ريال", "درهم", "دينار", "دولار", "يورو",
        "مبلغ", "تحويل", "حوال", "استلام", "استلمت", "ايداع", "إيداع", "رصيد", "خصم",
        "دفع", "سداد", "شحن", "محفظة", "سحب", "مدفوعات", "قيمة", "اضافة", "إضافة",
    )

    private val englishKeywords = listOf(
        "amount", "received", "transfer", "credited", "debited", "deposit", "withdraw",
        "payment", "paid", "balance", "purchase", "refund", "wallet", "cash", "sent you",
    )

    fun looksLikeMoney(body: String): Boolean {
        val text = normalizeDigits(body)
        if (!number.containsMatchIn(text)) return false
        val lower = text.lowercase()
        return currencyWord.containsMatchIn(text) ||
            arabicKeywords.any { it in text } ||
            englishKeywords.any { it in lower }
    }

    /** Arabic-Indic (٠-٩) and Eastern Arabic/Persian (۰-۹) digits → ASCII. */
    fun normalizeDigits(text: String): String = buildString(text.length) {
        for (c in text) append(
            when (c) {
                in '٠'..'٩' -> '0' + (c - '٠')
                in '۰'..'۹' -> '0' + (c - '۰')
                '٫' -> '.' // Arabic decimal separator
                '٬' -> ',' // Arabic thousands separator
                else -> c
            }
        )
    }
}
