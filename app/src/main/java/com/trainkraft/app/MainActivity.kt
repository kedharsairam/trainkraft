package com.trainkraft.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kraft.ui.theme.KraftTheme

class MainActivity : ComponentActivity() {
    /**
     * Notification-tap deep link (train number from EXTRA_TRAIN_NUMBER).
     * Manifest declares NO launchMode (standard) → every notification tap
     * creates a NEW activity instance, so onCreate is the live path;
     * onNewIntent is handled too (no-op today) in case launchMode ever
     * becomes singleTop/singleTask.
     */
    private var deepLinkTrainNumber by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_TrainKraft)
        super.onCreate(savedInstanceState)
        deepLinkTrainNumber =
            parseDeepLinkTrainNumber(intent?.getStringExtra(TrackingService.EXTRA_TRAIN_NUMBER))
        enableEdgeToEdge()
        setContent {
            // Kraft Foundation theme (dark-only for TrainKraft).
            KraftTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TrainKraftNavHost(deepLinkTrainNumber = deepLinkTrainNumber)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseDeepLinkTrainNumber(intent.getStringExtra(TrackingService.EXTRA_TRAIN_NUMBER))
            ?.let { deepLinkTrainNumber = it }
    }
}
