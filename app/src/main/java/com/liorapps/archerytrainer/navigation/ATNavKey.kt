package com.liorapps.archerytrainer.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import kotlinx.serialization.Serializable

@Serializable
sealed class ATNavKey(
    val isNavigationRoot: Boolean,
    val requiresSlideGestures: Boolean,  // Set to true on screens that need the nav drawer to pass through the slide-on-screen gestures
) : NavKey  {
    @Serializable
    data object ShootingSessionList : ATNavKey(isNavigationRoot = true, requiresSlideGestures = false)

    @Serializable
    data object DelayedVideo : ATNavKey(isNavigationRoot = true, requiresSlideGestures = true)

    @Serializable
    data object Settings : ATNavKey(isNavigationRoot = true, requiresSlideGestures = false)

    @Serializable
    data class EditShootingSession(val sessionId: Long = -1) : ATNavKey(isNavigationRoot = false, requiresSlideGestures = false)

    @Serializable
    data object ImportExport : ATNavKey(isNavigationRoot = true, requiresSlideGestures = false)

    @Serializable
    data object About : ATNavKey(isNavigationRoot = true, requiresSlideGestures = false)

    // Screens that carry navigation data as part of the key
//    @Serializable data class ExampleWithItemDetail(val itemId: String) : NavKey(requiresSlideGestures = false)
}

//@OptIn(ExperimentalSerializationApi::class)
//@Composable
//fun getATBackStack() : NavBackStack<ATNavKey> {
//    // 1. Create a serialization configuration so the OS knows about your custom keys
//    val navConfig = SavedStateConfiguration {
//        serializersModule = SerializersModule {
//            polymorphic(NavKey::class) {
//                subclass(ATNavKey.DelayedVideo::class, ATNavKey.DelayedVideo.serializer())
//                subclass(ATNavKey.ShootingSessionList::class, ATNavKey.ShootingSessionList.serializer())
//                // This registers all subclasses of your sealed class automatically
////                subclassesOfSealed<ATNavKey>()
//            }
//        }
//    }
//
//    // 2. Use the official rememberNavBackStack and pass the config
//    val backStack = rememberNavBackStack(
//        configuration = navConfig,
//        ATNavKey.DelayedVideo
//    ) as NavBackStack<ATNavKey>
//
//    return backStack
//    // ... Pass backStack to your NavDisplay
//}
// See: https://developer.android.com/guide/navigation/navigation-3/save-state#subtypes
//@Composable
//fun rememberATNavBackStack(vararg elements: ATNavKey): NavBackStack<ATNavKey> {
//    return rememberSerializable(serializer = serializer<NavBackStack<ATNavKey>>()) {
//        NavBackStack(*elements)
//    }
//}
//@Composable
//fun rememberATNavBackStack(vararg elements: ATNavKey): NavBackStack<ATNavKey> {
//    // 1. Use rememberSaveable
//    // 2. Pass the official NavBackStack.saver with your generic serializer
//    return rememberSaveable(
//        saver = NavBackStack.saver(serializer<NavBackStack<ATNavKey>>())
//    ) {
//        NavBackStack(*elements)
//    }
//}
//@Composable
//fun rememberATNavBackStack(initialKey: ATNavKey): NavBackStack<ATNavKey> {
//    // We use rememberSaveable with a custom saver for NavBackStack
//    return rememberSaveable(saver = NavBackStack.Saver(serializer<ATNavKey>())) {
//        NavBackStack(initialKey)
//    }
//}
@Composable
fun rememberATNavBackStack(initialKey: ATNavKey): NavBackStack<ATNavKey> {
    val backStack = rememberNavBackStack(initialKey)

    @Suppress("UNCHECKED_CAST")
    return backStack as NavBackStack<ATNavKey>
}
