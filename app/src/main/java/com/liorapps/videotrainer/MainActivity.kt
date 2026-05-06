package com.liorapps.videotrainer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.liorapps.videotrainer.ui.screens.ArcheryTrainerNavHost
import com.liorapps.videotrainer.ui.theme.VideoTrainerTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        val settingsRepository = (application as VideoTrainerApplication).settingsRepository
        MainViewModelFactory(application, settingsRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Allow the app to draw behind system bars
        setContent {
            VideoTrainerTheme {
                ArcheryTrainerNavHost(viewModel)
            }
        }
    }
}

