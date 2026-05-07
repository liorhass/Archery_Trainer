package com.liorapps.archerytrainer.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed class NavKey(
    val requiresSlideGestures: Boolean  // Set to true on screens that need the nav drawer to pass through the slide-on-screen gestures
) {
    @Serializable
    data object DelayedVideo : NavKey(requiresSlideGestures = true)

    @Serializable
    data object Settings : NavKey(requiresSlideGestures = false)

    // Screens that carry navigation data as part of the key
//    @Serializable data class ExampleWithItemDetail(val itemId: String) : NavKey(requiresSlideGestures = false)
}
