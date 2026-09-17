package com.trainkraft.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * GTFS trips.txt -> trips
 * Maps: trip_id -> tripId (PK), route_id -> routeId (FK -> trains),
 *       service_id -> serviceId (logical FK -> calendar),
 *       trip_headsign -> headsign (nullable, kept for display; not in spec
 *       but present in source and useful for direction).
 */
@Entity(
    tableName = "trips",
    foreignKeys = [
        ForeignKey(
            entity = TrainEntity::class,
            parentColumns = ["route_id"],
            childColumns = ["route_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["route_id"]),
        Index(value = ["service_id"])
    ]
)
data class TripEntity(
    @PrimaryKey
    @ColumnInfo(name = "trip_id")
    val tripId: String,

    @ColumnInfo(name = "route_id")
    val routeId: String,

    @ColumnInfo(name = "service_id")
    val serviceId: String,

    @ColumnInfo(name = "headsign")
    val headsign: String? = null
)
