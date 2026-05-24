package com.liorapps.archerytrainer.screens.sessions

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liorapps.archerytrainer.db.ShootingSessionWithStats
import com.liorapps.archerytrainer.navigation.ATNavKey
import com.liorapps.archerytrainer.screens.util.ConfirmationDialog
import com.liorapps.archerytrainer.screens.util.formatDate
import com.liorapps.archerytrainer.screens.util.formatDateTime
import com.liorapps.archerytrainer.screens.util.formatTime
import kotlin.math.abs

// ---------------------------------------------------------------------------
// Screen entry point
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShootingSessionListScreen(
    viewModel: ShootingSessionListViewModel,
    navigateTo: (ATNavKey) -> Unit,
    onOpenDrawer: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // The session awaiting the user's confirmation before it is deleted
    var sessionToDelete by remember { mutableStateOf<ShootingSessionWithStats?>(null) }

    // Collect one-shot navigation events emitted by the ViewModel
    LaunchedEffect(Unit) { //todo: seems that this is a wasteful round trip VM->Screen->VM. The VM might as well navigate directly
        viewModel.navigationEvent.collect { key -> navigateTo(key) }
    }

    Scaffold(
        topBar = { ShootingSessionListTopBar(onOpenDrawer = onOpenDrawer) },
        floatingActionButton = {
            FloatingActionButton(
                onClick            = viewModel::onNewShootingSessionClick,
                containerColor     = MaterialTheme.colorScheme.primary,
                contentColor       = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector        = Icons.Default.Add,
                    contentDescription = "New session"
                )
            }
        }
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {

                is ShootingSessionListUiState.Loading ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                is ShootingSessionListUiState.Empty ->
                    EmptyState(modifier = Modifier.align(Alignment.Center))

                is ShootingSessionListUiState.Success ->
                    SessionList(
                        sessions       = state.sessions,
                        onSessionClick = viewModel::onSessionClick,
                        onSwipeToDelete = { session -> sessionToDelete = session }
                    )
            }
        }
    }

    // Delete confirmation dialog - rendered outside the Scaffold so it floats above everything
    sessionToDelete?.let { session ->
        val dateText = remember(session.dateTimeUtc) { formatDateTime(session.dateTimeUtc) }
        ConfirmationDialog(
            title = "Delete Session?",
            text = "The session from $dateText will be permanently deleted.",
            onConfirm = {
                viewModel.onDeleteConfirmed(session)
                sessionToDelete = null
            },
            onDismiss = {
                // User canceled — simply close the dialog.  The card has
                // already snapped back, so no further UI action is needed.
                sessionToDelete = null
            }
        )
    }
}

@Composable
private fun SessionList(
    sessions: List<ShootingSessionWithStats>,
    onSessionClick: (Long) -> Unit,
    onSwipeToDelete: (ShootingSessionWithStats) -> Unit
) {
    LazyColumn(
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(
            items = sessions,
            key   = { it.id }          // stable keys = efficient recomposition
        ) { session ->
            SwipeableSessionCard(
                session         = session,
                onClick         = { onSessionClick(session.id) },
                onSwipeToDelete = { onSwipeToDelete(session) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableSessionCard(
    session: ShootingSessionWithStats,
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
        SessionCard(session = session, onClick = onClick)
    }
}

// The red background that is revealed while the user swipes.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeBackground(dismissState: SwipeToDismissBoxState) {
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
            targetValue = if (isActive) 1.5f else 0.75f,
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
private fun SessionCard(
    session: ShootingSessionWithStats,
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
            ShotCountBadge(count = session.totalShots)
            SessionInfo(
                session = session,
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.Top)
                    .padding(top = 6.dp)
            )
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
private fun SessionInfo(session: ShootingSessionWithStats, modifier: Modifier = Modifier) {
    // Format once per unique timestamp — avoids repeated object allocation
    // during recomposition.
    val dateText = remember(session.dateTimeUtc) { formatDate(session.dateTimeUtc) }
    val timeText = remember(session.dateTimeUtc) { formatTime(session.dateTimeUtc) }

    Column(
        modifier            = modifier,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text       = "$dateText $timeText",
            style      = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
//        Text(
//            text  = timeText,
//            style = MaterialTheme.typography.bodyMedium,
//            color = MaterialTheme.colorScheme.onSurfaceVariant
//        )
        if (session.comment.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text     = session.comment,
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Empty state
// ---------------------------------------------------------------------------

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier            = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text  = "🎯",
            style = MaterialTheme.typography.displayMedium
        )
        Text(
            text       = "No sessions yet",
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text      = "Tap the + button below to record your first session.",
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ShootingSessionListTopBar(
    onOpenDrawer: () -> Unit,
//    onNavigateToSettings: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = "Shooting Sessions",
                style    = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor    = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Default.Menu, "Open drawer")
            }
        },
//        actions = {
//            IconButton(onClick = onNavigateToSettings) {
//                Icon(Icons.Rounded.Settings, contentDescription = "Settings")
//            }
//        }
    )
}

@Preview
@Composable
private fun SwipeableSessionCardPreview1(
) {
    SessionCard(
        session = ShootingSessionWithStats(
            id = 1L,
            dateTimeUtc = 222222222L,
            comment = "zzz ejh fjhe eh j hjeh je\nehr jehjh ue iehr \n iher uhwe uf ewu",
            totalShots = 122
        ),
        onClick = {},
    )
}
@Preview
@Composable
private fun SwipeableSessionCardPreview2(
) {
    SessionCard(
        session = ShootingSessionWithStats(
            id = 1L,
            dateTimeUtc = 222222222L,
            comment = "",
            totalShots = 122
        ),
        onClick = {},
    )
}
