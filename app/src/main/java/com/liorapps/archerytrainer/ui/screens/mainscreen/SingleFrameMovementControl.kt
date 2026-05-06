package com.liorapps.archerytrainer.ui.screens.mainscreen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Surface
import androidx.compose.ui.tooling.preview.Preview
import com.liorapps.archerytrainer.ui.theme.ArcheryTrainerTheme
import androidx.compose.ui.unit.dp
import com.liorapps.archerytrainer.ui.theme.AppTheme

/*
 By Claude Sonnet 4.6 Adaptive
 SingleFrameMovementControl — the public composable. It owns sliderPosition state so the thumb
   responds immediately on drag, and uses LaunchedEffect to stay in sync with any external
   progress changes (e.g. playback engine seeking).
 FrameStepButton — a 48 dp round dark button with a red ripple. It accepts a content lambda so
   the icon drawing is decoupled.
 DrawStepIcon — draws the ▶| / |◀ step icons directly onto a Canvas using a filled triangle + a
   narrow rectangle, so there is no icon resource dependency at all.
 FrameSeekBar — a fully custom Canvas-based slider:
   Gray full-width inactive track
   Red active track from 0 → thumb
   Draggable red thumb with a semi-transparent glow halo and a small highlight dot for depth
   onSizeChanged captures the real pixel width so drag math stays correct regardless of screen
     density or layout changes
   detectDragGestures with onDragStart handles both taps and drags in a single gesture detector
*/

/**
 * A video frame-stepping control composable.
 *
 * Displays a row with:
 *  - A round "back one frame" button on the left
 *  - A custom red seek bar (line + draggable circle thumb) in the middle
 *  - A round "forward one frame" button on the right
 *
 * @param modifier            Standard Compose modifier.
 * @param appSliderPosition            Initial/current playback position in [0f, 1f].
 * @param onSliderMoved Called whenever the user moves the thumb; receives the new
 *                            position as a percentage in [0f, 1f].
 * @param onSingleFrameForward     Called when the user taps the forward-step button.
 * @param onSingleFrameBackward    Called when the user taps the back-step button.
 */
@Composable
fun SingleFrameMovementControl(
    modifier: Modifier = Modifier,
    appSliderPosition: Float,
    onSliderMoved: (newSliderLocationInPercentage: Float) -> Unit,
    onSingleFrameForward: () -> Unit,
    onSingleFrameBackward: () -> Unit,
) {
    // Keep an internal copy so the thumb moves immediately on drag,
    // even before the caller round-trips the new value back through [progress].
    var sliderPosition by remember { mutableFloatStateOf(appSliderPosition.coerceIn(0f, 1f)) }

    // Sync with external [progress] changes (e.g. programmatic seek).
    LaunchedEffect(appSliderPosition) {
        sliderPosition = appSliderPosition.coerceIn(0f, 1f)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {

        /* ── Left button: back one frame ── */
        FrameStepButton(
//            modifier = Modifier
//                .padding(start = 0.dp, top = 0.dp, end = 8.dp, bottom = 0.dp),
            contentDescription = "Back one frame",
            onClick = onSingleFrameBackward,
        ) {
//            DrawStepIcon(forward = false)
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = null, // already set on the button
//                tint = Color(0xFFFF3B30),
                tint = AppTheme.colors.singleFrameSliderButtons,
                modifier = Modifier
                    .padding(start = 0.dp)
                    .size(36.dp)
            )
        }

        /* ── Centre: red seek bar ── */
        FrameSeekBar(
            modifier = Modifier.weight(1f),
            position = sliderPosition,
            onPositionChanged = { newPos ->
                sliderPosition = newPos // Update internal copy for quick response
                onSliderMoved(newPos)
            },
        )

        /* ── Right button: forward one frame ── */
        FrameStepButton(
//            modifier = Modifier
//                .padding(start = 8.dp, top = 0.dp, end = 0.dp, bottom = 0.dp),
            contentDescription = "Forward one frame",
            onClick = onSingleFrameForward,
        ) {
//            DrawStepIcon(forward = true)
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = null,
//                tint = Color(0xFFFF3B30),
                tint = AppTheme.colors.singleFrameSliderButtons,
                modifier = Modifier
                    .padding(end = 0.dp)
                    .size(36.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Private helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Round dark button that draws its icon via [content].
 */
@Composable
private fun FrameStepButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(48.dp)
//            .border(
//                width = 1.dp,
//                color = AppTheme.colors.singleFrameSliderButtons,
//                shape = CircleShape
//            )
//            .shadow(elevation = 4.dp, shape = CircleShape)
            .clip(CircleShape)
//            .background(Color(0xFF1E1E1E))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = AppTheme.colors.singleFrameSliderButtons),
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

///**
// * Draws a "step" icon (vertical bar + filled triangle) directly on a Canvas
// * so the composable has no icon-resource dependency.
// *
// * @param forward  If true draws ▶| (forward); otherwise draws |◀ (backward).
// */
//@Composable
//private fun BoxScope.DrawStepIcon(forward: Boolean) {
//    Canvas(modifier = Modifier.size(22.dp)) {
//        val w = size.width
//        val h = size.height
//        val barW = w * 0.14f
//        val triBase = h * 0.72f          // height of triangle
//        val triWidth = w * 0.52f         // width (depth) of triangle
//        val iconColor = Color.White
//
//        if (forward) {
//            // Triangle pointing right, then vertical bar on the right
//            val triLeft = w * 0.08f
//            val triRight = triLeft + triWidth
//            drawPath(
//                path = androidx.compose.ui.graphics.Path().apply {
//                    moveTo(triLeft, (h - triBase) / 2f)
//                    lineTo(triRight, h / 2f)
//                    lineTo(triLeft, (h + triBase) / 2f)
//                    close()
//                },
//                color = iconColor,
//            )
//            // Vertical bar on the right
//            drawRect(
//                color = iconColor,
//                topLeft = Offset(triRight + w * 0.04f, (h - triBase) / 2f),
//                size = androidx.compose.ui.geometry.Size(barW, triBase),
//            )
//        } else {
//            // Vertical bar on the left, then triangle pointing left
//            val barLeft = w * 0.08f
//            drawRect(
//                color = iconColor,
//                topLeft = Offset(barLeft, (h - triBase) / 2f),
//                size = androidx.compose.ui.geometry.Size(barW, triBase),
//            )
//            val triRight = w * 0.92f
//            val triLeft = triRight - triWidth
//            drawPath(
//                path = androidx.compose.ui.graphics.Path().apply {
//                    moveTo(triRight, (h - triBase) / 2f)
//                    lineTo(triLeft, h / 2f)
//                    lineTo(triRight, (h + triBase) / 2f)
//                    close()
//                },
//                color = iconColor,
//            )
//        }
//    }
//}


/**
 * Custom red seek bar: inactive gray track, active red track, red draggable thumb.
 *
 * The track is drawn with horizontal padding equal to the thumb's glow radius so
 * that the thumb is never flush with the canvas edge. This ensures the user can
 * comfortably drag the thumb even at the minimum and maximum positions, because
 * the whole thumb circle remains inside the touch-target area.
 *
 * @param position          Current position in [0f, 1f].
 * @param onPositionChanged Emits the new position in [0f, 1f] while the user drags.
 */
@Composable
private fun FrameSeekBar(
    position: Float,
    onPositionChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    // Reserve this many px on each side so the thumb is never flush with the canvas edge.
    // Using the glow radius keeps the halo fully visible even at position 0f / 1f.
    val edgePaddingPx = with(density) { 13.dp.toPx() } // matches glowR below

    // Pixel width of the canvas, captured once layout is known.
    var canvasWidthPx by remember { mutableFloatStateOf(0f) }

    // Derived track geometry, recomputed whenever the canvas width changes.
    // trackStart / trackEnd are the x-coordinates of the two track end-points.
    // trackLength is the distance the thumb actually travels.
    val trackStart = edgePaddingPx
    val trackLength = (canvasWidthPx - 2f * edgePaddingPx).coerceAtLeast(0f)
    val singleFrameSliderActiveTrackLineColor = AppTheme.colors.singleFrameSliderActiveTrackLine
    val singleFrameSliderInactiveTrackLineColor = AppTheme.colors.singleFrameSliderInactiveTrackLine
    val singleFrameSliderThumb = AppTheme.colors.singleFrameSliderThumb
    val singleFrameSliderThumbGlow = AppTheme.colors.singleFrameSliderThumbGlow
    val singleFrameSliderThumbHighlight = AppTheme.colors.singleFrameSliderThumbHighlight

    Canvas(
        modifier = modifier
            .height(40.dp)
            .onSizeChanged { canvasWidthPx = it.width.toFloat() }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        if (trackLength > 0f) {
                            // Map the raw x coordinate into [0f, 1f] relative to the inset track.
                            onPositionChanged(
                                ((offset.x - trackStart) / trackLength).coerceIn(0f, 1f)
                            )
                        }
                    },
                    onDrag = { change, _ ->
                        if (trackLength > 0f) {
                            onPositionChanged(
                                ((change.position.x - trackStart) / trackLength).coerceIn(0f, 1f)
                            )
                        }
                    },
                )
            }
            .semantics { contentDescription = "Seek bar" },
    ) {
        val cy         = size.height / 2f
        val trackStroke = 3.dp.toPx()
        val thumbR     = 9.dp.toPx()
        val glowR      = 13.dp.toPx()          // must equal edgePaddingPx above

        // trackEnd mirrors trackStart on the right side.
        val trackEnd   = size.width - edgePaddingPx

        // Thumb x-position is now within [trackStart, trackEnd] instead of [0, width].
        val thumbX     = trackStart + position * trackLength

        // ── Inactive track (full inset width, dark grey) ──
        drawLine(
            color       = singleFrameSliderInactiveTrackLineColor,
            start       = Offset(trackStart, cy),
            end         = Offset(trackEnd, cy),
            strokeWidth = trackStroke,
            cap         = StrokeCap.Round,
        )

        // ── Active track (left of thumb, red) ──
        if (thumbX > trackStart) {
            drawLine(
                color       = singleFrameSliderActiveTrackLineColor,
                start       = Offset(trackStart, cy),
                end         = Offset(thumbX, cy),
                strokeWidth = trackStroke,
                cap         = StrokeCap.Round,
            )
        }

        // ── Thumb glow (semi-transparent halo) ──
        drawCircle(
            color  = singleFrameSliderThumbGlow,
            radius = glowR,
            center = Offset(thumbX, cy),
        )

        // ── Thumb (solid red circle) ──
        drawCircle(
            color  = singleFrameSliderThumb,
            radius = thumbR,
            center = Offset(thumbX, cy),
        )

        // ── Thumb highlight (small bright centre dot) ──
        drawCircle(
            color  = singleFrameSliderThumbHighlight,
            radius = thumbR * 0.35f,
            center = Offset(thumbX, cy),
        )
    }
}


///**
// * Custom red seek bar: inactive gray track, active red track, red draggable thumb.
// *
// * @param position          Current position in [0f, 1f].
// * @param onPositionChanged Emits the new position in [0f, 1f] while the user drags.
// */
//@Composable
//private fun FrameSeekBar(
//    position: Float,
//    onPositionChanged: (Float) -> Unit,
//    modifier: Modifier = Modifier,
//) {
//    // Pixel width of the track, captured once layout is known.
//    var trackWidthPx by remember { mutableFloatStateOf(0f) }
//
//    Canvas(
//        modifier = modifier
//            .height(40.dp)                      // tall touch target
//            .onSizeChanged { trackWidthPx = it.width.toFloat() }
//            .pointerInput(Unit) {
//                detectDragGestures(
//                    onDragStart = { offset ->
//                        if (trackWidthPx > 0f) {
//                            onPositionChanged((offset.x / trackWidthPx).coerceIn(0f, 1f))
//                        }
//                    },
//                    onDrag = { change, _ ->
//                        if (trackWidthPx > 0f) {
//                            onPositionChanged((change.position.x / trackWidthPx).coerceIn(0f, 1f)
//                            )
//                        }
//                    },
//                )
//            }
//            .semantics { contentDescription = "Seek bar" },
//    ) {
//        val cy = size.height / 2f
//        val trackStroke = 3.dp.toPx()
//        val thumbR = 9.dp.toPx()
//        val glowR = 13.dp.toPx()
//        val thumbX = position * size.width
//
//        // ── Inactive track (full width, dark grey) ──
//        drawLine(
//            color = Color(0xFF444444),
//            start = Offset(0f, cy),
//            end = Offset(size.width, cy),
//            strokeWidth = trackStroke,
//            cap = StrokeCap.Round,
//        )
//
//        // ── Active track (left of thumb, red) ──
//        if (thumbX > 0f) {
//            drawLine(
//                color = Color(0xFFFF3B30),
//                start = Offset(0f, cy),
//                end = Offset(thumbX, cy),
//                strokeWidth = trackStroke,
//                cap = StrokeCap.Round,
//            )
//        }
//
//        // ── Thumb glow (semi-transparent halo) ──
//        drawCircle(
//            color = Color(0x44FF3B30),
//            radius = glowR,
//            center = Offset(thumbX, cy),
//        )
//
//        // ── Thumb (solid red circle) ──
//        drawCircle(
//            color = Color(0xFFFF3B30),
//            radius = thumbR,
//            center = Offset(thumbX, cy),
//        )
//
//        // ── Thumb highlight (small bright centre dot) ──
//        drawCircle(
//            color = Color(0xFFFF7A73),
//            radius = thumbR * 0.35f,
//            center = Offset(thumbX, cy),
//        )
//    }
//}

@Preview(showBackground = true)
@Composable
private fun SingleFrameMovementControlPreview() {
    ArcheryTrainerTheme {
        Surface(color = Color.Black) {
            var progress by remember { mutableFloatStateOf(0.5f) }
            SingleFrameMovementControl(
                appSliderPosition = progress,
                onSliderMoved = { progress = it },
                onSingleFrameForward = {},
                onSingleFrameBackward = {}
            )
        }
    }
}
