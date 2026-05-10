package com.liorapps.archerytrainer.screens.editsession

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Main Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditShootingSessionScreen(
    viewModel: EditShootingSessionViewModel,
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // ── Dialogs (rendered above the Scaffold) ─────────────────────────────────

    if (uiState.showEditCommentDialog) {
        EditCommentDialog(
            draft = uiState.commentDraft,
            onDraftChanged = viewModel::onCommentDraftChanged,
            onConfirm = viewModel::onCommentConfirmed,
            onDismiss = viewModel::onCommentDismissed,
        )
    }

    if (uiState.showScoreDialog) {
        EnterScoreDialog(
            draft = uiState.scoreDraft,
            isValid = uiState.isScoreInputValid,
            onDraftChanged = viewModel::onScoreDraftChanged,
            onConfirm = viewModel::onScoreConfirmed,
            onDismiss = viewModel::onScoreDismissed,
        )
    }

    if (uiState.showEditButtonDialog) {
        EditButtonValueDialog(
            draft = uiState.buttonValueDraft,
            isValid = uiState.isButtonValueInputValid,
            onDraftChanged = viewModel::onButtonValueDraftChanged,
            onConfirm = viewModel::onButtonValueConfirmed,
            onDismiss = viewModel::onButtonValueDismissed,
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back",
                        )
                    }
                },
                title = {
                    Text(
                        text = formatSessionDateTime(uiState.sessionDateTimeUtc),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                actions = {
                    IconButton(onClick = viewModel::onEditCommentClicked) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit")
                    }
//                    TextButton(onClick = viewModel::onEditCommentClicked) {
//                        Text("Edit")
//                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                // "Session" tab – always selected; this is the only implemented tab.
                NavigationBarItem(
                    selected = true,
                    onClick = { /* already on this tab */ },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.DateRange,
                            contentDescription = null,
                        )
                    },
                    label = { Text("Session") },
                )
                // "Sets" tab – not yet implemented; silently does nothing.
                NavigationBarItem(
                    selected = false,
                    onClick = { /* TODO: navigate to Sets screen */ },
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = null,
                        )
                    },
                    label = { Text("Sets") },
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 1. Optional session description / comment
//            if (!uiState.comment.isNullOrBlank()) {
            if (!uiState.comment.isBlank()) {
                SessionCommentSection(comment = uiState.comment)
            }

            // 2. Arrow and score totals
            StatsCard(uiState = uiState)

            // 3. Add-Set button grid
            AddSetSection(
                buttonValues = uiState.buttonValues,
                onButtonTapped = viewModel::onSetButtonTapped,
                onButtonLongPressed = viewModel::onSetButtonLongPressed,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Session Comment Section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SessionCommentSection(comment: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        // The inner Box is constrained to ~3 lines tall (bodyMedium lineHeight ≈ 20 sp).
        // verticalScroll allows the user to read longer comments by scrolling.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 60.dp)          // ≈ 3 × 20 dp line height
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = comment,
                    style = MaterialTheme.typography.bodyMedium
                        .copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Stats Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatsCard(uiState: EditShootingSessionState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Total arrows – always shown
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "🏹",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Total Arrows:  ${uiState.totalArrows}",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            // Total + average score – only when scoring is enabled AND at least one
            // set in the session carries a score.
            if (EditShootingSessionViewModel.SETS_HAVE_SCORE && uiState.hasAnyScore) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "🎯",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Total Score:  ${uiState.totalScore}" +
                                "     Avg: ${"%.1f".format(uiState.averageScore)}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Add Set Section  (3 rows × 4 buttons)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AddSetSection(
    buttonValues: List<Int>,
    onButtonTapped: (Int) -> Unit,
    onButtonLongPressed: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

        // ── Section header ────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text(
                text = "   Add Set   ",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            HorizontalDivider(modifier = Modifier.weight(1f))
        }

        // ── Button grid ───────────────────────────────────────────────────────
        buttonValues.chunked(4).forEachIndexed { rowIndex, rowValues ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowValues.forEachIndexed { colIndex, value ->
                    val globalIndex = rowIndex * 4 + colIndex
                    SetButton(
                        modifier = Modifier.weight(1f),
                        value = value,
                        onTap = { onButtonTapped(value) },
                        onLongPress = { onButtonLongPressed(globalIndex) },
                    )
                }
            }
        }

        // ── Hint text ─────────────────────────────────────────────────────────
        Text(
            text = "Long-press a button to change its value",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            textAlign = TextAlign.Center,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Set Button
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SetButton(
    modifier: Modifier = Modifier,
    value: Int,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    Surface(
        modifier = modifier
            .aspectRatio(1f)
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress,
            ),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 4.dp,
        shadowElevation = 2.dp,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Dialogs
// ─────────────────────────────────────────────────────────────────────────────

/** Dialog for editing the optional session description / comment. */
@Composable
private fun EditCommentDialog(
    draft: String,
    onDraftChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Description") },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChanged,   // ViewModel enforces the 1 000-char cap
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Description") },
                placeholder = { Text("Add a description for this session…") },
                minLines = 3,
                maxLines = 8,
                supportingText = {
                    Text(
                        text = "${draft.length} / 1000",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.None,   // allow Enter key to insert newlines
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** Dialog that asks the user to enter a score (0–999) before the Set is saved. */
@Composable
private fun EnterScoreDialog(
    draft: String,
    isValid: Boolean,
    onDraftChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val showError = draft.isNotEmpty() && !isValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Score") },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { onDraftChanged(it.take(3)) },  // hard cap at 3 chars
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Score (0 – 999)") },
                isError = showError,
                supportingText = {
                    if (showError) {
                        Text("Enter a whole number between 0 and 999")
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = isValid,
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** Dialog that lets the user change the arrow-count on one of the 12 grid buttons. */
@Composable
private fun EditButtonValueDialog(
    draft: String,
    isValid: Boolean,
    onDraftChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val showError = draft.isNotEmpty() && !isValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Button Value") },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { onDraftChanged(it.take(3)) },  // hard cap at 3 chars
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Arrows (1 – 999)") },
                isError = showError,
                supportingText = {
                    if (showError) {
                        Text("Enter a whole number between 1 and 999")
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = isValid,
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Date / Time Helper
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Formats a UTC epoch-millisecond value as  "Mon, 14.5.26 14:33"
 * using the device's local time zone.
 *
 * Note: `JavaTextStyle` is an alias for `java.time.format.TextStyle`
 * to avoid clashing with Compose's own `TextStyle`.
 */
private fun formatSessionDateTime(utcMillis: Long): String {
    val ldt = LocalDateTime.ofInstant(
        Instant.ofEpochMilli(utcMillis),
        ZoneId.systemDefault(),
    )
    val dayName = ldt.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.getDefault())
    val day     = ldt.dayOfMonth
    val month   = ldt.monthValue
    val year    = ldt.year % 100                              // e.g. 2026 → 26
    val hour    = ldt.hour.toString().padStart(2, '0')
    val minute  = ldt.minute.toString().padStart(2, '0')
    return "$dayName, $day.$month.$year $hour:$minute"
}