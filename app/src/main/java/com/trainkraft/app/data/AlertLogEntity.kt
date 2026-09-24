package com.trainkraft.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One posted train alert, kept as an on-device log (`alert_log` in `user.db`
 * — the backup-excluded database, so alert history never leaves the device;
 * that exclusion is the reason this table lives here and not in `trains.db`).
 *
 * Written best-effort AFTER every posted notification (worker, minute
 * service, station-alarm receiver) via [AlertLogDao.log] — logging never
 * throws and never precedes the post. Growth is capped by pruning to
 * [ALERT_LOG_RETENTION_MS] (90 days) on each log.
 */
@Entity(
    tableName = "alert_log",
    indices = [Index(value = ["tsEpochMs"])],
)
data class AlertLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trainNumber: String,
    val title: String,
    val body: String,
    /** Epoch ms when the notification was posted. */
    val tsEpochMs: Long,
)
