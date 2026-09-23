package com.trainkraft.app.di

import android.content.Context
import com.trainkraft.app.LiveStatusNotificationWorker
import com.trainkraft.app.data.CacheDao
import com.trainkraft.app.data.NtesConfig
import com.trainkraft.app.data.NtesRepository
import com.trainkraft.app.data.ResponseCache
import com.trainkraft.app.data.SettingsStore
import com.trainkraft.app.data.TrackingDao
import com.trainkraft.app.data.TrainDao
import com.trainkraft.app.data.TrainDatabase
import com.trainkraft.app.data.UserDatabase

/**
 * Manual DI container — constructor-injected, no annotation processing
 * (keeps the toolchain light; swap with a fake container in tests).
 * Held by [com.trainkraft.app.TrainKraftApp]; ViewModels resolve it via
 * `(app as TrainKraftApp).container`.
 *
 * Everything is lazy: nothing touches the DB (asset pre-population!) on the
 * Application.onCreate critical path.
 *
 * Two-database wiring (privacy split, Sep 2026): static GTFS timetable reads
 * come from [TrainDatabase] (`trains.db`, backup-excluded, re-seeds from the
 * bundled asset), while per-user state — followed trains and the offline
 * response cache — comes from [UserDatabase] (`user.db`, never backed up).
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    val database: TrainDatabase by lazy {
        // Touch first so the file (and Room's master table) already exists
        // when trains.db's 3 -> 4 migration ATTACHes it for the one-shot copy.
        userDatabase
        TrainDatabase.getInstance(appContext)
    }

    /**
     * Opened before [database] on first use (see its initializer) so the file
     * (and Room's master table) already exists when `trains.db`'s 3 -> 4
     * migration ATTACHes it for the one-shot user-row copy.
     */
    val userDatabase: UserDatabase by lazy { UserDatabase.getInstance(appContext) }

    val trainDao: TrainDao get() = database.trainDao()

    /** Served from `user.db` — never leaves the device (backup-excluded). */
    val trackingDao: TrackingDao get() = userDatabase.trackingDao()

    /** Served from `user.db` — never leaves the device (backup-excluded). */
    val cacheDao: CacheDao get() = userDatabase.cacheDao()

    val responseCache: ResponseCache by lazy {
        ResponseCache(cacheDao) { SettingsStore.cacheDurationMs(appContext) }
    }

    val ntesRepository: NtesRepository by lazy {
        NtesRepository(responseCache) { NtesConfig.getKeys(appContext) }
    }

    /** Re-enqueues background notification workers for persisted tracked trains. */
    fun restoreTrackedWorkers() {
        LiveStatusNotificationWorker.restoreTracked(appContext)
    }
}
