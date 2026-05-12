package com.liorapps.archerytrainer

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.liorapps.archerytrainer.navigation.ArcheryTrainerNavHost
import com.liorapps.archerytrainer.ui.theme.ArcheryTrainerTheme

class MainActivity : ComponentActivity() {
//    private val navigationViewModel: NavigationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // See if we have device-admin privileges, and request it from the user if we don't. This
        // privilege is needed to be able to lock the screen
        ScrLockManagerImpl.requestAdminPrivilegeIfNeeded(this, ScrLockManagerImpl.REQUEST_CODE_ADMIN)

        // Tell the system that we want to be active even when the device is locked (over the lock screen)
        setShowWhenLocked(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setInheritShowWhenLocked(true)
        }

        enableEdgeToEdge() // Allow the app to draw behind system bars
        setContent {
            ArcheryTrainerTheme {
                ArcheryTrainerNavHost(/*navigationViewModel*/)
            }
        }
    }
}

