package com.trainkraft.app.`data`

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.FtsTableInfo
import androidx.room.util.TableInfo
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass
import androidx.room.util.FtsTableInfo.Companion.read as ftsTableInfoRead
import androidx.room.util.TableInfo.Companion.read as tableInfoRead

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class TrainDatabase_Impl : TrainDatabase() {
  private val _trainDao: Lazy<TrainDao> = lazy {
    TrainDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(1,
        "8f222d5a96955c3086ea29cd9204c827", "5ed763163416b9e1d652dfc774fa0a1a") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `stations` (`stop_id` TEXT NOT NULL, `code` TEXT NOT NULL, `name` TEXT NOT NULL, `lat` REAL, `lon` REAL, PRIMARY KEY(`stop_id`))")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_stations_code` ON `stations` (`code`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_stations_name` ON `stations` (`name`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `trains` (`route_id` TEXT NOT NULL, `train_number` TEXT NOT NULL, `name` TEXT NOT NULL, `type` INTEGER NOT NULL, PRIMARY KEY(`route_id`))")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_trains_train_number` ON `trains` (`train_number`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_trains_name` ON `trains` (`name`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `trips` (`trip_id` TEXT NOT NULL, `route_id` TEXT NOT NULL, `service_id` TEXT NOT NULL, `headsign` TEXT, PRIMARY KEY(`trip_id`), FOREIGN KEY(`route_id`) REFERENCES `trains`(`route_id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_trips_route_id` ON `trips` (`route_id`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_trips_service_id` ON `trips` (`service_id`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `calendar` (`service_id` TEXT NOT NULL, `mon` INTEGER NOT NULL, `tue` INTEGER NOT NULL, `wed` INTEGER NOT NULL, `thu` INTEGER NOT NULL, `fri` INTEGER NOT NULL, `sat` INTEGER NOT NULL, `sun` INTEGER NOT NULL, `start_date` INTEGER NOT NULL, `end_date` INTEGER NOT NULL, PRIMARY KEY(`service_id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `stop_times` (`trip_id` TEXT NOT NULL, `seq` INTEGER NOT NULL, `stop_id` TEXT NOT NULL, `arr_min` INTEGER NOT NULL, `dep_min` INTEGER NOT NULL, `day_offset` INTEGER NOT NULL, PRIMARY KEY(`trip_id`, `seq`), FOREIGN KEY(`trip_id`) REFERENCES `trips`(`trip_id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`stop_id`) REFERENCES `stations`(`stop_id`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_stop_times_trip_id` ON `stop_times` (`trip_id`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_stop_times_stop_id` ON `stop_times` (`stop_id`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_stop_times_trip_id_seq` ON `stop_times` (`trip_id`, `seq`)")
        connection.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `stations_fts` USING FTS4(`code` TEXT NOT NULL, `name` TEXT NOT NULL, content=`stations`)")
        connection.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_stations_fts_BEFORE_UPDATE BEFORE UPDATE ON `stations` BEGIN DELETE FROM `stations_fts` WHERE `docid`=OLD.`rowid`; END")
        connection.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_stations_fts_BEFORE_DELETE BEFORE DELETE ON `stations` BEGIN DELETE FROM `stations_fts` WHERE `docid`=OLD.`rowid`; END")
        connection.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_stations_fts_AFTER_UPDATE AFTER UPDATE ON `stations` BEGIN INSERT INTO `stations_fts`(`docid`, `code`, `name`) VALUES (NEW.`rowid`, NEW.`code`, NEW.`name`); END")
        connection.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_stations_fts_AFTER_INSERT AFTER INSERT ON `stations` BEGIN INSERT INTO `stations_fts`(`docid`, `code`, `name`) VALUES (NEW.`rowid`, NEW.`code`, NEW.`name`); END")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '8f222d5a96955c3086ea29cd9204c827')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `stations`")
        connection.execSQL("DROP TABLE IF EXISTS `trains`")
        connection.execSQL("DROP TABLE IF EXISTS `trips`")
        connection.execSQL("DROP TABLE IF EXISTS `calendar`")
        connection.execSQL("DROP TABLE IF EXISTS `stop_times`")
        connection.execSQL("DROP TABLE IF EXISTS `stations_fts`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        connection.execSQL("PRAGMA foreign_keys = ON")
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_stations_fts_BEFORE_UPDATE BEFORE UPDATE ON `stations` BEGIN DELETE FROM `stations_fts` WHERE `docid`=OLD.`rowid`; END")
        connection.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_stations_fts_BEFORE_DELETE BEFORE DELETE ON `stations` BEGIN DELETE FROM `stations_fts` WHERE `docid`=OLD.`rowid`; END")
        connection.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_stations_fts_AFTER_UPDATE AFTER UPDATE ON `stations` BEGIN INSERT INTO `stations_fts`(`docid`, `code`, `name`) VALUES (NEW.`rowid`, NEW.`code`, NEW.`name`); END")
        connection.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_stations_fts_AFTER_INSERT AFTER INSERT ON `stations` BEGIN INSERT INTO `stations_fts`(`docid`, `code`, `name`) VALUES (NEW.`rowid`, NEW.`code`, NEW.`name`); END")
      }

      public override fun onValidateSchema(connection: SQLiteConnection):
          RoomOpenDelegate.ValidationResult {
        val _columnsStations: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsStations.put("stop_id", TableInfo.Column("stop_id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsStations.put("code", TableInfo.Column("code", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsStations.put("name", TableInfo.Column("name", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsStations.put("lat", TableInfo.Column("lat", "REAL", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsStations.put("lon", TableInfo.Column("lon", "REAL", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysStations: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesStations: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesStations.add(TableInfo.Index("index_stations_code", false, listOf("code"),
            listOf("ASC")))
        _indicesStations.add(TableInfo.Index("index_stations_name", false, listOf("name"),
            listOf("ASC")))
        val _infoStations: TableInfo = TableInfo("stations", _columnsStations, _foreignKeysStations,
            _indicesStations)
        val _existingStations: TableInfo = tableInfoRead(connection, "stations")
        if (!_infoStations.equals(_existingStations)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |stations(com.trainkraft.app.data.StationEntity).
              | Expected:
              |""".trimMargin() + _infoStations + """
              |
              | Found:
              |""".trimMargin() + _existingStations)
        }
        val _columnsTrains: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsTrains.put("route_id", TableInfo.Column("route_id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsTrains.put("train_number", TableInfo.Column("train_number", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsTrains.put("name", TableInfo.Column("name", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsTrains.put("type", TableInfo.Column("type", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysTrains: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesTrains: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesTrains.add(TableInfo.Index("index_trains_train_number", true,
            listOf("train_number"), listOf("ASC")))
        _indicesTrains.add(TableInfo.Index("index_trains_name", false, listOf("name"),
            listOf("ASC")))
        val _infoTrains: TableInfo = TableInfo("trains", _columnsTrains, _foreignKeysTrains,
            _indicesTrains)
        val _existingTrains: TableInfo = tableInfoRead(connection, "trains")
        if (!_infoTrains.equals(_existingTrains)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |trains(com.trainkraft.app.data.TrainEntity).
              | Expected:
              |""".trimMargin() + _infoTrains + """
              |
              | Found:
              |""".trimMargin() + _existingTrains)
        }
        val _columnsTrips: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsTrips.put("trip_id", TableInfo.Column("trip_id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsTrips.put("route_id", TableInfo.Column("route_id", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsTrips.put("service_id", TableInfo.Column("service_id", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsTrips.put("headsign", TableInfo.Column("headsign", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysTrips: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        _foreignKeysTrips.add(TableInfo.ForeignKey("trains", "CASCADE", "NO ACTION",
            listOf("route_id"), listOf("route_id")))
        val _indicesTrips: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesTrips.add(TableInfo.Index("index_trips_route_id", false, listOf("route_id"),
            listOf("ASC")))
        _indicesTrips.add(TableInfo.Index("index_trips_service_id", false, listOf("service_id"),
            listOf("ASC")))
        val _infoTrips: TableInfo = TableInfo("trips", _columnsTrips, _foreignKeysTrips,
            _indicesTrips)
        val _existingTrips: TableInfo = tableInfoRead(connection, "trips")
        if (!_infoTrips.equals(_existingTrips)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |trips(com.trainkraft.app.data.TripEntity).
              | Expected:
              |""".trimMargin() + _infoTrips + """
              |
              | Found:
              |""".trimMargin() + _existingTrips)
        }
        val _columnsCalendar: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsCalendar.put("service_id", TableInfo.Column("service_id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCalendar.put("mon", TableInfo.Column("mon", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCalendar.put("tue", TableInfo.Column("tue", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCalendar.put("wed", TableInfo.Column("wed", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCalendar.put("thu", TableInfo.Column("thu", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCalendar.put("fri", TableInfo.Column("fri", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCalendar.put("sat", TableInfo.Column("sat", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCalendar.put("sun", TableInfo.Column("sun", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCalendar.put("start_date", TableInfo.Column("start_date", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCalendar.put("end_date", TableInfo.Column("end_date", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysCalendar: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesCalendar: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoCalendar: TableInfo = TableInfo("calendar", _columnsCalendar, _foreignKeysCalendar,
            _indicesCalendar)
        val _existingCalendar: TableInfo = tableInfoRead(connection, "calendar")
        if (!_infoCalendar.equals(_existingCalendar)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |calendar(com.trainkraft.app.data.CalendarEntity).
              | Expected:
              |""".trimMargin() + _infoCalendar + """
              |
              | Found:
              |""".trimMargin() + _existingCalendar)
        }
        val _columnsStopTimes: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsStopTimes.put("trip_id", TableInfo.Column("trip_id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsStopTimes.put("seq", TableInfo.Column("seq", "INTEGER", true, 2, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsStopTimes.put("stop_id", TableInfo.Column("stop_id", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsStopTimes.put("arr_min", TableInfo.Column("arr_min", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsStopTimes.put("dep_min", TableInfo.Column("dep_min", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsStopTimes.put("day_offset", TableInfo.Column("day_offset", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysStopTimes: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        _foreignKeysStopTimes.add(TableInfo.ForeignKey("trips", "CASCADE", "NO ACTION",
            listOf("trip_id"), listOf("trip_id")))
        _foreignKeysStopTimes.add(TableInfo.ForeignKey("stations", "NO ACTION", "NO ACTION",
            listOf("stop_id"), listOf("stop_id")))
        val _indicesStopTimes: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesStopTimes.add(TableInfo.Index("index_stop_times_trip_id", false, listOf("trip_id"),
            listOf("ASC")))
        _indicesStopTimes.add(TableInfo.Index("index_stop_times_stop_id", false, listOf("stop_id"),
            listOf("ASC")))
        _indicesStopTimes.add(TableInfo.Index("index_stop_times_trip_id_seq", false,
            listOf("trip_id", "seq"), listOf("ASC", "ASC")))
        val _infoStopTimes: TableInfo = TableInfo("stop_times", _columnsStopTimes,
            _foreignKeysStopTimes, _indicesStopTimes)
        val _existingStopTimes: TableInfo = tableInfoRead(connection, "stop_times")
        if (!_infoStopTimes.equals(_existingStopTimes)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |stop_times(com.trainkraft.app.data.StopTimeEntity).
              | Expected:
              |""".trimMargin() + _infoStopTimes + """
              |
              | Found:
              |""".trimMargin() + _existingStopTimes)
        }
        val _columnsStationsFts: MutableSet<String> = mutableSetOf()
        _columnsStationsFts.add("code")
        _columnsStationsFts.add("name")
        val _infoStationsFts: FtsTableInfo = FtsTableInfo("stations_fts", _columnsStationsFts,
            "CREATE VIRTUAL TABLE IF NOT EXISTS `stations_fts` USING FTS4(`code` TEXT NOT NULL, `name` TEXT NOT NULL, content=`stations`)")
        val _existingStationsFts: FtsTableInfo = ftsTableInfoRead(connection, "stations_fts")
        if (!_infoStationsFts.equals(_existingStationsFts)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |stations_fts(com.trainkraft.app.data.StationFts).
              | Expected:
              |""".trimMargin() + _infoStationsFts + """
              |
              | Found:
              |""".trimMargin() + _existingStationsFts)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    _shadowTablesMap.put("stations_fts", "stations")
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "stations", "trains", "trips",
        "calendar", "stop_times", "stations_fts")
  }

  public override fun clearAllTables() {
    super.performClear(true, "stations", "trains", "trips", "calendar", "stop_times",
        "stations_fts")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(TrainDao::class, TrainDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override
      fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>):
      List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun trainDao(): TrainDao = _trainDao.value
}
