package com.liorapps.archerytrainer.ui.screens.mainscreen

import android.content.Context
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext


@Composable
fun ScreenOrientationEffect(isFullScreen: Boolean, updateRotation: (Int) -> Unit) {
    val context = LocalContext.current
    val screenRotationDegrees = remember(LocalConfiguration.current) {
        computeScreenRotation(context)
    }

    LaunchedEffect(LocalConfiguration.current, isFullScreen) {
        updateRotation(screenRotationDegrees)
    }
}

fun computeScreenRotation(context: Context): Int {
    val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.display
    } else {
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
    }
    val screenRotationDegrees = when (display.rotation) {
        Surface.ROTATION_0 -> 0
        Surface.ROTATION_90 -> 90
        Surface.ROTATION_180 -> 180
        else -> 270  // Assume it's Surface.ROTATION_270
    }
    return screenRotationDegrees
}

