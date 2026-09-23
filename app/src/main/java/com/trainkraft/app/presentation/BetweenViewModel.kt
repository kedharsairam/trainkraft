package com.trainkraft.app.presentation

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trainkraft.app.TrainKraftApp
import com.trainkraft.app.data.BetweenResult
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

class BetweenViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val dao = TrainDatabase.getInstance(application).trainDao()
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

    // Results
    private val _results = MutableStateFlow<List<BetweenResult>>(emptyList())
    val results: StateFlow<List<BetweenResult>> = _results.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Which source produced [results] — drives the honesty badge. */
    private val _source = MutableStateFlow(DataSource.OFFLINE)
    val source: StateFlow<DataSource> = _source.asStateFlow()
    private val _sourceAgeMs = MutableStateFlow<Long?>(null)
    val sourceAgeMs: StateFlow<Long?> = _sourceAgeMs.asStateFlow()

    private val _uiState = MutableStateFlow<BetweenUiState>(BetweenUiState.Idle)
    val uiState: StateFlow<BetweenUiState> = _uiState.asStateFlow()

    private var fromSearchJob: Job? = null
    private var toSearchJob: Job? = null

    fun onFromQueryChange(value: String) {
        _fromQuery.value = value
        _fromStation.value = null
        _results.value = emptyList()
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
                when (val live = repo?.trainsBetween(from.code, to.code)
                    ?: LoadResult.Failed("offline build")) {
                    is LoadResult.Live -> {
                        rows = mapLive(live.value, from, to)
                        ds = DataSource.LIVE
                    }
                    is LoadResult.Offline -> {
                        rows = mapLive(live.value, from, to)
                        ds = DataSource.CACHED
                        age = live.ageMs
                    }
                    is LoadResult.Failed -> liveFailed = true
                }

                if (rows.isNullOrEmpty()) {
                    // Live unavailable or empty → offline weekday timetable.
                    val weekday = LocalDate.now().dayOfWeek.value - 1
                    val gtfs = dao.getTrainsBetween(from.code, to.code, weekday)
                    if (gtfs.isNotEmpty()) {
                        rows = gtfs
                        ds = DataSource.OFFLINE
                        age = null
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
                _source.value = ds
                _sourceAgeMs.value = age
                _uiState.value = BetweenUiState.Results(_results.value)
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
     * Live NTES rows → presentation model.
     *
     * NTES wraps arrival at midnight, so travel time gives the exact day
     * offset even for >24h legs (fallback: arr < dep ⇒ next day). Board and
     * alight codes can differ from the query — NTES resolves station pairs
     * regionally (NDLS→MMCT also matches NZM→BDTS rows). Dep day is assumed
     * today (the common case for corridor searches).
     */
    private fun mapLive(
        dto: BetweenTrainsDto,
        from: StationEntity,
        to: StationEntity,
    ): List<BetweenResult> = dto.trains.mapNotNull { t ->
        val dep = t.depMinutes() ?: return@mapNotNull null
        val arr = t.arrMinutes() ?: return@mapNotNull null
        val travelMin = NtesFormats.hhmmToMinutes(t.travelTime)
        val arrDay = when {
            travelMin != null -> (dep + travelMin) / 1440
            arr < dep -> 1
            else -> 0
        }
        BetweenResult(
            trainNumber = t.trainNumber,
            trainName = t.trainName,
            fromCode = t.boardCode.ifBlank { from.code },
            fromName = t.boardName.ifBlank { from.name },
            depMin = dep,
            depDayOffset = 0,
            toCode = t.alightCode.ifBlank { to.code },
            toName = t.alightName.ifBlank { to.name },
            arrMin = arr,
            arrDayOffset = arrDay,
        )
    }.sortedWith(compareBy({ it.depMin }, { it.trainNumber }))

    class Factory(
        private val application: Application,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BetweenViewModel(application) as T
        }
    }
}
