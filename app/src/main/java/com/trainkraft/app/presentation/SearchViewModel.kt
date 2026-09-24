package com.trainkraft.app.presentation

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trainkraft.app.TrainKraftApp
import com.trainkraft.app.data.LiveStatusDto
import com.trainkraft.app.data.LoadResult
import com.trainkraft.app.data.SettingsStore
import com.trainkraft.app.data.StationEntity
import com.trainkraft.app.BuildConfig
import com.trainkraft.app.data.TrainDatabase
import com.trainkraft.app.data.UserDatabase
import com.trainkraft.app.data.TrainEntity
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Search state: debounced (300ms) station + train lookup.
 *
 * Follows Kalc/WallKraft MVVM: UI collects StateFlows, all DB work stays
 * in [viewModelScope].
 */
class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = TrainDatabase.getInstance(application).trainDao()
    private val trackingDao = UserDatabase.getInstance(application).trackingDao()
    private val repo = (application as? TrainKraftApp)?.container?.ntesRepository

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

    /**
     * Recent searches (DataStore, max 10, most-recent-first). Collected once
     * here so saves/clears re-emit automatically; the section shows only when
     * the query box is blank and hides entirely when empty.
     */
    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    /**
     * Live enrichment for the tracked section, keyed by train number. Base
     * rows publish immediately; each entry lands as its `liveStatus` call
     * resolves. Absent key = still loading or silently failed → the card
     * falls back to the number+name row, never blocks, never invents.
     */
    private val _liveSummaries = MutableStateFlow<Map<String, TrackedLiveSummary>>(emptyMap())
    val liveSummaries: StateFlow<Map<String, TrackedLiveSummary>> = _liveSummaries.asStateFlow()

    private var liveEnrichJob: Job? = null

    init {
        refreshTracked()
        viewModelScope.launch {
            SettingsStore.recentSearchesFlow(getApplication()).collect { _recentSearches.value = it }
        }
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
     * Records one query into recent-search history. Called ONLY on explicit
     * submit (IME Search) or result tap — never per keystroke, so prefixes
     * typed mid-query don't flood the list. Dedup (case-insensitive) + cap
     * live in [addRecentSearch] via [SettingsStore.saveRecentSearch].
     */
    fun recordRecentSearch(raw: String) {
        val query = raw.trim()
        if (query.isEmpty()) return
        viewModelScope.launch {
            runCatching { SettingsStore.saveRecentSearch(getApplication(), query) }
        }
    }

    /** Clears the whole history (single Clear-all row; no per-item delete). */
    fun clearRecentSearches() {
        viewModelScope.launch {
            runCatching { SettingsStore.clearRecentSearches(getApplication()) }
        }
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
                val base = rows.map { row ->
                    val name = dao.searchTrains(row.trainNumber)
                        .firstOrNull { it.trainNumber.equals(row.trainNumber, ignoreCase = true) }
                        ?.name
                    TrackedRow(trainNumber = row.trainNumber, trainName = name)
                }
                // Base rows publish immediately — the section never waits on
                // the network; live enrichment lands incrementally below.
                _tracked.value = base
                val numbers = base.map { it.trainNumber }.toSet()
                if (_liveSummaries.value.keys.any { it !in numbers }) {
                    _liveSummaries.value = _liveSummaries.value.filterKeys { it in numbers }
                }
                enrichTrackedLive(base.map { it.trainNumber })
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e("SearchViewModel", "refreshTracked failed", e)
                }
                // Keep the last good list; the section simply doesn't update.
            }
        }
    }

    /**
     * One `liveStatus(number, today)` call per tracked train (2-3 typical),
     * sequential, each silent-failing to the base-row fallback. Runs after
     * the base rows publish so enrichment never blocks the section.
     */
    private fun enrichTrackedLive(numbers: List<String>) {
        liveEnrichJob?.cancel()
        val r = repo ?: return
        if (numbers.isEmpty()) return
        liveEnrichJob = viewModelScope.launch {
            val date = ntesTodayLabel()
            for (number in numbers.distinct()) {
                try {
                    val dto = when (val res = r.liveStatus(number, date)) {
                        is LoadResult.Live -> res.value
                        is LoadResult.Offline -> res.value
                        is LoadResult.Failed -> null
                    }
                    val summary = dto?.let { trackedLiveSummaryFrom(it) }
                    if (summary != null) {
                        _liveSummaries.value = _liveSummaries.value + (number to summary)
                    }
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) {
                        Log.e("SearchViewModel", "tracked live failed: ${e.message}")
                    }
                    // Per-train silent fail → card keeps its base row.
                }
            }
        }
    }

    private fun ntesTodayLabel(): String =
        SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }.format(Date()).uppercase(Locale.ENGLISH)

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
                _liveSummaries.value = _liveSummaries.value - trainNumber
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

/**
 * Live enrichment for one tracked home card: the NTES status line plus the
 * delay minutes for [DelayChip] and the journey progress % (null when the
 * payload carries no total distance — the card then shows no progress,
 * never a guessed one). Pure — unit tested.
 */
data class TrackedLiveSummary(
    val statusText: String,
    val delayMin: Int,
    val progressPercent: Int?,
)

/**
 * Maps a live-status payload to its home-card summary. Null when the payload
 * carries no status line (blank [LiveStatusDto.statusText]) — the card falls
 * back to the number+name row instead of showing an empty summary.
 * Pure — unit tested.
 */
fun trackedLiveSummaryFrom(dto: LiveStatusDto): TrackedLiveSummary? {
    val status = dto.statusText.trim()
    if (status.isEmpty()) return null
    val progress = if (dto.totalDistance > 0) dto.progressPercent() else null
    return TrackedLiveSummary(
        statusText = status,
        delayMin = dto.delayMin,
        progressPercent = progress,
    )
}

/**
 * TalkBack label for one tracked card: number + name, then the live status
 * and delay when enrichment has landed. Pure — unit tested.
 */
fun trackedCardDescription(row: TrackedRow, live: TrackedLiveSummary?): String {
    val base = if (row.trainName.isNullOrBlank()) row.trainNumber
    else "${row.trainNumber} ${row.trainName}"
    if (live == null) return base
    return "$base, ${live.statusText}, delay ${formatDelay(live.delayMin)}"
}
