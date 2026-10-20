package dev.smsgateway.app

import dev.smsgateway.app.capture.MoneyDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyDetectorTest {
    private val money = listOf(
        "تم استلام مبلغ 150.00 جنيه من 01012345678 رقم العملية 987654",
        "تم تحويل ٢٥٠ ج.م الى حسابك",
        "تم إيداع 1,500 في محفظتك. رصيدك الحالي 2,300",
        "You have received EGP 500.00 from 0100***678. Ref 123456",
        "Your account was credited with 1,200.50 LE",
        "Payment of 99 completed",
        "Transfer 300.00 to wallet 0101 successful",
    )

    private val notMoney = listOf(
        "Your verification code is 482913",
        "كود التفعيل الخاص بك هو ٤٨٢٩١٣",
        "Meeting moved to 3 pm",
        "مرحبا، كيف حالك؟",
        "Payment reminder: please update your app", // money word but no number
    )

    @Test
    fun `flags Arabic and English money messages from any provider`() {
        money.forEach { assertTrue(it, MoneyDetector.looksLikeMoney(it)) }
    }

    @Test
    fun `ignores OTPs and ordinary messages`() {
        notMoney.forEach { assertFalse(it, MoneyDetector.looksLikeMoney(it)) }
    }

    @Test
    fun `normalizes Arabic-Indic digits and separators`() {
        assertEquals("1,250.75", MoneyDetector.normalizeDigits("١٬٢٥٠٫٧٥"))
        assertEquals("0123", MoneyDetector.normalizeDigits("۰۱۲۳"))
    }
}
