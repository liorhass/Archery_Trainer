package com.liorapps.archerytrainer.screens.video.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@Composable
fun FullScreenEffect(
    isFullScreen: Boolean,
) {
    val activity = LocalActivity.current
    LaunchedEffect(isFullScreen) {
        if (activity != null) {
            val window = activity.window
            val controller = WindowInsetsControllerCompat(window, window.decorView)

            if (isFullScreen) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

//class FullScreenController(
//    private val activity: Activity?,
//    val isFullScreen: Boolean,
//    val toggle: () -> Unit,
//    val enter: () -> Unit,
//    val exit: () -> Unit,
//)
//
//@Composable
//fun rememberFullScreenController(): FullScreenController {
////    val activity = LocalContext.current as Activity
//    val activity = LocalActivity.current
//    var isFullScreen by remember { mutableStateOf(false) }
//
//    LaunchedEffect(isFullScreen) {
//        if (activity != null) {
//            val window = activity.window
//            val controller = WindowInsetsControllerCompat(window, window.decorView)
//
//            if (isFullScreen) {
//                WindowCompat.setDecorFitsSystemWindows(window, false)
//                controller.hide(WindowInsetsCompat.Type.systemBars())
//                controller.systemBarsBehavior =
//                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
//            } else {
//                WindowCompat.setDecorFitsSystemWindows(window, true)
//                controller.show(WindowInsetsCompat.Type.systemBars())
//            }
//        }
//    }
//
//    return remember(isFullScreen) {
//        FullScreenController(
//            activity = activity,
//            isFullScreen = isFullScreen,
//            toggle = { isFullScreen = !isFullScreen },
//            enter = { isFullScreen = true },
//            exit  = { isFullScreen = false },
//        )
//    }
//}
