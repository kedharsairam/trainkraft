package com.trainkraft.app.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Room-backed response cache (offline fallback for NTES calls).
 *
 * Semantics: network is always attempted first for live data; on failure the
 * cached raw response is served if it is younger than the user's
 * `cache_duration_hours` setting (the previously dead setting, now wired).
 * Fresh entries also absorb redundant refetches within [MIN_AGE_SKIP_MS]
 * (rapid screen re-entry / auto-refresh churn).
 */
class ResponseCache(
    private val dao: CacheDao,
    private val maxAgeMsProvider: suspend () -> Long,
) {

    companion object {
        /** Cached responses fresher than this skip the network entirely. */
        const val MIN_AGE_SKIP_MS = 30_000L
    }

    data class Hit(val json: String, val fetchedAt: Long) {
        val ageMs: Long get() = System.currentTimeMillis() - fetchedAt
    }

    /** Returns the cached entry when usable: fresh-enough to skip network, or
     *  any age when [forFallback] (serving after a network failure). */
    suspend fun get(key: String, forFallback: Boolean = false): Hit? {
        val entry = dao.get(key) ?: return null
        val age = System.currentTimeMillis() - entry.fetchedAt
        val maxAge = maxAgeMsProvider()
        return when {
            age <= MIN_AGE_SKIP_MS -> Hit(entry.json, entry.fetchedAt)
            forFallback && age <= maxAge -> Hit(entry.json, entry.fetchedAt)
            else -> null
        }
    }

    suspend fun put(key: String, json: String) {
        dao.put(CachedResponseEntity(key, json, System.currentTimeMillis()))
        // Opportunistic prune: drop entries older than the fallback window.
        val cutoff = System.currentTimeMillis() - maxAgeMsProvider()
        dao.prune(cutoff)
    }
}
