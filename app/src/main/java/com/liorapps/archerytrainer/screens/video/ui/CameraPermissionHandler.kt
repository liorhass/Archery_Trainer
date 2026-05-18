@file:Suppress("AssignedValueIsNeverRead")

package com.liorapps.archerytrainer.screens.video.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import timber.log.Timber

@Composable
fun CameraPermissionHandler(
    onPermissionGranted: () -> Unit,
    onPermissionDenied: () -> Unit
) {
    val context = LocalContext.current
    var showRationale by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onPermissionGranted()
        } else {
            val activity = context as? ComponentActivity
            val isRationaleNeeded = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
            } ?: false

            if (!isRationaleNeeded) {
                showSettingsDialog = true
            } else {
                onPermissionDenied()
            }
        }
    }

    // This triggers the check automatically on launch
    LaunchedEffect(Unit) {
        val activity = context as? ComponentActivity
        val isRationaleNeeded = activity?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
        } ?: false

        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                Timber.i("#######CPH Camera permission granted")
                onPermissionGranted()
            }
            isRationaleNeeded -> {
                Timber.i("#######CPH Camera permission denied - show rationale")
                showRationale = true
            }
            else -> {
                // First time asking or hard-denied previously
                Timber.i("#######CPH Camera permission denied - request`")
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    // Rationale Dialog (Soft-Reject)
    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text("Camera Permission") },
            text = { Text("We need camera access to proceed with this feature.") },
            confirmButton = {
                Button(onClick = {
                    showRationale = false
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }) { Text("Grant") }
            },
            dismissButton = {
                Button(onClick = {
                    showRationale = false
                    onPermissionDenied()
                }) { Text("Deny") }
            }
        )
    }

    // Settings Dialog (Hard-Reject)
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Permission Required") },
            text = { Text("Camera access is permanently disabled. Please enable it in Settings.") },
            confirmButton = {
                Button(onClick = {
                    showSettingsDialog = false
                    openAppSettings(context)
                }) { Text("Settings") }
            },
            dismissButton = {
                Button(onClick = {
                    showSettingsDialog = false
                    onPermissionDenied()
                }) { Text("Cancel") }
            }
        )
    }
}

// Helper function to open app settings
fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

