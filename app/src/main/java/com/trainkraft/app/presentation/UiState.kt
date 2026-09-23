package com.trainkraft.app.presentation

/**
 * Where displayed data came from — drives the honesty badges.
 * LIVE = fresh NTES network response; CACHED = NTES served from the response
 * cache after a network failure; OFFLINE = bundled GTFS snapshot.
 */
enum class DataSource { LIVE, CACHED, OFFLINE }

/** Compact age label: "45s ago" / "12m ago" / "3h ago" / "2d ago". */
fun formatAge(ageMs: Long): String {
    val s = (ageMs / 1000).coerceAtLeast(0)
    return when {
        s < 60 -> "${s}s ago"
        s < 3600 -> "${s / 60}m ago"
        s < 86_400 -> "${s / 3600}h ago"
        else -> "${s / 86_400}d ago"
    }
}

/**
 * One station-board row — offline GTFS trip or live NTES entry (merged).
 * [platform], [delayMin] and [cancelled] are only populated from live data.
 */
data class BoardRow(
    val key: String,
    val trainNumber: String,
    val trainName: String,
    val depMin: Int?,
    val dayOffset: Int,
    val destCode: String?,
    val destName: String?,
    /** Platform from live NTES, if known. */
    val platform: String? = null,
    /** Departure delay in minutes; null = unknown, 0 = right time. */
    val delayMin: Int? = null,
    val cancelled: Boolean = false,
)

sealed class SearchUiState {
    data object Idle : SearchUiState()
    data object Loading : SearchUiState()
    data class Results(
        val stations: List<com.trainkraft.app.data.StationEntity>,
        val trains: List<com.trainkraft.app.data.TrainEntity>,
    ) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}

sealed class TrainDetailUiState {
    data object Loading : TrainDetailUiState()
    data class Loaded(
        val schedule: List<com.trainkraft.app.data.ScheduleStop>,
        val train: com.trainkraft.app.data.TrainEntity?,
    ) : TrainDetailUiState()
    data class Error(val message: String) : TrainDetailUiState()
}

sealed class StationBoardUiState {
    data object Loading : StationBoardUiState()
    data class Loaded(
        val station: com.trainkraft.app.data.StationEntity?,
        val departures: List<BoardRow>,
    ) : StationBoardUiState()
    data class Error(val message: String) : StationBoardUiState()
}

sealed class BetweenUiState {
    data object Idle : BetweenUiState()
    data object Loading : BetweenUiState()
    data class Results(val results: List<com.trainkraft.app.data.BetweenResult>) : BetweenUiState()
    data class Error(val message: String) : BetweenUiState()
}
