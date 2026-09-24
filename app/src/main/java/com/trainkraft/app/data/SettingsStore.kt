package com.trainkraft.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
    /** First-Go-live onboarding sheet shown (Phase C live tracking). */
    private val KEY_LIVE_TRACKING_ONBOARDING_SHOWN = booleanPreferencesKey("live_tracking_onboarding_shown")

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

    /** First-Go-live onboarding sheet shown-flag (default false = show once). */
    fun liveTrackingOnboardingShownFlow(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { prefs ->
            prefs[KEY_LIVE_TRACKING_ONBOARDING_SHOWN] ?: false
        }

    suspend fun setLiveTrackingOnboardingShown(context: Context, shown: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_LIVE_TRACKING_ONBOARDING_SHOWN] = shown
        }
    }

    // ------------------------------------------------------- recent searches
    // Same preferencesDataStore file ("trainkraft_settings") — no new store:
    // history is small (≤10 short strings), read once per SearchViewModel init,
    // written only on explicit submit/result-tap (never per keystroke).

    private val KEY_RECENT_SEARCHES = stringPreferencesKey("recent_searches")

    /** Max stored queries: most-recent-first, case-insensitive dedup on save. */
    const val MAX_RECENT_SEARCHES = 10


    fun recentSearchesFlow(context: Context): Flow<List<String>> =
        context.settingsDataStore.data.map { prefs ->
            decodeRecentSearches(prefs[KEY_RECENT_SEARCHES].orEmpty())
        }

    suspend fun saveRecentSearch(context: Context, raw: String) {
        val query = raw.trim()
        if (query.isEmpty()) return
        context.settingsDataStore.edit { prefs ->
            val updated = addRecentSearch(
                decodeRecentSearches(prefs[KEY_RECENT_SEARCHES].orEmpty()),
                query,
                MAX_RECENT_SEARCHES,
            )
            prefs[KEY_RECENT_SEARCHES] = encodeRecentSearches(updated)
        }
    }

    suspend fun clearRecentSearches(context: Context) {
        context.settingsDataStore.edit { prefs ->
            prefs.remove(KEY_RECENT_SEARCHES)
        }
    }
}

/** Unit-separator join: queries never contain it; preserves order. */
private const val RECENT_SEPARATOR = "\u001F"

/**
 * Prepends [query] most-recent-first, dropping any existing entry that
 * matches case-insensitively, capping at [max]. Blank queries are ignored.
 * Pure — unit tested.
 */
fun addRecentSearch(
    existing: List<String>,
    query: String,
    max: Int = SettingsStore.MAX_RECENT_SEARCHES,
): List<String> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return existing
    return (listOf(trimmed) + existing.filterNot { it.equals(trimmed, ignoreCase = true) })
        .take(max.coerceAtLeast(0))
}

/** Order-preserving encode for the single-string DataStore value. Pure. */
fun encodeRecentSearches(items: List<String>): String = items.joinToString(RECENT_SEPARATOR)

/** Inverse of [encodeRecentSearches]; drops blanks. Pure — unit tested. */
fun decodeRecentSearches(raw: String): List<String> {
    if (raw.isEmpty()) return emptyList()
    return raw.split(RECENT_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }
}
