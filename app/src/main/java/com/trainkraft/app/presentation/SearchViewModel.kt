package com.trainkraft.app.presentation

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trainkraft.app.data.StationEntity
import com.trainkraft.app.BuildConfig
import com.trainkraft.app.data.TrainDatabase
import com.trainkraft.app.data.UserDatabase
import com.trainkraft.app.data.TrainEntity
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Search state: debounced (300ms) station + train lookup.
 *
 * Follows Kalc/WallKraft MVVM: UI collects StateFlows, all DB work stays
 * in [viewModelScope].
 */
class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = TrainDatabase.getInstance(application).trainDao()
    private val trackingDao = UserDatabase.getInstance(application).trackingDao()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _stationResults = MutableStateFlow<List<StationEntity>>(emptyList())
    val stationResults: StateFlow<List<StationEntity>> = _stationResults.asStateFlow()

    private val _trainResults = MutableStateFlow<List<TrainEntity>>(emptyList())
    val trainResults: StateFlow<List<TrainEntity>> = _trainResults.asStateFlow()

    private val _dbError = MutableStateFlow<String?>(null)
    val dbError: StateFlow<String?> = _dbError.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _tracked = MutableStateFlow<List<TrackedRow>>(emptyList())
    val tracked: StateFlow<List<TrackedRow>> = _tracked.asStateFlow()

    init {
        refreshTracked()
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            _query
                .debounce(300)
                .distinctUntilChanged()
                .collect { q ->
                    search(q)
                }
        }
    }

    private suspend fun search(q: String) {
        val trimmed = q.trim()
        if (trimmed.isEmpty()) {
            _stationResults.value = emptyList()
            _trainResults.value = emptyList()
            _dbError.value = null
            _isSearching.value = false
            _uiState.value = SearchUiState.Idle
        } else {
            _isSearching.value = true
            _uiState.value = SearchUiState.Loading
            try {
                val stations = dao.searchStations(trimmed)
                val trains = dao.searchTrains(trimmed)
                _stationResults.value = stations
                _trainResults.value = trains
                _dbError.value = null
                _uiState.value = SearchUiState.Results(stations, trains)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e("SearchViewModel", "search failed", e)
                }
                val msg = mapDatabaseError(e)
                _dbError.value = msg
                _uiState.value = SearchUiState.Error(msg)
            } finally {
                _isSearching.value = false
            }
        }
    }

    /** Re-runs the current query (used by the Retry button after a DB error). */
    fun retrySearch() {
        viewModelScope.launch {
            search(_query.value)
        }
    }

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun clearQuery() {
        _query.value = ""
    }

    /**
     * Reloads tracked trains (bells) from `tracked_trains`.
     *
     * Refresh strategy: called from init AND on ON_RESUME (see SearchScreen)
     * because the bells are toggled on TrainDetail — a suspend getAll() is the
     * simplest reliable option (no list-all Flow exists on TrackingDao, only a
     * per-train observe()). Name resolution is one-shot per refresh: exact
     * train_number match via [TrainDao.searchTrains]; null when the GTFS
     * snapshot has no row for the number.
     */
    fun refreshTracked() {
        viewModelScope.launch {
            try {
                val rows = trackingDao.getAll()
                _tracked.value = rows.map { row ->
                    val name = dao.searchTrains(row.trainNumber)
                        .firstOrNull { it.trainNumber.equals(row.trainNumber, ignoreCase = true) }
                        ?.name
                    TrackedRow(trainNumber = row.trainNumber, trainName = name)
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e("SearchViewModel", "refreshTracked failed", e)
                }
                // Keep the last good list; the section simply doesn't update.
            }
        }
    }

    /**
     * Untracks a train: same persistence path as TrainDetail's bell
     * (TrackingDao.delete + worker stop), then refreshes the section.
     */
    fun untrack(trainNumber: String) {
        viewModelScope.launch {
            try {
                trackingDao.delete(trainNumber)
                com.trainkraft.app.LiveStatusNotificationWorker.stop(
                    getApplication(), trainNumber
                )
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e("SearchViewModel", "untrack failed", e)
                }
            } finally {
                refreshTracked()
            }
        }
    }

    private fun mapDatabaseError(e: Exception): String {
        // Never leak SQLite internals to UI.
        return "Timetable unavailable. Please try again."
    }
}

/** One bell row on home: number always present, name when GTFS resolves it. */
data class TrackedRow(
    val trainNumber: String,
    val trainName: String?,
)

/** Header label for the home tracked-trains section. Pure — unit tested. */
fun trackedSectionLabel(count: Int): String =
    if (count == 1) "Tracked trains (1)" else "Tracked trains ($count)"
