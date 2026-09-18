package com.trainkraft.app.presentation

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
        val departures: List<com.trainkraft.app.data.StationDeparture>,
    ) : StationBoardUiState()
    data class Error(val message: String) : StationBoardUiState()
}

sealed class BetweenUiState {
    data object Idle : BetweenUiState()
    data object Loading : BetweenUiState()
    data class Results(val results: List<com.trainkraft.app.data.BetweenResult>) : BetweenUiState()
    data class Error(val message: String) : BetweenUiState()
}
