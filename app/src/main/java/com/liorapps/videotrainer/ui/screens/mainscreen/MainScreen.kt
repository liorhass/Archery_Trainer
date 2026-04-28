package com.liorapps.videotrainer.ui.screens.mainscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.liorapps.videotrainer.CameraPermissionState
import com.liorapps.videotrainer.MainViewModel
import com.liorapps.videotrainer.PlaybackState
import com.liorapps.videotrainer.VideoTrainerDefaults
import com.liorapps.videotrainer.ui.theme.VideoTrainerTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(),
    onNavigateToSettings: () -> Unit
) {
    val cameraPermissionState by viewModel.cameraPermissionStateFlow.collectAsStateWithLifecycle()

    // Check camera permission status on launch
    // This handler has a LaunchedEffect that handles the permission request logic
    CameraPermissionHandler(
        onPermissionGranted = { viewModel.onCameraPermissionGranted() },
        onPermissionDenied = { viewModel.onCameraPermissionDenied() }
    )

    if (cameraPermissionState == CameraPermissionState.GRANTED) {
        MainScreenContent(viewModel, onNavigateToSettings)
    } else {
        NoCameraPermissionMsg()
    }
}

@Composable
fun MainScreenContent(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit
) {
    val videoResolution by viewModel.videoResolution.collectAsStateWithLifecycle()
    val settings by viewModel.settingsFlow.collectAsStateWithLifecycle()
    val isFullScreen = viewModel.isFullScreen

    // A launchedEffect to set the window as necessary (e.g. to show or hide system bars)
    FullScreenEffect(isFullScreen)

    // When in full-screen we want the back button to simply exit full-screen
    BackHandler(enabled = isFullScreen) {
        viewModel.navigateBack()
    }

    // A launchedEffect that updates the viewModel about the current screen orientation
    ScreenOrientationEffect(isFullScreen, viewModel::onScreenRotationUpdate)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(if (isFullScreen) Modifier else Modifier.safeDrawingPadding())
    ) {
        val isLandscape =
            LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

        if (isFullScreen) {
            VideoArea(
                innerPadding = PaddingValues(0.dp),
                videoResolution = videoResolution,
                isLandscape = isLandscape,
                isPlaying = viewModel.playbackState == PlaybackState.PLAYING,
                delaySec = settings.delaySec,
                onSurfaceReady = viewModel::onSurfaceReady,
                onSurfaceDestroyed = viewModel::onSurfaceDestroyed,
                onTogglePlayback = viewModel::onTogglePlayback,
                onToggleFullScreen = viewModel::toggleIsFullScreen,
                onVideoDelayChange = viewModel::onDelayChange,
                onSelectSingleFrame = viewModel::onSetSingleFrameLocation,
                onSingleFrameForward = viewModel::onSingleFrameForward,
                onSingleFrameBackward = viewModel::onSingleFrameBackward,
            )
        } else { // Not at full screen
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = { TopBar(onNavigateToSettings) }
            ) { innerPadding ->
                VideoArea(
                    innerPadding = innerPadding,
                    videoResolution = videoResolution,
                    isLandscape = isLandscape,
                    isPlaying = viewModel.playbackState == PlaybackState.PLAYING,
                    delaySec = settings.delaySec,
                    onSurfaceReady = viewModel::onSurfaceReady,
                    onSurfaceDestroyed = viewModel::onSurfaceDestroyed,
                    onTogglePlayback = viewModel::onTogglePlayback,
                    onToggleFullScreen = viewModel::toggleIsFullScreen,
                    onVideoDelayChange = viewModel::onDelayChange,
                    onSelectSingleFrame = viewModel::onSetSingleFrameLocation,
                    onSingleFrameForward = viewModel::onSingleFrameForward,
                    onSingleFrameBackward = viewModel::onSingleFrameBackward,
                )
            }
        }
    }
}

@Composable
fun VideoArea(
    innerPadding: PaddingValues,
    videoResolution:  VideoTrainerDefaults.VideoResolution,
    isLandscape: Boolean,
    isPlaying: Boolean,
    delaySec: Int,
    onSurfaceReady: (Surface) -> Unit,
    onSurfaceDestroyed: () -> Unit,
    onTogglePlayback: () -> Unit,
    onToggleFullScreen: () -> Unit,
    onVideoDelayChange: (Int) -> Unit,
    onSelectSingleFrame: (Float) -> Unit,
    onSingleFrameForward: () -> Unit,
    onSingleFrameBackward: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        VideoPlayer(
            videoResolution = videoResolution,
            onSurfaceReady = onSurfaceReady,
            onSurfaceDestroyed = onSurfaceDestroyed,
        )

        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                SingleFrameMovementControl(
                    modifier = Modifier
                        .align(Alignment.Bottom)
                        .weight(1.0f)
                        .visible(! isPlaying),
                    progress = 0.33f,
                    onSelectSingleFrame = onSelectSingleFrame,
                    onSingleFrameForward = onSingleFrameForward,
                    onSingleFrameBackward = onSingleFrameBackward,
                )

                ControlBar(
                    // Adaptive Control Bar
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .padding(start = 0.dp, top = 0.dp, bottom = 0.dp, end = 12.dp),
                    isLandscape = true,
                    isFullScreen = false,
                    isPlaying = isPlaying,
                    delay = delaySec,
                    onTogglePlayback = onTogglePlayback,
                    onToggleFullScreen = onToggleFullScreen,
                    onDelayChange = onVideoDelayChange,
                )
            }
        } else { // portrait
            Column(
                modifier = Modifier
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                SingleFrameMovementControl(
                    modifier = Modifier
                        .visible(! isPlaying),
                    progress = 0.33f,
                    onSelectSingleFrame = onSelectSingleFrame,
                    onSingleFrameForward = onSingleFrameForward,
                    onSingleFrameBackward = onSingleFrameBackward,
                )

                ControlBar(
                    // Adaptive Control Bar
                    modifier = Modifier
                        .padding(start = 0.dp, top = 0.dp, bottom = 12.dp, end = 0.dp),
                    isLandscape = false,
                    isFullScreen = false,
                    isPlaying = isPlaying,
                    delay = delaySec,
                    onTogglePlayback = onTogglePlayback,
                    onToggleFullScreen = onToggleFullScreen,
                    onDelayChange = onVideoDelayChange,
                )
            }
        }


//                    // Enter full-screen button (bottom-end corner of the video)
//                    IconButton(
//                        onClick = { viewModel.setIsFullScreen(true) },
//                        modifier = Modifier.align(Alignment.TopEnd)
//                    ) {
//                        Icon(
//                            imageVector = Icons.Default.Fullscreen,
//                            contentDescription = "Enter full screen",
//                            tint = Color.Blue
//                        )
//                    }
    }
}







//    Scaffold(
//        modifier = Modifier.fillMaxSize(),
//        topBar = { TopBar(onNavigateToSettings) }
//    ) { innerPadding ->
//        val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
//
//        Box(
//            modifier = Modifier
//                .fillMaxSize()
//                .padding(innerPadding)
//        ) {
//            if (cameraPermissionState == CameraPermissionState.GRANTED) {
////                CameraPreview(
////                    selectedCamera = viewModel.selectedCamera,
////                    isPlaying = viewModel.playbackState == PlaybackState.PLAYING
////                )
//                VideoPlayer(
//                    videoSize = videoSize,
//                    onSurfaceReady = viewModel::onSurfaceReady,
//                    onSurfaceDestroyed = viewModel::onSurfaceDestroyed,
//                    modifier = Modifier,
//                )
//            } else {
//                NoCameraPermissionMsg()
//            }
//
//            // TODO LH: show only if camera permission granted (move to above)
//            // Adaptive Control Bar
//            ControlBar(
//                modifier = if (isLandscape) {
//                    Modifier
//                        .align(Alignment.CenterEnd)
//                        .padding(24.dp)
//                } else {
//                    Modifier
//                        .align(Alignment.BottomCenter)
//                        .padding(24.dp)
//                },
//                isLandscape = isLandscape,
//                isPlaying = viewModel.playbackState == PlaybackState.PLAYING,
//                delay = viewModel.delaySec,
//                onTogglePlayback = viewModel::togglePlayback,
//                onSwitchCamera = viewModel::switchCamera,
//                onDelayChange = viewModel::updateDelay
//            )
//        }
//    }
//}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TopBar(onNavigateToSettings: () -> Unit) {
    TopAppBar(
        title = { Text("Video Trainer") },
        actions = {
            IconButton(onClick = onNavigateToSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = "Settings")
            }
        }
    )
}


//@Composable
//fun CameraPreview(
//    selectedCamera: CameraSelector,
//    isPlaying: Boolean
//) {
//    val context = LocalContext.current
//    val lifecycleOwner = LocalLifecycleOwner.current
//    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
//    val cameraProviderState = remember { mutableStateOf<ProcessCameraProvider?>(null) }
//
//    LaunchedEffect(Unit) {
//        cameraProviderState.value = ProcessCameraProvider.getInstance(context).await()
//    }
//
//    val cameraProvider = cameraProviderState.value
//
//    LaunchedEffect(cameraProvider, selectedCamera, isPlaying) {
//        if (cameraProvider == null) return@LaunchedEffect
//
//        cameraProvider.unbindAll()
//
//        if (isPlaying) {
//            val preview = Preview.Builder().build()
//            preview.setSurfaceProvider { request ->
//                surfaceRequest = request
//            }
//
//            try {
//                cameraProvider.bindToLifecycle(
//                    lifecycleOwner,
//                    selectedCamera,
//                    preview
//                )
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
//        } else {
//            surfaceRequest = null
//        }
//    }
//
//    surfaceRequest?.let { request ->
//        CameraXViewfinder(
//            surfaceRequest = request,
//            modifier = Modifier.fillMaxSize()
//        )
//    } ?: Box(
//        modifier = Modifier
//            .fillMaxSize()
//            .background(Color.Black),
//        contentAlignment = Alignment.Center
//    ) {
//        if (isPlaying) {
//            CircularProgressIndicator()
//        } else {
//            Icon(
//                imageVector = Icons.Rounded.PauseCircleFilled,
//                contentDescription = "Paused",
//                modifier = Modifier.size(64.dp),
//                tint = Color.White.copy(alpha = 0.5f)
//            )
//        }
//    }
//}

@Composable
fun NoCameraPermissionMsg() {
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
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ControlBar(
    modifier: Modifier = Modifier,
    isLandscape: Boolean,
    isFullScreen: Boolean,
    isPlaying: Boolean,
    delay: Int,
    onTogglePlayback: () -> Unit = {},
    onToggleFullScreen: () -> Unit = {},
    onDelayChange: (Int) -> Unit = {}
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
        tonalElevation = 4.dp
    ) {
        if (isLandscape) {
            Column(
                modifier = Modifier.padding(vertical = 16.dp, horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ControlIcons(
                    isPlaying = isPlaying,
                    isFullScreen = isFullScreen,
                    delay = delay,
                    onTogglePlayback = onTogglePlayback,
                    onToggleFullScreen = onToggleFullScreen,
                    onDelayChange = onDelayChange,
                )
            }
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ControlIcons(
                    isPlaying = isPlaying,
                    isFullScreen = isFullScreen,
                    delay = delay,
                    onTogglePlayback = onTogglePlayback,
                    onToggleFullScreen = onToggleFullScreen,
                    onDelayChange = onDelayChange,
                )
            }
        }
    }
}

@Composable
fun ControlIcons(
    isPlaying: Boolean,
    isFullScreen: Boolean,
    delay: Int,
    onTogglePlayback: () -> Unit,
    onToggleFullScreen: () -> Unit,
    onDelayChange: (Int) -> Unit,
) {
    // Play/Pause button
    IconButton(onClick = onTogglePlayback) {
        Icon(
            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            tint = MaterialTheme.colorScheme.onSurface,
            contentDescription = if (isPlaying) "Pause" else "Play"
        )
    }

    var showDelayDialog by remember { mutableStateOf(false) }
    TextButton(onClick = { showDelayDialog = true }) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Delay",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "$delay s",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }

    IconButton(onClick = onToggleFullScreen) {
        Icon(
            imageVector = if (isFullScreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
            contentDescription = if (isFullScreen) "Exit full screen" else "Full screen",
            tint = MaterialTheme.colorScheme.onSurface,
        )
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
    val origDelay = remember {currentValue}

    AlertDialog(
        title = { Text("Adjust Delay") },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = { onValueChange(origDelay); onDismiss()} ) {
                Text("Cancel")
            }
        },
        text = {
            Column {
                Text(text = "Delay: $currentValue seconds")
                Slider(
                    value = currentValue.toFloat(),
                    onValueChange = { onValueChange(it.toInt()) },
                    valueRange = 0f..30f, //todo: max should come from config
                    steps = 29
                )
            }
        }
    )
}

@Preview(showBackground = true, widthDp = 800, heightDp = 480)
@Composable
fun VideoAreaLandscapePreview() {
    VideoTrainerTheme {
        VideoArea(
            innerPadding = PaddingValues(0.dp),
            videoResolution = VideoTrainerDefaults.VideoResolution.HD_1280x720(),
            isLandscape = true,
            isPlaying = true,
            delaySec = 5,
            onSurfaceReady = {},
            onSurfaceDestroyed = {},
            onTogglePlayback = {},
            onToggleFullScreen = {},
            onVideoDelayChange = {},
            onSelectSingleFrame = {},
            onSingleFrameForward = {},
            onSingleFrameBackward = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun VideoAreaPortraitPreview() {
    VideoTrainerTheme {
        VideoArea(
            innerPadding = PaddingValues(0.dp),
            videoResolution = VideoTrainerDefaults.VideoResolution.HD_1280x720(),
            isLandscape = false,
            isPlaying = false,
            delaySec = 10,
            onSurfaceReady = {},
            onSurfaceDestroyed = {},
            onTogglePlayback = {},
            onToggleFullScreen = {},
            onVideoDelayChange = {},
            onSelectSingleFrame = {},
            onSingleFrameForward = {},
            onSingleFrameBackward = {},
        )
    }
}

fun Modifier.visible(visible: Boolean): Modifier =
    this
        .alpha(if (visible) 1f else 0f)
        .pointerInput(visible) {
            if (! visible) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(pass = PointerEventPass.Initial)
                        .changes.forEach { it.consume() }
                }
            }
        }
