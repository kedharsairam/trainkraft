package com.trainkraft.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Persistence for tracked trains (bell toggles + worker poll state). */
@Dao
interface TrackingDao {

    @Query("SELECT * FROM tracked_trains WHERE trainNumber = :trainNumber")
    suspend fun get(trainNumber: String): TrackedTrainEntity?

    @Query("SELECT * FROM tracked_trains ORDER BY trackedAt DESC")
    suspend fun getAll(): List<TrackedTrainEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: TrackedTrainEntity)

    @Query("DELETE FROM tracked_trains WHERE trainNumber = :trainNumber")
    suspend fun delete(trainNumber: String)

    /**
     * Stores the state observed at a poll so the next run can diff against it.
     * No-op when the train is no longer tracked.
     */
    @Query(
        """
        UPDATE tracked_trains
        SET lastDelayMin = :delay,
            lastStation = :station,
            lastCategory = :category,
            lastPollAt = :pollAt
        WHERE trainNumber = :trainNumber
        """
    )
    suspend fun updatePollState(
        trainNumber: String,
        delay: Int?,
        station: String?,
        category: String?,
        pollAt: Long,
    )

    /** Live observation for bell-icon state (reflects delete-on-completion). */
    @Query("SELECT * FROM tracked_trains WHERE trainNumber = :trainNumber")
    fun observe(trainNumber: String): kotlinx.coroutines.flow.Flow<TrackedTrainEntity?>

    /** Sets (or clears, with null) the station-approach alarm target. */
    @Query("UPDATE tracked_trains SET watchStationCode = :stationCode WHERE trainNumber = :trainNumber")
    suspend fun setWatchStation(trainNumber: String, stationCode: String?)

    /** One-shot gate for approach notifications (see NotificationPolicy.evaluateApproach). */
    @Query("UPDATE tracked_trains SET lastApproachFor = :stationCode WHERE trainNumber = :trainNumber")
    suspend fun markApproachNotified(trainNumber: String, stationCode: String?)

    /**
     * Resets the one-shot gate when the user re-arms a station alarm: without
     * this, a re-armed alarm would stay silent forever (priorApproachFor
     * still holds the fired station). Called on every fresh arm.
     */
    @Query("UPDATE tracked_trains SET lastApproachFor = NULL WHERE trainNumber = :trainNumber")
    suspend fun clearApproachNotified(trainNumber: String)

    /** Go-live tier flag (see [TrackedTrainEntity.liveTracking]). */
    @Query("UPDATE tracked_trains SET liveTracking = :enabled WHERE trainNumber = :trainNumber")
    suspend fun setLiveTracking(trainNumber: String, enabled: Boolean)

    /** Cold-start reconciliation: no minute presence survives process death. */
    @Query("UPDATE tracked_trains SET liveTracking = 0")
    suspend fun clearAllLiveTracking()
}
