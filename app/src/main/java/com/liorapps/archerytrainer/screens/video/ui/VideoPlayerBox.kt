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
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.liorapps.archerytrainer.ArcheryTrainerDefaults
import com.liorapps.archerytrainer.ui.theme.ArcheryTrainerTheme

@Composable
fun VideoPlayerBox(
    innerPadding: PaddingValues,
    videoResolution:  ArcheryTrainerDefaults.VideoResolution,
    isLandscape: Boolean,
    isPlaying: Boolean,
    delaySec: Int,
    onSurfaceReady: (Surface) -> Unit,
    onSurfaceDestroyed: () -> Unit,
    onTogglePlayback: () -> Unit,
    onToggleFullScreen: () -> Unit,
    onVideoDelayChange: (Int) -> Unit,
    appSliderPosition: Float,
    onSingleFrameSliderMoved: (newSliderPosition: Float) -> Unit,
    onSingleFrameForward: () -> Unit,
    onSingleFrameBackward: () -> Unit,
    onSurfaceTouched: (x: Float) -> Unit,
    onHorizontalDrag: (deltaX: Float) -> Unit,
    dragSensitivityPx: Float, // e.g. 40f
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
                        .visible(!isPlaying),
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
fun ArcheryPlayerBoxLandscapePreview() {
    ArcheryTrainerTheme {
        VideoPlayerBox(
            innerPadding = PaddingValues(0.dp),
            videoResolution = ArcheryTrainerDefaults.VideoResolution.HD_1280x720(),
            isLandscape = true,
            isPlaying = true,
            delaySec = 5,
            onSurfaceReady = {},
            onSurfaceDestroyed = {},
            onTogglePlayback = {},
            onToggleFullScreen = {},
            onVideoDelayChange = {},
            appSliderPosition = 55f,
            onSingleFrameSliderMoved = {},
            onSingleFrameForward = {},
            onSingleFrameBackward = {},
            onSurfaceTouched = {},
            onHorizontalDrag = {},            dragSensitivityPx = 40f,
            )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun ArcheryPlayerBoxPortraitPreview() {
    ArcheryTrainerTheme {
        VideoPlayerBox(
            innerPadding = PaddingValues(0.dp),
            videoResolution = ArcheryTrainerDefaults.VideoResolution.HD_1280x720(),
            isLandscape = false,
            isPlaying = false,
            delaySec = 10,
            onSurfaceReady = {},
            onSurfaceDestroyed = {},
            onTogglePlayback = {},
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

