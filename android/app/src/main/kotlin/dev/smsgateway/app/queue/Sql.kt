package dev.smsgateway.app.queue

import android.database.sqlite.SQLiteDatabase

internal object Sql {
    /** First column of the first row as a Long; 0 when there is no row or it is NULL. */
    fun long(db: SQLiteDatabase, sql: String, vararg args: String): Long =
        db.rawQuery(sql, args).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else 0L }
}
