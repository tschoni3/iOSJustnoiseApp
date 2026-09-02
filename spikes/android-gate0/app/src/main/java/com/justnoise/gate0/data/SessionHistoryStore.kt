package com.justnoise.gate0.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

/** Completed session metadata only: never foreground observations, tag values or app names. */
data class SessionHistoryEntry(
    val id: String,
    val startedAtEpochMilliseconds: Long,
    val endedAtEpochMilliseconds: Long,
    val durationMilliseconds: Long,
    val targetCount: Int,
    val interrupted: Boolean,
) {
    init {
        require(id.isNotBlank() && startedAtEpochMilliseconds >= 0L)
        require(endedAtEpochMilliseconds >= 0L && durationMilliseconds >= 0L && targetCount > 0)
    }

    internal fun encode(): String = JSONObject().apply {
        put("id", id)
        put("start", startedAtEpochMilliseconds)
        put("end", endedAtEpochMilliseconds)
        put("duration", durationMilliseconds)
        put("targets", targetCount)
        put("interrupted", interrupted)
    }.toString()

    companion object {
        internal fun decode(value: String): SessionHistoryEntry = JSONObject(value).let {
            SessionHistoryEntry(
                it.getString("id"), it.getLong("start"), it.getLong("end"),
                it.getLong("duration"), it.getInt("targets"), it.getBoolean("interrupted"),
            )
        }
    }
}

data class SessionHistorySnapshot(
    val entries: List<SessionHistoryEntry> = emptyList(),
    val totalCount: Int = 0,
    val totalDurationMilliseconds: Long = 0L,
    val interruptedCount: Int = 0,
    val hasMore: Boolean = false,
    val error: Boolean = false,
)

interface SessionHistoryStorage {
    /** Duplicate ID with identical contents is a successful retry, not another session. */
    fun insert(entry: SessionHistoryEntry)
    fun load(limit: Int): SessionHistorySnapshot
    fun clear()
}

class SqliteSessionHistoryStorage(context: Context) : SessionHistoryStorage {
    private val context = context.applicationContext

    override fun insert(entry: SessionHistoryEntry) = database { db ->
        val values = ContentValues().apply {
            put("id", entry.id)
            put("started_at", entry.startedAtEpochMilliseconds)
            put("ended_at", entry.endedAtEpochMilliseconds)
            put("duration_ms", entry.durationMilliseconds)
            put("target_count", entry.targetCount)
            put("interrupted", if (entry.interrupted) 1 else 0)
        }
        val inserted = db.insertWithOnConflict("sessions", null, values, SQLiteDatabase.CONFLICT_IGNORE)
        if (inserted == -1L) {
            db.query("sessions", null, "id = ?", arrayOf(entry.id), null, null, null).use { cursor ->
                check(cursor.moveToFirst() && cursor.entry() == entry) { "History retry conflict" }
            }
        }
    }

    override fun load(limit: Int): SessionHistorySnapshot = database { db ->
        require(limit > 0)
        // Summary and page describe the same database snapshot.
        db.beginTransactionNonExclusive()
        try {
            val summary = db.rawQuery(
                "SELECT COUNT(*), TOTAL(duration_ms), COALESCE(SUM(interrupted), 0) FROM sessions",
                null,
            ).use { cursor ->
                check(cursor.moveToFirst())
                Triple(cursor.getInt(0), cursor.getDouble(1).toLong(), cursor.getInt(2))
            }
            val entries = db.query(
                "sessions", null, null, null, null, null, "ended_at DESC, id DESC", limit.toString(),
            ).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.entry()) }
            }
            db.setTransactionSuccessful()
            SessionHistorySnapshot(
                entries, summary.first, summary.second, summary.third,
                hasMore = entries.size < summary.first,
            )
        } finally {
            db.endTransaction()
        }
    }

    override fun clear() = database { db ->
        db.delete("sessions", null, null)
        Unit
    }

    private fun android.database.Cursor.entry() = SessionHistoryEntry(
        getString(getColumnIndexOrThrow("id")),
        getLong(getColumnIndexOrThrow("started_at")),
        getLong(getColumnIndexOrThrow("ended_at")),
        getLong(getColumnIndexOrThrow("duration_ms")),
        getInt(getColumnIndexOrThrow("target_count")),
        getInt(getColumnIndexOrThrow("interrupted")) != 0,
    )

    private fun <T> database(block: (SQLiteDatabase) -> T): T =
        HistoryDatabase(context).use { block(it.writableDatabase) }

    private class HistoryDatabase(context: Context) :
        SQLiteOpenHelper(context, "session_history.db", null, 1) {
        override fun onConfigure(db: SQLiteDatabase) {
            // Remove deleted metadata from reusable pages too; backup/transfer are disabled.
            // This PRAGMA returns a row; execSQL is only for statements without results.
            db.rawQuery("PRAGMA secure_delete=ON", null).use { cursor ->
                check(cursor.moveToFirst() && cursor.getInt(0) == 1)
            }
        }

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE sessions (id TEXT PRIMARY KEY NOT NULL, started_at INTEGER NOT NULL, " +
                    "ended_at INTEGER NOT NULL, duration_ms INTEGER NOT NULL CHECK(duration_ms >= 0), " +
                    "target_count INTEGER NOT NULL CHECK(target_count > 0), " +
                    "interrupted INTEGER NOT NULL CHECK(interrupted IN (0, 1)))",
            )
            db.execSQL("CREATE INDEX sessions_by_end ON sessions(ended_at DESC, id DESC)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            error("Unsupported session history upgrade")
        }
    }
}
