package com.trainkraft.app.presentation

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trainkraft.app.LiveStatusNotificationWorker
import com.trainkraft.app.TrainKraftApp
import com.trainkraft.app.BuildConfig
import com.trainkraft.app.data.AvgDelayDto
import com.trainkraft.app.data.GtfsTime
import com.trainkraft.app.data.LiveStatusDto
import com.trainkraft.app.data.LoadResult
import com.trainkraft.app.data.ScheduleStop
import com.trainkraft.app.data.TrackedTrainEntity
import com.trainkraft.app.data.TrainDatabase
import com.trainkraft.app.data.TrainEntity
import com.trainkraft.app.data.TrainExcpDto
import com.trainkraft.app.data.TrainInstanceDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Train detail: offline GTFS schedule (official NTES schedule as fallback for
 * trains missing from the snapshot), typed live status via [NtesRepository],
 * and persisted tracking rows that drive the background notification worker.
 */
class TrainDetailViewModel(
    application: Application,
    val trainNumber: String,
) : AndroidViewModel(application) {

    companion object {
        private val TRAIN_NUMBER_REGEX = Regex("^[0-9]{4,6}$")
    }

    private val dao = TrainDatabase.getInstance(application).trainDao()
    private val container = (application as? TrainKraftApp)?.container
    private val repo = container?.ntesRepository
    private val trackingDao = container?.trackingDao

    private val _schedule = MutableStateFlow<List<ScheduleStop>>(emptyList())
    val schedule: StateFlow<List<ScheduleStop>> = _schedule.asStateFlow()

    private val _train = MutableStateFlow<TrainEntity?>(null)
    val train: StateFlow<TrainEntity?> = _train.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _dbError = MutableStateFlow<String?>(null)
    val dbError: StateFlow<String?> = _dbError.asStateFlow()

    /** True when the schedule came from official NTES (GTFS snapshot had no data). */
    private val _isOfficialSchedule = MutableStateFlow(false)
    val isOfficialSchedule: StateFlow<Boolean> = _isOfficialSchedule.asStateFlow()

    // --- Live status (typed; see NtesRepository.LoadResult) ---
    private val _liveStatus = MutableStateFlow<LiveStatusDto?>(null)
    val liveStatus: StateFlow<LiveStatusDto?> = _liveStatus.asStateFlow()

    /** Non-null when live status was served from cache (offline fallback). */
    private val _liveCachedAgeMs = MutableStateFlow<Long?>(null)
    val liveCachedAgeMs: StateFlow<Long?> = _liveCachedAgeMs.asStateFlow()

    private val _liveError = MutableStateFlow<String?>(null)
    val liveError: StateFlow<String?> = _liveError.asStateFlow()

    private val _isLiveLoading = MutableStateFlow(false)
    val isLiveLoading: StateFlow<Boolean> = _isLiveLoading.asStateFlow()

    // --- Date picker support ---
    private val _selectedDate = MutableStateFlow<String?>(null)
    val selectedDate: StateFlow<String?> = _selectedDate.asStateFlow()

    // --- Run instances + service exceptions (independent sidecars) ---
    // Loaded alongside live status but on separate flows that silent-fail:
    // a failed instance/exception call must NEVER block the main timeline.
    private val _instances = MutableStateFlow<TrainInstanceDto?>(null)
    val instances: StateFlow<TrainInstanceDto?> = _instances.asStateFlow()

    private val _exceptions = MutableStateFlow<TrainExcpDto?>(null)
    val exceptions: StateFlow<TrainExcpDto?> = _exceptions.asStateFlow()

    // --- Average delay (typed) ---
    private val _avgDelay = MutableStateFlow<AvgDelayDto?>(null)
    val avgDelay: StateFlow<AvgDelayDto?> = _avgDelay.asStateFlow()

    /** Minimal surfacing when average-delay data can't be loaded. */
    private val _avgDelayError = MutableStateFlow<String?>(null)
    val avgDelayError: StateFlow<String?> = _avgDelayError.asStateFlow()

    private val _isAvgDelayLoading = MutableStateFlow(false)
    val isAvgDelayLoading: StateFlow<Boolean> = _isAvgDelayLoading.asStateFlow()

    // --- Notification tracking (persisted; see TrackingDao) ---
    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private val _uiState = MutableStateFlow<TrainDetailUiState>(TrainDetailUiState.Loading)
    val uiState: StateFlow<TrainDetailUiState> = _uiState.asStateFlow()

    init {
        // Bell state follows the persisted row: survives process death and
        // flips off automatically when the worker completes the journey.
        val td = trackingDao
        if (td != null) {
            viewModelScope.launch {
                td.observe(trainNumber).collect { _isTracking.value = it != null }
            }
        }
        loadSchedule()
        loadSidecar()
    }

    fun hasNotificationPermission(): Boolean {
        val ctx = getApplication<Application>()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun toggleTracking() {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            if (_isTracking.value) {
                trackingDao?.delete(trainNumber)
                LiveStatusNotificationWorker.stop(ctx, trainNumber)
                _isTracking.value = false
            } else {
                trackingDao?.upsert(
                    TrackedTrainEntity(
                        trainNumber = trainNumber,
                        trackedAt = System.currentTimeMillis(),
                    ),
                )
                LiveStatusNotificationWorker.start(ctx, trainNumber)
                _isTracking.value = true
            }
        }
    }

    fun loadSchedule() {
        viewModelScope.launch {
            _isLoading.value = true
            _dbError.value = null
            _uiState.value = TrainDetailUiState.Loading
            try {
                var stops = dao.getTrainSchedule(trainNumber)
                var train = dao.searchTrains(trainNumber)
                    .firstOrNull { it.trainNumber.equals(trainNumber, ignoreCase = true) }
                _isOfficialSchedule.value = false

                if (stops.isEmpty()) {
                    // GTFS snapshot gap (train introduced after Aug 2026):
                    // fall back to the official NTES schedule.
                    val dto = when (val s = repo?.schedule(trainNumber.trim(), "")
                        ?: LoadResult.Failed("offline")) {
                        is LoadResult.Live -> s.value
                        is LoadResult.Offline -> s.value
                        is LoadResult.Failed -> null
                    }
                    if (dto != null && dto.stations.isNotEmpty()) {
                        stops = dto.stations.map { st ->
                            ScheduleStop(
                                tripId = "official-${st.seq}",
                                seq = st.seq,
                                stopId = st.code,
                                code = st.code,
                                name = st.name,
                                arrMin = st.arrMinutes(),
                                depMin = st.depMinutes(),
                                dayOffset = st.dayOffset,
                            )
                        }
                        if (train == null && dto.trainName.isNotBlank()) {
                            train = TrainEntity(
                                routeId = "official-$trainNumber",
                                trainNumber = trainNumber,
                                name = dto.trainName,
                            )
                        }
                        _isOfficialSchedule.value = true
                    }
                }

                _schedule.value = stops
                _train.value = train
                _uiState.value = TrainDetailUiState.Loaded(stops, train)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e("TrainDetailVM", "loadSchedule failed", e)
                }
                val msg = "Timetable unavailable. Please try again."
                _dbError.value = msg
                _uiState.value = TrainDetailUiState.Error(msg)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun retry() = loadSchedule()

    /**
     * Recent runs (`vInstanceList`: start date, run state, position,
     * exception message) plus service exceptions (cancellations/diversions).
     * Independent of the live timeline: every failure mode resolves to null
     * and the UI hides the dependent strip/banner instead of erroring.
     */
    fun loadSidecar() {
        viewModelScope.launch {
            val r = repo ?: return@launch
            when (val res = r.trainInstance(trainNumber.trim())) {
                is LoadResult.Live -> _instances.value = res.value
                is LoadResult.Offline -> _instances.value = res.value
                is LoadResult.Failed -> _instances.value = null
            }
            when (val res = r.trainExceptions(trainNumber.trim())) {
                is LoadResult.Live -> _exceptions.value = res.value
                is LoadResult.Offline -> _exceptions.value = res.value
                is LoadResult.Failed -> _exceptions.value = null
            }
        }
    }

    fun setSelectedDate(date: String?) {
        _selectedDate.value = date
        // Re-fetch live status with the new date
        _liveStatus.value = null
        _liveCachedAgeMs.value = null
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
                if (!TRAIN_NUMBER_REGEX.matches(trimmed)) {
                    _liveError.value = "Invalid train number"
                    return@launch
                }
                val r = repo
                if (r == null) {
                    _liveError.value = "Live status unavailable. Please try again."
                    return@launch
                }
                val date = _selectedDate.value ?: ntesToday()
                when (val result = r.liveStatus(trimmed, date)) {
                    is LoadResult.Live -> {
                        _liveStatus.value = result.value
                        _liveCachedAgeMs.value = null
                    }
                    is LoadResult.Offline -> {
                        _liveStatus.value = result.value
                        _liveCachedAgeMs.value = result.ageMs
                    }
                    is LoadResult.Failed -> {
                        _liveError.value = mapLiveError(result.reason)
                    }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e("TrainDetailVM", "liveStatus exception", e)
                }
                _liveError.value = mapLiveError(e.message ?: "")
            } finally {
                _isLiveLoading.value = false
                // Best-effort sidecar retry: still silent, still never blocks.
                if (_instances.value == null || _exceptions.value == null) loadSidecar()
            }
        }
    }

    fun loadAvgDelay() {
        if (_isAvgDelayLoading.value) return
        viewModelScope.launch {
            _isAvgDelayLoading.value = true
            _avgDelayError.value = null
            try {
                val r = repo ?: return@launch
                val result = r.avgDelay(trainNumber.trim())
                val value = when (result) {
                    is LoadResult.Live -> result.value
                    is LoadResult.Offline -> result.value
                    is LoadResult.Failed -> null
                }
                if (value != null) {
                    _avgDelay.value = value
                } else {
                    // Section renders a one-line note instead of vanishing silently.
                    _avgDelayError.value = "Average delay unavailable right now."
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e("TrainDetailVM", "avgDelay exception", e)
                }
                _avgDelayError.value = "Average delay unavailable right now."
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

    private fun ntesToday(): String =
        SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }.format(Date()).uppercase(Locale.ENGLISH)

    private fun mapLiveError(reason: String): String {
        val msg = reason.lowercase()
        return when {
            "keys not loaded" in msg ->
                "Keys not loaded. Connect to the internet once to initialize, then try again."
            "response format changed" in msg ->
                "Live data format changed on the server. Please try again later."
            "unable to resolve host" in msg || "timeout" in msg || "network" in msg ||
                "unavailable" in msg ->
                "Network unavailable. Check your connection and try again."
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
