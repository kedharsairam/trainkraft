package com.trainkraft.app.di

import android.content.Context
import com.trainkraft.app.LiveStatusNotificationWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.trainkraft.app.data.CacheDao
import com.trainkraft.app.data.NtesConfig
import com.trainkraft.app.data.NtesRepository
import com.trainkraft.app.data.PackBootstrap
import com.trainkraft.app.data.ResponseCache
import com.trainkraft.app.data.SettingsStore
import com.trainkraft.app.data.TrackingDao
import com.trainkraft.app.data.TrainDao
import com.trainkraft.app.data.TrainDatabase
import com.trainkraft.app.data.UserDataMigrator
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

    private val bootstrapScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: TrainDatabase by lazy {
        // Touch first so the user.db file exists for later opens.
        userDatabase
        // v3 user-data rescue BEFORE Room opens trains.db: plain framework
        // SQLite in autocommit (ATTACH inside Room's migration transaction
        // throws under WAL mode on-device — never again). Silent + idempotent.
        UserDataMigrator.migrateV3UserTables(appContext)
        // Cold-start reconciliation: no minute presence survives process
        // death, so clear all Go-live flags (rows stay = baseline follows
        // continue on the worker; the pill re-arms on next Go-live tap).
        bootstrapScope.launch {
            runCatching { userDatabase.trackingDao().clearAllLiveTracking() }
        }
        val instance = TrainDatabase.getInstance(appContext)
        // Pack bootstrap lives here (not in VMs): single process-once trigger
        // at the earliest DB-touching point, off the Activity init path.
        // Fire-and-forget: ensureImported is silent + idempotent, and the
        // lazy initializer cannot suspend.
        bootstrapScope.launch {
            try {
                PackBootstrap.ensureImported(appContext, instance)
            } catch (_: Exception) {
                // ensureImported never throws; belt-and-braces to protect init.
            }
        }
        instance
    }

    /**
     * Opened before [database] on first use (see its initializer) so the file
     * exists for the v3 user-row rescue ([UserDataMigrator]) and later opens.
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
