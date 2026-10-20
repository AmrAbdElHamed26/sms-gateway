package dev.smsgateway.app.queue

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Durable outbox. Every captured event is written here before any delivery
 * attempt, so a killed process, reboot or offline period never loses it.
 */
class EventStore internal constructor(context: Context, name: String?) :
    SQLiteOpenHelper(context.applicationContext, name, null, DB_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        createEventsTable(db)
        createCounters(db, start = 0)
        createHeartbeatsTable(db)
    }

    private fun createEventsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sequence INTEGER UNIQUE,
                event_id TEXT NOT NULL UNIQUE,
                dedupe_key TEXT NOT NULL UNIQUE,
                source_type TEXT NOT NULL DEFAULT 'sms',
                source_package TEXT,
                sender TEXT NOT NULL DEFAULT '',
                title TEXT,
                body TEXT NOT NULL,
                received_at_ms INTEGER NOT NULL,
                received_at_iso TEXT NOT NULL,
                sms_timestamp_ms INTEGER,
                source TEXT NOT NULL,
                money_hint INTEGER NOT NULL DEFAULT 0,
                status TEXT NOT NULL,
                attempts INTEGER NOT NULL DEFAULT 0,
                next_attempt_at_ms INTEGER NOT NULL,
                last_error TEXT,
                last_http_status INTEGER,
                delivered_at_ms INTEGER
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_events_due ON events(status, next_attempt_at_ms)")
    }

    private fun createHeartbeatsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE heartbeats (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                trigger TEXT NOT NULL,
                due_at_ms INTEGER NOT NULL,
                sent_at_ms INTEGER NOT NULL,
                http_status INTEGER,
                error TEXT,
                latency_ms INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE events ADD COLUMN money_hint INTEGER NOT NULL DEFAULT 0")
        if (oldVersion < 3) {
            // v2 used the AUTOINCREMENT id as the sequence. Keep the numbers already sent,
            // continue from the highest stored one.
            db.execSQL("ALTER TABLE events ADD COLUMN sequence INTEGER")
            db.execSQL("UPDATE events SET sequence = id")
            db.execSQL("CREATE UNIQUE INDEX idx_events_sequence ON events(sequence)")
            createCounters(db, start = Sql.long(db, "SELECT COALESCE(MAX(id), 0) FROM events"))
        }
        if (oldVersion < 4) {
            // Rebuild rather than ALTER: a fresh v3 install declared `sequence NOT NULL`,
            // and v4 needs it nullable (simulated events never get a sequence).
            db.execSQL("DROP INDEX IF EXISTS idx_events_due")
            db.execSQL("DROP INDEX IF EXISTS idx_events_sequence")
            db.execSQL("ALTER TABLE events RENAME TO events_v3")
            createEventsTable(db)
            val columns = "id, sequence, event_id, dedupe_key, sender, body, received_at_ms, received_at_iso, " +
                "sms_timestamp_ms, source, money_hint, status, attempts, next_attempt_at_ms, last_error, " +
                "last_http_status, delivered_at_ms"
            db.execSQL("INSERT INTO events ($columns) SELECT $columns FROM events_v3")
            db.execSQL("DROP TABLE events_v3")
        }
        if (oldVersion < 5) createHeartbeatsTable(db)
    }

    private fun createCounters(db: SQLiteDatabase, start: Long) {
        db.execSQL("CREATE TABLE counters (name TEXT PRIMARY KEY, value INTEGER NOT NULL)")
        db.execSQL("INSERT INTO counters (name, value) VALUES ('$SEQUENCE_COUNTER', $start)")
    }

    /**
     * Stores [event]; events that will be sent get the next gap-free `sequence`.
     *
     * The duplicate check runs before the insert, in the same transaction: an
     * `INSERT OR IGNORE` that hits a UNIQUE conflict still burns an
     * AUTOINCREMENT value, so the row id can't be the sequence. The counter
     * only moves when a row is actually stored.
     *
     * @return true if stored, false if [NewEvent.dedupeKey] or [NewEvent.eventId] already exists.
     */
    fun insert(event: NewEvent): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val duplicate = Sql.long(
                db,
                "SELECT COUNT(*) FROM events WHERE dedupe_key = ? OR event_id = ?",
                event.dedupeKey, event.eventId,
            ) > 0
            if (duplicate) return false

            val sequence = if (event.simulated) null else currentSequence(db) + 1
            db.insertOrThrow(TABLE, null, valuesOf(event, sequence))
            if (sequence != null) {
                db.execSQL("UPDATE counters SET value = ? WHERE name = ?", arrayOf<Any>(sequence, SEQUENCE_COUNTER))
            }
            db.setTransactionSuccessful()
            return true
        } catch (e: SQLiteConstraintException) {
            return false // rolled back; counter untouched
        } finally {
            db.endTransaction()
        }
    }

    private fun currentSequence(db: SQLiteDatabase): Long =
        Sql.long(db, "SELECT value FROM counters WHERE name = ?", SEQUENCE_COUNTER)

    private fun valuesOf(event: NewEvent, sequence: Long?) = ContentValues().apply {
        put("sequence", sequence)
        put("event_id", event.eventId)
        put("dedupe_key", event.dedupeKey)
        put("source_type", event.sourceType)
        put("source_package", event.sourcePackage)
        put("sender", event.sender.orEmpty())
        put("title", event.title)
        put("body", event.body)
        put("received_at_ms", event.receivedAtMs)
        put("received_at_iso", event.receivedAtIso)
        put("sms_timestamp_ms", event.originTimestampMs)
        put("source", event.source)
        put("money_hint", if (event.moneyHint) 1 else 0)
        put("status", (if (event.simulated) EventStatus.SIMULATED else EventStatus.PENDING).name)
        put("attempts", 0)
        put("next_attempt_at_ms", event.receivedAtMs)
    }

    fun due(nowMs: Long, limit: Int): List<GatewayEvent> = query(
        "status = ? AND next_attempt_at_ms <= ?",
        arrayOf(EventStatus.PENDING.name, nowMs.toString()),
        "next_attempt_at_ms ASC, id ASC",
        limit,
    )

    fun get(id: Long): GatewayEvent? = query("id = ?", arrayOf(id.toString()), "id", 1).firstOrNull()

    fun list(limit: Int, offset: Int, status: EventStatus?, moneyOnly: Boolean = false): List<GatewayEvent> {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (status != null) {
            clauses += "status = ?"
            args += status.name
        }
        if (moneyOnly) clauses += "money_hint = 1"
        return query(
            clauses.joinToString(" AND ").ifEmpty { null },
            args.toTypedArray().ifEmpty { null },
            "id DESC",
            limit,
            offset,
        )
    }

    /**
     * Data minimisation: once the backend holds the event, the phone keeps only
     * its metadata. Title and body are wiped.
     */
    fun markDelivered(id: Long, attempts: Int, httpStatus: Int?, nowMs: Long) = update(id) {
        put("status", EventStatus.DELIVERED.name)
        put("attempts", attempts)
        put("last_http_status", httpStatus)
        putNull("last_error")
        put("delivered_at_ms", nowMs)
        put("body", "")
        putNull("title")
    }

    fun markRetry(id: Long, attempts: Int, nextAttemptAtMs: Long, error: String?, httpStatus: Int?) =
        update(id) {
            put("status", EventStatus.PENDING.name)
            put("attempts", attempts)
            put("next_attempt_at_ms", nextAttemptAtMs)
            put("last_error", error)
            put("last_http_status", httpStatus)
        }

    fun markFailed(id: Long, attempts: Int, error: String?, httpStatus: Int?) = update(id) {
        put("status", EventStatus.FAILED.name)
        put("attempts", attempts)
        put("last_error", error)
        put("last_http_status", httpStatus)
    }

    /** Circuit breaker: when the backend is down, push every other due event back too. */
    fun deferDue(nowMs: Long, untilMs: Long): Int = writableDatabase.update(
        TABLE,
        ContentValues().apply { put("next_attempt_at_ms", untilMs) },
        "status = ? AND next_attempt_at_ms <= ?",
        arrayOf(EventStatus.PENDING.name, nowMs.toString()),
    )

    /** Makes every pending and failed event due now. */
    fun retryAll(nowMs: Long): Int = writableDatabase.update(
        TABLE,
        ContentValues().apply {
            put("status", EventStatus.PENDING.name)
            put("next_attempt_at_ms", nowMs)
        },
        "status IN (?, ?)",
        arrayOf(EventStatus.PENDING.name, EventStatus.FAILED.name),
    )

    /** Retention. Pending events are never purged: they have not reached the backend. */
    fun purgeFinishedBefore(cutoffMs: Long): Int = writableDatabase.delete(
        TABLE,
        "status IN (?, ?, ?) AND received_at_ms < ?",
        arrayOf(EventStatus.DELIVERED.name, EventStatus.FAILED.name, EventStatus.SIMULATED.name, cutoffMs.toString()),
    )

    fun nextPendingAt(): Long? = scalarLong(
        "SELECT MIN(next_attempt_at_ms) FROM events WHERE status = ?",
        arrayOf(EventStatus.PENDING.name),
    )

    fun stats(): Map<String, Any?> {
        val counts = mutableMapOf<String, Any?>()
        for (key in listOf(
            "total", "pending", "delivered", "failed", "simulated",
            "moneyTotal", "moneyPending", "moneyFailed",
        )) counts[key] = 0
        readableDatabase.rawQuery(
            "SELECT status, COUNT(*), SUM(money_hint) FROM events GROUP BY status", null
        ).use { c ->
            while (c.moveToNext()) {
                val status = c.getString(0).lowercase()
                val n = c.getInt(1)
                val money = c.getInt(2)
                counts[status] = n
                if (status == "simulated") continue // never sent: kept out of the delivery totals
                counts["total"] = counts["total"] as Int + n
                counts["moneyTotal"] = counts["moneyTotal"] as Int + money
                if (status == "pending") counts["moneyPending"] = money
                if (status == "failed") counts["moneyFailed"] = money
            }
        }
        counts["lastSequence"] = lastSequence()
        counts["oldestPendingAt"] = scalarLong(
            "SELECT MIN(received_at_ms) FROM events WHERE status = ?", arrayOf(EventStatus.PENDING.name)
        )
        counts["oldestMoneyPendingAt"] = scalarLong(
            "SELECT MIN(received_at_ms) FROM events WHERE status = ? AND money_hint = 1",
            arrayOf(EventStatus.PENDING.name),
        )
        return counts
    }

    /** Logs one heartbeat attempt and keeps only the newest [HEARTBEAT_KEEP]. */
    fun logHeartbeat(record: HeartbeatRecord) {
        val db = writableDatabase
        db.insertOrThrow(HEARTBEATS, null, ContentValues().apply {
            put("trigger", record.trigger)
            put("due_at_ms", record.dueAtMs)
            put("sent_at_ms", record.sentAtMs)
            put("http_status", record.httpStatus)
            put("error", record.error)
            put("latency_ms", record.latencyMs)
        })
        db.execSQL(
            "DELETE FROM $HEARTBEATS WHERE id <= (SELECT MAX(id) FROM $HEARTBEATS) - ?",
            arrayOf<Any>(HEARTBEAT_KEEP),
        )
    }

    /** Newest first. */
    fun heartbeats(limit: Int): List<HeartbeatRecord> =
        readableDatabase.query(HEARTBEATS, null, null, null, null, null, "id DESC", limit.toString()).use { c ->
            fun idx(col: String) = c.getColumnIndexOrThrow(col)
            buildList {
                while (c.moveToNext()) add(
                    HeartbeatRecord(
                        id = c.getLong(idx("id")),
                        trigger = c.getString(idx("trigger")),
                        dueAtMs = c.getLong(idx("due_at_ms")),
                        sentAtMs = c.getLong(idx("sent_at_ms")),
                        httpStatus = idx("http_status").let { if (c.isNull(it)) null else c.getInt(it) },
                        error = idx("error").let { if (c.isNull(it)) null else c.getString(it) },
                        latencyMs = c.getLong(idx("latency_ms")),
                    )
                )
            }
        }

    /** Highest sequence ever assigned on this install (survives retention purges). */
    fun lastSequence(): Long = currentSequence(readableDatabase)

    private fun update(id: Long, block: ContentValues.() -> Unit) {
        writableDatabase.update(TABLE, ContentValues().apply(block), "id = ?", arrayOf(id.toString()))
    }

    private fun scalarLong(sql: String, args: Array<String>): Long? =
        readableDatabase.rawQuery(sql, args).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }

    private fun query(
        selection: String?,
        args: Array<String>?,
        orderBy: String,
        limit: Int,
        offset: Int = 0,
    ): List<GatewayEvent> =
        readableDatabase.query(TABLE, null, selection, args, null, null, orderBy, "$offset, $limit")
            .use { c -> buildList { while (c.moveToNext()) add(c.toEvent()) } }

    private fun Cursor.toEvent(): GatewayEvent {
        fun idx(col: String) = getColumnIndexOrThrow(col)
        fun long(col: String) = getLong(idx(col))
        fun longOrNull(col: String) = idx(col).let { if (isNull(it)) null else getLong(it) }
        fun str(col: String): String = getString(idx(col)) ?: ""
        fun strOrNull(col: String): String? = idx(col).let { if (isNull(it)) null else getString(it) }
        return GatewayEvent(
            id = long("id"),
            sequence = longOrNull("sequence"),
            eventId = str("event_id"),
            sourceType = str("source_type"),
            sourcePackage = strOrNull("source_package"),
            sender = str("sender").ifEmpty { null },
            title = strOrNull("title"),
            body = str("body"),
            receivedAtMs = long("received_at_ms"),
            receivedAtIso = str("received_at_iso"),
            source = str("source"),
            moneyHint = long("money_hint") == 1L,
            status = EventStatus.valueOf(str("status")),
            attempts = long("attempts").toInt(),
            nextAttemptAtMs = long("next_attempt_at_ms"),
            lastError = strOrNull("last_error"),
            lastHttpStatus = longOrNull("last_http_status")?.toInt(),
            deliveredAtMs = longOrNull("delivered_at_ms"),
        )
    }

    companion object {
        private const val DB_NAME = "sms_gateway.db"
        private const val DB_VERSION = 5
        private const val TABLE = "events"
        private const val HEARTBEATS = "heartbeats"
        /** ~1.5 days at one beat every 2 min. */
        const val HEARTBEAT_KEEP = 1000
        private const val SEQUENCE_COUNTER = "sequence"

        @Volatile private var instance: EventStore? = null

        fun get(context: Context): EventStore =
            instance ?: synchronized(this) { instance ?: EventStore(context, DB_NAME).also { instance = it } }
    }
}
