package com.trainkraft.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * One on-device GPS fix of a Phase D travel-mode session (`travel_fixes`).
 *
 * Privacy: lives in `user.db`, which `backup_rules.xml` and
 * `data_extraction_rules.xml` already exclude — raw lat/lon never leaves the
 * device via backup. Rows are session-ephemeral evidence for the live layer
 * only (speed/ETA/next-stop derivation); growth is capped by
 * [TravelTraceDao.pruneBefore] (the service prunes sessions older than 30
 * days on every start).
 *
 * Identity is the natural composite key (`trainNumber`, `tsEpochMs`): no
 * autoincrement row, so no `sqlite_sequence` bookkeeping table — the schema
 * dump/replay migration tests stay a clean table list.
 */
@Entity(
    tableName = "travel_fixes",
    primaryKeys = ["trainNumber", "tsEpochMs"],
    indices = [
        Index(value = ["trainNumber"]),
        Index(value = ["tsEpochMs"]),
    ],
)
data class TravelFixEntity(
    /** NTES/GTFS train number this session was started for. */
    @ColumnInfo(name = "trainNumber")
    val trainNumber: String,
    /** Fix wall-clock, epoch ms. */
    @ColumnInfo(name = "tsEpochMs")
    val tsEpochMs: Long,
    /** WGS-84 latitude / longitude. */
    @ColumnInfo(name = "lat")
    val lat: Double,
    @ColumnInfo(name = "lon")
    val lon: Double,
    /** Fused-provider speed at fix time, km/h. */
    @ColumnInfo(name = "speedKmh")
    val speedKmh: Double,
    /** Fused-provider accuracy radius, metres. */
    @ColumnInfo(name = "accuracyM")
    val accuracyM: Float,
)
