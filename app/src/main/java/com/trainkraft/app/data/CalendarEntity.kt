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
    val mon: Int? = 0,

    @ColumnInfo(name = "tue")
    val tue: Int? = 0,

    @ColumnInfo(name = "wed")
    val wed: Int? = 0,

    @ColumnInfo(name = "thu")
    val thu: Int? = 0,

    @ColumnInfo(name = "fri")
    val fri: Int? = 0,

    @ColumnInfo(name = "sat")
    val sat: Int? = 0,

    @ColumnInfo(name = "sun")
    val sun: Int? = 0,

    @ColumnInfo(name = "start_date")
    val startDate: Int? = 0,

    @ColumnInfo(name = "end_date")
    val endDate: Int? = 0
)
