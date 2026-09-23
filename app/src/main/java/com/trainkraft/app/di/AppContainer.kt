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

/**
 * Manual DI container — constructor-injected, no annotation processing
 * (keeps the toolchain light; swap with a fake container in tests).
 * Held by [com.trainkraft.app.TrainKraftApp]; ViewModels resolve it via
 * `(app as TrainKraftApp).container`.
 *
 * Everything is lazy: nothing touches the DB (asset pre-population!) on the
 * Application.onCreate critical path.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    val database: TrainDatabase by lazy { TrainDatabase.getInstance(appContext) }

    val trainDao: TrainDao get() = database.trainDao()
    val trackingDao: TrackingDao get() = database.trackingDao()
    val cacheDao: CacheDao get() = database.cacheDao()

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
