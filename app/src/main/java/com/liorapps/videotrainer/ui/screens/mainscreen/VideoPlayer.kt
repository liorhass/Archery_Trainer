package com.liorapps.videotrainer.ui.screens.mainscreen

import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

@Composable
fun VideoPlayer(
    videoResolution: VideoTrainerDefaults.VideoResolution,
    onSurfaceReady: (Surface) -> Unit,
    onSurfaceDestroyed: () -> Unit,
    modifier: Modifier = Modifier,
) {
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

                Timber.d("#######C vid=${actualVideoResolution.width}x${actualVideoResolution.height} box=${maxWidth}x$maxHeight screenRotation=$screenRotationDegrees")
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
                            onSurfaceReady(holder.surface)
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
                            onSurfaceDestroyed()
                        }
                    })
                }
            }
        )
    }
}
