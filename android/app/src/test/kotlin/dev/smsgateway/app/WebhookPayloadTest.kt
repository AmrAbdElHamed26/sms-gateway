package dev.smsgateway.app

import dev.smsgateway.app.config.WebhookEndpoint
import dev.smsgateway.app.delivery.RequestSigner
import dev.smsgateway.app.delivery.WebhookPayload
import dev.smsgateway.app.util.TimeFormat
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class WebhookPayloadTest {
    private val sharedFields = setOf(
        "event_id", "device_id", "sender", "body", "received_at", "source", "sequence", "install_id", "money_hint",
    )

    @Test
    fun `notification uses the shared fields with the package as sender and a note in the body`() {
        val json = JSONObject(
            WebhookPayload.build(
                eventId = "e-1", deviceId = "android-gateway-001", sourceType = "notification",
                sourcePackage = "com.bank.app", sender = null, title = "Payment received",
                body = "You received a payment of 100.00 EGP", receivedAtIso = "2026-10-14T15:30:00+03:00",
                source = "android_notification", sequence = 7, installId = "i-1", moneyHint = true,
            )
        )
        assertEquals(sharedFields, json.keys().asSequence().toSet())
        assertEquals("com.bank.app", json.getString("sender"))
        assertEquals(
            "Payment received\nYou received a payment of 100.00 EGP\n[via notification: com.bank.app]",
            json.getString("body"),
        )
        assertEquals("android_notification", json.getString("source"))
        assertEquals(7L, json.getLong("sequence"))
    }

    @Test
    fun `source is sent as recorded`() {
        for (source in listOf("android_sms", "android_notification", "simulated_sms", "simulated_notification", "gateway_test")) {
            val json = JSONObject(
                WebhookPayload.build(
                    eventId = "e", deviceId = "d", sourceType = "sms", sourcePackage = null, sender = "s",
                    title = null, body = "b", receivedAtIso = "t", source = source,
                )
            )
            assertEquals(source, json.getString("source"))
        }
    }

    @Test
    fun `title already repeated in the text is not duplicated`() {
        assertEquals(
            "Payment received: 100 EGP\n[via notification: com.bank.app]",
            WebhookPayload.notificationBody("Payment received", "Payment received: 100 EGP", "com.bank.app"),
        )
        assertEquals("100 EGP\n[via notification: com.bank.app]", WebhookPayload.notificationBody(null, "100 EGP", "com.bank.app"))
    }

    @Test
    fun `sms payload has exactly the shared fields`() {
        val body = "تم تحويل 250 جنيه\nRef: 123"
        val json = JSONObject(
            WebhookPayload.build(
                eventId = "e", deviceId = "d", sourceType = "sms", sourcePackage = null, sender = "VF-Cash",
                title = null, body = body, receivedAtIso = "t", source = "android_sms", sequence = 3, installId = "i",
            )
        )
        // No extra fields, so a backend with a closed schema accepts it.
        assertEquals(sharedFields, json.keys().asSequence().toSet())
        assertEquals("VF-Cash", json.getString("sender"))
        assertEquals(body, json.getString("body"))
    }

    @Test
    fun `sms sender is never null`() {
        val json = JSONObject(
            WebhookPayload.build(
                eventId = "e", deviceId = "d", sourceType = "sms", sourcePackage = null, sender = null,
                title = null, body = "b", receivedAtIso = "t", source = "gateway_ping",
            )
        )
        assertEquals("", json.getString("sender"))
        assertTrue("pings carry no sequence", !json.has("sequence"))
    }

    @Test
    fun `422 validation detail keeps field and message but not the input`() {
        val body = """{"detail":[{"type":"string_type","loc":["body","sender"],"msg":"Input should be a valid string","input":null},""" +
            """{"type":"extra_forbidden","loc":["body","title"],"msg":"Extra inputs are not permitted","input":"secret text"}]}"""
        val summary = dev.smsgateway.app.delivery.ErrorDetail.summarize(body)!!
        assertEquals("body.sender: Input should be a valid string; body.title: Extra inputs are not permitted", summary)
        assertTrue(!summary.contains("secret text"))
        assertEquals("Invalid signature", dev.smsgateway.app.delivery.ErrorDetail.summarize("""{"detail":"Invalid signature"}"""))
        assertNull(dev.smsgateway.app.delivery.ErrorDetail.summarize("<html>Bad Request</html>"))
        assertEquals(
            "code: invalid_source; message: Unsupported source",
            dev.smsgateway.app.delivery.ErrorDetail.summarize(
                """{"detail":{"code":"invalid_source","message":"Unsupported source","input":"secret text"}}"""
            ),
        )
        assertEquals("Unsupported source", dev.smsgateway.app.delivery.ErrorDetail.summarize("""{"error":"Unsupported source"}"""))
    }

    @Test
    fun `reason includes the backend detail`() {
        val outcome = dev.smsgateway.app.delivery.DeliveryPolicy.classify(422, null, "body.sender: Input should be a valid string")
        assertEquals("HTTP 422 (body.sender: Input should be a valid string)", (outcome as dev.smsgateway.app.delivery.DeliveryOutcome.Reject).reason)
    }

    @Test
    fun `iso timestamp uses device offset at second precision`() {
        assertEquals("2026-10-14T11:00:00+03:00", TimeFormat.iso(1_791_964_800_123L, ZoneId.of("Africa/Cairo")))
    }

    @Test
    fun `signature matches known HMAC vector`() {
        // printf '1700000000.{"a":1}' | openssl dgst -sha256 -hmac 'test-secret-0123456789'
        assertEquals(
            "sha256=6e873f59dc6002f6de5f578e9bf3f90a7906473a2924651d6f4cc33d61aff598",
            RequestSigner.sign("test-secret-0123456789", 1_700_000_000L, "{\"a\":1}"),
        )
    }

    @Test
    fun `endpoint requires https and appends the fixed path`() {
        assertEquals("https://api.example.com/gateway/events", WebhookEndpoint.resolve("https://api.example.com/"))
        assertEquals("https://api.example.com/gateway/heartbeat", WebhookEndpoint.heartbeat("https://api.example.com"))
        assertNull(WebhookEndpoint.resolve("http://api.example.com"))
        assertNull(WebhookEndpoint.resolve("https://user:pw@api.example.com"))
    }
}
