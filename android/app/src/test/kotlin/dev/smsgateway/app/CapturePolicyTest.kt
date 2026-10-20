package dev.smsgateway.app

import dev.smsgateway.app.capture.CaptureConfig
import dev.smsgateway.app.capture.CapturePolicy
import dev.smsgateway.app.capture.CapturedEvent
import dev.smsgateway.app.capture.Decision
import dev.smsgateway.app.capture.sms.SenderFilter
import dev.smsgateway.app.queue.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapturePolicyTest {
    private val config = CaptureConfig(
        gatewayEnabled = true,
        monitoredPackages = setOf("com.bank.app", "com.wallet.app"),
        smsEnabled = true,
        smsAllowlist = listOf("VF-Cash", "01012345678"),
    )

    private fun notification(pkg: String, body: String = "You received 100.00 EGP", title: String? = "Payment received") =
        CapturedEvent(SourceType.NOTIFICATION, "android_notification", pkg, null, title, body, 1L, "k")

    private fun sms(sender: String, body: String = "تم استلام مبلغ 150 جنيه") =
        CapturedEvent(SourceType.SMS, "android_sms", null, sender, null, body, 1L, "k")

    @Test
    fun `approved app notification is accepted`() {
        assertEquals(Decision.ACCEPT, CapturePolicy.evaluate(notification("com.bank.app"), config))
    }

    @Test
    fun `notification from an unapproved app is never forwarded`() {
        assertEquals(Decision.NOT_APPROVED, CapturePolicy.evaluate(notification("com.chat.app"), config))
        assertEquals(Decision.NOT_APPROVED, CapturePolicy.evaluate(notification("com.bank.app.evil"), config))
    }

    @Test
    fun `nothing is forwarded while the gateway is off, except simulations`() {
        val off = config.copy(gatewayEnabled = false)
        assertEquals(Decision.GATEWAY_DISABLED, CapturePolicy.evaluate(notification("com.bank.app"), off))
        assertEquals(Decision.ACCEPT, CapturePolicy.evaluate(notification("com.bank.app"), off, simulated = true))
    }

    @Test
    fun `sms needs the module on and an approved sender`() {
        assertEquals(Decision.ACCEPT, CapturePolicy.evaluate(sms("vf cash"), config))
        assertEquals(Decision.ACCEPT, CapturePolicy.evaluate(sms("+201012345678"), config))
        assertEquals(Decision.NOT_APPROVED, CapturePolicy.evaluate(sms("+201099999999"), config))
        assertEquals(Decision.SOURCE_DISABLED, CapturePolicy.evaluate(sms("VF-Cash"), config.copy(smsEnabled = false)))
        assertEquals(Decision.NOT_APPROVED, CapturePolicy.evaluate(sms("VF-Cash"), config.copy(smsAllowlist = emptyList())))
    }

    @Test
    fun `OTPs are dropped even from approved sources`() {
        assertEquals(
            Decision.OTP,
            CapturePolicy.evaluate(notification("com.bank.app", "Your OTP is 482913. It expires in 5 minutes"), config),
        )
        assertEquals(Decision.OTP, CapturePolicy.evaluate(sms("VF-Cash", "رمز التحقق الخاص بك هو 4829"), config))
    }

    @Test
    fun `empty notifications are dropped`() {
        assertEquals(Decision.EMPTY, CapturePolicy.evaluate(notification("com.bank.app", " ", title = null), config))
    }

    @Test
    fun `sender normalization`() {
        assertTrue(SenderFilter.allows("VF CASH", listOf("vf-cash")))
        assertTrue(SenderFilter.allows("00201012345678", listOf("01012345678")))
        assertFalse(SenderFilter.allows("", listOf("")))
        assertFalse(SenderFilter.allows("VF-Cash", emptyList()))
    }
}
