package com.liorapps.videotrainer

import androidx.camera.core.CameraSelector
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import com.liorapps.videotrainer.navigation.NavKey

class MainViewModel : ViewModel() {
    val backStack = mutableStateListOf<NavKey>(NavKey.Main)

    // Playback State
    var isPlaying by mutableStateOf(true)
        private set

    // Camera Selector State
    var cameraSelector by mutableStateOf(CameraSelector.DEFAULT_BACK_CAMERA)
        private set

    // Delay State (in seconds, 0-30)
    var delay by mutableIntStateOf(5)
        private set

    fun togglePlayback() {
        isPlaying = !isPlaying
    }

    fun switchCamera() {
        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    fun updateDelay(value: Int) {
        delay = value.coerceIn(0, 30)
    }

    fun navigateTo(key: NavKey) {
        backStack.add(key)
    }

    fun navigateBack(): Boolean {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.size - 1)
            return true
        }
        return false
    }
}
