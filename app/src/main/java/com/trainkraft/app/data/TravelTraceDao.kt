package com.trainkraft.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Trace persistence for Phase D travel-mode GPS sessions (`travel_fixes` in
 * `user.db` — backup-excluded, so raw fixes never leave the device).
 *
 * Budget: the service calls [pruneBefore] on every start with now − 30 days,
 * so the table holds at most a month of sessions regardless of trip count.
 */
@Dao
interface TravelTraceDao {

    /**
     * Same-train same-millisecond re-delivery dedups (fused bursts can
     * repeat a timestamp); a repeat carries the same position.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(fix: TravelFixEntity)

    /** All fixes of one train's sessions, oldest first (for ETA replay/debug). */
    @Query("SELECT * FROM travel_fixes WHERE trainNumber = :train ORDER BY tsEpochMs ASC")
    suspend fun fixesForTrain(train: String): List<TravelFixEntity>

    /** Drops fixes older than [olderThanEpochMs]; called on service start. */
    @Query("DELETE FROM travel_fixes WHERE tsEpochMs < :olderThanEpochMs")
    suspend fun pruneBefore(olderThanEpochMs: Long)
}
