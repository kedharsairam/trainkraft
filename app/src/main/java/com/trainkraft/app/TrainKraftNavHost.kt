package com.trainkraft.app

import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.trainkraft.app.presentation.BetweenScreen
import com.trainkraft.app.presentation.PnrScreen
import com.trainkraft.app.presentation.SearchScreen
import com.trainkraft.app.presentation.SettingsScreen
import com.trainkraft.app.presentation.StationBoardScreen
import com.trainkraft.app.presentation.TrainDetailScreen

/**
 * Apple-style navigation transitions:
 * - Root (Search): no animation (default)
 * - Detail pushes: slide in from right (300ms), slide out to left (300ms)
 * - Back pops: slide in from left (300ms), slide out to right (300ms)
 * - Settings: slide up from bottom (300ms), slide down on back
 */
private const val TRANSITION_DURATION = 300

private val slideInFromRight: EnterTransition = slideInHorizontally(
    initialOffsetX = { fullWidth -> fullWidth },
    animationSpec = tween(TRANSITION_DURATION),
) + fadeIn(animationSpec = tween(TRANSITION_DURATION))

private val slideOutToLeft: ExitTransition = slideOutHorizontally(
    targetOffsetX = { fullWidth -> -fullWidth / 3 },
    animationSpec = tween(TRANSITION_DURATION),
) + fadeOut(animationSpec = tween(TRANSITION_DURATION))

private val slideInFromLeft: EnterTransition = slideInHorizontally(
    initialOffsetX = { fullWidth -> -fullWidth / 3 },
    animationSpec = tween(TRANSITION_DURATION),
) + fadeIn(animationSpec = tween(TRANSITION_DURATION))

private val slideOutToRight: ExitTransition = slideOutHorizontally(
    targetOffsetX = { fullWidth -> fullWidth },
    animationSpec = tween(TRANSITION_DURATION),
) + fadeOut(animationSpec = tween(TRANSITION_DURATION))

private val slideUpFromBottom: EnterTransition = slideInHorizontally(
    initialOffsetX = { fullWidth -> fullWidth },
    animationSpec = tween(TRANSITION_DURATION),
) + fadeIn(animationSpec = tween(TRANSITION_DURATION))

private val slideDownToBottom: ExitTransition = slideOutHorizontally(
    targetOffsetX = { fullWidth -> fullWidth },
    animationSpec = tween(TRANSITION_DURATION),
) + fadeOut(animationSpec = tween(TRANSITION_DURATION))

object TrainKraftDestinations {
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val BETWEEN = "between"
    const val PNR = "pnr"

    const val TRAIN_DETAIL_ROUTE = "trainDetail/{trainNumber}"
    const val STATION_BOARD_ROUTE = "stationBoard/{stationCode}"

    private val TrainNumberRegex = Regex("^[0-9]{4,6}$")

    fun isValidTrainNumber(number: String): Boolean =
        TrainNumberRegex.matches(number.trim())

    fun isValidStationCode(code: String): Boolean =
        code.trim().isNotEmpty() && code.trim().length in 2..10 && code.trim().all { it.isLetterOrDigit() }

    fun trainDetail(trainNumber: String): String {
        val trimmed = trainNumber.trim()
        require(isValidTrainNumber(trimmed)) { "Invalid train number: $trainNumber" }
        return "trainDetail/${Uri.encode(trimmed)}"
    }

    fun stationBoard(stationCode: String): String {
        val trimmed = stationCode.trim().uppercase()
        require(isValidStationCode(trimmed)) { "Invalid station code: $stationCode" }
        return "stationBoard/${Uri.encode(trimmed)}"
    }
}

/**
 * Notification-tap extra → detail route input. Returns the trimmed train
 * number when valid, null otherwise (blank, malformed, or wrong shape —
 * the NavHost then stays on home). Unit-tested.
 */
fun parseDeepLinkTrainNumber(extra: String?): String? {
    val trimmed = extra?.trim().orEmpty()
    return if (trimmed.isNotEmpty() && TrainKraftDestinations.isValidTrainNumber(trimmed)) {
        trimmed
    } else {
        null
    }
}

@Composable
fun TrainKraftNavHost(deepLinkTrainNumber: String? = null) {
    val navController = rememberNavController()
    // Notification-tap routing: service/receiver notifications carry
    // EXTRA_TRAIN_NUMBER into MainActivity, which forwards it here. Mirrors
    // the existing search→detail navigation call (same route, launchSingleTop).
    androidx.compose.runtime.LaunchedEffect(deepLinkTrainNumber) {
        val number = deepLinkTrainNumber
        if (number != null && TrainKraftDestinations.isValidTrainNumber(number)) {
            navController.navigate(TrainKraftDestinations.trainDetail(number)) {
                launchSingleTop = true
            }
        }
    }
    NavHost(
        navController = navController,
        startDestination = TrainKraftDestinations.SEARCH,
    ) {
        // Root: Search — no transition (first screen)
        composable(
            route = TrainKraftDestinations.SEARCH,
            enterTransition = { fadeIn(animationSpec = tween(TRANSITION_DURATION)) },
            exitTransition = { slideOutToLeft },
            popEnterTransition = { slideInFromLeft },
            popExitTransition = { fadeOut(animationSpec = tween(TRANSITION_DURATION)) },
        ) {
            SearchScreen(
                onTrainClick = { number ->
                    if (TrainKraftDestinations.isValidTrainNumber(number)) {
                        navController.navigate(TrainKraftDestinations.trainDetail(number)) {
                            launchSingleTop = true
                        }
                    }
                },
                onStationClick = { code ->
                    if (TrainKraftDestinations.isValidStationCode(code)) {
                        navController.navigate(TrainKraftDestinations.stationBoard(code)) {
                            launchSingleTop = true
                        }
                    }
                },
                onBetweenClick = {
                    navController.navigate(TrainKraftDestinations.BETWEEN) {
                        launchSingleTop = true
                    }
                },
                onPnrClick = {
                    navController.navigate(TrainKraftDestinations.PNR) {
                        launchSingleTop = true
                    }
                },
                onSettingsClick = {
                    navController.navigate(TrainKraftDestinations.SETTINGS) {
                        launchSingleTop = true
                    }
                },
            )
        }
        // Detail: Train schedule — slide from right
        composable(
            route = TrainKraftDestinations.TRAIN_DETAIL_ROUTE,
            arguments = listOf(navArgument("trainNumber") { type = NavType.StringType }),
            enterTransition = { slideInFromRight },
            exitTransition = { slideOutToLeft },
            popEnterTransition = { slideInFromLeft },
            popExitTransition = { slideOutToRight },
        ) { backStackEntry ->
            val raw = backStackEntry.arguments?.getString("trainNumber").orEmpty()
            val trainNumber = Uri.decode(raw).trim()
            TrainDetailScreen(
                trainNumber = trainNumber,
                onBack = { navController.popBackStack() },
            )
        }
        // Detail: Station board — slide from right
        composable(
            route = TrainKraftDestinations.STATION_BOARD_ROUTE,
            arguments = listOf(navArgument("stationCode") { type = NavType.StringType }),
            enterTransition = { slideInFromRight },
            exitTransition = { slideOutToLeft },
            popEnterTransition = { slideInFromLeft },
            popExitTransition = { slideOutToRight },
        ) { backStackEntry ->
            val raw = backStackEntry.arguments?.getString("stationCode").orEmpty()
            val stationCode = Uri.decode(raw).trim().uppercase()
            StationBoardScreen(
                stationCode = stationCode,
                onBack = { navController.popBackStack() },
                onTrainClick = { number ->
                    if (TrainKraftDestinations.isValidTrainNumber(number)) {
                        navController.navigate(TrainKraftDestinations.trainDetail(number)) {
                            launchSingleTop = true
                        }
                    }
                },
            )
        }
        // Between stations — slide from right
        composable(
            route = TrainKraftDestinations.BETWEEN,
            enterTransition = { slideInFromRight },
            exitTransition = { slideOutToLeft },
            popEnterTransition = { slideInFromLeft },
            popExitTransition = { slideOutToRight },
        ) {
            BetweenScreen(
                onBack = { navController.popBackStack() },
                onTrainClick = { number ->
                    if (TrainKraftDestinations.isValidTrainNumber(number)) {
                        navController.navigate(TrainKraftDestinations.trainDetail(number)) {
                            launchSingleTop = true
                        }
                    }
                },
            )
        }
        // Settings: slide from right (consistent with push navigation)
        composable(
            route = TrainKraftDestinations.SETTINGS,
            enterTransition = { slideUpFromBottom },
            exitTransition = { slideDownToBottom },
            popEnterTransition = { slideInFromLeft },
            popExitTransition = { slideOutToRight },
        ) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onPnrClick = {
                    navController.navigate(TrainKraftDestinations.PNR) {
                        launchSingleTop = true
                    }
                },
            )
        }
        // PNR Status — slide from right
        composable(
            route = TrainKraftDestinations.PNR,
            enterTransition = { slideInFromRight },
            exitTransition = { slideOutToLeft },
            popEnterTransition = { slideInFromLeft },
            popExitTransition = { slideOutToRight },
        ) {
            PnrScreen(onBack = { navController.popBackStack() })
        }
    }
}
