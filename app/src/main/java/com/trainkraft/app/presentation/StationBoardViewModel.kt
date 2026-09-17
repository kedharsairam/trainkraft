package com.trainkraft.app.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trainkraft.app.data.StationDeparture
import com.trainkraft.app.data.StationEntity
import com.trainkraft.app.data.TrainDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Offline station board: station header + departures for today.
 *
 * Weekday follows the DAO convention: `DayOfWeek.value - 1` (0 = Mon).
 * Date-range / afterMin filtering is skipped (null) so the board shows
 * the full weekday timetable; callers that know "today" can narrow it.
 */
class StationBoardViewModel(
    application: Application,
    val stationCode: String,
) : AndroidViewModel(application) {

    private val dao = TrainDatabase.getInstance(application).trainDao()

    private val _station = MutableStateFlow<StationEntity?>(null)
    val station: StateFlow<StationEntity?> = _station.asStateFlow()

    private val _departures = MutableStateFlow<List<StationDeparture>>(emptyList())
    val departures: StateFlow<List<StationDeparture>> = _departures.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _dbError = MutableStateFlow<String?>(null)
    val dbError: StateFlow<String?> = _dbError.asStateFlow()

    init {
        loadBoard()
    }

    /** Loads the station board; re-called by Retry after a DB error. */
    fun loadBoard() {
        viewModelScope.launch {
            _isLoading.value = true
            _dbError.value = null
            try {
                // No dedicated getStationByCode in the DAO; exact-match first
                // via the LIKE search (same pattern as TrainDetailViewModel).
                val matches = dao.searchStations(stationCode)
                _station.value = matches.firstOrNull {
                    it.code.equals(stationCode, ignoreCase = true)
                } ?: matches.firstOrNull()
                val weekday = LocalDate.now().dayOfWeek.value - 1
                _departures.value = dao.getStationBoard(stationCode, weekday)
            } catch (e: Exception) {
                _dbError.value = e.message ?: "Database error"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun retry() = loadBoard()

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
