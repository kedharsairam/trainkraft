package com.trainkraft.app

import android.app.Application
import android.util.Log
import com.trainkraft.app.data.NtesConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TrainKraftApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        NtesConfig.init(this)
        // Pre-fetch NTES keys in background so they're ready when the user
        // first taps live status. getKeys() writes to memory + file cache.
        scope.launch {
            try {
                NtesConfig.getKeys(this@TrainKraftApp)
            } catch (e: Exception) {
                Log.d("TrainKraftApp", "Key pre-fetch failed (will retry on demand): ${e.message}")
            }
        }
    }
}
