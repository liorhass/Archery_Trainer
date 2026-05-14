package com.liorapps.archerytrainer.screens.video.ui

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import kotlin.math.ceil

/**
 * A circular countdown overlay intended to be placed inside a [Box] that wraps
 * a SurfaceView-based video player.  It renders a shrinking arc ring with a
 * remaining-seconds digit in the centre and a subtle pulse animation.
 *
 * Typical usage inside your existing Box:
 *
 * ```
 * Box {
 *     AndroidView(...)          // SurfaceView
 *     // … your other buttons …
 *
 *     if (isBuffering) {
 *         BufferingCountdown(
 *             durationSeconds  = bufferDurationSeconds,
 *             isBufferingDone  = isBufferingDone,
 *             onFinished       = { isBuffering = false }
 *         )
 *     }
 * }
 * ```
 *
 * @param durationSeconds   Known buffering duration, 1–30 s.
 * @param isBufferingDone   Set to `true` from outside (e.g. a ViewModel state) to
 *                          trigger early completion without waiting for the timer.
 * @param onBufferingTimeTerminated        Called exactly once when the timer expires **or** when
 *                          [isBufferingDone] becomes `true`, whichever comes first.
 * @param modifier          Applied to the full-size overlay container.
 * @param ringColor         Colour of the depleting arc.
 * @param trackColor        Colour of the static background circle behind the arc.
 * @param textColor         Colour of the countdown digit.
 * @param ringStrokeWidth   Stroke width of both the arc and the track.
 * @param ringSize          Outer diameter of the ring.
 * @param fontSize          Size of the centre countdown digit.
 */
@Composable
fun BufferingCountdown(
    durationSeconds: Int,
    isBufferingDone: Boolean,
    onBufferingTimeTerminated: () -> Unit,
    modifier: Modifier = Modifier,
    ringColor: Color = Color(0xFF4FC3F7),      // light blue
    trackColor: Color = Color(0x33FFFFFF),     // faint white
    textColor: Color = Color.White,
    ringStrokeWidth: Dp = 8.dp,
    ringSize: Dp = 120.dp,
    fontSize: TextUnit = 36.sp,
) {
    // ── 1. Timing state ──────────────────────────────────────────────────────

    val totalMs = remember(durationSeconds) { durationSeconds * 1_000f }
    var elapsedMs by remember { mutableFloatStateOf(0f) }

    // Drive the countdown with frame-accurate timing.
    LaunchedEffect(durationSeconds) {
        var startTime = -1L
        while (isActive) {
            withFrameMillis { frameMs ->
                if (startTime < 0L) startTime = frameMs
                elapsedMs = (frameMs - startTime).toFloat()
            }
            if (elapsedMs >= totalMs) {
                onBufferingTimeTerminated()
                break
            }
        }
    }

    // React to an externally signalled early finish.
    LaunchedEffect(isBufferingDone) {
        if (isBufferingDone) onBufferingTimeTerminated()
    }

    // ── 2. Derived display values ─────────────────────────────────────────────

    // 1.0 = full ring (just started) → 0.0 = empty ring (done)
    val progress = ((totalMs - elapsedMs) / totalMs).coerceIn(0f, 1f)

    // Whole seconds remaining, clamped so we never show 0 until truly done.
    val remainingSeconds = ceil((totalMs - elapsedMs) / 1_000f)
        .toInt()
        .coerceIn(0, durationSeconds)

    // ── 3. Pulse animation ───────────────────────────────────────────────────

    val infiniteTransition = rememberInfiniteTransition(label = "bufferingPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = InfiniteRepeatableSpec(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // ── 4. Layout ────────────────────────────────────────────────────────────

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Inner Box applies the pulse scale to both the ring canvas and the label
        // so they move together as a single unit.
        Box(
            modifier = Modifier.scale(pulseScale),
            contentAlignment = Alignment.Center
        ) {
            // Ring drawn on a Compose Canvas
            Canvas(modifier = Modifier.size(ringSize)) {

                val strokePx = ringStrokeWidth.toPx()
                val inset = strokePx / 2f

                // Full circle (track)
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - strokePx, size.height - strokePx),
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )

                // Depleting arc, starting from the top (–90°), sweeping clockwise
                if (progress > 0f) {
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - strokePx, size.height - strokePx),
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                }
            }

            // Countdown digit centred inside the ring
//            Text(
//                text = remainingSeconds.toString(),
//                color = textColor,
//                fontSize = fontSize,
//                fontWeight = FontWeight.Bold
//            )
            // We use BasicTextField instead of a simpler Text() because we need a shadow effect
            // so text can be read when placed over a video frame (that might be with the same
            // colors as the text)
            val state = rememberTextFieldState("")
            state.edit { replace(0, length, remainingSeconds.toString()) }
            BasicTextField(
                state = state,
                textStyle = TextStyle(
                    textAlign = TextAlign.Center,
                    color = textColor,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    shadow = Shadow(
                        color = Color.Black,
                        offset = Offset(2f, 2f),
                        blurRadius = 12f
                    )
                ),
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}
