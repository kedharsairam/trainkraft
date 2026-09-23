package com.trainkraft.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A train the user opted to follow (bell icon on TrainDetail).
 *
 * Doubles as the worker's persisted poll state so that:
 *  - bell UI survives process restarts (was lost before — WorkManager-only),
 *  - the worker can notify only on *meaningful changes* (delay category,
 *    station change, completion) instead of on every 15-minute poll.
 */
@Entity(tableName = "tracked_trains")
data class TrackedTrainEntity(
    @PrimaryKey val trainNumber: String,
    /** Epoch ms when tracking started. */
    val trackedAt: Long,
    /** Delay minutes reported at the previous poll (null = unknown). */
    val lastDelayMin: Int? = null,
    /** Last reported station at the previous poll. */
    val lastStation: String? = null,
    /** Notification category at the previous poll (see NotificationPolicy). */
    val lastCategory: String? = null,
    /** Epoch ms of the last successful poll. */
    val lastPollAt: Long? = null,
    /**
     * Station the user wants an approach alarm for (Phase C station alarms;
     * null = destination-only behavior). Set via TrackingDao.setWatchStation.
     */
    val watchStationCode: String? = null,
    /**
     * Watch-station code already notified for (one-shot approach gate;
     * null = none). Set via TrackingDao.markApproachNotified.
     */
    val lastApproachFor: String? = null,
)
