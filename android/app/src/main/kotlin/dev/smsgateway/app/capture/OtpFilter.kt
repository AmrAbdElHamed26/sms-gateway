package dev.smsgateway.app.capture

/**
 * Drops one-time passwords and verification codes — the gateway must never
 * collect them, even from an approved app or sender.
 *
 * Matches only phrases that name a code. Generic boilerplate such as "never
 * share your PIN", which banks append to real transaction alerts, is NOT
 * matched, so those alerts still go through. A dropped message is counted
 * (never stored) so the operator can spot a payment alert lost to this filter.
 */
object OtpFilter {
    private val english = Regex(
        """(?i)\b(otp|one[- ]time (password|passcode|pin|code)|verification code|""" +
            """security code|authentication code|auth code|login code|confirmation code|activation code|""" +
            """your code is)"""
    )

    private val arabic = listOf(
        "رمز التحقق", "كود التحقق", "رمز التفعيل", "كود التفعيل", "رمز التأكيد", "كود التأكيد",
        "رمز الدخول", "كود الدخول", "رمز المرور لمرة واحدة", "كلمة المرور لمرة واحدة",
        "كلمة سر لمرة واحدة", "الرمز السري المؤقت", "رمز التحقق الخاص بك",
    )

    fun looksLikeOtp(text: String): Boolean {
        val normalized = MoneyDetector.normalizeDigits(text)
        return english.containsMatchIn(normalized) || arabic.any { it in normalized }
    }
}
