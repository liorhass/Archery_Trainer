package com.liorapps.videotrainer.ui.screens.mainscreen

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.liorapps.videotrainer.MainViewModel
import com.liorapps.videotrainer.VideoTrainerDefaults
import timber.log.Timber
import kotlin.math.abs

@SuppressLint("ClickableViewAccessibility")
@Composable
fun VideoPlayer(
    videoResolution: VideoTrainerDefaults.VideoResolution,
    onSurfaceReady: (Surface) -> Unit,
    onSurfaceDestroyed: () -> Unit,
    modifier: Modifier = Modifier,
    onSurfaceTouched: (x: Float) -> Unit,
    onHorizontalDrag: (deltaX: Float) -> Unit,
    dragSensitivityPx: Float, // e.g. 40f
) {
    // Keep the lambda references stable across recompositions, so the
    // AndroidView factory closure always calls the latest version without
    // needing to recreate the SurfaceView (which is very expensive)
    val currentOnSurfaceReady by rememberUpdatedState(onSurfaceReady)
    val currentOnSurfaceDestroyed by rememberUpdatedState(onSurfaceDestroyed)
    val currentOnSurfaceTouched by rememberUpdatedState(onSurfaceTouched)
    val currentOnHorizontalDrag by rememberUpdatedState(onHorizontalDrag)

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val context = LocalContext.current
        val screenRotationDegrees = remember(LocalConfiguration.current) {
            computeScreenRotation(context)
        }

        // Recompute whenever the video dimensions or the available box size change
        // maxWidth and max Height come from BoxWithConstraints
        val surfaceModifier = remember(videoResolution, maxWidth, maxHeight) {
            val actualVideoResolution = if (screenRotationDegrees % 180 != 0) {
                videoResolution
            } else {
                // If screen is rotated 90 or 270 deg, swap width and height
                VideoTrainerDefaults.VideoResolution(videoResolution.height, videoResolution.width)
            }
            if (actualVideoResolution.width > 0 && actualVideoResolution.height > 0) {
                val videoAspect = actualVideoResolution.width.toFloat() / actualVideoResolution.height.toFloat()
                val boxAspect   = maxWidth.value / maxHeight.value

//                Timber.d("#######C vid=${actualVideoResolution.width}x${actualVideoResolution.height} box=${maxWidth}x$maxHeight screenRotation=$screenRotationDegrees")
                if (videoAspect > boxAspect) {
                    // Video is wider than the box → constrain by width, letterbox top/bottom
                    val fittedHeight = (maxWidth.value / videoAspect).dp
                    Modifier.width(maxWidth).height(fittedHeight)
                } else {
                    // Video is taller than the box → constrain by height, pillarbox left/right
                    val fittedWidth = (maxHeight.value * videoAspect).dp
                    Modifier.width(fittedWidth).height(maxHeight)
                }
            } else {
                // Dimensions not yet known — fill the box as a safe default.
                Timber.d("#######C fillMaxSize()")
                Modifier.fillMaxSize()
            }
        }

        AndroidView(
            modifier = surfaceModifier
                .background(Color.Black),
            factory = { context ->
                SurfaceView(context).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {

                        override fun surfaceCreated(holder: SurfaceHolder) {
                            // Surface is ready — hand it to the ViewModel so MediaCodec
                            // can start rendering.
                            currentOnSurfaceReady(holder.surface)
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder, format: Int, width: Int, height: Int
                        ) {
                            // No action needed here; size changes are driven by
                            // surfaceModifier recomposition, not by MediaCodec.
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            // Surface is going away — tell the ViewModel to stop
                            // using it immediately (before the function returns).
                            currentOnSurfaceDestroyed()
                        }
                    })

                    // ── Horizontal drag detection ─────────────────────────────
                    var lastEmittedX = 0f
                    setOnTouchListener { view, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                // Anchor the "last emitted" position at finger-down.
                                lastEmittedX = event.x
                                // Notify the system this view handles the gesture,
                                // so subsequent MOVE/UP events keep arriving.
                                view.performClick()  // accessibility requirement
                                onSurfaceTouched(event.x)
                                true
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val deltaFromLastEmit = event.x - lastEmittedX
                                // Only fire once the finger has moved far enough.
                                if (abs(deltaFromLastEmit) >= dragSensitivityPx) {
                                    currentOnHorizontalDrag(event.x)
                                    // Reset the baseline so the *next* chunk is
                                    // also measured against a fresh starting point.
                                    lastEmittedX = event.x
                                }
                                true
                            }
                            MotionEvent.ACTION_UP,
                            MotionEvent.ACTION_CANCEL -> {
                                // Nothing to clean up, but consume the event.
                                true
                            }
                            else -> false
                        }
                    }
                }
            }
        )
    }
}
