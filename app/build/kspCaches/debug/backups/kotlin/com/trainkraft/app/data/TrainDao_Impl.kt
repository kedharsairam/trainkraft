package com.trainkraft.app.`data`

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import javax.`annotation`.processing.Generated
import kotlin.Double
import kotlin.Int
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class TrainDao_Impl(
  __db: RoomDatabase,
) : TrainDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfStationEntity: EntityInsertAdapter<StationEntity>

  private val __insertAdapterOfTrainEntity: EntityInsertAdapter<TrainEntity>

  private val __insertAdapterOfTripEntity: EntityInsertAdapter<TripEntity>

  private val __insertAdapterOfCalendarEntity: EntityInsertAdapter<CalendarEntity>

  private val __insertAdapterOfStopTimeEntity: EntityInsertAdapter<StopTimeEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfStationEntity = object : EntityInsertAdapter<StationEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `stations` (`stop_id`,`code`,`name`,`lat`,`lon`) VALUES (?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: StationEntity) {
        statement.bindText(1, entity.stopId)
        statement.bindText(2, entity.code)
        statement.bindText(3, entity.name)
        val _tmpLat: Double? = entity.lat
        if (_tmpLat == null) {
          statement.bindNull(4)
        } else {
          statement.bindDouble(4, _tmpLat)
        }
        val _tmpLon: Double? = entity.lon
        if (_tmpLon == null) {
          statement.bindNull(5)
        } else {
          statement.bindDouble(5, _tmpLon)
        }
      }
    }
    this.__insertAdapterOfTrainEntity = object : EntityInsertAdapter<TrainEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `trains` (`route_id`,`train_number`,`name`,`type`) VALUES (?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: TrainEntity) {
        statement.bindText(1, entity.routeId)
        statement.bindText(2, entity.trainNumber)
        statement.bindText(3, entity.name)
        statement.bindLong(4, entity.type.toLong())
      }
    }
    this.__insertAdapterOfTripEntity = object : EntityInsertAdapter<TripEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `trips` (`trip_id`,`route_id`,`service_id`,`headsign`) VALUES (?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: TripEntity) {
        statement.bindText(1, entity.tripId)
        statement.bindText(2, entity.routeId)
        statement.bindText(3, entity.serviceId)
        val _tmpHeadsign: String? = entity.headsign
        if (_tmpHeadsign == null) {
          statement.bindNull(4)
        } else {
          statement.bindText(4, _tmpHeadsign)
        }
      }
    }
    this.__insertAdapterOfCalendarEntity = object : EntityInsertAdapter<CalendarEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `calendar` (`service_id`,`mon`,`tue`,`wed`,`thu`,`fri`,`sat`,`sun`,`start_date`,`end_date`) VALUES (?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: CalendarEntity) {
        statement.bindText(1, entity.serviceId)
        statement.bindLong(2, entity.mon.toLong())
        statement.bindLong(3, entity.tue.toLong())
        statement.bindLong(4, entity.wed.toLong())
        statement.bindLong(5, entity.thu.toLong())
        statement.bindLong(6, entity.fri.toLong())
        statement.bindLong(7, entity.sat.toLong())
        statement.bindLong(8, entity.sun.toLong())
        statement.bindLong(9, entity.startDate.toLong())
        statement.bindLong(10, entity.endDate.toLong())
      }
    }
    this.__insertAdapterOfStopTimeEntity = object : EntityInsertAdapter<StopTimeEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `stop_times` (`trip_id`,`seq`,`stop_id`,`arr_min`,`dep_min`,`day_offset`) VALUES (?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: StopTimeEntity) {
        statement.bindText(1, entity.tripId)
        statement.bindLong(2, entity.seq.toLong())
        statement.bindText(3, entity.stopId)
        statement.bindLong(4, entity.arrMin.toLong())
        statement.bindLong(5, entity.depMin.toLong())
        statement.bindLong(6, entity.dayOffset.toLong())
      }
    }
  }

  public override suspend fun insertStations(rows: List<StationEntity>): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfStationEntity.insert(_connection, rows)
  }

  public override suspend fun insertTrains(rows: List<TrainEntity>): Unit = performSuspending(__db,
      false, true) { _connection ->
    __insertAdapterOfTrainEntity.insert(_connection, rows)
  }

  public override suspend fun insertTrips(rows: List<TripEntity>): Unit = performSuspending(__db,
      false, true) { _connection ->
    __insertAdapterOfTripEntity.insert(_connection, rows)
  }

  public override suspend fun insertCalendar(rows: List<CalendarEntity>): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfCalendarEntity.insert(_connection, rows)
  }

  public override suspend fun insertStopTimes(rows: List<StopTimeEntity>): Unit =
      performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfStopTimeEntity.insert(_connection, rows)
  }

  public override suspend fun searchStations(query: String): List<StationEntity> {
    val _sql: String = """
        |
        |        SELECT * FROM stations
        |        WHERE code LIKE '%' || ? || '%' COLLATE NOCASE
        |           OR name LIKE '%' || ? || '%' COLLATE NOCASE
        |        ORDER BY
        |          CASE WHEN code = ? COLLATE NOCASE THEN 0
        |               WHEN code LIKE ? || '%' COLLATE NOCASE THEN 1
        |               ELSE 2 END,
        |          name
        |        LIMIT 20
        |        
        """.trimMargin()
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, query)
        _argIndex = 2
        _stmt.bindText(_argIndex, query)
        _argIndex = 3
        _stmt.bindText(_argIndex, query)
        _argIndex = 4
        _stmt.bindText(_argIndex, query)
        val _columnIndexOfStopId: Int = getColumnIndexOrThrow(_stmt, "stop_id")
        val _columnIndexOfCode: Int = getColumnIndexOrThrow(_stmt, "code")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfLat: Int = getColumnIndexOrThrow(_stmt, "lat")
        val _columnIndexOfLon: Int = getColumnIndexOrThrow(_stmt, "lon")
        val _result: MutableList<StationEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: StationEntity
          val _tmpStopId: String
          _tmpStopId = _stmt.getText(_columnIndexOfStopId)
          val _tmpCode: String
          _tmpCode = _stmt.getText(_columnIndexOfCode)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpLat: Double?
          if (_stmt.isNull(_columnIndexOfLat)) {
            _tmpLat = null
          } else {
            _tmpLat = _stmt.getDouble(_columnIndexOfLat)
          }
          val _tmpLon: Double?
          if (_stmt.isNull(_columnIndexOfLon)) {
            _tmpLon = null
          } else {
            _tmpLon = _stmt.getDouble(_columnIndexOfLon)
          }
          _item = StationEntity(_tmpStopId,_tmpCode,_tmpName,_tmpLat,_tmpLon)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun searchStationsLike(query: String): List<StationEntity> {
    val _sql: String = """
        |
        |        SELECT * FROM stations
        |        WHERE code LIKE '%' || ? || '%' COLLATE NOCASE
        |           OR name LIKE '%' || ? || '%' COLLATE NOCASE
        |        ORDER BY name
        |        LIMIT 20
        |        
        """.trimMargin()
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, query)
        _argIndex = 2
        _stmt.bindText(_argIndex, query)
        val _columnIndexOfStopId: Int = getColumnIndexOrThrow(_stmt, "stop_id")
        val _columnIndexOfCode: Int = getColumnIndexOrThrow(_stmt, "code")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfLat: Int = getColumnIndexOrThrow(_stmt, "lat")
        val _columnIndexOfLon: Int = getColumnIndexOrThrow(_stmt, "lon")
        val _result: MutableList<StationEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: StationEntity
          val _tmpStopId: String
          _tmpStopId = _stmt.getText(_columnIndexOfStopId)
          val _tmpCode: String
          _tmpCode = _stmt.getText(_columnIndexOfCode)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpLat: Double?
          if (_stmt.isNull(_columnIndexOfLat)) {
            _tmpLat = null
          } else {
            _tmpLat = _stmt.getDouble(_columnIndexOfLat)
          }
          val _tmpLon: Double?
          if (_stmt.isNull(_columnIndexOfLon)) {
            _tmpLon = null
          } else {
            _tmpLon = _stmt.getDouble(_columnIndexOfLon)
          }
          _item = StationEntity(_tmpStopId,_tmpCode,_tmpName,_tmpLat,_tmpLon)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun searchStationsFts(query: String): List<StationEntity> {
    val _sql: String = """
        |
        |        SELECT s.* FROM stations AS s
        |        JOIN stations_fts ON s.rowid = stations_fts.rowid
        |        WHERE stations_fts MATCH ? || '*'
        |        LIMIT 20
        |        
        """.trimMargin()
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, query)
        val _columnIndexOfStopId: Int = getColumnIndexOrThrow(_stmt, "stop_id")
        val _columnIndexOfCode: Int = getColumnIndexOrThrow(_stmt, "code")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfLat: Int = getColumnIndexOrThrow(_stmt, "lat")
        val _columnIndexOfLon: Int = getColumnIndexOrThrow(_stmt, "lon")
        val _result: MutableList<StationEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: StationEntity
          val _tmpStopId: String
          _tmpStopId = _stmt.getText(_columnIndexOfStopId)
          val _tmpCode: String
          _tmpCode = _stmt.getText(_columnIndexOfCode)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpLat: Double?
          if (_stmt.isNull(_columnIndexOfLat)) {
            _tmpLat = null
          } else {
            _tmpLat = _stmt.getDouble(_columnIndexOfLat)
          }
          val _tmpLon: Double?
          if (_stmt.isNull(_columnIndexOfLon)) {
            _tmpLon = null
          } else {
            _tmpLon = _stmt.getDouble(_columnIndexOfLon)
          }
          _item = StationEntity(_tmpStopId,_tmpCode,_tmpName,_tmpLat,_tmpLon)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun searchTrains(query: String): List<TrainEntity> {
    val _sql: String = """
        |
        |        SELECT * FROM trains
        |        WHERE train_number LIKE '%' || ? || '%'
        |           OR name LIKE '%' || ? || '%' COLLATE NOCASE
        |        ORDER BY train_number
        |        LIMIT 20
        |        
        """.trimMargin()
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, query)
        _argIndex = 2
        _stmt.bindText(_argIndex, query)
        val _columnIndexOfRouteId: Int = getColumnIndexOrThrow(_stmt, "route_id")
        val _columnIndexOfTrainNumber: Int = getColumnIndexOrThrow(_stmt, "train_number")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _result: MutableList<TrainEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: TrainEntity
          val _tmpRouteId: String
          _tmpRouteId = _stmt.getText(_columnIndexOfRouteId)
          val _tmpTrainNumber: String
          _tmpTrainNumber = _stmt.getText(_columnIndexOfTrainNumber)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpType: Int
          _tmpType = _stmt.getLong(_columnIndexOfType).toInt()
          _item = TrainEntity(_tmpRouteId,_tmpTrainNumber,_tmpName,_tmpType)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getTrainSchedule(trainNumber: String): List<ScheduleStop> {
    val _sql: String = """
        |
        |        SELECT t.trip_id AS trip_id,
        |               st.seq AS seq,
        |               s.stop_id AS stop_id,
        |               s.code AS code,
        |               s.name AS name,
        |               st.arr_min AS arr_min,
        |               st.dep_min AS dep_min,
        |               st.day_offset AS day_offset
        |        FROM trains AS tr
        |        JOIN trips AS t ON t.route_id = tr.route_id
        |        JOIN stop_times AS st ON st.trip_id = t.trip_id
        |        JOIN stations AS s ON s.stop_id = st.stop_id
        |        WHERE tr.train_number = ?
        |          AND t.trip_id = (
        |              SELECT t2.trip_id FROM trips AS t2
        |              JOIN trains AS tr2 ON tr2.route_id = t2.route_id
        |              WHERE tr2.train_number = ?
        |              ORDER BY t2.trip_id
        |              LIMIT 1
        |          )
        |        ORDER BY st.seq
        |        
        """.trimMargin()
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, trainNumber)
        _argIndex = 2
        _stmt.bindText(_argIndex, trainNumber)
        val _columnIndexOfTripId: Int = 0
        val _columnIndexOfSeq: Int = 1
        val _columnIndexOfStopId: Int = 2
        val _columnIndexOfCode: Int = 3
        val _columnIndexOfName: Int = 4
        val _columnIndexOfArrMin: Int = 5
        val _columnIndexOfDepMin: Int = 6
        val _columnIndexOfDayOffset: Int = 7
        val _result: MutableList<ScheduleStop> = mutableListOf()
        while (_stmt.step()) {
          val _item: ScheduleStop
          val _tmpTripId: String
          _tmpTripId = _stmt.getText(_columnIndexOfTripId)
          val _tmpSeq: Int
          _tmpSeq = _stmt.getLong(_columnIndexOfSeq).toInt()
          val _tmpStopId: String
          _tmpStopId = _stmt.getText(_columnIndexOfStopId)
          val _tmpCode: String
          _tmpCode = _stmt.getText(_columnIndexOfCode)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpArrMin: Int
          _tmpArrMin = _stmt.getLong(_columnIndexOfArrMin).toInt()
          val _tmpDepMin: Int
          _tmpDepMin = _stmt.getLong(_columnIndexOfDepMin).toInt()
          val _tmpDayOffset: Int
          _tmpDayOffset = _stmt.getLong(_columnIndexOfDayOffset).toInt()
          _item =
              ScheduleStop(_tmpTripId,_tmpSeq,_tmpStopId,_tmpCode,_tmpName,_tmpArrMin,_tmpDepMin,_tmpDayOffset)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getTrainsBetween(fromCode: String, toCode: String):
      List<TrainsBetweenResult> {
    val _sql: String = """
        |
        |        SELECT t.trip_id AS trip_id,
        |               tr.train_number AS train_number,
        |               tr.name AS train_name,
        |               t.headsign AS headsign,
        |               fromSt.seq AS from_seq,
        |               toSt.seq AS to_seq,
        |               fromSt.dep_min AS from_dep,
        |               toSt.arr_min AS to_arr,
        |               fromSt.day_offset AS from_day_offset,
        |               toSt.day_offset AS to_day_offset
        |        FROM trips AS t
        |        JOIN trains AS tr ON tr.route_id = t.route_id
        |        JOIN stop_times AS fromSt ON fromSt.trip_id = t.trip_id
        |        JOIN stations AS fromS ON fromS.stop_id = fromSt.stop_id
        |        JOIN stop_times AS toSt ON toSt.trip_id = t.trip_id
        |        JOIN stations AS toS ON toS.stop_id = toSt.stop_id
        |        WHERE fromS.code = ?
        |          AND toS.code = ?
        |          AND fromSt.seq < toSt.seq
        |        ORDER BY fromSt.dep_min, fromSt.day_offset
        |        
        """.trimMargin()
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, fromCode)
        _argIndex = 2
        _stmt.bindText(_argIndex, toCode)
        val _columnIndexOfTripId: Int = 0
        val _columnIndexOfTrainNumber: Int = 1
        val _columnIndexOfTrainName: Int = 2
        val _columnIndexOfHeadsign: Int = 3
        val _columnIndexOfFromSeq: Int = 4
        val _columnIndexOfToSeq: Int = 5
        val _columnIndexOfFromDep: Int = 6
        val _columnIndexOfToArr: Int = 7
        val _columnIndexOfFromDayOffset: Int = 8
        val _columnIndexOfToDayOffset: Int = 9
        val _result: MutableList<TrainsBetweenResult> = mutableListOf()
        while (_stmt.step()) {
          val _item: TrainsBetweenResult
          val _tmpTripId: String
          _tmpTripId = _stmt.getText(_columnIndexOfTripId)
          val _tmpTrainNumber: String
          _tmpTrainNumber = _stmt.getText(_columnIndexOfTrainNumber)
          val _tmpTrainName: String
          _tmpTrainName = _stmt.getText(_columnIndexOfTrainName)
          val _tmpHeadsign: String?
          if (_stmt.isNull(_columnIndexOfHeadsign)) {
            _tmpHeadsign = null
          } else {
            _tmpHeadsign = _stmt.getText(_columnIndexOfHeadsign)
          }
          val _tmpFromSeq: Int
          _tmpFromSeq = _stmt.getLong(_columnIndexOfFromSeq).toInt()
          val _tmpToSeq: Int
          _tmpToSeq = _stmt.getLong(_columnIndexOfToSeq).toInt()
          val _tmpFromDep: Int
          _tmpFromDep = _stmt.getLong(_columnIndexOfFromDep).toInt()
          val _tmpToArr: Int
          _tmpToArr = _stmt.getLong(_columnIndexOfToArr).toInt()
          val _tmpFromDayOffset: Int
          _tmpFromDayOffset = _stmt.getLong(_columnIndexOfFromDayOffset).toInt()
          val _tmpToDayOffset: Int
          _tmpToDayOffset = _stmt.getLong(_columnIndexOfToDayOffset).toInt()
          _item =
              TrainsBetweenResult(_tmpTripId,_tmpTrainNumber,_tmpTrainName,_tmpHeadsign,_tmpFromSeq,_tmpToSeq,_tmpFromDep,_tmpToArr,_tmpFromDayOffset,_tmpToDayOffset)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getStationBoard(
    stationCode: String,
    weekday: Int,
    todayDate: Int?,
    afterMin: Int?,
  ): List<StationDeparture> {
    val _sql: String = """
        |
        |        SELECT t.trip_id AS trip_id,
        |               tr.train_number AS train_number,
        |               tr.name AS train_name,
        |               t.headsign AS headsign,
        |               st.dep_min AS dep_min,
        |               st.day_offset AS day_offset,
        |               st.seq AS seq,
        |               t.service_id AS service_id
        |        FROM stop_times AS st
        |        JOIN stations AS s ON s.stop_id = st.stop_id
        |        JOIN trips AS t ON t.trip_id = st.trip_id
        |        JOIN trains AS tr ON tr.route_id = t.route_id
        |        JOIN calendar AS c ON c.service_id = t.service_id
        |        WHERE s.code = ?
        |          AND (
        |            CASE ?
        |              WHEN 0 THEN c.mon
        |              WHEN 1 THEN c.tue
        |              WHEN 2 THEN c.wed
        |              WHEN 3 THEN c.thu
        |              WHEN 4 THEN c.fri
        |              WHEN 5 THEN c.sat
        |              ELSE c.sun
        |            END
        |          ) = 1
        |          AND (? IS NULL OR (c.start_date <= ? AND ? <= c.end_date))
        |          AND (? IS NULL OR st.dep_min >= ?)
        |        ORDER BY st.day_offset, st.dep_min
        |        LIMIT 50
        |        
        """.trimMargin()
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, stationCode)
        _argIndex = 2
        _stmt.bindLong(_argIndex, weekday.toLong())
        _argIndex = 3
        if (todayDate == null) {
          _stmt.bindNull(_argIndex)
        } else {
          _stmt.bindLong(_argIndex, todayDate.toLong())
        }
        _argIndex = 4
        if (todayDate == null) {
          _stmt.bindNull(_argIndex)
        } else {
          _stmt.bindLong(_argIndex, todayDate.toLong())
        }
        _argIndex = 5
        if (todayDate == null) {
          _stmt.bindNull(_argIndex)
        } else {
          _stmt.bindLong(_argIndex, todayDate.toLong())
        }
        _argIndex = 6
        if (afterMin == null) {
          _stmt.bindNull(_argIndex)
        } else {
          _stmt.bindLong(_argIndex, afterMin.toLong())
        }
        _argIndex = 7
        if (afterMin == null) {
          _stmt.bindNull(_argIndex)
        } else {
          _stmt.bindLong(_argIndex, afterMin.toLong())
        }
        val _columnIndexOfTripId: Int = 0
        val _columnIndexOfTrainNumber: Int = 1
        val _columnIndexOfTrainName: Int = 2
        val _columnIndexOfHeadsign: Int = 3
        val _columnIndexOfDepMin: Int = 4
        val _columnIndexOfDayOffset: Int = 5
        val _columnIndexOfSeq: Int = 6
        val _columnIndexOfServiceId: Int = 7
        val _result: MutableList<StationDeparture> = mutableListOf()
        while (_stmt.step()) {
          val _item: StationDeparture
          val _tmpTripId: String
          _tmpTripId = _stmt.getText(_columnIndexOfTripId)
          val _tmpTrainNumber: String
          _tmpTrainNumber = _stmt.getText(_columnIndexOfTrainNumber)
          val _tmpTrainName: String
          _tmpTrainName = _stmt.getText(_columnIndexOfTrainName)
          val _tmpHeadsign: String?
          if (_stmt.isNull(_columnIndexOfHeadsign)) {
            _tmpHeadsign = null
          } else {
            _tmpHeadsign = _stmt.getText(_columnIndexOfHeadsign)
          }
          val _tmpDepMin: Int
          _tmpDepMin = _stmt.getLong(_columnIndexOfDepMin).toInt()
          val _tmpDayOffset: Int
          _tmpDayOffset = _stmt.getLong(_columnIndexOfDayOffset).toInt()
          val _tmpSeq: Int
          _tmpSeq = _stmt.getLong(_columnIndexOfSeq).toInt()
          val _tmpServiceId: String
          _tmpServiceId = _stmt.getText(_columnIndexOfServiceId)
          _item =
              StationDeparture(_tmpTripId,_tmpTrainNumber,_tmpTrainName,_tmpHeadsign,_tmpDepMin,_tmpDayOffset,_tmpSeq,_tmpServiceId)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
