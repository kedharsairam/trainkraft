package com.trainkraft.app.presentation

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trainkraft.app.data.StationEntity
import com.trainkraft.app.BuildConfig
import com.trainkraft.app.data.TrainDatabase
import com.trainkraft.app.data.TrainEntity
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Search state: debounced (300ms) station + train lookup.
 *
 * Follows Kalc/WallKraft MVVM: UI collects StateFlows, all DB work stays
 * in [viewModelScope].
 */
class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = TrainDatabase.getInstance(application).trainDao()

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

    @OptIn(FlowPreview::class)
    val hasResults: StateFlow<Boolean> = combine(_stationResults, _trainResults) { s, t ->
        s.isNotEmpty() || t.isNotEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
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
        } else {
            _isSearching.value = true
            try {
                _stationResults.value = dao.searchStations(trimmed)
                _trainResults.value = dao.searchTrains(trimmed)
                _dbError.value = null
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e("SearchViewModel", "search failed", e)
                }
                _dbError.value = mapDatabaseError(e)
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

    private fun mapDatabaseError(e: Exception): String {
        // Never leak SQLite internals to UI.
        return "Timetable unavailable. Please try again."
    }
}
