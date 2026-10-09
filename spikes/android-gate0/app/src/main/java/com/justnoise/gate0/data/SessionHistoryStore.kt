package com.justnoise.gate0.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject
import com.justnoise.gate0.domain.HistoryDateRange
import com.justnoise.gate0.domain.HistoryDayAccumulator
import com.justnoise.gate0.domain.HistoryDaySummary
import com.justnoise.gate0.domain.HistoryInterval

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

data class SessionHistoryTimelineSnapshot(
    val range: HistoryDateRange,
    val days: List<HistoryDaySummary> = emptyList(),
    val totalCount: Int = 0,
    val totalDurationMilliseconds: Long = 0L,
    val interruptedCount: Int = 0,
    val timingUncertainCount: Int = 0,
    val error: Boolean = false,
)

interface SessionHistoryStorage {
    /** Existing test/local stores are deliberately not silently reused for an account. */
    fun forWorkspace(workspace: LocalWorkspaceId): SessionHistoryStorage {
        check(workspace == LocalWorkspaceId.LOCAL) { "Workspace History adapter required" }
        return this
    }
    /** Duplicate ID with identical contents is a successful retry, not another session. */
    fun insert(entry: SessionHistoryEntry)
    /** Worker-only, all rows; never substitute a paginated display snapshot. */
    fun visitEntries(visit: (SessionHistoryEntry) -> Unit) { error("History transfer adapter required") }
    /** Read-only preflight, so known conflicts never establish a blocking transfer claim. */
    fun canMergeFrom(source: SessionHistoryStorage): Boolean { error("History transfer adapter required") }
    /** Atomic destination merge; any same-UUID/different-content conflict rolls back. */
    fun mergeFrom(source: SessionHistoryStorage) { error("History transfer adapter required") }
    fun load(limit: Int): SessionHistorySnapshot
    /** Blocking read: call on a worker. A page-limited load must never substitute for this query. */
    fun loadTimeline(range: HistoryDateRange): SessionHistoryTimelineSnapshot
    fun clear()
    /** Permanent account cleanup, including its database file. Never an implicit local reset. */
    fun eraseAccountData() { error("Account History erasure adapter required") }
}

class SqliteSessionHistoryStorage(context: Context, private val workspace: LocalWorkspaceId = LocalWorkspaceId.LOCAL) : SessionHistoryStorage {
    private val context = context.applicationContext

    override fun forWorkspace(workspace: LocalWorkspaceId): SessionHistoryStorage =
        if (workspace == this.workspace) this else SqliteSessionHistoryStorage(context, workspace)

    override fun insert(entry: SessionHistoryEntry) = database { db -> insertInto(db, entry) }

    private fun insertInto(db: SQLiteDatabase, entry: SessionHistoryEntry) {
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

    override fun visitEntries(visit: (SessionHistoryEntry) -> Unit) = database { db ->
        db.query("sessions", null, null, null, null, null, "id ASC").use { cursor ->
            while (cursor.moveToNext()) visit(cursor.entry())
        }
    }

    override fun mergeFrom(source: SessionHistoryStorage) = database { db ->
        db.beginTransaction()
        try {
            source.visitEntries { insertInto(db, it) }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    override fun canMergeFrom(source: SessionHistoryStorage): Boolean = database { db ->
        var compatible = true
        source.visitEntries { entry ->
            db.query("sessions", null, "id = ?", arrayOf(entry.id), null, null, null).use { cursor ->
                if (cursor.moveToFirst() && cursor.entry() != entry) compatible = false
            }
        }
        compatible
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

    override fun eraseAccountData() {
        check(workspace != LocalWorkspaceId.LOCAL) { "Local History is not account-owned" }
        // Gate0Preferences holds STATE_LOCK; every ordinary read closes its helper before
        // releasing that lock. Do not open a database just to erase an already absent one.
        val file = context.getDatabasePath(workspace.historyDatabaseName)
        SQLiteDatabase.deleteDatabase(file)
        check(listOf("", "-journal", "-wal", "-shm").none { suffix ->
            java.io.File(file.path + suffix).exists()
        }) { "Account History cleanup incomplete" }
    }

    override fun loadTimeline(range: HistoryDateRange): SessionHistoryTimelineSnapshot = database { db ->
        // One streaming read computes all-time totals and every relevant day from the same snapshot.
        // It does not depend on the list's page limit and never rewrites existing rows/schema.
        val days = HistoryDayAccumulator(range)
        var count = 0
        var duration = 0L
        var interrupted = 0
        var timingUncertain = 0
        db.beginTransactionNonExclusive()
        try {
            db.query("sessions", arrayOf("started_at", "ended_at", "duration_ms", "interrupted"),
                null, null, null, null, null).use { cursor ->
                while (cursor.moveToNext()) {
                    val interval = HistoryInterval(cursor.getLong(0), cursor.getLong(1), cursor.getLong(2), cursor.getInt(3) != 0)
                    count = Math.incrementExact(count)
                    duration = Math.addExact(duration, interval.durationMilliseconds)
                    if (interval.interrupted) interrupted = Math.incrementExact(interrupted)
                    if (interval.timingUncertain) timingUncertain = Math.incrementExact(timingUncertain)
                    days.add(interval)
                }
            }
            db.setTransactionSuccessful()
            SessionHistoryTimelineSnapshot(range, days.summaries(), count, duration, interrupted, timingUncertain)
        } finally {
            db.endTransaction()
        }
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
        HistoryDatabase(context, workspace.historyDatabaseName).use { block(it.writableDatabase) }

    private class HistoryDatabase(context: Context, name: String) :
        SQLiteOpenHelper(context, name, null, 1) {
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
