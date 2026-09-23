package com.trainkraft.app.presentation

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trainkraft.app.TrainKraftApp
import com.trainkraft.app.data.BetweenResult
import com.trainkraft.app.data.BetweenTrainDto
import com.trainkraft.app.data.BetweenTrainsDto
import com.trainkraft.app.data.LoadResult
import com.trainkraft.app.data.NtesFormats
import com.trainkraft.app.data.StationEntity
import com.trainkraft.app.BuildConfig
import com.trainkraft.app.data.TrainDatabase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class BetweenViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val dao = TrainDatabase.getInstance(application).trainDao()
    private val packDao = TrainDatabase.getInstance(application).packDao()
    private val repo = (application as? TrainKraftApp)?.container?.ntesRepository

    // Station pickers
    private val _fromQuery = MutableStateFlow("")
    val fromQuery: StateFlow<String> = _fromQuery.asStateFlow()
    private val _toQuery = MutableStateFlow("")
    val toQuery: StateFlow<String> = _toQuery.asStateFlow()

    private val _fromResults = MutableStateFlow<List<StationEntity>>(emptyList())
    val fromResults: StateFlow<List<StationEntity>> = _fromResults.asStateFlow()
    private val _toResults = MutableStateFlow<List<StationEntity>>(emptyList())
    val toResults: StateFlow<List<StationEntity>> = _toResults.asStateFlow()

    // Selected stations
    private val _fromStation = MutableStateFlow<StationEntity?>(null)
    val fromStation: StateFlow<StationEntity?> = _fromStation.asStateFlow()
    private val _toStation = MutableStateFlow<StationEntity?>(null)
    val toStation: StateFlow<StationEntity?> = _toStation.asStateFlow()

    // Results (BetweenResult kept for compat; allRows is the enriched source
    // the redesigned screen renders — BetweenResult carries no dayOfRun /
    // classes / typeDesc, so the NTES metadata rides along in BetweenUiRow).
    private val _results = MutableStateFlow<List<BetweenResult>>(emptyList())
    val results: StateFlow<List<BetweenResult>> = _results.asStateFlow()
    private val _allRows = MutableStateFlow<List<BetweenUiRow>>(emptyList())
    val allRows: StateFlow<List<BetweenUiRow>> = _allRows.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Local fetch time of the currently shown payload (honesty badge). */
    private val _fetchEpochMs = MutableStateFlow<Long?>(null)
    val fetchEpochMs: StateFlow<Long?> = _fetchEpochMs.asStateFlow()

    /** Date-carousel selection; defaults to today, reset on each new search. */
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    /** Train-type filter; null = All. Reset on each new search. */
    private val _typeFilter = MutableStateFlow<String?>(null)
    val typeFilter: StateFlow<String?> = _typeFilter.asStateFlow()

    /** Client-side sort; kept across searches. */
    private val _sort = MutableStateFlow(BetweenSort.DEPARTURE)
    val sort: StateFlow<BetweenSort> = _sort.asStateFlow()

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun selectTypeFilter(filter: String?) {
        _typeFilter.value = filter
    }

    fun selectSort(sort: BetweenSort) {
        _sort.value = sort
    }

    /** Which source produced [results] — drives the honesty badge. */
    private val _source = MutableStateFlow(DataSource.OFFLINE)
    val source: StateFlow<DataSource> = _source.asStateFlow()
    private val _sourceAgeMs = MutableStateFlow<Long?>(null)
    val sourceAgeMs: StateFlow<Long?> = _sourceAgeMs.asStateFlow()

    private val _uiState = MutableStateFlow<BetweenUiState>(BetweenUiState.Idle)
    val uiState: StateFlow<BetweenUiState> = _uiState.asStateFlow()

    /**
     * Typical arrival delay at the destination stop, per train number
     * (pack `arrAvgMin`; absent key = no pack row = no badge, never invented).
     * Loaded once per results payload — one local-DB DAO call per train
     * ([PackDao.priorForStation]), which is cheap enough to stay inline; the
     * whole batch silent-fails to an empty map.
     */
    private val _usualDelays = MutableStateFlow<Map<String, Int>>(emptyMap())
    val usualDelays: StateFlow<Map<String, Int>> = _usualDelays.asStateFlow()

    init {
        // Pack rows load with the badge batch below; the pack file itself is
        // bootstrapped once in AppContainer.database. Absent pack = no rows
        // = no badges, never invented.
    }

    private var fromSearchJob: Job? = null
    private var toSearchJob: Job? = null

    fun onFromQueryChange(value: String) {
        _fromQuery.value = value
        _fromStation.value = null
        _results.value = emptyList()
        _allRows.value = emptyList()
        fromSearchJob?.cancel()
        if (value.isBlank()) {
            _fromResults.value = emptyList()
            return
        }
        fromSearchJob = viewModelScope.launch {
            delay(300)
            _fromResults.value = dao.searchStations(value)
        }
    }

    fun onToQueryChange(value: String) {
        _toQuery.value = value
        _toStation.value = null
        _results.value = emptyList()
        _allRows.value = emptyList()
        toSearchJob?.cancel()
        if (value.isBlank()) {
            _toResults.value = emptyList()
            return
        }
        toSearchJob = viewModelScope.launch {
            delay(300)
            _toResults.value = dao.searchStations(value)
        }
    }

    fun selectFrom(station: StationEntity) {
        _fromStation.value = station
        _fromQuery.value = "${station.code} — ${station.name}"
        _fromResults.value = emptyList()
        if (_toStation.value != null) search()
    }

    fun selectTo(station: StationEntity) {
        _toStation.value = station
        _toQuery.value = "${station.code} — ${station.name}"
        _toResults.value = emptyList()
        if (_fromStation.value != null) search()
    }

    fun swap() {
        val f = _fromStation.value
        val t = _toStation.value
        val fq = _fromQuery.value
        val tq = _toQuery.value
        _fromStation.value = t
        _toStation.value = f
        _fromQuery.value = tq
        _toQuery.value = fq
        if (_fromStation.value != null && _toStation.value != null) search()
    }

    /** Re-runs the current search — used by the error-state retry button. */
    fun retry() {
        if (_fromStation.value != null && _toStation.value != null) search()
    }

    /** Manual reload for the top-bar refresh affordance. */
    fun refresh() {
        retry()
    }

    private fun search() {
        val from = _fromStation.value ?: return
        val to = _toStation.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _uiState.value = BetweenUiState.Loading
            try {
                var rows: List<BetweenResult>? = null
                var ds = DataSource.OFFLINE
                var age: Long? = null
                var liveFailed = false

                // Live-first: strict NTES rows, cache fallback by repository.
                // enriched carries the NTES metadata (dayOfRun / classes /
                // typeDesc) that BetweenResult cannot hold.
                var enriched: List<BetweenUiRow>? = null
                when (val live = repo?.trainsBetween(from.code, to.code)
                    ?: LoadResult.Failed("offline build")) {
                    is LoadResult.Live -> {
                        enriched = mapLive(live.value, from, to)
                        rows = enriched.toBetweenResults()
                        ds = DataSource.LIVE
                    }
                    is LoadResult.Offline -> {
                        enriched = mapLive(live.value, from, to)
                        rows = enriched.toBetweenResults()
                        ds = DataSource.CACHED
                        age = live.ageMs
                    }
                    is LoadResult.Failed -> liveFailed = true
                }

                if (rows.isNullOrEmpty()) {
                    // Live unavailable or empty → offline weekday timetable.
                    val today = LocalDate.now()
                    val weekday = today.dayOfWeek.value - 1
                    val gtfs = dao.getTrainsBetween(from.code, to.code, weekday)
                    if (gtfs.isNotEmpty()) {
                        rows = gtfs
                        ds = DataSource.OFFLINE
                        age = null
                        // GTFS rows were already weekday-filtered by the DAO
                        // query, so tag each with today's abbreviation — keeps
                        // the date carousel counts honest for offline data.
                        val abbrev = today.format(WEEKDAY_ABBREV)
                        enriched = gtfs.map { it.toUiRow(abbrev) }
                    } else if (rows == null && liveFailed) {
                        val msg =
                            "Couldn't reach live data, and the offline timetable " +
                                "has no trains between ${from.code} and ${to.code}."
                        _error.value = msg
                        _uiState.value = BetweenUiState.Error(msg)
                        return@launch
                    }
                    // else: both sources genuinely empty → EmptyState below.
                }

                _results.value = rows.orEmpty()
                _allRows.value = enriched.orEmpty()
                _source.value = ds
                _sourceAgeMs.value = age
                // Actual payload-arrival time for the honesty badge.
                _fetchEpochMs.value = System.currentTimeMillis()
                // New station pair → restart the carousel on today, clear the
                // type filter; the sort preference is kept.
                _selectedDate.value = LocalDate.now()
                _typeFilter.value = null
                _uiState.value = BetweenUiState.Results(_results.value)
                loadUsualDelays(enriched.orEmpty())
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e("BetweenVM", "search failed", e)
                }
                val msg = "Search failed. Please try again."
                _error.value = msg
                _uiState.value = BetweenUiState.Error(msg)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Live NTES rows → enriched presentation rows.
     *
     * NTES wraps arrival at midnight, so travel time gives the exact day
     * offset even for >24h legs (fallback: arr <= dep ⇒ next day — see
     * [inferArrivalDayOffset]). Board and alight codes can differ from the
     * query — NTES resolves station pairs regionally (NDLS→MMCT also matches
     * NZM→BDTS rows). Dep day is assumed today (the common case for corridor
     * searches).
     */
    private fun mapLive(
        dto: BetweenTrainsDto,
        from: StationEntity,
        to: StationEntity,
    ): List<BetweenUiRow> = dto.trains.mapNotNull { t ->
        mapLiveRow(t, from.code, from.name, to.code, to.name)
    }.sortedWith(compareBy({ it.depMin }, { it.trainNumber }))

    /**
     * Batch-loads destination arrival priors for one results payload: one
     * [PackDao.priorForStation] call per train (local DB — no batching layer
     * needed), keyed by train number. Silent-fails to an empty map; a missing
     * row simply yields no badge for that card.
     */
    private fun loadUsualDelays(rows: List<BetweenUiRow>) {
        _usualDelays.value = emptyMap()
        if (rows.isEmpty()) return
        viewModelScope.launch {
            try {
                val map = mutableMapOf<String, Int>()
                for (row in rows) {
                    val prior = packDao.priorForStation(row.trainNumber, row.toCode)
                    if (prior != null) map[row.trainNumber] = prior.arrAvgMin
                }
                _usualDelays.value = map
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e("BetweenVM", "usual delays failed: ${e.message}")
                }
                _usualDelays.value = emptyMap()
            }
        }
    }

    class Factory(
        private val application: Application,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BetweenViewModel(application) as T
        }
    }

    companion object {
        private val WEEKDAY_ABBREV: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
    }
}

/**
 * One NTES between-trains DTO row → enriched UI row. Null when the leg times
 * don't parse. Top-level (same file, outside the class) so it stays unit-
 * testable without Android.
 */
fun mapLiveRow(
    t: BetweenTrainDto,
    fromCode: String,
    fromName: String,
    toCode: String,
    toName: String,
): BetweenUiRow? {
    val dep = t.depMinutes() ?: return null
    val arr = t.arrMinutes() ?: return null
    // durationMinutes (presentation) also covers >24h legs like "26:25",
    // which NtesFormats.hhmmToMinutes rejects (hh < 24) — without it those
    // legs would land on the wrong day offset.
    val travelMin = durationMinutes(t.travelTime) ?: NtesFormats.hhmmToMinutes(t.travelTime)
    val arrDay = inferArrivalDayOffset(dep, arr, travelMin)
    return BetweenUiRow(
        trainNumber = t.trainNumber,
        trainName = t.trainName,
        fromCode = t.boardCode.ifBlank { fromCode },
        fromName = t.boardName.ifBlank { fromName },
        depMin = dep,
        depDayOffset = 0,
        toCode = t.alightCode.ifBlank { toCode },
        toName = t.alightName.ifBlank { toName },
        arrMin = arr,
        arrDayOffset = arrDay,
        dayOfRun = t.dayOfRun,
        classes = t.classes,
        typeDesc = t.typeDesc,
        travelRaw = t.travelTime,
    )
}

/** Offline GTFS row → UI row, tagged with the queried weekday's abbreviation. */
fun BetweenResult.toUiRow(dayOfRunAbbrev: String): BetweenUiRow = BetweenUiRow(
    trainNumber = trainNumber,
    trainName = trainName,
    fromCode = fromCode,
    fromName = fromName,
    depMin = depMin,
    depDayOffset = depDayOffset,
    toCode = toCode,
    toName = toName,
    arrMin = arrMin,
    arrDayOffset = arrDayOffset,
    dayOfRun = dayOfRunAbbrev,
)

/** UI rows back to the compat [BetweenResult] list. */
fun List<BetweenUiRow>.toBetweenResults(): List<BetweenResult> = map { row ->
    BetweenResult(
        trainNumber = row.trainNumber,
        trainName = row.trainName,
        fromCode = row.fromCode,
        fromName = row.fromName,
        depMin = row.depMin,
        depDayOffset = row.depDayOffset,
        toCode = row.toCode,
        toName = row.toName,
        arrMin = row.arrMin,
        arrDayOffset = row.arrDayOffset,
    )
}

/**
 * Typical-delay badge label from the pack arrival prior at the destination
 * stop. Null when [arrAvgMin] is null (no pack row → no badge, never
 * invented); `<= 0` means typically on time. Pure — unit-tested.
 */
fun usualDelayBadgeLabel(arrAvgMin: Int?): String? = when {
    arrAvgMin == null -> null
    arrAvgMin <= 0 -> "usually on time"
    else -> "usually +$arrAvgMin"
}
