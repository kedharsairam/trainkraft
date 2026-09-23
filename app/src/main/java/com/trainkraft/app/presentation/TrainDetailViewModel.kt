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
import com.trainkraft.app.data.DelayPriorEntity
import com.trainkraft.app.data.FogOverlayEntity
import com.trainkraft.app.data.GtfsTime
import com.trainkraft.app.data.LiveStatusDto
import com.trainkraft.app.data.LoadResult
import com.trainkraft.app.data.NtesFormats
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

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
    private val packDao = TrainDatabase.getInstance(application).packDao()
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

    // --- Prediction engine (Phase B; independent flow, silent-fail → null) ---
    // Priors/fog/vintage come from the local pack tables (empty when the pack
    // is absent — the engine handles that); predictions recompute whenever
    // liveStatus/priors/fog change. The timeline falls back to today's
    // server-ETA rendering whenever this flow is null — never blank.
    private val _priors = MutableStateFlow<Map<String, DelayPriorEntity>>(emptyMap())
    val priors: StateFlow<Map<String, DelayPriorEntity>> = _priors.asStateFlow()

    private val _fog = MutableStateFlow<FogOverlayEntity?>(null)

    /** Full vintage caption (`"delay data · Sep 2026"`); null = pack absent → hidden. */
    private val _packVintage = MutableStateFlow<String?>(null)
    val packVintage: StateFlow<String?> = _packVintage.asStateFlow()

    private val _predictions = MutableStateFlow<JourneyPrediction?>(null)
    val predictions: StateFlow<JourneyPrediction?> = _predictions.asStateFlow()

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
        // Pack rows load here; the pack file itself is bootstrapped once in
        // AppContainer.database — then recompute predictions on arrival.
        viewModelScope.launch {
            loadPackInputs()
            recomputePredictions()
        }
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
        _predictions.value = null
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
                        recomputePredictions()
                    }
                    is LoadResult.Offline -> {
                        _liveStatus.value = result.value
                        _liveCachedAgeMs.value = result.ageMs
                        recomputePredictions()
                    }
                    is LoadResult.Failed -> {
                        _liveError.value = mapLiveError(result.reason)
                        _predictions.value = null
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

    /**
     * Loads Phase B pack inputs (priors map, today's fog overlay, vintage
     * caption) from the local pack tables. Silent on every failure: a missing
     * pack leaves the defaults (empty map, null fog, hidden caption) and the
     * engine handles pack-absent runs itself.
     */
    private suspend fun loadPackInputs() {
        try {
            val number = trainNumber.trim()
            val rows = packDao.priorsForTrain(number)
            _priors.value = rows.associateBy { it.stationCode.uppercase(Locale.ENGLISH) }
            _fog.value = packDao.fogForTrain(number, istTodayYMD())
            _packVintage.value = packVintageCaption(packDao.meta("generatedAt"))
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.d("TrainDetailVM", "pack inputs unavailable: ${e.message}")
            }
            _priors.value = emptyMap()
            _fog.value = null
            _packVintage.value = null
        }
    }

    /**
     * Recomputes [predictions] from the current live status + pack inputs.
     * Independent flow: every failure (no live data, pre-departure / completed
     * run, unparseable server clock, engine throw) resolves to null and the UI
     * keeps today's server-ETA rendering.
     *
     * Derivation (VM-side inputs per contract):
     * - anchorIndex = [currentStopIndex] over ISA/ISD flags (null anchor, or an
     *   anchor at the last stop with no future rows, means no call).
     * - anchorDelayMin = anchor departure DDEP ?: arrival DARR ?: header LDEL.
     * - anchorAgeMin = now minus the server LTIME event stamp ([anchorAgeMinutes]);
     *   null when LTIME is unparseable → null predictions, never guessed.
     * - elapsedMin = anchorAgeMin (LTIME is the only event-time proxy the
     *   server gives us); schedSegMin = anchor STD → next STD in minutes
     *   ([schedSegmentMinutes], DF day flags honored), null when unparseable
     *   → the engine hides position, never guesses.
     * - priors = pack map (empty when pack absent); fog = today's overlay (or
     *   null); todayYMD = IST calendar date (`YYYY-MM-DD`, matching the fog
     *   table's lexicographic range compare).
     */
    private fun recomputePredictions() {
        try {
            val live = _liveStatus.value ?: run {
                _predictions.value = null
                return
            }
            val stops = live.stops
            if (stops.isEmpty()) {
                _predictions.value = null
                return
            }
            val anchor = currentStopIndex(
                stops.map { it.arrived },
                stops.map { it.departed },
            ) ?: run {
                _predictions.value = null
                return
            }
            if (anchor >= stops.lastIndex) {
                // Completed run: no future rows for the engine to correct.
                _predictions.value = null
                return
            }
            val anchorStop = stops[anchor]
            val anchorDelay = anchorStop.departureDelayMinutes()
                ?: anchorStop.arrivalDelayMinutes()
                ?: live.delayMin
            val anchorAge = anchorAgeMinutes(live.lastUpdateTime, System.currentTimeMillis())
                ?: run {
                    _predictions.value = null
                    return
                }
            val next = stops[anchor + 1]
            val schedSeg = schedSegmentMinutes(
                anchorStop.scheduledDeparture, anchorStop.dayFlag,
                next.scheduledDeparture, next.dayFlag,
            )
            _predictions.value = predictJourney(
                stops, anchor, anchorDelay, anchorAge.toLong(),
                _priors.value, _fog.value, istTodayYMD(), anchorAge.toLong(), schedSeg,
            )
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.d("TrainDetailVM", "predictJourney failed: ${e.message}")
            }
            _predictions.value = null
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

// ------------------------------------------------- Phase B pure helpers
// Top-level (no Android dependencies beyond java.time) so they stay unit-
// testable on the JVM. They encode every VM-side derivation decision for the
// prediction-engine contract; the ViewModel only wires flows around them.

private val IST_ZONE: ZoneId = ZoneId.of("Asia/Kolkata")

/** IST calendar date in fog-table shape (`"2026-09-23"`); same zone as [ntesTodayLabel]. */
fun istTodayYMD(today: LocalDate = LocalDate.now(IST_ZONE)): String =
    today.format(DateTimeFormatter.ISO_LOCAL_DATE)

private val ltimeParser: DateTimeFormatter = DateTimeFormatterBuilder()
    .parseCaseInsensitive()
    .appendPattern("d-MMM-yyyy H:mm")
    .toFormatter(Locale.ENGLISH)

/**
 * Whole minutes from the server LTIME event stamp (`"23-Sep-2026 15:00"`,
 * case-insensitive month) to [nowMs], interpreted in IST like every other
 * NTES clock in the app. Null when [lTimeRaw] is blank or unparseable (the
 * caller nulls predictions — staleness is never guessed); future stamps from
 * clock skew clamp to 0.
 */
fun anchorAgeMinutes(
    lTimeRaw: String?,
    nowMs: Long,
    zone: ZoneId = IST_ZONE,
): Int? {
    val raw = lTimeRaw?.trim().orEmpty()
    if (raw.isEmpty()) return null
    return try {
        val eventMs = LocalDateTime.parse(raw, ltimeParser).atZone(zone)
            .toInstant().toEpochMilli()
        (((nowMs - eventMs) / 60_000).toInt()).coerceAtLeast(0)
    } catch (_: Exception) {
        null
    }
}

/**
 * Scheduled minutes for the anchor → next segment from STD clocks + DF day
 * flags (both via [NtesFormats.hhmmToMinutes]). Null when either clock is
 * unparseable, or when the result is negative (data quirk) — the engine then
 * hides position instead of interpolating on fiction.
 */
fun schedSegmentMinutes(
    anchorStd: String?,
    anchorDf: Int,
    nextStd: String?,
    nextDf: Int,
): Int? {
    val anchor = NtesFormats.hhmmToMinutes(anchorStd) ?: return null
    val next = NtesFormats.hhmmToMinutes(nextStd) ?: return null
    val seg = (next + nextDf * 1440) - (anchor + anchorDf * 1440)
    return if (seg < 0) null else seg
}

/**
 * Engine-wins rule for one future row: a prediction exists with confidence ≥
 * MED, the server delay is known, and the two differ by ≥ 2 min. Anything
 * else keeps today's server-ETA rendering unchanged.
 */
fun shouldShowEnginePrediction(prediction: StopPrediction?, serverDelayMin: Int?): Boolean {
    if (prediction == null || serverDelayMin == null) return false
    if (prediction.confidence != PredictionConfidence.HIGH &&
        prediction.confidence != PredictionConfidence.MED
    ) {
        return false
    }
    return abs(prediction.predictedDelayMin - serverDelayMin) >= 2
}

/** Literal basis-chip vocabulary (muted in UI; freshness honesty: always adjacent when engine drives). */
fun basisChipLabel(basis: PredictionBasis): String = when (basis) {
    PredictionBasis.PATTERN -> "typical pattern"
    PredictionBasis.CARRIED -> "carried"
    PredictionBasis.SCHEDULE -> "timetable"
}

/**
 * Engine clock for a future row: scheduled minutes-of-day + predicted delay,
 * wrapped to 0..1439. Null when the schedule base is unknown (caller keeps
 * the server rendering). The UI formats the result with the user's 24h pref.
 */
fun engineExpMinutes(schedMin: Int?, predictedDelayMin: Int): Int? {
    if (schedMin == null) return null
    return (((schedMin + predictedDelayMin) % 1440) + 1440) % 1440
}

/**
 * Per-future-stop priors chip (AvgDelayFootnote vocabulary family).
 * Null when no pack row exists for the stop (no chip, never invented);
 * `<= 0` means the pack says typically on time.
 */
fun priorChipLabel(avgMin: Int?): String? = when {
    avgMin == null -> null
    avgMin <= 0 -> "typically on time here"
    else -> "typically +$avgMin here"
}

/**
 * Position-marker row label. Null when [positionKm] is null (row hidden —
 * position is never guessed). Tilde is mandatory per contract.
 */
fun positionMarkerLabel(positionKm: Int?, between: Pair<String, String>?): String? {
    if (positionKm == null) return null
    val leg = between?.let { (a, b) ->
        if (a.isNotBlank() && b.isNotBlank()) " · between $a and $b" else ""
    }.orEmpty()
    return "~$positionKm km$leg"
}

private val vintageMonthNames = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)
private val vintageYmRegex = Regex("""(\d{4})-(\d{2})""")

/**
 * Pack-vintage caption from `pack_meta.generatedAt` (pipeline UTC ISO,
 * `"2026-09-23T12:34:56Z"`): `"delay data · Sep 2026"`. Null when [generatedAt]
 * is null/blank/unparseable — the caption hides, never shows "unknown".
 */
fun packVintageCaption(generatedAt: String?): String? {
    val raw = generatedAt?.trim().orEmpty()
    if (raw.isEmpty()) return null
    val match = vintageYmRegex.find(raw) ?: return null
    val year = match.groupValues[1].toIntOrNull() ?: return null
    val month = match.groupValues[2].toIntOrNull() ?: return null
    if (month !in 1..12) return null
    return "delay data · ${vintageMonthNames[month - 1]} $year"
}
