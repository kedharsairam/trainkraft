package com.trainkraft.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.kraft.ui.theme.KraftTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_TrainKraft)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Kraft Foundation theme (dark-only for TrainKraft).
            KraftTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TrainKraftNavHost()
                }
            }
        }
    }
}
