package com.trainkraft.app.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

// ---- Query result POJOs (must be top-level for Room KSP) ----

/** One row of a train's full route, ordered by [seq]. */
data class ScheduleStop(
    @ColumnInfo(name = "trip_id")
    val tripId: String,
    @ColumnInfo(name = "seq")
    val seq: Int,
    @ColumnInfo(name = "stop_id")
    val stopId: String,
    @ColumnInfo(name = "code")
    val code: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "arr_min")
    val arrMin: Int?,
    @ColumnInfo(name = "dep_min")
    val depMin: Int?,
    @ColumnInfo(name = "day_offset")
    val dayOffset: Int
)

/** One departure row for a station board. */
data class StationDeparture(
    @ColumnInfo(name = "trip_id")
    val tripId: String,
    @ColumnInfo(name = "train_number")
    val trainNumber: String,
    @ColumnInfo(name = "train_name")
    val trainName: String,
    @ColumnInfo(name = "dep_min")
    val depMin: Int?,
    @ColumnInfo(name = "day_offset")
    val dayOffset: Int,
    @ColumnInfo(name = "seq")
    val seq: Int,
    @ColumnInfo(name = "service_id")
    val serviceId: String,
    @ColumnInfo(name = "dest_code")
    val destCode: String? = null,
    @ColumnInfo(name = "dest_name")
    val destName: String? = null
)

/** One row for trains-between-stations results. */
data class BetweenResult(
    @ColumnInfo(name = "train_number")
    val trainNumber: String,
    @ColumnInfo(name = "train_name")
    val trainName: String,
    @ColumnInfo(name = "from_code")
    val fromCode: String,
    @ColumnInfo(name = "from_name")
    val fromName: String,
    @ColumnInfo(name = "dep_min")
    val depMin: Int,
    @ColumnInfo(name = "dep_day_offset")
    val depDayOffset: Int,
    @ColumnInfo(name = "to_code")
    val toCode: String,
    @ColumnInfo(name = "to_name")
    val toName: String,
    @ColumnInfo(name = "arr_min")
    val arrMin: Int,
    @ColumnInfo(name = "arr_day_offset")
    val arrDayOffset: Int,
)

@Dao
interface TrainDao {

    // ---- Inserts (for future GTFS import; REPLACE keeps re-imports idempotent) ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStations(rows: List<StationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrains(rows: List<TrainEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrips(rows: List<TripEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalendar(rows: List<CalendarEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStopTimes(rows: List<StopTimeEntity>)

    // ---- 1. Station search ----

    /**
     * Default station search: LIKE on code/name, limit 20.
     * Case-insensitive via COLLATE NOCASE. Stable, no FTS query-syntax risk.
     */
    @Query(
        """
        SELECT * FROM stations
        WHERE code LIKE '%' || :query || '%' COLLATE NOCASE
           OR name LIKE '%' || :query || '%' COLLATE NOCASE
        ORDER BY
          CASE WHEN code = :query COLLATE NOCASE THEN 0
               WHEN code LIKE :query || '%' COLLATE NOCASE THEN 1
               ELSE 2 END,
          name
        LIMIT 20
        """
    )
    suspend fun searchStations(query: String): List<StationEntity>

    // ---- 2. Train search ----

    @Query(
        """
        SELECT * FROM trains
        WHERE train_number LIKE '%' || :query || '%'
           OR name LIKE '%' || :query || '%' COLLATE NOCASE
        ORDER BY train_number
        LIMIT 20
        """
    )
    suspend fun searchTrains(query: String): List<TrainEntity>

    // ---- 3. Full route for one train number ----
    // A route (train) can own many trips; we return the stops of a single
    // representative trip (lowest trip_id) so the UI gets one clean route.

    @Query(
        """
        SELECT t.trip_id AS trip_id,
               st.seq AS seq,
               s.stop_id AS stop_id,
               s.code AS code,
               s.name AS name,
               st.arr_min AS arr_min,
               st.dep_min AS dep_min,
               st.day_offset AS day_offset
        FROM trains AS tr
        JOIN trips AS t ON t.route_id = tr.route_id
        JOIN stop_times AS st ON st.trip_id = t.trip_id
        JOIN stations AS s ON s.stop_id = st.stop_id
        WHERE tr.train_number = :trainNumber
          AND t.trip_id = (
              SELECT t2.trip_id FROM trips AS t2
              JOIN trains AS tr2 ON tr2.route_id = t2.route_id
              WHERE tr2.train_number = :trainNumber
              ORDER BY t2.trip_id
              LIMIT 1
          )
        ORDER BY st.seq
        """
    )
    suspend fun getTrainSchedule(trainNumber: String): List<ScheduleStop>

    // ---- 4. Station board: departures for a weekday ----
    // Best effort: filters trips whose calendar row runs on :weekday
    // (0=Mon..6=Sun, matching java.time.DayOfWeek.value - 1).
    // Date-range (start/end) filtering is left to the caller, which knows
    // "today"; pass :todayDate (YYYYMMDD Int) or null to skip it.
    // Ranking: earliest departures first.

    @Query(
        """
        SELECT t.trip_id AS trip_id,
               tr.train_number AS train_number,
               tr.name AS train_name,
               st.dep_min AS dep_min,
               st.day_offset AS day_offset,
               st.seq AS seq,
               t.service_id AS service_id,
               (
                 SELECT s2.code FROM stop_times AS st2
                 JOIN stations AS s2 ON s2.stop_id = st2.stop_id
                 WHERE st2.trip_id = t.trip_id
                 ORDER BY st2.seq DESC LIMIT 1
               ) AS dest_code,
               (
                 SELECT s2.name FROM stop_times AS st2
                 JOIN stations AS s2 ON s2.stop_id = st2.stop_id
                 WHERE st2.trip_id = t.trip_id
                 ORDER BY st2.seq DESC LIMIT 1
               ) AS dest_name
        FROM stop_times AS st
        JOIN stations AS s ON s.stop_id = st.stop_id
        JOIN trips AS t ON t.trip_id = st.trip_id
        JOIN trains AS tr ON tr.route_id = t.route_id
        JOIN calendar AS c ON c.service_id = t.service_id
        WHERE s.code = :stationCode
          AND (
            CASE :weekday
              WHEN 0 THEN c.mon
              WHEN 1 THEN c.tue
              WHEN 2 THEN c.wed
              WHEN 3 THEN c.thu
              WHEN 4 THEN c.fri
              WHEN 5 THEN c.sat
              ELSE c.sun
            END
          ) = 1
          AND (:todayDate IS NULL OR (c.start_date <= :todayDate AND :todayDate <= c.end_date))
          AND (:afterMin IS NULL OR st.dep_min >= :afterMin)
        ORDER BY st.day_offset, st.dep_min
        LIMIT 50
        """
    )
    suspend fun getStationBoard(
        stationCode: String,
        weekday: Int,
        todayDate: Int? = null,
        afterMin: Int? = null
    ): List<StationDeparture>

    // ---- 5. Trains between two stations ----
    // Finds all trips where both stations exist, source comes before destination,
    // filtered by weekday. Returns departure from source + arrival at destination.

    @Query(
        """
        SELECT tr.train_number AS train_number,
               tr.name AS train_name,
               s_from.code AS from_code,
               s_from.name AS from_name,
               st_from.dep_min AS dep_min,
               st_from.day_offset AS dep_day_offset,
               s_to.code AS to_code,
               s_to.name AS to_name,
               st_to.arr_min AS arr_min,
               st_to.day_offset AS arr_day_offset
        FROM stop_times AS st_from
        JOIN stop_times AS st_to ON st_from.trip_id = st_to.trip_id
        JOIN stations AS s_from ON s_from.stop_id = st_from.stop_id
        JOIN stations AS s_to ON s_to.stop_id = st_to.stop_id
        JOIN trips AS t ON t.trip_id = st_from.trip_id
        JOIN trains AS tr ON tr.route_id = t.route_id
        JOIN calendar AS c ON c.service_id = t.service_id
        WHERE s_from.code = :fromCode
          AND s_to.code = :toCode
          AND st_from.seq < st_to.seq
          AND (
            CASE :weekday
              WHEN 0 THEN c.mon
              WHEN 1 THEN c.tue
              WHEN 2 THEN c.wed
              WHEN 3 THEN c.thu
              WHEN 4 THEN c.fri
              WHEN 5 THEN c.sat
              ELSE c.sun
            END
          ) = 1
        ORDER BY st_from.day_offset, st_from.dep_min
        LIMIT 50
        """
    )
    suspend fun getTrainsBetween(
        fromCode: String,
        toCode: String,
        weekday: Int,
    ): List<BetweenResult>
}
