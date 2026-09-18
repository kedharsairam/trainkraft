package com.trainkraft.app.presentation

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trainkraft.app.LiveStatusNotificationWorker
import com.trainkraft.app.data.NtesApi
import com.trainkraft.app.data.NtesConfig
import com.trainkraft.app.data.ScheduleStop
import com.trainkraft.app.BuildConfig
import com.trainkraft.app.data.GtfsTime
import com.trainkraft.app.data.TrainDatabase
import com.trainkraft.app.data.TrainEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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

    private val _liveStatusJson = MutableStateFlow<String?>(null)
    val liveStatusJson: StateFlow<String?> = _liveStatusJson.asStateFlow()

    private val _liveError = MutableStateFlow<String?>(null)
    val liveError: StateFlow<String?> = _liveError.asStateFlow()

    private val _isLiveLoading = MutableStateFlow(false)
    val isLiveLoading: StateFlow<Boolean> = _isLiveLoading.asStateFlow()

    // --- Date picker support ---
    private val _selectedDate = MutableStateFlow<String?>(null)
    val selectedDate: StateFlow<String?> = _selectedDate.asStateFlow()

    // --- Average delay data ---
    private val _avgDelayJson = MutableStateFlow<String?>(null)
    val avgDelayJson: StateFlow<String?> = _avgDelayJson.asStateFlow()

    private val _isAvgDelayLoading = MutableStateFlow(false)
    val isAvgDelayLoading: StateFlow<Boolean> = _isAvgDelayLoading.asStateFlow()

    // --- Notification tracking ---
    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    fun hasNotificationPermission(): Boolean {
        val ctx = getApplication<Application>()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            ctx, android.Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun toggleTracking() {
        val ctx = getApplication<Application>()
        if (_isTracking.value) {
            LiveStatusNotificationWorker.stop(ctx, trainNumber)
            _isTracking.value = false
        } else {
            LiveStatusNotificationWorker.start(ctx, trainNumber)
            _isTracking.value = true
        }
    }

    init {
        loadSchedule()
    }

    fun loadSchedule() {
        viewModelScope.launch {
            _isLoading.value = true
            _dbError.value = null
            try {
                _schedule.value = dao.getTrainSchedule(trainNumber)
                _train.value = dao.searchTrains(trainNumber)
                    .firstOrNull { it.trainNumber.equals(trainNumber, ignoreCase = true) }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e("TrainDetailVM", "loadSchedule failed", e)
                }
                _dbError.value = "Timetable unavailable. Please try again."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun retry() = loadSchedule()

    fun setSelectedDate(date: String?) {
        _selectedDate.value = date
        // Re-fetch live status with the new date
        _liveStatusJson.value = null
        _liveError.value = null
        refreshLiveStatus()
    }

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
                val date = _selectedDate.value ?: dateFormat.format(Date()).uppercase(Locale.ENGLISH)
                val keys = NtesConfig.getKeys(getApplication())
                val apiResult = NtesApi.liveStatus(trimmed, date, keys)
                apiResult
                    .onSuccess { _liveStatusJson.value = it }
                    .onFailure { e ->
                        if (BuildConfig.DEBUG) {
                            Log.e("TrainDetailVM", "liveStatus failed", e)
                        }
                        _liveError.value = mapLiveError(e)
                    }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e("TrainDetailVM", "liveStatus exception", e)
                }
                _liveError.value = mapLiveError(e)
            } finally {
                _isLiveLoading.value = false
            }
        }
    }

    fun loadAvgDelay() {
        if (_isAvgDelayLoading.value) return
        viewModelScope.launch {
            _isAvgDelayLoading.value = true
            try {
                val keys = NtesConfig.getKeys(getApplication())
                val result = NtesApi.avgDelay(trainNumber.trim(), keys)
                result
                    .onSuccess { _avgDelayJson.value = it }
                    .onFailure { e ->
                        if (BuildConfig.DEBUG) {
                            Log.e("TrainDetailVM", "avgDelay failed", e)
                        }
                    }
            } finally {
                _isAvgDelayLoading.value = false
            }
        }
    }

    /** Build share text from train info + schedule. */
    fun buildShareText(): String {
        val t = _train.value
        val stops = _schedule.value
        val name = t?.name?.takeIf { it.isNotBlank() } ?: "Train $trainNumber"
        return buildString {
            appendLine("$name ($trainNumber)")
            if (stops.isNotEmpty()) {
                val first = stops.first()
                val last = stops.last()
                val depTime = first.depMin?.let { GtfsTime.format(it) } ?: "??:??"
                val arrTime = last.arrMin?.let { GtfsTime.format(it) } ?: "??:??"
                appendLine("${first.code} $depTime → ${last.code} $arrTime")
                appendLine("${stops.size} stops")
            }
            appendLine("Source: TrainKraft")
        }
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
