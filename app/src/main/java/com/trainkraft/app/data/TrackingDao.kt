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
}
