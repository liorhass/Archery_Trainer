package com.liorapps.videotrainer.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed class NavKey(
    val requiresSlideGestures: Boolean  // Set to true on screens that need the nav drawer to pass through the slide-on-screen gestures
) {
    @Serializable
    data object DelayedVideo : NavKey(requiresSlideGestures = true)

    @Serializable
    data object Settings : NavKey(requiresSlideGestures = false)
}
