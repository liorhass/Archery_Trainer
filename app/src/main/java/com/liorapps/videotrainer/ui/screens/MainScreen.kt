package com.liorapps.videotrainer.ui.screens

import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview as PreviewAnnotation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.liorapps.videotrainer.ui.theme.VideoTrainerTheme
import kotlinx.coroutines.guava.await

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(
    viewModel: com.liorapps.videotrainer.MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onNavigateToSettings: () -> Unit
) {
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Video Trainer") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { innerPadding ->
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (cameraPermissionState.status.isGranted) {
                CameraPreview(
                    cameraSelector = viewModel.cameraSelector,
                    isPlaying = viewModel.isPlaying
                )
            } else {
                PermissionRequest(
                    onRequestPermission = { cameraPermissionState.launchPermissionRequest() }
                )
            }

            // Adaptive Control Bar
            ControlBar(
                modifier = if (isLandscape) {
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(24.dp)
                } else {
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp)
                },
                isLandscape = isLandscape,
                isPlaying = viewModel.isPlaying,
                delay = viewModel.delay,
                onTogglePlayback = viewModel::togglePlayback,
                onSwitchCamera = viewModel::switchCamera,
                onDelayChange = viewModel::updateDelay
            )
        }
    }
}

@Composable
fun CameraPreview(
    cameraSelector: CameraSelector,
    isPlaying: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    val cameraProviderState = remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(Unit) {
        cameraProviderState.value = ProcessCameraProvider.getInstance(context).await()
    }

    val cameraProvider = cameraProviderState.value

    LaunchedEffect(cameraProvider, cameraSelector, isPlaying) {
        if (cameraProvider == null) return@LaunchedEffect

        cameraProvider.unbindAll()

        if (isPlaying) {
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider { request ->
                surfaceRequest = request
            }

            try {
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            surfaceRequest = null
        }
    }

    surfaceRequest?.let { request ->
        CameraXViewfinder(
            surfaceRequest = request,
            modifier = Modifier.fillMaxSize()
        )
    } ?: Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (isPlaying) {
            CircularProgressIndicator()
        } else {
            Icon(
                imageVector = Icons.Rounded.PauseCircleFilled,
                contentDescription = "Paused",
                modifier = Modifier.size(64.dp),
                tint = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun PermissionRequest(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.CameraAlt,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Camera permission is required to use this app",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequestPermission) {
            Text("Grant Permission")
        }
    }
}

@Composable
fun ControlBar(
    modifier: Modifier = Modifier,
    isLandscape: Boolean = false,
    isPlaying: Boolean = true,
    delay: Int = 5,
    onTogglePlayback: () -> Unit = {},
    onSwitchCamera: () -> Unit = {},
    onDelayChange: (Int) -> Unit = {}
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f),
        tonalElevation = 4.dp
    ) {
        if (isLandscape) {
            Column(
                modifier = Modifier.padding(vertical = 16.dp, horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ControlIcons(
                    isLandscape = true,
                    isPlaying = isPlaying,
                    delay = delay,
                    onTogglePlayback = onTogglePlayback,
                    onSwitchCamera = onSwitchCamera,
                    onDelayChange = onDelayChange
                )
            }
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ControlIcons(
                    isLandscape = false,
                    isPlaying = isPlaying,
                    delay = delay,
                    onTogglePlayback = onTogglePlayback,
                    onSwitchCamera = onSwitchCamera,
                    onDelayChange = onDelayChange
                )
            }
        }
    }
}

@Composable
fun ControlIcons(
    isLandscape: Boolean,
    isPlaying: Boolean,
    delay: Int,
    onTogglePlayback: () -> Unit,
    onSwitchCamera: () -> Unit,
    onDelayChange: (Int) -> Unit
) {
    IconButton(onClick = onTogglePlayback) {
        Icon(
            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play"
        )
    }
    IconButton(onClick = onSwitchCamera) {
        Icon(Icons.Rounded.FlipCameraAndroid, contentDescription = "Switch Camera")
    }

    var showDelayDialog by remember { mutableStateOf(false) }

    TextButton(onClick = { showDelayDialog = true }) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Delay",
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = "$delay s",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }

    if (showDelayDialog) {
        DelayDialog(
            currentValue = delay,
            onDismiss = { showDelayDialog = false },
            onValueChange = onDelayChange
        )
    }
}

@Composable
fun DelayDialog(
    currentValue: Int,
    onDismiss: () -> Unit,
    onValueChange: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        },
        title = { Text("Adjust Delay") },
        text = {
            Column {
                Text(text = "Delay: $currentValue seconds")
                Slider(
                    value = currentValue.toFloat(),
                    onValueChange = { onValueChange(it.toInt()) },
                    valueRange = 0f..30f,
                    steps = 29
                )
            }
        }
    )
}

@PreviewAnnotation(showBackground = true)
@Composable
fun MainScreenPreview() {
    VideoTrainerTheme {
        MainScreen(onNavigateToSettings = {})
    }
}
