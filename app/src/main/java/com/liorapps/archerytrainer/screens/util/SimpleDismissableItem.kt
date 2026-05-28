package com.liorapps.archerytrainer.screens.util

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// See: https://proandroiddev.com/swipe-to-dismiss-with-compose-material-3-38445e0143f7
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleDismissibleItem(
    icon: @Composable (BoxScope.() -> Unit),
    onDelete: () -> Unit,
    content: @Composable (BoxScope.() -> Unit),
) {
    // Initialize the state without confirmValueChange (confirmValueChange is deprecated)
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance * 0.40f } // SwipeToDismissBoxDefaults.positionalThreshold
    )
    var isDeleting by remember { mutableStateOf(false) }

    // Launch deletion after exit animation completes
    LaunchedEffect(isDeleting) {
        if (isDeleting) {
            delay(300) // must match the animation duration
            onDelete()
        }
    }

    // Prevent onDismiss from being handled multiple times
    var itemRemoved by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = !isDeleting,
        exit = fadeOut(animationSpec = tween(300)) + shrinkVertically(animationSpec = tween(300))
    ) {
        SwipeToDismissBox(
            state = dismissState,
            modifier = Modifier.fillMaxWidth(),
            onDismiss = { dismissValue ->
                when (dismissValue) {
                    SwipeToDismissBoxValue.EndToStart -> {
                        if (!itemRemoved) {
                            itemRemoved = true
                            isDeleting = true
                        }
                    }
                    SwipeToDismissBoxValue.StartToEnd -> { /* Handle swipe from left-to-right */ }
                    SwipeToDismissBoxValue.Settled -> { /* Do nothing */ }
                }
            },
            enableDismissFromStartToEnd = false, // Disables swiping from left to right
            backgroundContent = {
                BackgroundSwipeContent(swipeDirection = dismissState.dismissDirection) {
                    icon()
                }
            }
        ) {
            // Actual item (foreground) content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Transparent)
                    // .onGloballyPositioned { Timber.d("Box wrapping content: ${it.size.height}") },
            ) {
                content()
            }
        }
    }
}

@Composable
private fun BackgroundSwipeContent(
    swipeDirection: SwipeToDismissBoxValue,
    icon: @Composable (BoxScope.() -> Unit),
) {
    val color by animateColorAsState(
        targetValue = when (swipeDirection) {
            SwipeToDismissBoxValue.StartToEnd -> Color(0xFF4CAF50)
            SwipeToDismissBoxValue.EndToStart -> Color(0xFFF44336)
            SwipeToDismissBoxValue.Settled -> Color.Transparent
        },
        label = "background color"
    )
//    val color by animateColorAsState(
//        targetValue = when (dismissState.targetValue) {
//            SwipeToDismissBoxValue.EndToStart -> Color.Red
//            else -> Color.LightGray
//        },
//        label = "BackgroundColorAnimation"
//    )
    val alignment = when (swipeDirection) {
        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
        SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
        SwipeToDismissBoxValue.Settled -> Alignment.Center
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color)
            .padding(horizontal = 20.dp),
        contentAlignment = alignment
    ) {
        icon()
    }
}