package com.trainkraft.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * GTFS routes.txt -> trains
 * Maps: route_id -> routeId (PK),
 *       route_short_name -> trainNumber (unique, trimmed at import time),
 *       route_long_name -> name,
 *       route_type -> type (Int, GTFS route_type verbatim).
 */
@Entity(
    tableName = "trains",
    indices = [
        Index(value = ["train_number"], unique = true),
        Index(value = ["name"])
    ]
)
data class TrainEntity(
    @PrimaryKey
    @ColumnInfo(name = "route_id")
    val routeId: String,

    @ColumnInfo(name = "train_number")
    val trainNumber: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "type")
    val type: Int
)
