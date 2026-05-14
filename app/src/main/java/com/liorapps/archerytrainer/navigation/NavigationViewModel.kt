package com.liorapps.archerytrainer.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation3.runtime.NavBackStack
import timber.log.Timber

class NavigationViewModel(/*val backStack: NavBackStack<ATNavKey>*/) : ViewModel() {

    val backStack = NavBackStack<ATNavKey>(ATNavKey.ShootingSessionList)

    fun navigateTo(key: ATNavKey) {
        Timber.d("#### navigateTo() keyClassName=${key.javaClass.name}")
        backStack.add(key)
    }

    fun navigateBack() {
        Timber.d("#### navigateBack()")
        when {
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