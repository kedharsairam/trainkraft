package com.trainkraft.app.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trainkraft.app.data.NtesApi
import com.trainkraft.app.data.NtesConfig
import com.trainkraft.app.data.ScheduleStop
import com.trainkraft.app.data.TrainDatabase
import com.trainkraft.app.data.TrainEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Loads the full timetable ([ScheduleStop] list, ordered by seq) for one
 * train number, plus the [TrainEntity] header row.
 */
class TrainDetailViewModel(
    application: Application,
    val trainNumber: String,
) : AndroidViewModel(application) {

    private val dao = TrainDatabase.getInstance(application).trainDao()

    private val _schedule = MutableStateFlow<List<ScheduleStop>>(emptyList())
    val schedule: StateFlow<List<ScheduleStop>> = _schedule.asStateFlow()

    private val _train = MutableStateFlow<TrainEntity?>(null)
    val train: StateFlow<TrainEntity?> = _train.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Raw NTES live-status JSON (v1: unparsed), null until first success. */
    private val _liveStatusJson = MutableStateFlow<String?>(null)
    val liveStatusJson: StateFlow<String?> = _liveStatusJson.asStateFlow()

    /** Last live-status failure message, null when no error. */
    private val _liveError = MutableStateFlow<String?>(null)
    val liveError: StateFlow<String?> = _liveError.asStateFlow()

    private val _isLiveLoading = MutableStateFlow(false)
    val isLiveLoading: StateFlow<Boolean> = _isLiveLoading.asStateFlow()

    init {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _schedule.value = dao.getTrainSchedule(trainNumber)
                _train.value = dao.searchTrains(trainNumber)
                    .firstOrNull { it.trainNumber.equals(trainNumber, ignoreCase = true) }
                    ?: dao.searchTrains(trainNumber).firstOrNull()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Fetches live running status for today (DD-MMM-YYYY). Failures surface
     * via [liveError]; the offline [schedule] is always left untouched so it
     * stays visible as the fallback layer.
     */
    fun refreshLiveStatus() {
        if (_isLiveLoading.value) return
        viewModelScope.launch {
            _isLiveLoading.value = true
            _liveError.value = null
            try {
                val date = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)
                    .format(Date())
                    .uppercase(Locale.ENGLISH)
                val keys = NtesConfig.getKeys(getApplication())
                val result = NtesApi.liveStatus(trainNumber, date, keys)
                result
                    .onSuccess { _liveStatusJson.value = it }
                    .onFailure { e ->
                        _liveError.value = e.message ?: "Live status failed"
                    }
            } catch (e: Exception) {
                _liveError.value = e.message ?: "Live status failed"
            } finally {
                _isLiveLoading.value = false
            }
        }
    }

    class Factory(
        private val application: Application,
        private val trainNumber: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TrainDetailViewModel(application, trainNumber) as T
        }
    }
}
