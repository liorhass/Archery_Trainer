package com.liorapps.archerytrainer.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel

class NavigationViewModel : ViewModel() {

    val backStack = mutableStateListOf<NavKey>(NavKey.DelayedVideo)

    fun navigateTo(key: NavKey) {
        backStack.add(key)
    }

    fun navigateBack() {
        when {
//            _isFullScreen.value -> setIsFullScreen(false)
            backStack.size > 1  -> backStack.removeLastOrNull()
        }
    }


}