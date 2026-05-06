package com.liorapps.archerytrainer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.liorapps.archerytrainer.ui.screens.ArcheryTrainerNavHost
import com.liorapps.archerytrainer.ui.theme.ArcheryTrainerTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        val settingsRepository = (application as ArcheryTrainerApplication).settingsRepository
        MainViewModelFactory(application, settingsRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Allow the app to draw behind system bars
        setContent {
            ArcheryTrainerTheme {
                ArcheryTrainerNavHost(viewModel)
            }
        }
    }
}

