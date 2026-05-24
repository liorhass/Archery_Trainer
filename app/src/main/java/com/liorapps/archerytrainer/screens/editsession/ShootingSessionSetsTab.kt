package com.liorapps.archerytrainer.screens.editsession

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liorapps.archerytrainer.db.ShootingSetWithSession
import com.liorapps.archerytrainer.screens.util.ConfirmationDialog
import com.liorapps.archerytrainer.screens.util.formatDate
import com.liorapps.archerytrainer.screens.util.formatDateTime
import com.liorapps.archerytrainer.screens.util.formatTime
import kotlin.math.abs

@Composable
fun ShootingSessionSetsTab(
    viewModel: EditShootingSessionViewModel,
    uiState: EditShootingSessionState,
    innerPadding: PaddingValues,
) {
    // The set awaiting the user's confirmation before it is deleted
    var setToDelete by remember { mutableStateOf<ShootingSetWithSession?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        if (uiState.sets.isEmpty()) {
            Text(
                text = "No Sets yet",
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            SetList(
                sets            = uiState.sets,
                onSetClick      = viewModel::onSetClick,
                onSwipeToDelete = { set -> setToDelete = set }
            )
        }

        // Delete confirmation dialog - rendered outside the Scaffold so it floats above everything
        setToDelete?.let { set ->
            val dateText = remember(set.dateTimeUtc) { formatDateTime(set.dateTimeUtc) }
            ConfirmationDialog(
                title = "Delete Set?",
                text = "The set from $dateText will be permanently deleted.",
                onConfirm = {
                    viewModel.onDeleteSetConfirmed(set)
                    setToDelete = null
                },
                onDismiss = {
                    // User cancelled — simply close the dialog.  The card has
                    // already snapped back, so no further UI action is needed.
                    setToDelete = null
                }
            )
        }

    }
}

@Composable
private fun SetList(
    sets: List<ShootingSetWithSession>,
    onSetClick: (Long) -> Unit,
    onSwipeToDelete: (ShootingSetWithSession) -> Unit
) {
    LazyColumn(
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(
            items = sets,
            key   = { it.id }          // stable keys = efficient recomposition
        ) { set ->
            SwipeableSetCard(
                set             = set,
                onClick         = { onSetClick(set.id) },
                onSwipeToDelete = { onSwipeToDelete(set) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableSetCard(
    set: ShootingSetWithSession,
    onClick: () -> Unit,
    onSwipeToDelete: () -> Unit
) {
    // confirmValueChange returns false so the card ALWAYS snaps back. Deletion happens only when
    // the user taps "Delete" in the dialog
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onSwipeToDelete()
            }
            false   // prevent the item from staying in dismissed position
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.40f }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,        // only left-swipe
        backgroundContent = { SwipeBackground(dismissState) }
    ) {
        SetCard(set = set, onClick = onClick)
    }
}

// The red background that is revealed while the user swipes.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeBackground(dismissState: SwipeToDismissBoxState) {
//    val isActive = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart
    BoxWithConstraints {
        val cardWidthPx = constraints.maxWidth.toFloat()
        val isActive = remember(dismissState, cardWidthPx) {
            derivedStateOf {
                val offset = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                    dismissState.requireOffset()
                } else {
                    0f
                }
                (abs(offset) / cardWidthPx) > 0.25f
            }
        }.value

        val bgColor by animateColorAsState(
            targetValue = if (isActive) MaterialTheme.colorScheme.errorContainer else Color.Transparent,
            label = "swipeBackground"
        )
        val iconScale by animateFloatAsState(
            targetValue = if (isActive) 1.5f else 0.95f,
            label = "deleteIconScale"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor, shape = MaterialTheme.shapes.medium)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.scale(iconScale)
            )
        }
    }
}

@Composable
private fun SetCard(
    set: ShootingSetWithSession,
    onClick: () -> Unit
) {
    Card(
        onClick    = onClick,
        modifier   = Modifier.fillMaxWidth(),
        shape      = MaterialTheme.shapes.medium,
        elevation  = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment   = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ShotCountBadge(count = set.numberOfShots)
            SetInfo(set = set, modifier = Modifier.weight(1f))
        }
    }
}
// Left-hand pill showing the shot count.
@Composable
private fun ShotCountBadge(count: Int) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.width(60.dp)
    ) {
        Column(
            modifier            = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text       = count.toString(),
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text  = "shots",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

// Right-hand area: date, time, and optional comment.
@Composable
private fun SetInfo(set: ShootingSetWithSession, modifier: Modifier = Modifier) {
    // Format once per unique timestamp — avoids repeated object allocation
    // during recomposition.
    val dateText = remember(set.dateTimeUtc) { formatDate(set.dateTimeUtc) }
    val timeText = remember(set.dateTimeUtc) { formatTime(set.dateTimeUtc) }

    Column(
        modifier            = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text       = dateText,
            style      = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text  = timeText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

