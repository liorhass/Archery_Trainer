package com.liorapps.archerytrainer.screens.editsession

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

// ── ID generator ──────────────────────────────────────────────────────────────
// todo: 2brm   private val floaterIdCounter = AtomicLong(0L)

// ── Tuneable constants ────────────────────────────────────────────────────────
private val FLOAT_DISTANCE  = 50.dp   // how far up the label travels
private const val ANIMATION_MS  = 1000  // total animation duration (ms)
private const val FADE_DELAY_MS = 200L // ms the label stays fully opaque before fading
private val floatTextColor = Color(0xFF8FC3C5)
private val floatTextSize = 24.sp

// ─────────────────────────────────────────────────────────────────────────────
// ScoreDisplay
// ─────────────────────────────────────────────────────────────────────────────

/** Represents a single floating "+N" label instance.
 * Each gets a unique ID so Compose can track and animate them independently  */
data class FloatingLabel(
    val id: Long,
    val text: String,
)


/**
 * Displays the current score and spawns a floating "+N" label each time the
 * score increases. Multiple labels stack and animate independently.
 *
 * Usage:
 *   val viewModel: ScoreViewModel = viewModel()
 *   ScoreDisplay(viewModel, modifier = Modifier.wrapContentSize())
 */
@Composable
fun TextWithFloater(
//    viewModel: ScoreViewModel,
    baseText: String,
    activeFloaters: SnapshotStateList<FloatingLabel>,
    modifier: Modifier = Modifier,
) {
//    Timber.d("#### ScoreDisplay(): baseText=$baseText nActiveFloaters=${activeFloaters.size} float=${if (activeFloaters.isNotEmpty()) activeFloaters[0].text else "none"}")
//    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
//    val activeFloaters = remember { mutableStateListOf<FloatingLabel>() }

    // Collect one-shot delta events and spawn a floater per event.
//    LaunchedEffect(Unit) {
//        viewModel.scoreEvents.collect { delta ->
//            activeFloaters.add(
//                FloatingLabel(
//                    id   = floaterIdCounter.incrementAndGet(),
//                    text = "+$delta",
//                )
//            )
//        }
//    }

    // Box does not clip children by default, so floaters render freely above it.
    Box(
        contentAlignment = Alignment.BottomStart,
        modifier = modifier,
    ) {
        // The score text sits at the bottom-center anchor of the Box.
        Text(
//            text       = uiState.score.toString(),
            text       = baseText,
//            fontSize   = 48.sp,
//            fontWeight = FontWeight.Bold,
        )

        // Each floater is keyed by its unique ID so Compose always creates a
        // fresh composable (and fresh Animatables) rather than reusing one.
        activeFloaters.forEach { floater ->
//            Timber.d("#### ScoreDisplay(): float=${if (activeFloaters.isNotEmpty()) activeFloaters[0].text else "none"}")
            key(floater.id) {
                FloatingLabelItem(
                    label      = floater,
                    onFinished = {
                        activeFloaters.remove(floater)
                    },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FloatingLabelItem
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A single animated "+N" label. Shares the BottomCenter anchor of its parent
 * Box, then rises [FLOAT_DISTANCE] upward while fading to transparent.
 *
 * [onFinished] is called once both animations complete so the parent can
 * remove this item from the list and let Compose dispose the composable.
 */
@Composable
private fun BoxScope.FloatingLabelItem(
    label: FloatingLabel,
    onFinished: () -> Unit,
) {
    // Read density in composable scope (never inside a coroutine).
    val floatDistancePx = with(LocalDensity.current) { FLOAT_DISTANCE.toPx() }

    val offsetY = remember { Animatable(0f) }   // px; 0 = label origin, negative = upward
    val alpha   = remember { Animatable(1f) }   // 1 = fully opaque

    LaunchedEffect(label.id) {
        // Rise and fade run in parallel via two child coroutines.
        coroutineScope {
            launch {
                offsetY.animateTo(
                    targetValue   = -floatDistancePx,
                    animationSpec = tween(durationMillis = ANIMATION_MS),
                )
            }
            launch {
                delay(FADE_DELAY_MS)    // brief pause — label pops in solidly first
                alpha.animateTo(
                    targetValue   = 0f,
                    animationSpec = tween(durationMillis = ANIMATION_MS - FADE_DELAY_MS.toInt()),
                )
            }
        }
        // Both child coroutines have finished — safe to remove from the list.
        onFinished()
    }

    Text(
        text       = label.text,
        fontSize   = floatTextSize,
        fontWeight = FontWeight.Bold,
        color      = floatTextColor,   // green — swap to your brand color
        modifier   = Modifier
            .matchParentSize()
            // layout{} lets us shift the placement by the animated offset without
            // reserving extra space in the parent, so the Box stays naturally sized.
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    placeable.placeRelative(
                        x = 0,
                        y = offsetY.value.roundToInt(),
                    )
                }
            }
            .alpha(alpha.value),
    )
}
