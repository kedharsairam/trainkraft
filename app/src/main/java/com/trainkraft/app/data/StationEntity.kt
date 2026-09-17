package com.trainkraft.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * GTFS stops.txt -> stations
 * Maps: stop_id -> stopId (PK), stop_code -> code, stop_name -> name,
 *       stop_lat -> lat, stop_lon -> lon. All other stops.txt columns ignored.
 */
@Entity(
    tableName = "stations",
    indices = [
        Index(value = ["code"]),
        Index(value = ["name"])
    ]
)
data class StationEntity(
    @PrimaryKey
    @ColumnInfo(name = "stop_id")
    val stopId: String,

    @ColumnInfo(name = "code")
    val code: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "lat")
    val lat: Double?,

    @ColumnInfo(name = "lon")
    val lon: Double?
)
