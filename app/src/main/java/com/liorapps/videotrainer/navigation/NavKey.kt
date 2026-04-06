package com.liorapps.videotrainer.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed class NavKey {
    @Serializable
    data object Main : NavKey()

    @Serializable
    data object Settings : NavKey()
}
