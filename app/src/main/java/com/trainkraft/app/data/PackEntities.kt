package com.trainkraft.app.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Phase A intelligence-pack tables (TrainDatabase v5).
 *
 * Written by: the offline pipeline (`tools/packs/`, peer-owned) which scrapes
 * CRIS average-delay pages + fog-season circulars and ships them as a signed
 * `pack.db`; rows land in these tables via [PackImporter].
 * Read by: the Phase B prediction engine (ETA correction + fog timetable
 * overlays). GTFS static tables are never touched by packs.
 *
 * DDL contract (byte agreement with the pipeline — Room must generate
 * identical tables via the @ColumnInfo names/types/defaults below):
 * ```
 * CREATE TABLE IF NOT EXISTS `delay_priors` (
 *   `trainNumber` TEXT NOT NULL, `stationCode` TEXT NOT NULL,
 *   `arrAvgMin` INTEGER NOT NULL DEFAULT 0, `depAvgMin` INTEGER NOT NULL DEFAULT 0,
 *   `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`trainNumber`, `stationCode`));
 * CREATE TABLE IF NOT EXISTS `fog_overlays` (
 *   `trainNumber` TEXT NOT NULL PRIMARY KEY, `action` TEXT NOT NULL,
 *   `fromDate` TEXT NOT NULL, `toDate` TEXT NOT NULL,
 *   `season` TEXT NOT NULL, `note` TEXT NOT NULL DEFAULT '');
 * CREATE TABLE IF NOT EXISTS `pack_meta` (`key` TEXT NOT NULL PRIMARY KEY, `value` TEXT NOT NULL);
 * ```
 */

/**
 * Per-train-per-station CRIS average delay, in whole minutes.
 *
 * 0 means on time: CRIS reports "On Time" / "HH:MM" / blank, and blank maps
 * to 0 (documented pipeline rule). Phase B reads these via
 * [PackDao.priorsForTrain] to bias arrival/departure predictions.
 *
 * @param trainNumber 5-digit train number, e.g. "12951".
 * @param stationCode Station code, e.g. "BRC".
 * @param arrAvgMin CRIS arrival average delay in minutes (0 = on time).
 * @param depAvgMin CRIS departure average delay in minutes (0 = on time).
 * @param updatedAt Epoch ms when the pipeline scraped this row.
 */
@Entity(
    tableName = "delay_priors",
    primaryKeys = ["trainNumber", "stationCode"]
)
data class DelayPriorEntity(
    @ColumnInfo(name = "trainNumber")
    val trainNumber: String,

    @ColumnInfo(name = "stationCode")
    val stationCode: String,

    @ColumnInfo(name = "arrAvgMin", defaultValue = "0")
    val arrAvgMin: Int = 0,

    @ColumnInfo(name = "depAvgMin", defaultValue = "0")
    val depAvgMin: Int = 0,

    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long
)

/**
 * Fog-season timetable overlay for one train.
 *
 * Written by the pipeline from railway fog circulars; read by Phase B to
 * explain / adjust winter predictions. One row per train (latest circular
 * wins at import time via INSERT OR REPLACE).
 *
 * @param trainNumber 5-digit train number, e.g. "12951".
 * @param action One of CANCELLED, REDUCED_FREQ, REVISED_TIMING.
 * @param fromDate First affected date, YYYY-MM-DD (inclusive).
 * @param toDate Last affected date, YYYY-MM-DD (inclusive).
 * @param season Fog-season label, e.g. "2026-27".
 * @param note Free-text circular reference; empty when none.
 */
@Entity(tableName = "fog_overlays")
data class FogOverlayEntity(
    @PrimaryKey
    @ColumnInfo(name = "trainNumber")
    val trainNumber: String,

    /** CANCELLED, REDUCED_FREQ, or REVISED_TIMING. */
    @ColumnInfo(name = "action")
    val action: String,

    /** First affected date, YYYY-MM-DD inclusive. */
    @ColumnInfo(name = "fromDate")
    val fromDate: String,

    /** Last affected date, YYYY-MM-DD inclusive. */
    @ColumnInfo(name = "toDate")
    val toDate: String,

    /** Fog-season label, e.g. "2026-27". */
    @ColumnInfo(name = "season")
    val season: String,

    /** Free-text circular reference; empty when none. */
    @ColumnInfo(name = "note", defaultValue = "''")
    val note: String = ""
)

/**
 * Pack key/value metadata (single `pack_meta` table).
 *
 * Pipeline-written keys: packVersion, generatedAt, trainCount, gtfsVintage,
 * fogSeason, source. The app additionally writes `importedAt` (epoch ms of
 * the last successful [PackImporter.importPack]) so Phase B / settings can
 * show pack freshness. Read via [PackDao.meta] / [PackDao.metaAll].
 */
@Entity(tableName = "pack_meta")
data class PackMetaEntity(
    @PrimaryKey
    @ColumnInfo(name = "key")
    val key: String,

    @ColumnInfo(name = "value")
    val value: String
)

@Dao
interface PackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPriors(rows: List<DelayPriorEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOverlays(rows: List<FogOverlayEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMeta(row: PackMetaEntity)

    /**
     * All delay priors for one train. Phase B use: per-station delay bias
     * when predicting arrival/departure at each upcoming stop.
     */
    @Query("SELECT * FROM `delay_priors` WHERE `trainNumber` = :train ORDER BY `stationCode`")
    suspend fun priorsForTrain(train: String): List<DelayPriorEntity>

    /**
     * Fog overlay active for one train on one date. Phase B use: if a row
     * matches (date within [fromDate, toDate], YYYY-MM-DD compares
     * lexicographically), winter predictions defer to the overlay action.
     */
    @Query(
        "SELECT * FROM `fog_overlays` " +
            "WHERE `trainNumber` = :train " +
            "AND `fromDate` <= :dateYMD AND :dateYMD <= `toDate` " +
            "LIMIT 1"
    )
    suspend fun fogForTrain(train: String, dateYMD: String): FogOverlayEntity?

    /**
     * One metadata value by key (null when absent). Phase B use: packVersion
     * gate, fogSeason label, importedAt freshness badge.
     */
    @Query("SELECT `value` FROM `pack_meta` WHERE `key` = :key")
    suspend fun meta(key: String): String?

    /**
     * Whole metadata table as a map. Phase B use: settings/debug screen
     * showing pack provenance (source, gtfsVintage, generatedAt, ...).
     */
    @Query("SELECT `key`, `value` FROM `pack_meta`")
    suspend fun metaAll(): Map<@MapColumn("key") String, @MapColumn("value") String>
}
