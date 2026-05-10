package com.liorapps.archerytrainer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.liorapps.archerytrainer.navigation.NavigationViewModel
import com.liorapps.archerytrainer.navigation.ArcheryTrainerNavHost
import com.liorapps.archerytrainer.ui.theme.ArcheryTrainerTheme

class MainActivity : ComponentActivity() {
//    private val navigationViewModel: NavigationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Allow the app to draw behind system bars
        setContent {
            ArcheryTrainerTheme {
                ArcheryTrainerNavHost(/*navigationViewModel*/)
            }
        }
    }
}

