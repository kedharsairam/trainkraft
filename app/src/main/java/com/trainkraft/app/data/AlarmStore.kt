package com.trainkraft.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

private val Context.alarmDataStore by preferencesDataStore(name = "trainkraft_alarms")

/**
 * A trip the user wants a destination alarm for.
 *
 * v1 foundation: persistence only. Presence in the store == [active].
 * Geofencing / location triggering comes later.
 */
data class WatchedTrip(
    val trainNumber: String,
    val destinationStationCode: String,
    val active: Boolean = true,
)

/** Persists watched trips (trainNumber + destinationStationCode) in DataStore Preferences. */
object AlarmStore {

    private val KEY_WATCHED_TRIPS = stringSetPreferencesKey("watched_trips")

    private fun encode(trainNumber: String, stationCode: String): String =
        "${trainNumber.trim().uppercase()}|${stationCode.trim().uppercase()}"

    private fun decode(raw: String): WatchedTrip? {
        val parts = raw.split("|")
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) return null
        return WatchedTrip(
            trainNumber = parts[0],
            destinationStationCode = parts[1],
            active = true,
        )
    }

    fun watchedTripsFlow(context: Context): Flow<List<WatchedTrip>> =
        context.alarmDataStore.data.map { prefs ->
            (prefs[KEY_WATCHED_TRIPS] ?: emptySet()).mapNotNull(::decode)
        }

    fun isWatchingFlow(
        context: Context,
        trainNumber: String,
        stationCode: String,
    ): Flow<Boolean> {
        if (trainNumber.isBlank() || stationCode.isBlank()) return flowOf(false)
        val key = encode(trainNumber, stationCode)
        return context.alarmDataStore.data.map { prefs ->
            (prefs[KEY_WATCHED_TRIPS] ?: emptySet()).contains(key)
        }
    }

    suspend fun watch(context: Context, trainNumber: String, stationCode: String) {
        if (trainNumber.isBlank() || stationCode.isBlank()) return
        val key = encode(trainNumber, stationCode)
        context.alarmDataStore.edit { prefs ->
            prefs[KEY_WATCHED_TRIPS] = (prefs[KEY_WATCHED_TRIPS] ?: emptySet()) + key
        }
    }

    suspend fun unwatch(context: Context, trainNumber: String, stationCode: String) {
        if (trainNumber.isBlank() || stationCode.isBlank()) return
        val key = encode(trainNumber, stationCode)
        context.alarmDataStore.edit { prefs ->
            prefs[KEY_WATCHED_TRIPS] = (prefs[KEY_WATCHED_TRIPS] ?: emptySet()) - key
        }
    }
}
