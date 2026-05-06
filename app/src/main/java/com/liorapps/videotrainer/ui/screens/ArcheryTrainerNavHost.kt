package com.liorapps.videotrainer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoCameraFront
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.liorapps.videotrainer.MainViewModel
import com.liorapps.videotrainer.navigation.NavKey
import com.liorapps.videotrainer.ui.screens.mainscreen.DelayedVideoShellScreen
import kotlinx.coroutines.launch

@Composable
fun ArcheryTrainerNavHost(viewModel: MainViewModel) {
    val drawerState  = rememberDrawerState(DrawerValue.Closed)
    val scope        = rememberCoroutineScope()

//    BackHandler(enabled = viewModel.backStack.size > 1) { //todo: enabled probably can be simply always true
//        viewModel.navigateBack()
//    }

    ModalNavigationDrawer(
        drawerState    = drawerState,
        // Prevent accidental swipe-open while in full-screen
        gesturesEnabled = drawerState.isOpen || ( ! (viewModel.backStack.lastOrNull()?.requiresSlideGestures ?: false) ),
        drawerContent  = {
            AppDrawerContent(
                currentRoute = viewModel.backStack.lastOrNull(),
                onNavigate   = { navKey ->
                    viewModel.navigateTo(navKey)
//                    viewModel.backStack.clear(); viewModel.backStack.add(navKey)
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Box(Modifier.fillMaxSize()) {
            NavDisplay(
                backStack     = viewModel.backStack,
                onBack        = { viewModel.backStack.removeLastOrNull() },
                entryProvider = { key: NavKey ->
                    when (key) {
                        NavKey.DelayedVideo -> NavEntry(key) {
                            DelayedVideoShellScreen(
                                onNavigateToSettings = { viewModel.navigateTo(NavKey.Settings) },
                                onOpenDrawer         = { scope.launch { drawerState.open() } }
                            )
                        }
                        NavKey.Settings -> NavEntry(key) {
                            SettingsScreen(
                                onNavigateBack = { viewModel.navigateBack() },
                                onOpenDrawer   = { scope.launch { drawerState.open() } },
                            )
                        }
                    }
                }
            )
//                entryProvider = entryProvider {
//                    entry<HomeRoute> {
//                        HomeScreen(onOpenDrawer = { scope.launch { drawerState.open() } })
//                    }
//                    entry<DetailRoute> {
//                        DetailScreen(
//                            isFullScreen      = uiState.isFullScreen,
//                            onToggleFullScreen = viewModel::toggleFullScreen,
//                            onOpenDrawer      = { scope.launch { drawerState.open() } }
//                        )
//                    }
//                }
//            )

            // ↑ NavDisplay registers its BackHandler first (composed first).
            // This BackHandler is composed AFTER it, so it wins when enabled —
            // giving us the correct priority chain:
            //   1. drawer open  → close drawer
            //   2. (disabled)   → NavDisplay handles normal back navigation
            BackHandler(enabled = drawerState.isOpen) {
                when {
                    drawerState.isOpen -> scope.launch { drawerState.close() }
                }
            }
        }
    }



//    NavDisplay(
//        backStack = viewModel.backStack,
//        modifier = Modifier.fillMaxSize(),
//        onBack = { viewModel.navigateBack() }
//    ) { key: NavKey ->
//        when (key) {
//            NavKey.DelayedVideo -> NavEntry(key) {
//                DelayedVideoShellScreen(
//                    onNavigateToSettings = { viewModel.navigateTo(NavKey.Settings) }
//                )
//            }
//            NavKey.Settings -> NavEntry(key) {
//                SettingsScreen(
//                    onNavigateBack = { viewModel.navigateBack() }
//                )
//            }
//        }
//    }

}

// ─── Drawer Content ───────────────────────────────────────────────────────────
//@Composable
//fun AppDrawerContent(
//    currentRoute: NavKey?,
//    onNavigate: (NavKey) -> Unit,
//) {
//    ModalDrawerSheet {
//        Spacer(Modifier.height(12.dp))
//        NavigationDrawerItem(
//            label    = { Text("Delayed Video") },
//            selected = currentRoute == NavKey.DelayedVideo,
//            onClick  = { onNavigate(NavKey.DelayedVideo) },
//            icon     = { Icon(Icons.Default.VideoCameraFront, null) }
//        )
//        NavigationDrawerItem(
//            label    = { Text("Settings") },
//            selected = currentRoute == NavKey.Settings,
//            onClick  = { onNavigate(NavKey.Settings) },
//            icon     = { Icon(Icons.Default.Settings, null) }
//        )
//    }
//}


@Composable
fun AppDrawerContent(
//    currentRoute: String,
//    onItemClick: (String) -> Unit
    currentRoute: NavKey?,
    onNavigate: (NavKey) -> Unit,
) {
    ModalDrawerSheet {
        Text(
            text = "My App Title",
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(8.dp))

        StandardDrawerItem(
            label    = "Delayed Video",
            icon     = Icons.Default.VideoCameraFront,
            selected = currentRoute == NavKey.DelayedVideo,
            onClick  = { onNavigate(NavKey.DelayedVideo) }
        )
        StandardDrawerItem(
            label    = "Settings",
            icon     = Icons.Default.Settings,
            selected = currentRoute == NavKey.Settings,
            onClick  = { onNavigate(NavKey.Settings) }
        )

        StandardDrawerItem(
            label = "Home",
            icon = Icons.Default.Home,
            selected = false,
            onClick = {  }
        )
        StandardDrawerItem(
            label = "Profile",
            icon = Icons.Default.Person,
            selected = false,
            onClick = { }
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 28.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )

        StandardDrawerItem(
            label = "Settings",
            icon = Icons.Default.Settings,
            selected = false,
            onClick = { }
        )
        StandardDrawerItem(
            label = "About",
            icon = Icons.Default.Info,
            selected = false,
            onClick = { }
        )
    }
}

@Composable
fun StandardDrawerItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        label = { Text(text = label) },
        icon = { Icon(imageVector = icon, contentDescription = null) },
        selected = selected,
        onClick = onClick,
        // NavigationDrawerItemDefaults.ItemPadding provides the standard 12.dp horizontal margin
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}