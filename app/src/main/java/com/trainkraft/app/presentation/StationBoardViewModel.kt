package com.trainkraft.app.presentation

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trainkraft.app.BuildConfig
import com.trainkraft.app.TrainKraftApp
import com.trainkraft.app.data.LoadResult
import com.trainkraft.app.data.NtesFormats
import com.trainkraft.app.data.StationDeparture
import com.trainkraft.app.data.StationEntity
import com.trainkraft.app.data.StationLiveDto
import com.trainkraft.app.data.TrainDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

/**
 * Station board: live NTES departures merged over the offline GTFS weekday
 * timetable.
 *
 * Sources (see [DataSource]):
 *  - [DataSource.LIVE]/[DataSource.CACHED]: NTES `TrainsAtStationJson` for the
 *    next [LIVE_HOURS] hours — rows carry platform + delay — merged by train
 *    number over the full-day GTFS list so the live window never shrinks the
 *    board.
 *  - [DataSource.OFFLINE]: GTFS snapshot only (network failed, no cache).
 *
 * Weekday follows the DAO convention: `DayOfWeek.value - 1` (0 = Mon).
 */
class StationBoardViewModel(
    application: Application,
    val stationCode: String,
) : AndroidViewModel(application) {

    companion object {
        /** NTES live window in hours (server echoes `NextHr`). */
        private const val LIVE_HOURS = 4
    }

    private val dao = TrainDatabase.getInstance(application).trainDao()
    private val repo = (application as? TrainKraftApp)?.container?.ntesRepository

    private val _station = MutableStateFlow<StationEntity?>(null)
    val station: StateFlow<StationEntity?> = _station.asStateFlow()

    private val _departures = MutableStateFlow<List<BoardRow>>(emptyList())
    val departures: StateFlow<List<BoardRow>> = _departures.asStateFlow()

    private val _source = MutableStateFlow(DataSource.OFFLINE)
    val source: StateFlow<DataSource> = _source.asStateFlow()

    private val _sourceAgeMs = MutableStateFlow<Long?>(null)
    val sourceAgeMs: StateFlow<Long?> = _sourceAgeMs.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _dbError = MutableStateFlow<String?>(null)
    val dbError: StateFlow<String?> = _dbError.asStateFlow()

    private val _uiState = MutableStateFlow<StationBoardUiState>(StationBoardUiState.Loading)
    val uiState: StateFlow<StationBoardUiState> = _uiState.asStateFlow()

    private val todayStamp = SimpleDateFormat("d-MMM", Locale.ENGLISH).format(Date())

    init {
        loadBoard()
    }

    /** Loads the station board; re-called by Retry after a DB error. */
    fun loadBoard() {
        viewModelScope.launch {
            _isLoading.value = true
            _dbError.value = null
            _uiState.value = StationBoardUiState.Loading
            try {
                val matches = dao.searchStations(stationCode)
                val station = matches.firstOrNull {
                    it.code.equals(stationCode, ignoreCase = true)
                } ?: matches.firstOrNull()
                val weekday = LocalDate.now().dayOfWeek.value - 1
                val gtfs = dao.getStationBoard(stationCode, weekday)
                _station.value = station

                var rows: List<BoardRow>? = null
                var ds = DataSource.OFFLINE
                var age: Long? = null
                val live = repo?.stationLive(stationCode.uppercase(Locale.ROOT), LIVE_HOURS)
                    ?: LoadResult.Failed("offline build")
                when (live) {
                    is LoadResult.Live -> {
                        rows = merge(gtfs, live.value)
                        ds = DataSource.LIVE
                    }
                    is LoadResult.Offline -> {
                        rows = merge(gtfs, live.value)
                        ds = DataSource.CACHED
                        age = live.ageMs
                    }
                    is LoadResult.Failed -> {
                        // Fall through to the GTFS-only board below.
                    }
                }
                if (rows == null) {
                    rows = gtfs.map { it.toRow() }
                    ds = DataSource.OFFLINE
                    age = null
                }

                _departures.value = rows
                _source.value = ds
                _sourceAgeMs.value = age
                _uiState.value = StationBoardUiState.Loaded(station, rows)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e("StationBoardVM", "loadBoard failed", e)
                }
                val msg = mapBoardError(e)
                _dbError.value = msg
                _uiState.value = StationBoardUiState.Error(msg)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun retry() = loadBoard()

    /**
     * GTFS full-day rows, enriched/extended by the live NTES window: matching
     * train numbers gain platform/delay/live times; live-only rows (missing
     * from the snapshot) are appended.
     */
    private fun merge(gtfs: List<StationDeparture>, live: StationLiveDto): List<BoardRow> {
        val liveByTrain = live.trains
            .filter { it.departsFromHere() }
            .mapNotNull { t ->
                val dep = NtesFormats.timeToMinutes(t.scheduledDeparture)
                    ?: return@mapNotNull null
                BoardRow(
                    key = "live-${t.trainNumber}",
                    trainNumber = t.trainNumber,
                    trainName = t.trainName,
                    depMin = dep,
                    dayOffset = liveDayOffset(t.scheduledDeparture),
                    destCode = t.destCode,
                    destName = t.destName,
                    platform = t.platform.ifBlank { null },
                    delayMin = t.delayDepMinutes(),
                    cancelled = t.cancelled > 0 || t.depCancelled > 0,
                )
            }
            .associateBy { it.trainNumber }

        val merged = gtfs.map { g ->
            val row = g.toRow()
            val l = liveByTrain[g.trainNumber]
                ?: return@map row
            row.copy(
                depMin = l.depMin,
                dayOffset = l.dayOffset,
                destCode = l.destCode ?: row.destCode,
                destName = l.destName ?: row.destName,
                platform = l.platform,
                delayMin = l.delayMin,
                cancelled = l.cancelled,
            )
        } + liveByTrain.values.filter { l ->
            gtfs.none { it.trainNumber == l.trainNumber }
        }

        return merged.sortedWith(
            compareBy({ it.dayOffset }, { it.depMin ?: Int.MAX_VALUE }),
        )
    }

    /**
     * "05:10 23-Sep" → 0 when 23-Sep is today, else 1 (the live window can
     * spill past midnight). Unparseable → 0.
     */
    private fun liveDayOffset(std: String): Int {
        val stamp = Regex("\\d{1,2}-[A-Za-z]{3}$").find(std.trim())?.value ?: return 0
        val normalized = stamp.trimStart('0').ifEmpty { "0" }
        return if (normalized.equals(todayStamp, ignoreCase = true)) 0 else 1
    }

    private fun StationDeparture.toRow() = BoardRow(
        key = tripId,
        trainNumber = trainNumber,
        trainName = trainName,
        depMin = depMin,
        dayOffset = dayOffset,
        destCode = destCode,
        destName = destName,
    )

    private fun mapBoardError(e: Exception): String =
        "Timetable unavailable. Please try again."

    class Factory(
        private val application: Application,
        private val stationCode: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StationBoardViewModel(application, stationCode) as T
        }
    }
}
