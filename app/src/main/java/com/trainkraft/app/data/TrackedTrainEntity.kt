package com.trainkraft.app.data

import androidx.room.ColumnInfo
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
    /**
     * Minute-level live tracking enabled (Go-live tier). The bell (row
     * existence) is the 15-minute worker baseline; this flag is the
     * foreground-service minute presence. Stopping Go-live clears the flag
     * but keeps the row — unfollowing is the bell's job alone. Reset for all
     * rows on cold start (no minute presence survives process death).
     *
     * DB-level `DEFAULT 0` is deliberate (not just the Kotlin default): raw
     * INSERTs that omit the column (older migration-test DDL, hand SQL) must
     * not violate NOT NULL, and Room validates the default on open — so the
     * [UserDatabase.MIGRATION_3_4] ADD COLUMN and [UserDataMigrator]'s CREATE
     * carry the identical default. Keep all three in sync.
     */
    @ColumnInfo(name = "liveTracking", defaultValue = "0")
    val liveTracking: Boolean = false,
)
