package dev.smsgateway.app

import dev.smsgateway.app.capture.notification.NotificationExtractor
import dev.smsgateway.app.capture.notification.RawNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationExtractorTest {
    private val base = RawNotification(
        packageName = "com.bank.app",
        key = "0|com.bank.app|42|null|10123",
        whenMs = 1_000L,
        postTimeMs = 1_050L,
        title = "Payment received",
        text = "You received 100.00 EGP",
        bigText = "You received 100.00 EGP from Ahmed. Ref 123456",
        textLines = null,
        isGroupSummary = false,
        isOngoing = false,
    )

    @Test
    fun `extracts package, title and the most complete body`() {
        val e = NotificationExtractor.extract(base)!!
        assertEquals("notification", e.sourceType)
        assertEquals("com.bank.app", e.sourcePackage)
        assertNull(e.sender)
        assertEquals("Payment received", e.title)
        assertEquals("You received 100.00 EGP from Ahmed. Ref 123456", e.body)
        assertEquals(1_000L, e.originTimestampMs)
    }

    @Test
    fun `falls back to inbox lines, then short text`() {
        val inbox = base.copy(bigText = null, textLines = listOf("100 EGP from A", "200 EGP from B"))
        assertEquals("100 EGP from A\n200 EGP from B", NotificationExtractor.extract(inbox)!!.body)
        assertEquals("You received 100.00 EGP", NotificationExtractor.extract(base.copy(bigText = " "))!!.body)
    }

    @Test
    fun `an identical re-post is a duplicate`() {
        val first = NotificationExtractor.extract(base)!!
        val update = NotificationExtractor.extract(base.copy(postTimeMs = 9_999L))!!
        assertEquals(first.dedupeKey, update.dedupeKey)
    }

    @Test
    fun `a second payment with the same text is a new event`() {
        val first = NotificationExtractor.extract(base)!!
        val second = NotificationExtractor.extract(base.copy(whenMs = 61_000L))!!
        assertNotEquals(first.dedupeKey, second.dedupeKey)
    }

    @Test
    fun `changed content under the same key is a new event`() {
        val first = NotificationExtractor.extract(base)!!
        val changed = NotificationExtractor.extract(base.copy(bigText = "You received 250.00 EGP"))!!
        assertNotEquals(first.dedupeKey, changed.dedupeKey)
    }

    @Test
    fun `group summaries, ongoing and empty notifications are ignored`() {
        assertNull(NotificationExtractor.extract(base.copy(isGroupSummary = true)))
        assertNull(NotificationExtractor.extract(base.copy(isOngoing = true)))
        assertNull(NotificationExtractor.extract(base.copy(text = null, bigText = null, textLines = emptyList())))
    }

    @Test
    fun `missing when falls back to post time`() {
        assertEquals(1_050L, NotificationExtractor.extract(base.copy(whenMs = 0))!!.originTimestampMs)
    }
}
