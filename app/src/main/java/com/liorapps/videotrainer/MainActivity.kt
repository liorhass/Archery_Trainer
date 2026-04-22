package com.liorapps.videotrainer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.liorapps.videotrainer.navigation.NavKey
import com.liorapps.videotrainer.ui.screens.mainscreen.MainScreen
import com.liorapps.videotrainer.ui.screens.SettingsScreen
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
                VideoTrainerApp(viewModel)
            }
        }
    }
}

@Composable
fun VideoTrainerApp(viewModel: MainViewModel) {
    BackHandler(enabled = viewModel.backStack.size > 1) {
        viewModel.navigateBack()
    }

    NavDisplay(
        backStack = viewModel.backStack,
        modifier = Modifier.fillMaxSize(),
        onBack = { viewModel.navigateBack() }
    ) { key: NavKey ->
        when (key) {
            NavKey.Main -> NavEntry(key) {
                MainScreen(
                    onNavigateToSettings = { viewModel.navigateTo(NavKey.Settings) }
                )
            }
            NavKey.Settings -> NavEntry(key) {
                SettingsScreen(
                    onNavigateBack = { viewModel.navigateBack() }
                )
            }
        }
    }
}
