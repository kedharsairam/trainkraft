package com.trainkraft.app

import android.app.Application
import com.trainkraft.app.data.NtesConfig

class TrainKraftApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NtesConfig.init(this)
    }
}
