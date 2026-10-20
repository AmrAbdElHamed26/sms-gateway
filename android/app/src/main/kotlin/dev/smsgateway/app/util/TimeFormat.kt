package dev.smsgateway.app.util

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object TimeFormat {
    /** e.g. 2026-10-14T11:00:00+03:00 — device-local offset, second precision. */
    fun iso(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMs), zone)
            .truncatedTo(ChronoUnit.SECONDS)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}
