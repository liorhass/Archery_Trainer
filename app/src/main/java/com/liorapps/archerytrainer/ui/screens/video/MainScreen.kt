package com.liorapps.archerytrainer.ui.screens.video

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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.filled.Menu
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.liorapps.archerytrainer.CameraPermissionState
import com.liorapps.archerytrainer.MainViewModel
import com.liorapps.archerytrainer.PlaybackState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun DelayedVideoShellScreen(
    viewModel: MainViewModel = viewModel(),
    onNavigateToSettings: () -> Unit,
    onOpenDrawer: () -> Unit,
) {
    val cameraPermissionState by viewModel.cameraPermissionStateFlow.collectAsStateWithLifecycle()

    // Check camera permission status on launch
    // This handler has a LaunchedEffect that handles the permission request logic
    CameraPermissionHandler(
        onPermissionGranted = { viewModel.onCameraPermissionGranted() },
        onPermissionDenied = { viewModel.onCameraPermissionDenied() }
    )

    if (cameraPermissionState == CameraPermissionState.GRANTED) {
        DelayedVideoScreen(viewModel, onOpenDrawer, onNavigateToSettings)
    } else {
        NoCameraPermissionMsg()
    }
}

@Composable
fun DelayedVideoScreen(
    viewModel: MainViewModel,
    onOpenDrawer: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val videoResolution by viewModel.videoResolution.collectAsStateWithLifecycle()
    val settings by viewModel.settingsFlow.collectAsStateWithLifecycle()
    val isFullScreen by viewModel.isFullScreen.collectAsStateWithLifecycle()
    val singleFrameSliderPosition by viewModel.singleFrameSliderPositionFlow.collectAsStateWithLifecycle()
    val horizontalDragSensitivity = viewModel.horizontalDragSensitivity

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
            VideoPlayerBox(
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
                appSliderPosition = singleFrameSliderPosition,
                onSingleFrameSliderMoved = viewModel::onSetSingleFrameLocation,
                onSingleFrameForward = viewModel::onSingleFrameForward,
                onSingleFrameBackward = viewModel::onSingleFrameBackward,
                onSurfaceTouched = viewModel::onVideoSurfaceTouched,
                onHorizontalDrag = viewModel::onHorizontalDragOverVideo,
                dragSensitivityPx = horizontalDragSensitivity,
            )
        } else { // Not at full screen
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    DelayedVideoTopBar(
                        onOpenDrawer = onOpenDrawer,
                        onNavigateToSettings = onNavigateToSettings,
                    )
                }
            ) { innerPadding ->
                VideoPlayerBox(
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
                    appSliderPosition = singleFrameSliderPosition,
                    onSingleFrameSliderMoved = viewModel::onSetSingleFrameLocation,
                    onSingleFrameForward = viewModel::onSingleFrameForward,
                    onSingleFrameBackward = viewModel::onSingleFrameBackward,
                    onSurfaceTouched = viewModel::onVideoSurfaceTouched,
                    onHorizontalDrag = viewModel::onHorizontalDragOverVideo,
                    dragSensitivityPx = horizontalDragSensitivity,
                )
            }
        }
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
private fun DelayedVideoTopBar(
    onOpenDrawer: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    TopAppBar(
        title = { Text("Archery Trainer") },
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Default.Menu, "Open drawer")
            }
        },
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
