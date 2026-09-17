package com.trainkraft.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.trainkraft.app.presentation.SearchScreen
import com.trainkraft.app.presentation.TrainDetailScreen

object TrainKraftDestinations {
    const val SEARCH = "search"
    const val TRAIN_DETAIL = "trainDetail"
    const val STATION_BOARD = "stationBoard"
    const val SETTINGS = "settings"

    const val TRAIN_DETAIL_ROUTE = "trainDetail/{trainNumber}"
    const val STATION_BOARD_ROUTE = "stationBoard/{stationCode}"

    fun trainDetail(trainNumber: String) = "trainDetail/$trainNumber"
    fun stationBoard(stationCode: String) = "stationBoard/$stationCode"
}

@Composable
fun TrainKraftNavHost() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = TrainKraftDestinations.SEARCH
    ) {
        composable(TrainKraftDestinations.SEARCH) {
            SearchScreen(
                onTrainClick = { number ->
                    navController.navigate(TrainKraftDestinations.trainDetail(number))
                },
                onStationClick = { code ->
                    navController.navigate(TrainKraftDestinations.stationBoard(code))
                },
            )
        }
        composable(
            route = TrainKraftDestinations.TRAIN_DETAIL_ROUTE,
            arguments = listOf(navArgument("trainNumber") { type = NavType.StringType })
        ) { backStackEntry ->
            val trainNumber = backStackEntry.arguments?.getString("trainNumber").orEmpty()
            TrainDetailScreen(
                trainNumber = trainNumber,
                onBack = { navController.popBackStack() },
                // Live Status (NTES) wiring comes later; no-op for now.
                onLiveStatus = {},
            )
        }
        composable(
            route = TrainKraftDestinations.STATION_BOARD_ROUTE,
            arguments = listOf(navArgument("stationCode") { type = NavType.StringType })
        ) { backStackEntry ->
            val stationCode = backStackEntry.arguments?.getString("stationCode").orEmpty()
            PlaceholderScreen("Station $stationCode")
        }
        composable(TrainKraftDestinations.SETTINGS) {
            PlaceholderScreen("Settings")
        }
    }
}

@Composable
private fun PlaceholderScreen(label: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label)
    }
}
