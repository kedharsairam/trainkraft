package com.trainkraft.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Room-backed response cache (offline fallback for NTES calls). */
@Dao
interface CacheDao {

    @Query("SELECT * FROM cached_responses WHERE cacheKey = :key")
    suspend fun get(key: String): CachedResponseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: CachedResponseEntity)

    /** Drops entries older than [olderThanEpochMs]; called opportunistically. */
    @Query("DELETE FROM cached_responses WHERE fetchedAt < :olderThanEpochMs")
    suspend fun prune(olderThanEpochMs: Long)
}
