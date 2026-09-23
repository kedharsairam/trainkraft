package com.trainkraft.app.data

/**
 * Result of a repository load:
 *  - [Live]    — fresh network data, strictly parsed
 *  - [Offline] — network failed/unusable; served from cache (UI must label it)
 *  - [Failed]  — no usable network or cache; show [Failed.reason]
 */
sealed interface LoadResult<out T> {
    data class Live<T>(val value: T) : LoadResult<T>
    data class Offline<T>(val value: T, val ageMs: Long) : LoadResult<T>
    data class Failed(val reason: String) : LoadResult<Nothing>
}

/**
 * NTES data access: network-first with strict-parse + cache-fallback pipeline.
 *
 * Flow per call:
 *  0. cache fresher than [ResponseCache.MIN_AGE_SKIP_MS] → Live (absorbs
 *     rapid screen re-entry / auto-refresh churn without touching the network)
 *  1. network → strict decode → cache raw → Live
 *  2. network failure OR schema drift → cached raw within the user's
 *     cache-duration window → Offline(age)
 *  3. otherwise → Failed(reason)
 *
 * All subServices share one endpoint/crypto ([NtesApi]); cache keys are per
 * request shape (e.g. `live:12952:23-SEP-2026`, `btw:NDLS:MMCT`).
 */
class NtesRepository(
    private val cache: ResponseCache,
    private val keys: suspend () -> NtesKeys = { NtesKeys() },
) {

    private suspend fun <T> cachedLoad(
        cacheKey: String,
        network: suspend (NtesKeys) -> Result<String>,
        parse: (String) -> T,
    ): LoadResult<T> {
        // 0. Very fresh cache → serve as live.
        cache.get(cacheKey)?.let { hit ->
            runCatching { parse(hit.json) }.getOrNull()?.let { return LoadResult.Live(it) }
        }

        // 1. Network → strict parse → cache.
        val apiResult = runCatching { network(keys()) }
            .getOrElse { return fallback(cacheKey, parse) ?: fail(it.message) }
        val raw = apiResult.getOrElse { return fallback(cacheKey, parse) ?: fail(it.message) }
        val parsed = runCatching { parse(raw) }.getOrElse { drift ->
            return fallback(cacheKey, parse)
                ?: LoadResult.Failed("Response format changed (${drift.message ?: "schema"})")
        }
        cache.put(cacheKey, raw)
        return LoadResult.Live(parsed)
    }

    /** Cached raw response within the fallback window, parsed — or null. */
    private suspend fun <T> fallback(
        cacheKey: String,
        parse: (String) -> T,
    ): LoadResult.Offline<T>? {
        val hit = cache.get(cacheKey, forFallback = true) ?: return null
        val parsed = runCatching { parse(hit.json) }.getOrNull() ?: return null
        return LoadResult.Offline(parsed, hit.ageMs)
    }

    private fun fail(message: String?): LoadResult.Failed =
        LoadResult.Failed(message ?: "Live data unavailable")

    // ------------------------------------------------------------- endpoints

    suspend fun liveStatus(trainNumber: String, date: String): LoadResult<LiveStatusDto> =
        cachedLoad("live:$trainNumber:$date", { NtesApi.liveStatus(trainNumber, date, it) }) {
            NtesJson.decode(it)
        }

    suspend fun avgDelay(trainNumber: String): LoadResult<AvgDelayDto> =
        cachedLoad("avg:$trainNumber", { NtesApi.avgDelay(trainNumber, it) }) {
            NtesJson.decode(it)
        }

    suspend fun stationLive(stationCode: String, hours: Int): LoadResult<StationLiveDto> =
        cachedLoad("stn:$stationCode:$hours", { NtesApi.trainsAtStation(stationCode, hours, it) }) {
            NtesJson.decode(it)
        }

    suspend fun trainsBetween(fromCode: String, toCode: String): LoadResult<BetweenTrainsDto> =
        cachedLoad("btw:$fromCode:$toCode", { NtesApi.trainsBetween(fromCode, toCode, keys = it) }) {
            NtesJson.decode(it)
        }

    suspend fun schedule(trainNumber: String, date: String): LoadResult<TrainScheduleDto> =
        cachedLoad("sch:$trainNumber:$date", { NtesApi.trainSchedule(trainNumber, date, it) }) {
            NtesJson.decode(it)
        }

    suspend fun findTrain(query: String): LoadResult<FindTrainDto> =
        cachedLoad("find:$query", { NtesApi.findTrain(query, it) }) {
            NtesJson.decode(it)
        }

    suspend fun trainInstance(trainNumber: String): LoadResult<TrainInstanceDto> =
        cachedLoad("inst:$trainNumber", { NtesApi.trainInstance(trainNumber, it) }) {
            NtesJson.decode(it)
        }

    suspend fun trainExceptions(trainNumber: String): LoadResult<TrainExcpDto> =
        cachedLoad("exc:$trainNumber", { NtesApi.trainExceptions(trainNumber, it) }) {
            NtesJson.decode(it)
        }
}
