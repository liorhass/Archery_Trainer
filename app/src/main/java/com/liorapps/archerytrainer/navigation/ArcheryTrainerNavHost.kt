package com.liorapps.archerytrainer.navigation

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
import androidx.compose.material.icons.filled.Storage
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
//import androidx.navigation3.ui.rememberSceneSetupNavEntryDecorator
import com.liorapps.archerytrainer.ArcheryTrainerApplication
import com.liorapps.archerytrainer.screens.about.AboutScreen
import com.liorapps.archerytrainer.screens.about.AboutViewModel
import com.liorapps.archerytrainer.screens.editsession.EditShootingSessionScreen
import com.liorapps.archerytrainer.screens.editsession.EditShootingSessionViewModel
import com.liorapps.archerytrainer.screens.sessions.ShootingSessionListScreen
import com.liorapps.archerytrainer.screens.sessions.ShootingSessionListViewModel
import com.liorapps.archerytrainer.screens.settings.SettingsScreen
import com.liorapps.archerytrainer.screens.settings.SettingsViewModel
import com.liorapps.archerytrainer.screens.video.logic.DelayedVideoViewModel
import com.liorapps.archerytrainer.screens.video.ui.DelayedVideoShellScreen
import kotlinx.coroutines.launch

@Composable
fun ArcheryTrainerNavHost(/*navigationViewModel: NavigationViewModel*/) {

    // Create the app's backStack with rememberXXXNavBackStack. This gives us persistence across
    // process death so when the user re-opens the app he gets the last screen he was on
    // See: https://developer.android.com/guide/navigation/navigation-3/save-state#use-remembernavbackstack
//    val backStack = rememberATNavBackStack(ATNavKey.DelayedVideo)
//    val backStack = getATBackStack()
    val navigationViewModel: NavigationViewModel = viewModel(
//        factory = NavigationViewModel.Factory(backStack)
        factory = NavigationViewModel.Factory()
    )

    val drawerState  = rememberDrawerState(DrawerValue.Closed)
    val scope        = rememberCoroutineScope()
    val app          = LocalContext.current.applicationContext as ArcheryTrainerApplication

//    val savedStateNavEntryDecorator = rememberSavedStateNavEntryDecorator<NavKey>()
    // Navigation 3 requires these decorators to provide Lifecycle, SavedState, and ViewModel support
    val saveableStateHolderNavEntryDecorator = rememberSaveableStateHolderNavEntryDecorator<ATNavKey>()
    val viewModelStoreDecorator = rememberViewModelStoreNavEntryDecorator<ATNavKey>()

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Prevent accidental swipe-open while in full-screen
        gesturesEnabled = drawerState.isOpen || (!(navigationViewModel.backStack.lastOrNull()?.requiresSlideGestures
            ?: false)),
        drawerContent = {
            AppDrawerContent(
                currentRoute = navigationViewModel.backStack.lastOrNull(),
                onNavigate = { navKey ->
                    navigationViewModel.navigateTo(navKey)
//                    viewModel.backStack.clear(); viewModel.backStack.add(navKey)
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Box(Modifier.Companion.fillMaxSize()) {
            NavDisplay(
                backStack = navigationViewModel.backStack,
                onBack = { navigationViewModel.navigateBack() },
                entryDecorators  = listOf(saveableStateHolderNavEntryDecorator, viewModelStoreDecorator),
                entryProvider = { key: ATNavKey ->
                    when (key) {
                        is ATNavKey.ShootingSessionList -> NavEntry(key) {
                            val viewModel: ShootingSessionListViewModel = viewModel(
                                factory = ShootingSessionListViewModel.Factory(app)
                            )
                            ShootingSessionListScreen (
                                viewModel = viewModel,
                                navigateTo = { navKey: ATNavKey -> navigationViewModel.navigateTo(navKey) },
                                onOpenDrawer = { scope.launch { drawerState.open() } }
                            )
                        }

                        is ATNavKey.DelayedVideo -> NavEntry(key) {
                            val viewModel: DelayedVideoViewModel = viewModel(
                                factory = DelayedVideoViewModel.Factory(app, app.settingsRepository)
                            )
                            DelayedVideoShellScreen(
                                viewModel = viewModel,
                                onNavigateToSettings = { navigationViewModel.navigateTo(ATNavKey.Settings) },
                                onOpenDrawer = { scope.launch { drawerState.open() } }
                            )
                        }

                        is ATNavKey.EditShootingSession -> NavEntry(key) {
                            val viewModel: EditShootingSessionViewModel = viewModel(
                                factory = EditShootingSessionViewModel.Factory(
                                    sessionId = key.sessionId,
                                    application = app,
                                    settingsRepo = app.settingsRepository,
                                )
                            )
                            EditShootingSessionScreen (
                                viewModel = viewModel,
                                onNavigateBack = { navigationViewModel.navigateBack() },
                            )
                        }

                        is ATNavKey.Settings -> NavEntry(key) {
                            val viewModel: SettingsViewModel = viewModel(
                                factory = SettingsViewModel.Factory(app.settingsRepository)
                            )
                            SettingsScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navigationViewModel.navigateBack() },
                                onOpenDrawer = { scope.launch { drawerState.open() } },
                            )
                        }

                        is ATNavKey.About -> NavEntry(key) {
                            val viewModel: AboutViewModel = viewModel(
                                factory = AboutViewModel.Factory(app)
                            )
                            AboutScreen(
                                viewModel = viewModel,
                                onOpenDrawer = { scope.launch { drawerState.open() } },
                            )
                        }

//                        is NavKey.ExampleWithItemDetail -> NavEntry(key) {
//                            // The key itself carries the itemId — no shared mutable state needed
//                            val viewModel: ExampleWithItemDetailViewModel = viewModel(
//                                factory = ExampleWithItemDetailViewModel.Factory(
//                                    itemId              = key.itemId,
//                                    settingsRepository  = app.settingsRepository
//                                )
//                            )
//                            ExampleWithItemDetailShellScreen(
//                                viewModel      = viewModel,
//                                onNavigateBack = { navigationViewModel.navigateBack() }
//                            )
//                        }
                    }
                }
            )

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
}


@Composable
fun AppDrawerContent(
//    currentRoute: String,
//    onItemClick: (String) -> Unit
    currentRoute: ATNavKey?,
    onNavigate: (ATNavKey) -> Unit,
) {
    ModalDrawerSheet {
        Text(
            text = "Archery Trainer",
            modifier = Modifier.Companion.padding(horizontal = 28.dp, vertical = 24.dp),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.Companion.height(8.dp))

        StandardDrawerItem(
            label = "Shooting Sessions",
            icon = Icons.Default.Storage,
            selected = currentRoute == ATNavKey.ShootingSessionList,
            onClick = { onNavigate(ATNavKey.ShootingSessionList) }
        )

        StandardDrawerItem(
            label = "Delayed Video",
            icon = Icons.Default.VideoCameraFront,
            selected = currentRoute == ATNavKey.DelayedVideo,
            onClick = { onNavigate(ATNavKey.DelayedVideo) }
        )

        HorizontalDivider(
            modifier = Modifier.Companion.padding(vertical = 12.dp, horizontal = 28.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )

        StandardDrawerItem(
            label = "Settings",
            icon = Icons.Default.Settings,
            selected = currentRoute == ATNavKey.Settings,
            onClick = { onNavigate(ATNavKey.Settings) }
        )

        StandardDrawerItem(
            label = "About",
            icon = Icons.Default.Info,
            selected = currentRoute == ATNavKey.About,
            onClick = { onNavigate(ATNavKey.About) }
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
        modifier = Modifier.Companion.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}