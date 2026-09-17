package com.trainkraft.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * GTFS calendar.txt -> calendar
 * Maps: service_id -> serviceId (PK),
 *       monday..sunday -> mon..sun (Int 0/1),
 *       start_date/end_date (YYYYMMDD strings in GTFS) -> startDate/endDate Int YYYYMMDD.
 */
@Entity(tableName = "calendar")
data class CalendarEntity(
    @PrimaryKey
    @ColumnInfo(name = "service_id")
    val serviceId: String,

    @ColumnInfo(name = "mon")
    val mon: Int,

    @ColumnInfo(name = "tue")
    val tue: Int,

    @ColumnInfo(name = "wed")
    val wed: Int,

    @ColumnInfo(name = "thu")
    val thu: Int,

    @ColumnInfo(name = "fri")
    val fri: Int,

    @ColumnInfo(name = "sat")
    val sat: Int,

    @ColumnInfo(name = "sun")
    val sun: Int,

    @ColumnInfo(name = "start_date")
    val startDate: Int,

    @ColumnInfo(name = "end_date")
    val endDate: Int
)
