package com.trainkraft.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * On-device alert history (`alert_log` in `user.db` — backup-excluded).
 *
 * Budget: every log prunes entries older than [ALERT_LOG_RETENTION_MS]
 * (90 days), so the table holds at most ~90 days × a few alerts/day —
 * hundreds of small rows, never unbounded.
 */
@Dao
interface AlertLogDao {

    /** Appends one posted alert; returns the auto-generated row id. */
    @Insert
    suspend fun log(row: AlertLogEntity): Long

    /** Newest-first page for the alerts log screen (default 100). */
    @Query("SELECT * FROM alert_log ORDER BY tsEpochMs DESC LIMIT :limit")
    fun recent(limit: Int = 100): Flow<List<AlertLogEntity>>

    /** Drops entries older than [olderThanEpochMs]; called on every log. */
    @Query("DELETE FROM alert_log WHERE tsEpochMs < :olderThanEpochMs")
    suspend fun pruneBefore(olderThanEpochMs: Long)

    /** Clears the whole log (screen Clear-all; no per-row delete in scope). */
    @Query("DELETE FROM alert_log")
    suspend fun clearAll()
}

/** Alert-log retention: 90 days in ms (see [AlertLogDao] budget). */
const val ALERT_LOG_RETENTION_MS = 90L * 24 * 60 * 60 * 1000

/**
 * Best-effort log-and-prune after a posted notification. Call AFTER the
 * post, inside `runCatching`, on an existing coroutine scope — never throws,
 * never blocks the notification path. Pure except for the two DAO calls.
 */
suspend fun logAlertWithPrune(
    dao: AlertLogDao,
    trainNumber: String,
    title: String,
    body: String,
    nowEpochMs: Long = System.currentTimeMillis(),
) {
    dao.log(
        AlertLogEntity(
            trainNumber = trainNumber,
            title = title,
            body = body,
            tsEpochMs = nowEpochMs,
        )
    )
    dao.pruneBefore(nowEpochMs - ALERT_LOG_RETENTION_MS)
}
