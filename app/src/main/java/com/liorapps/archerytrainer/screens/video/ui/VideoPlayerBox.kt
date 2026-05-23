package com.liorapps.archerytrainer.screens.video.ui

import android.view.Surface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.liorapps.archerytrainer.ArcheryTrainerDefaults
import com.liorapps.archerytrainer.screens.util.IconButtonWithLongClick
import com.liorapps.archerytrainer.screens.util.icons.Autoplay
import com.liorapps.archerytrainer.screens.video.logic.DelayedVideoViewModel
import com.liorapps.archerytrainer.ui.theme.ArcheryTrainerTheme
import timber.log.Timber

@Composable
fun VideoPlayerBox(
    innerPadding: PaddingValues,
    videoResolution:  ArcheryTrainerDefaults.VideoResolution,
    isLandscape: Boolean,
    playbackState: DelayedVideoViewModel.PlaybackState,
    bufferingTime: Int,
    onBufferingTimeTerminated: () -> Unit,
    delaySec: Int,
    onSurfaceReady: (Surface) -> Unit,
    onSurfaceDestroyed: () -> Unit,
    onTogglePlayback: () -> Unit,
    onToggleLoopPlayback: () -> Unit,
    onToggleFullScreen: () -> Unit,
    onVideoDelayChange: (Int) -> Unit,
    appSliderPosition: Float,
    onSingleFrameSliderMoved: (newSliderPosition: Float) -> Unit,
    onSingleFrameForward: () -> Unit,
    onSingleFrameBackward: () -> Unit,
    onSurfaceTouched: (x: Float) -> Unit,
    onHorizontalDrag: (deltaX: Float) -> Unit,
    dragSensitivityPx: Float, // How large (in pixels) a drag needs to be to move one frame. e.g. 40f
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .keepScreenOn()
    ) {
        VideoPlayer(
            videoResolution = videoResolution,
            onSurfaceReady = onSurfaceReady,
            onSurfaceDestroyed = onSurfaceDestroyed,
            onSurfaceTouched = onSurfaceTouched,
            onHorizontalDrag = onHorizontalDrag,
            dragSensitivityPx = dragSensitivityPx,
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
                        .visible(playbackState == DelayedVideoViewModel.PlaybackState.PAUSED),
                    appSliderPosition = appSliderPosition,
                    onSliderMoved = onSingleFrameSliderMoved,
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
                    playbackState = playbackState,
                    delay = delaySec,
                    onTogglePlayback = onTogglePlayback,
                    onToggleLoopPlayback = onToggleLoopPlayback,
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
                        .visible(playbackState == DelayedVideoViewModel.PlaybackState.PAUSED),
                    appSliderPosition = appSliderPosition,
                    onSliderMoved = onSingleFrameSliderMoved,
                    onSingleFrameForward = onSingleFrameForward,
                    onSingleFrameBackward = onSingleFrameBackward,
                )

                ControlBar(
                    // Adaptive Control Bar
                    modifier = Modifier
                        .padding(start = 0.dp, top = 0.dp, bottom = 12.dp, end = 0.dp),
                    isLandscape = false,
                    isFullScreen = false,
                    playbackState = playbackState,
                    delay = delaySec,
                    onTogglePlayback = onTogglePlayback,
                    onToggleLoopPlayback = onToggleLoopPlayback,
                    onToggleFullScreen = onToggleFullScreen,
                    onDelayChange = onVideoDelayChange,
                )
            }
        }
        if (bufferingTime > 0) {
            BufferingCountdown(
                durationSeconds = bufferingTime,
                isBufferingDone = false,
                onBufferingTimeTerminated = onBufferingTimeTerminated,
            )
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

@Composable
fun ControlBar(
    modifier: Modifier = Modifier,
    isLandscape: Boolean,
    isFullScreen: Boolean,
    playbackState: DelayedVideoViewModel.PlaybackState,
    delay: Int,
    onTogglePlayback: () -> Unit,
    onToggleLoopPlayback: () -> Unit,
    onToggleFullScreen: () -> Unit,
    onDelayChange: (Int) -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.3f),
        tonalElevation = 4.dp
    ) {
        if (isLandscape) {
            Column(
                modifier = Modifier.padding(vertical = 16.dp, horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MainControlButtons(
                    playbackState = playbackState,
                    isFullScreen = isFullScreen,
                    delay = delay,
                    onTogglePlayback = onTogglePlayback,
                    onToggleLoopPlayback = onToggleLoopPlayback,
                    onToggleFullScreen = onToggleFullScreen,
                    onDelayChanged = onDelayChange,
                )
            }
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MainControlButtons(
                    playbackState = playbackState,
                    isFullScreen = isFullScreen,
                    delay = delay,
                    onTogglePlayback = onTogglePlayback,
                    onToggleLoopPlayback = onToggleLoopPlayback,
                    onToggleFullScreen = onToggleFullScreen,
                    onDelayChanged = onDelayChange,
                )
            }
        }
    }
}

@Composable
private fun MainControlButtons(
    playbackState: DelayedVideoViewModel.PlaybackState,
    isFullScreen: Boolean,
    delay: Int,
    onTogglePlayback: () -> Unit,
    onToggleLoopPlayback: () -> Unit,
    onToggleFullScreen: () -> Unit,
    onDelayChanged: (Int) -> Unit,
) {
    // Play/Pause button
    IconButton(onClick = onTogglePlayback) {
        if (playbackState == DelayedVideoViewModel.PlaybackState.PLAYING) {
            Icon(
                imageVector = Icons.Default.Pause,
                tint = MaterialTheme.colorScheme.onSurface,
                contentDescription = "Pause",
            )
        } else {
            Icon(
                imageVector = Icons.Default.PlayCircle,
                tint = Color.Red,
                contentDescription = "Play"
            )
        }
    }

    // Loop replay
    IconButtonWithLongClick(
        onClick = onToggleLoopPlayback,
        onLongClick = onToggleLoopPlayback,
    ) {
        if (playbackState != DelayedVideoViewModel.PlaybackState.LOOP_REPLAYING) {
            Icon(
                imageVector = Autoplay,
                tint = MaterialTheme.colorScheme.onSurface,
                contentDescription = "Play in loop"
            )
        } else {
            Icon(
                imageVector = Icons.Default.Pause,
                tint = MaterialTheme.colorScheme.onSurface,
                contentDescription = "Pause"
            )
        }
    }

    // Delay
    var showDelayDialog by rememberSaveable { mutableStateOf(false) }
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

    // Full screen
    IconButton(onClick = onToggleFullScreen) {
        Icon(
            imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
            contentDescription = if (isFullScreen) "Exit full screen" else "Full screen",
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }

    if (showDelayDialog) {
        DelayDialog(
            currentValue = delay,
            onDismiss = { showDelayDialog = false },
            onValueChanged = onDelayChanged
        )
    }
}

@Composable
fun DelayDialog(
    currentValue: Int,
    onDismiss: () -> Unit,
    onValueChanged: (Int) -> Unit
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
            TextButton(onClick = { onValueChanged(origDelay); onDismiss()} ) {
                Text("Cancel")
            }
        },
        text = {
            Column {
                Text(text = "Delay: $currentValue seconds")
                Slider(
                    value = currentValue.toFloat(),
                    onValueChange = { onValueChanged(it.toInt()) },
                    valueRange = 0f..30f, //todo: max should come from config
                    steps = 0
                )
            }
        }
    )
}

fun Modifier.visible(visible: Boolean): Modifier =
    this
        .alpha(if (visible) 1f else 0f)
        .pointerInput(visible) {
            if (!visible) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(pass = PointerEventPass.Initial)
                        .changes.forEach { it.consume() }
                }
            }
        }

@Preview(showBackground = true, widthDp = 800, heightDp = 480)
@Composable
fun VideoPlayerBoxPreview() {
    ArcheryTrainerTheme {
        VideoPlayerBox(
            innerPadding = PaddingValues(0.dp),
            videoResolution = ArcheryTrainerDefaults.VideoResolution.HD_1280x720,
            isLandscape = true,
            playbackState = DelayedVideoViewModel.PlaybackState.LOOP_REPLAYING,
            bufferingTime = 22,
            onBufferingTimeTerminated = {},
            delaySec = 5,
            onSurfaceReady = {},
            onSurfaceDestroyed = {},
            onTogglePlayback = {},
            onToggleLoopPlayback = {},
            onToggleFullScreen = {},
            onVideoDelayChange = {},
            appSliderPosition = 55f,
            onSingleFrameSliderMoved = {},
            onSingleFrameForward = {},
            onSingleFrameBackward = {},
            onSurfaceTouched = {},
            onHorizontalDrag = {},
            dragSensitivityPx = 40f,
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun VideoPlayerBoxPreview1() {
    ArcheryTrainerTheme {
        VideoPlayerBox(
            innerPadding = PaddingValues(0.dp),
            videoResolution = ArcheryTrainerDefaults.VideoResolution.HD_1280x720,
            isLandscape = false,
            playbackState = DelayedVideoViewModel.PlaybackState.LOOP_REPLAYING,
            bufferingTime = 22,
            onBufferingTimeTerminated = {},
            delaySec = 10,
            onSurfaceReady = {},
            onSurfaceDestroyed = {},
            onTogglePlayback = {},
            onToggleLoopPlayback = {},
            onToggleFullScreen = {},
            onVideoDelayChange = {},
            appSliderPosition = 55f,
            onSingleFrameSliderMoved = {},
            onSingleFrameForward = {},
            onSingleFrameBackward = {},
            onSurfaceTouched = {},
            onHorizontalDrag = {},
            dragSensitivityPx = 40f,
        )
    }
}

