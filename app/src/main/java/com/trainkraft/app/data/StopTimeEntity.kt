package com.trainkraft.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * GTFS stop_times.txt -> stop_times
 * Maps: trip_id -> tripId (part of composite PK, FK -> trips),
 *       stop_sequence -> seq (part of composite PK),
 *       stop_id -> stopId (FK -> stations),
 *       arrival_time/departure_time ("HH:MM:SS", hours may exceed 23)
 *         -> arrMin/depMin (Int minutes within the day, 0..1439) +
 *            dayOffset (Int, floor(hours / 24)).
 *       Example: "25:30:00" -> arrMin/depMin = 90 (01:30), dayOffset = 1.
 *       See [GtfsTime] for the canonical parsing logic used at import time.
 */
@Entity(
    tableName = "stop_times",
    primaryKeys = ["trip_id", "seq"],
    indices = [
        Index(value = ["trip_id"]),
        Index(value = ["stop_id"]),
        Index(value = ["trip_id", "seq"])
    ]
)
data class StopTimeEntity(
    @ColumnInfo(name = "trip_id")
    val tripId: String,

    @ColumnInfo(name = "seq")
    val seq: Int,

    @ColumnInfo(name = "stop_id")
    val stopId: String,

    @ColumnInfo(name = "arr_min")
    val arrMin: Int?,

    @ColumnInfo(name = "dep_min")
    val depMin: Int?,

    @ColumnInfo(name = "day_offset")
    val dayOffset: Int = 0
)
