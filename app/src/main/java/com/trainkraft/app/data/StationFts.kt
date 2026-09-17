package com.trainkraft.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4

/**
 * FTS choice (documented):
 * - Primary search path is plain LIKE (see TrainDao.searchStations /
 *   searchStationsLike): zero-maintenance, always correct, fast enough for
 *   a stations table (Indian Railways ~7-8k rows) with indices on code/name.
 * - This FTS4 table (content-synced to [StationEntity] via Room triggers)
 *   is provided for fast prefix / token search on large DBs or slow devices.
 *   Use TrainDao.searchStationsFts() to query it.
 *
 * Room auto-creates the content-sync triggers because contentEntity is set,
 * so no manual trigger SQL is needed.
 */
@Fts4(contentEntity = StationEntity::class)
@Entity(tableName = "stations_fts")
data class StationFts(
    @ColumnInfo(name = "code")
    val code: String,

    @ColumnInfo(name = "name")
    val name: String
)
