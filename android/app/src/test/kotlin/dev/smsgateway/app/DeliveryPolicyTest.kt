package dev.smsgateway.app

import dev.smsgateway.app.delivery.DeliveryOutcome
import dev.smsgateway.app.delivery.DeliveryPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DeliveryPolicyTest {
    @Test
    fun `2xx and 409 count as delivered`() {
        listOf(200, 201, 202, 204, 409).forEach {
            assertEquals("HTTP $it", DeliveryOutcome.Delivered, DeliveryPolicy.classify(it))
        }
    }

    @Test
    fun `payload rejections are permanent`() {
        listOf(400, 413, 415, 422).forEach {
            assertTrue("HTTP $it", DeliveryPolicy.classify(it) is DeliveryOutcome.Reject)
        }
    }

    @Test
    fun `transient, auth and routing errors are retried`() {
        listOf(301, 401, 403, 404, 408, 425, 429, 500, 502, 503, 504).forEach {
            assertTrue("HTTP $it", DeliveryPolicy.classify(it) is DeliveryOutcome.Retry)
        }
        assertEquals(DeliveryOutcome.Retry("SocketTimeoutException"), DeliveryPolicy.classify(null, "SocketTimeoutException"))
    }

    @Test
    fun `retries after one minute three times then backs off to 15 minutes`() {
        val noJitter = object : Random() {
            override fun nextBits(bitCount: Int) = 0
            override fun nextDouble() = 0.5 // jitter factor exactly 1.0
        }
        val minutes = (1..9).map { DeliveryPolicy.backoffDelayMs(it, noJitter) / 60_000L }
        assertEquals(listOf(1L, 1L, 1L, 2L, 5L, 10L, 15L, 15L, 15L), minutes)
        assertEquals(15 * 60_000L, DeliveryPolicy.backoffDelayMs(1_000, noJitter))
    }

    @Test
    fun `jitter stays within 10 percent`() {
        repeat(200) {
            assertTrue(DeliveryPolicy.backoffDelayMs(1) in 54_000L..66_000L)
        }
    }
}
