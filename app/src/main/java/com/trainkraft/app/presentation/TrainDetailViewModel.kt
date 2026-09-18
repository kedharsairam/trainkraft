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
import java.util.TimeZone

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

    private val _dbError = MutableStateFlow<String?>(null)
    val dbError: StateFlow<String?> = _dbError.asStateFlow()

    /** Raw NTES live-status JSON (v1: unparsed), null until first success. */
    private val _liveStatusJson = MutableStateFlow<String?>(null)
    val liveStatusJson: StateFlow<String?> = _liveStatusJson.asStateFlow()

    /** Last live-status failure message, null when no error. */
    private val _liveError = MutableStateFlow<String?>(null)
    val liveError: StateFlow<String?> = _liveError.asStateFlow()

    private val _isLiveLoading = MutableStateFlow(false)
    val isLiveLoading: StateFlow<Boolean> = _isLiveLoading.asStateFlow()

    init {
        loadSchedule()
    }

    /** Loads the offline schedule; re-called by Retry after a DB error. */
    fun loadSchedule() {
        viewModelScope.launch {
            _isLoading.value = true
            _dbError.value = null
            try {
                _schedule.value = dao.getTrainSchedule(trainNumber)
                _train.value = dao.searchTrains(trainNumber)
                    .firstOrNull { it.trainNumber.equals(trainNumber, ignoreCase = true) }
                    ?: dao.searchTrains(trainNumber).firstOrNull()
            } catch (e: Exception) {
                if (com.trainkraft.app.BuildConfig.DEBUG) {
                    android.util.Log.e("TrainDetailVM", "loadSchedule failed", e)
                }
                _dbError.value = mapTimetableError(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun retry() = loadSchedule()

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
                val trimmed = trainNumber.trim()
                if (!Regex("^[0-9]{4,6}$").matches(trimmed)) {
                    _liveError.value = "Invalid train number"
                    return@launch
                }
                val dateFormat = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH).apply {
                    timeZone = TimeZone.getTimeZone("Asia/Kolkata")
                }
                val date = dateFormat.format(Date()).uppercase(Locale.ENGLISH)
                val keys = NtesConfig.getKeys(getApplication())
                val apiResult = NtesApi.liveStatus(trimmed, date, keys)
                apiResult
                    .onSuccess { _liveStatusJson.value = it }
                    .onFailure { e ->
                        if (com.trainkraft.app.BuildConfig.DEBUG) {
                            android.util.Log.e("TrainDetailVM", "liveStatus failed", e)
                        }
                        _liveError.value = mapLiveError(e)
                    }
            } catch (e: Exception) {
                if (com.trainkraft.app.BuildConfig.DEBUG) {
                    android.util.Log.e("TrainDetailVM", "liveStatus exception", e)
                }
                _liveError.value = mapLiveError(e)
            } finally {
                _isLiveLoading.value = false
            }
        }
    }

    private fun mapTimetableError(e: Exception): String {
        // Never surface raw DB internals.
        return "Timetable unavailable. Please try again."
    }

    private fun mapLiveError(e: Throwable): String {
        val msg = e.message?.lowercase().orEmpty()
        return when {
            e is java.io.IOException || "unable to resolve host" in msg || "timeout" in msg || "network" in msg ->
                "Network unavailable. Check your connection and try again."
            "server error" in msg || "http" in msg ->
                "Live status unavailable. Please try again later."
            msg.isBlank() -> "Live status unavailable. Please try again."
            msg.length > 120 -> "Live status unavailable. Please try again."
            else -> "Live status unavailable. Please try again."
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
