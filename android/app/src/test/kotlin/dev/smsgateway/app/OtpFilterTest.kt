package dev.smsgateway.app

import dev.smsgateway.app.capture.OtpFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OtpFilterTest {
    @Test
    fun `detects one-time codes in English and Arabic`() {
        listOf(
            "Your OTP is 482913",
            "482913 is your one-time password for login",
            "Use verification code 1234 to confirm",
            "Your code is 5521",
            "رمز التحقق الخاص بك هو ٤٨٢٩١٣",
            "كود التفعيل: 9981",
            "كلمة المرور لمرة واحدة 1122",
        ).forEach { assertTrue(it, OtpFilter.looksLikeOtp(it)) }
    }

    @Test
    fun `keeps payment alerts, including ones with security boilerplate`() {
        listOf(
            "You received a payment of 100.00 EGP",
            "تم استلام مبلغ 150.00 جنيه من 01012345678 رقم العملية 987654",
            "Transfer of 300 EGP completed. Never share your PIN with anyone.",
            "تم تحويل 250 ج.م. لا تشارك الرقم السري مع أحد",
        ).forEach { assertFalse(it, OtpFilter.looksLikeOtp(it)) }
    }
}
