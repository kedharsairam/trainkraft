package com.trainkraft.app.presentation

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trainkraft.app.data.BetweenResult
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

    private fun search() {
        val from = _fromStation.value ?: return
        val to = _toStation.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val weekday = LocalDate.now().dayOfWeek.value - 1
                val r = dao.getTrainsBetween(from.code, to.code, weekday)
                _results.value = r
                if (r.isEmpty()) {
                    _error.value = "No trains found between ${from.code} and ${to.code} today."
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e("BetweenVM", "search failed", e)
                }
                _error.value = "Search failed. Please try again."
            } finally {
                _isLoading.value = false
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
}
