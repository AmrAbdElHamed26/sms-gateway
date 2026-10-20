package dev.smsgateway.app

import dev.smsgateway.app.delivery.DeliveryPolicy
import dev.smsgateway.app.queue.EventStatus
import dev.smsgateway.app.queue.EventStore
import dev.smsgateway.app.queue.HeartbeatRecord
import dev.smsgateway.app.queue.NewEvent
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.database.sqlite.SQLiteDatabase

/** Queue persistence, dedupe, sequencing and retry bookkeeping against real SQLite. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EventStoreTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val dbName = "test-events.db"
    private lateinit var store: EventStore

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
        store = EventStore(context, dbName)
    }

    @After
    fun tearDown() = store.close()

    private fun event(id: String, dedupe: String = id, simulated: Boolean = false, at: Long = 1_000L) = NewEvent(
        eventId = id, dedupeKey = dedupe, sourceType = "notification", sourcePackage = "com.bank.app",
        sender = null, title = "Payment received", body = "You received 100.00 EGP", receivedAtMs = at,
        receivedAtIso = "2026-10-14T11:00:00+03:00", originTimestampMs = at, source = "android_notification",
        moneyHint = true, simulated = simulated,
    )

    @Test
    fun `events survive closing and reopening the database`() {
        assertTrue(store.insert(event("a")))
        store.close()
        store = EventStore(context, dbName)
        val restored = store.due(nowMs = 2_000L, limit = 10).single()
        assertEquals("a", restored.eventId)
        assertEquals("com.bank.app", restored.sourcePackage)
        assertNull(restored.sender)
    }

    @Test
    fun `duplicates are rejected without burning a sequence number`() {
        assertTrue(store.insert(event("a", dedupe = "same")))
        assertFalse(store.insert(event("b", dedupe = "same")))
        assertTrue(store.insert(event("c")))
        assertEquals(listOf(1L, 2L), store.list(10, 0, null).mapNotNull { it.sequence }.sorted())
        assertEquals(2L, store.lastSequence())
    }

    @Test
    fun `simulated events get no sequence and are never due`() {
        assertTrue(store.insert(event("sim", simulated = true)))
        assertTrue(store.insert(event("real")))
        assertEquals(listOf("real"), store.due(2_000L, 10).map { it.eventId })
        assertNull(store.list(10, 0, EventStatus.SIMULATED).single().sequence)
        assertEquals(1L, store.lastSequence())
    }

    @Test
    fun `a failed attempt is not due again until its backoff passes`() {
        store.insert(event("a"))
        val e = store.due(2_000L, 10).single()
        val nextAt = 2_000L + DeliveryPolicy.backoffDelayMs(1)
        store.markRetry(e.id, 1, nextAt, "HTTP 503", 503)
        assertTrue(store.due(nextAt - 1, 10).isEmpty())
        val retried = store.due(nextAt, 10).single()
        assertEquals(1, retried.attempts)
        assertEquals("HTTP 503", retried.lastError)
    }

    @Test
    fun `circuit breaker defers the rest of the due queue`() {
        store.insert(event("a"))
        store.insert(event("b"))
        store.deferDue(nowMs = 2_000L, untilMs = 62_000L)
        assertTrue(store.due(61_999L, 10).isEmpty())
        assertEquals(2, store.due(62_000L, 10).size)
    }

    @Test
    fun `delivered events keep metadata but lose their content`() {
        store.insert(event("a"))
        val e = store.due(2_000L, 10).single()
        store.markDelivered(e.id, 1, 200, 3_000L)
        val delivered = store.get(e.id)!!
        assertEquals(EventStatus.DELIVERED, delivered.status)
        assertEquals("", delivered.body)
        assertNull(delivered.title)
        assertEquals(1L, delivered.sequence)
    }

    @Test
    fun `retention purges finished events but never pending ones`() {
        store.insert(event("pending", at = 1_000L))
        store.insert(event("done", at = 1_000L))
        store.markDelivered(store.list(10, 0, null).first { it.eventId == "done" }.id, 1, 200, 2_000L)
        assertEquals(1, store.purgeFinishedBefore(cutoffMs = 5_000L))
        assertEquals(listOf("pending"), store.list(10, 0, null).map { it.eventId })
    }

    @Test
    fun `retry all makes failed events pending and due`() {
        store.insert(event("a"))
        val e = store.due(2_000L, 10).single()
        store.markFailed(e.id, 1, "HTTP 422", 422)
        assertTrue(store.due(2_000L, 10).isEmpty())
        assertEquals(1, store.retryAll(3_000L))
        assertEquals(EventStatus.PENDING, store.due(3_000L, 10).single().status)
    }

    @Test
    fun `upgrading a fresh v3 install keeps events, sequence and counter`() {
        store.close()
        context.deleteDatabase(dbName)
        // The v3 schema as shipped: sequence NOT NULL, no notification columns.
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(dbName), null).use { db ->
            db.execSQL(
                "CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT, sequence INTEGER NOT NULL UNIQUE, " +
                    "event_id TEXT NOT NULL UNIQUE, dedupe_key TEXT NOT NULL UNIQUE, sender TEXT NOT NULL, " +
                    "body TEXT NOT NULL, received_at_ms INTEGER NOT NULL, received_at_iso TEXT NOT NULL, " +
                    "sms_timestamp_ms INTEGER, source TEXT NOT NULL, money_hint INTEGER NOT NULL DEFAULT 0, " +
                    "status TEXT NOT NULL, attempts INTEGER NOT NULL DEFAULT 0, next_attempt_at_ms INTEGER NOT NULL, " +
                    "last_error TEXT, last_http_status INTEGER, delivered_at_ms INTEGER)"
            )
            db.execSQL("CREATE INDEX idx_events_due ON events(status, next_attempt_at_ms)")
            db.execSQL("CREATE TABLE counters (name TEXT PRIMARY KEY, value INTEGER NOT NULL)")
            db.execSQL("INSERT INTO counters VALUES ('sequence', 1)")
            db.execSQL(
                "INSERT INTO events (sequence, event_id, dedupe_key, sender, body, received_at_ms, received_at_iso, " +
                    "source, status, next_attempt_at_ms) VALUES (1, 'old', 'old', 'VF-Cash', 'body', 1000, 't', " +
                    "'android_sms', 'PENDING', 1000)"
            )
            db.version = 3
        }

        store = EventStore(context, dbName)
        val old = store.due(2_000L, 10).single()
        assertEquals("sms", old.sourceType)
        assertEquals("VF-Cash", old.sender)
        assertEquals(1L, old.sequence)
        assertTrue("simulated rows need a nullable sequence", store.insert(event("sim", simulated = true)))
        assertTrue(store.insert(event("new")))
        assertEquals(2L, store.list(10, 0, null).first { it.eventId == "new" }.sequence)
        store.logHeartbeat(beat(1))
        assertEquals("upgrade creates the heartbeat log", 1, store.heartbeats(10).size)
    }

    private fun beat(i: Long, status: Int? = 200) = HeartbeatRecord(
        trigger = HeartbeatRecord.TRIGGER_ALARM, dueAtMs = i * 120_000L, sentAtMs = i * 120_000L + 500,
        httpStatus = status, error = if (status == null) "SocketTimeoutException" else null, latencyMs = 300,
    )

    @Test
    fun `heartbeat log returns newest first and keeps only the newest entries`() {
        for (i in 1..EventStore.HEARTBEAT_KEEP + 5L) store.logHeartbeat(beat(i, status = if (i % 2 == 0L) 200 else null))
        val newest = store.heartbeats(EventStore.HEARTBEAT_KEEP + 50)
        assertEquals(EventStore.HEARTBEAT_KEEP, newest.size)
        assertEquals((EventStore.HEARTBEAT_KEEP + 5L) * 120_000L, newest.first().dueAtMs)
        assertEquals(6 * 120_000L, newest.last().dueAtMs)
        assertFalse(newest.first().ok)
        assertEquals("SocketTimeoutException", newest.first().error)
        assertTrue(newest[1].ok)
    }
}
