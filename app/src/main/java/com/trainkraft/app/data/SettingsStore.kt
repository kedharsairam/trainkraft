package com.trainkraft.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "trainkraft_settings")

/** App settings backed by DataStore Preferences (dark-only app, no proto needed). */
object SettingsStore {

    private val KEY_AUTO_REFRESH_LIVE = booleanPreferencesKey("auto_refresh_live")
    private val KEY_CACHE_DURATION_HOURS = intPreferencesKey("cache_duration_hours")
    private val KEY_USE_24H = booleanPreferencesKey("use_24h")

    const val DEFAULT_AUTO_REFRESH_LIVE = true
    const val DEFAULT_CACHE_DURATION_HOURS = 24
    const val DEFAULT_USE_24H = true

    /** Selectable cache durations, in hours (168 = 7 days). */
    val CACHE_DURATION_OPTIONS = listOf(1, 6, 24, 168)

    fun cacheDurationLabel(hours: Int): String = when (hours) {
        1 -> "1 hour"
        6 -> "6 hours"
        24 -> "24 hours"
        168 -> "7 days"
        else -> "$hours hours"
    }

    fun autoRefreshLiveFlow(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { prefs ->
            prefs[KEY_AUTO_REFRESH_LIVE] ?: DEFAULT_AUTO_REFRESH_LIVE
        }

    fun cacheDurationHoursFlow(context: Context): Flow<Int> =
        context.settingsDataStore.data.map { prefs ->
            prefs[KEY_CACHE_DURATION_HOURS] ?: DEFAULT_CACHE_DURATION_HOURS
        }

    /**
     * One-shot read of the offline-fallback window, in ms. This wires the
     * previously dead `cache_duration_hours` setting into [ResponseCache].
     */
    suspend fun cacheDurationMs(context: Context): Long =
        cacheDurationHoursFlow(context).first() * 60L * 60L * 1000L

    suspend fun setAutoRefreshLive(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_AUTO_REFRESH_LIVE] = enabled
        }
    }

    suspend fun setCacheDurationHours(context: Context, hours: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_CACHE_DURATION_HOURS] = hours
        }
    }

    fun use24hFlow(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { prefs ->
            prefs[KEY_USE_24H] ?: DEFAULT_USE_24H
        }

    suspend fun setUse24h(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_USE_24H] = enabled
        }
    }
}
