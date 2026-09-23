package com.trainkraft.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One cached NTES/PRS response keyed by a stable request key
 * (e.g. `live:12952:23-SEP-2026`, `between:NDLS:MMCT`).
 *
 * Serves as the offline fallback: network is always attempted first; on
 * failure any entry younger than the user's cache-duration setting is
 * returned instead of an error (the README's "graceful fallback" promise).
 * [fetchedAt] is epoch ms of the successful network fetch.
 */
@Entity(tableName = "cached_responses")
data class CachedResponseEntity(
    @PrimaryKey val cacheKey: String,
    val json: String,
    val fetchedAt: Long,
)
