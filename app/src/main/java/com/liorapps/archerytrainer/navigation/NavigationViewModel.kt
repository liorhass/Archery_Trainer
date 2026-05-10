package com.liorapps.archerytrainer.navigation

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.liorapps.archerytrainer.screens.settings.SettingsRepository
import com.liorapps.archerytrainer.screens.video.logic.DelayedVideoViewModel

class NavigationViewModel(/*val backStack: NavBackStack<ATNavKey>*/) : ViewModel() {

//    val backStack = mutableStateListOf<ATNavKey>(ATNavKey.DelayedVideo)
    val backStack = NavBackStack<ATNavKey>(ATNavKey.DelayedVideo)

    fun navigateTo(key: ATNavKey) {
        backStack.add(key)
    }

    fun navigateBack() {
        when {
//            _isFullScreen.value -> setIsFullScreen(false)
            backStack.size > 1  -> backStack.removeLastOrNull()
        }
    }

    // https://proandroiddev.com/rethinking-multi-backstack-the-case-for-a-segmented-single-stack-in-compose-navigation-3-fc5f9d747edb
    private fun <T : ATNavKey> NavBackStack<T>.bringToTop(targetRoot: T) {
        val targets = mutableListOf<T>()
        var target = false

        this.removeIf { key ->
            if (key.isNavigationRoot) target = (key == targetRoot)
            if (target) targets.add(key)
            target
        }

        this.addAll(targets.ifEmpty { listOf(targetRoot) })
    }
    private val <T : ATNavKey> NavBackStack<T>.currentRoot: T
        get() = this.last { it.isNavigationRoot }


    class Factory(
//        private val backStack: NavBackStack<ATNavKey>,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(NavigationViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return NavigationViewModel(/*backStack*/) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

}