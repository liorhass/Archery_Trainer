package com.liorapps.archerytrainer.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed class ATNavKey(
    val requiresSlideGestures: Boolean  // Set to true on screens that need the nav drawer to pass through the slide-on-screen gestures
) {
    @Serializable
    data object DelayedVideo : ATNavKey(requiresSlideGestures = true)

    @Serializable
    data object Settings : ATNavKey(requiresSlideGestures = false)

    // Screens that carry navigation data as part of the key
//    @Serializable data class ExampleWithItemDetail(val itemId: String) : NavKey(requiresSlideGestures = false)
}
