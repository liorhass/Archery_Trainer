package com.liorapps.archerytrainer.screens.editsession

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import java.time.format.TextStyle as JavaTextStyle

// ── ID generator ──────────────────────────────────────────────────────────────
@OptIn(ExperimentalAtomicApi::class)
private val floaterIdCounter = AtomicLong(0L)

@OptIn(ExperimentalAtomicApi::class)
@Composable
fun EditShootingSessionTab(
    viewModel: EditShootingSessionViewModel,
    uiState: EditShootingSessionState,
    innerPadding: PaddingValues,
) {
    // Collect one-shot events and spawn a floater per event.
    val activeFloaters = remember { mutableStateListOf<FloatingLabel>() }
    LaunchedEffect(Unit) {
        viewModel.moreShotsEvents.collect { nNewShots ->
            activeFloaters.add(
                FloatingLabel(
                    id   = floaterIdCounter.incrementAndFetch(),
                    text = "+$nNewShots",
                )
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val sessionStartTime = formatSessionDateTime(uiState.sessionDateTimeUtc)
        Text(
            text = "Started at: $sessionStartTime",
            style = MaterialTheme.typography.titleMedium,
        )

        // Arrow and score totals
        StatsSection(uiState = uiState, activeFloaters)

        // Add-Set button grid
        AddSetSection(
            buttonValues = uiState.buttonValues,
            onButtonTapped = viewModel::onAddSetButtonTapped,
            onButtonLongPressed = viewModel::onSetButtonLongPressed,
        )

        // Optional session description / comment
        if (!uiState.comment.isBlank()) {
            SessionCommentSection(comment = uiState.comment)
        }
    }
}

@Composable
private fun SessionCommentSection(comment: String) {
//    Card(
//        modifier = Modifier.fillMaxWidth(),
//        colors = CardDefaults.cardColors(
//            containerColor = MaterialTheme.colorScheme.surfaceVariant,
//        ),
//    ) {
        // The inner Box is constrained to ~3 lines tall (bodyMedium lineHeight ≈ 20 sp).
        // verticalScroll allows the user to read longer comments by scrolling.
        Column (
            modifier = Modifier
                .fillMaxWidth()
//                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // ── Section header ────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    text = "   Description   ",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                HorizontalDivider(modifier = Modifier.weight(1f))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 60.dp)          // ≈ 3 × 20 dp line height
                    .verticalScroll(rememberScrollState())
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Text(
                    text = comment,
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
//    }
}

@Composable
private fun StatsSection(
    uiState: EditShootingSessionState,
    activeFloaters: SnapshotStateList<FloatingLabel>,
) {
//    Card(
//        modifier = Modifier.fillMaxWidth(),
//        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
//    ) {

    Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 0.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Total shots – always shown
            Column {
//                Text(
//                    text = "🏹",
//                    style = MaterialTheme.typography.titleMedium,
//                )
//                Spacer(modifier = Modifier.width(10.dp))
//                Text(
//                    text = "Sets:  ${uiState.sets.size}  (${uiState.scoredSets.size} with score)",
//                    style = MaterialTheme.typography.bodyLarge,
//                )
//                Text(
//                    text = "Shots:  ${uiState.totalArrows}  (${uiState.totalScoredArrows} with score)",
//                    style = MaterialTheme.typography.bodyLarge,
//                )
//                Text(
//                    text = "Total Score:  ${uiState.totalScore}  Average Score: ${"%.1f".format(uiState.averageScore)}",
//                    style = MaterialTheme.typography.bodyLarge,
//                )
//                Row(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .padding(vertical = 0.dp)
//                ) {
//                    Text(
//                        text = "Test",
//                        modifier = Modifier.weight(40f),
//                        fontWeight = FontWeight.Bold
//                    )
////        Text(
////            text = col2,
////            modifier = Modifier.weight(15f),
////            fontWeight = FontWeight.Normal
////        )
//                    ScoreDisplay(
//                        baseText = "${uiState.sets.size}",
//                        activeFloaters = activeFloaters,
//                        modifier = Modifier.weight(15f),
//                    )
////                    VerticalSlidingText(
////                        targetText = col2,
////                        modifier = Modifier.weight(15f),
////                        fontWeight = FontWeight.Normal
////                    )
//                    Text(
//                        text = "(${uiState.scoredSets.size} with score)",
//                        modifier = Modifier.weight(45f),
//                        fontWeight = FontWeight.Normal
//                    )
//                }

                StatSectionRow("Sets:", "${uiState.sets.size}", "(${uiState.scoredSets.size} with score)")
                StatSectionRowWithFloaters("Shots:", "${uiState.totalArrows}", "(${uiState.totalScoredArrows} with score)", activeFloaters)
                if (uiState.hasAnyScore) {
                    StatSectionRow("Total Score:", "${uiState.totalScore}", "")
                    StatSectionRow("Avg. Score:", "%.1f".format(uiState.averageScore), "")
                }
            }

//            // Total + average score – only when scoring is enabled AND at least one
//            // set in the session carries a score.
//            if (uiState.hasAnyScore) {
//                Row(verticalAlignment = Alignment.CenterVertically) {
//                    Text(
//                        text = "🎯",
//                        style = MaterialTheme.typography.titleMedium,
//                    )
//                    Spacer(modifier = Modifier.width(10.dp))
//                    Text(
//                        text = "Total Score:  ${uiState.totalScore}" +
//                                "     Avg: ${"%.1f".format(uiState.averageScore)}",
//                        style = MaterialTheme.typography.bodyLarge,
//                    )
//                }
//            }
        }
//    }
}

@Composable
private fun StatSectionRow(
    col1: String,
    col2: String,
    col3: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 0.dp)
    ) {
        Text(
            text = col1,
            modifier = Modifier.weight(40f),
            fontWeight = FontWeight.Bold
        )
//        Text(
//            text = col2,
//            modifier = Modifier.weight(15f),
//            fontWeight = FontWeight.Normal
//        )
        VerticalSlidingText(
            targetText = col2,
            modifier = Modifier.weight(20f),
            fontWeight = FontWeight.Normal
        )
        Text(
            text = col3,
            modifier = Modifier.weight(40f),
            fontWeight = FontWeight.Normal
        )
    }
}


@Composable
private fun StatSectionRowWithFloaters(
    @Suppress("SameParameterValue", "SameParameterValue") col1: String,
    col2: String,
    col3: String,
    activeFloaters: SnapshotStateList<FloatingLabel>,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 0.dp)
    ) {
        Text(
            text = col1,
            modifier = Modifier.weight(40f),
            fontWeight = FontWeight.Bold
        )
        TextWithFloater(
            baseText = col2,
            activeFloaters = activeFloaters,
            modifier = Modifier.weight(20f),
        )
        Text(
            text = col3,
            modifier = Modifier.weight(40f),
            fontWeight = FontWeight.Normal
        )
    }
}



@Composable
fun VerticalSlidingText(targetText: String, fontWeight: FontWeight?, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = targetText,
        modifier = modifier,
        transitionSpec = {
            // Increase durationMillis to slow down the animation (e.g., 1000ms = 1 second)
            val duration = 700

            // New text slides in from the bottom
//            val enterTransition = slideInVertically { height -> height } + fadeIn()
            val enterTransition = slideInVertically(
                animationSpec = tween(durationMillis = duration)
            ) { height -> height } + fadeIn(
                animationSpec = tween(durationMillis = duration)
            )

            // Old text slides out towards the top
//            val exitTransition = slideOutVertically { height -> -height } + fadeOut()
            val exitTransition = slideOutVertically(
                animationSpec = tween(durationMillis = duration)
            ) { height -> -height } + fadeOut(
                animationSpec = tween(durationMillis = duration)
            )

            enterTransition togetherWith exitTransition
        },
        label = "TextSlideAnimation"
    ) { text ->
        Text(text = text, fontWeight = fontWeight, modifier = modifier)
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
    val haptics = LocalHapticFeedback.current

    Surface(
        modifier = modifier
            .aspectRatio(1f)
            .combinedClickable(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onTap()
                },
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

@Composable
fun EditShootingSessionTabDialogs( //  Dialogs (rendered above the Scaffold)
    viewModel: EditShootingSessionViewModel,
    uiState: EditShootingSessionState,
) {
    if (uiState.showEditCommentDialog) {
        EditCommentDialog(
            commentDraft = uiState.commentDraft,
            onCommentDraftChanged = viewModel::onCommentDraftChanged,
            onConfirm = viewModel::onCommentConfirmed,
            onDismiss = viewModel::onCommentDismissed,
        )
    }

    if (uiState.showScoreDialog) {
        EnterScoreDialog(
            scoreDraft = uiState.scoreDraft,
            isValid = uiState.isScoreInputValid,
            onScoreDraftChanged = viewModel::onScoreDraftChanged,
            onConfirm = viewModel::onScoreConfirmed,
            onDismiss = viewModel::onScoreDismissed,
        )
    }

    if (uiState.showEditButtonValueDialog) {
        EditButtonValueDialog(
            buttonValueDraft = uiState.buttonValueDraft,
            isValid = uiState.isButtonValueInputValid,
            onButtonValueDraftChanged = viewModel::onButtonValueDraftChanged,
            onConfirm = viewModel::onButtonValueConfirmed,
            onDismiss = viewModel::onButtonValueDismissed,
        )
    }

    if (uiState.showAddingSetTooSoonDialog) {
        AddingSetTooSoonDialog(
            secSinceLastAddSet = uiState.secSinceLastAddSet,
            onConfirm = viewModel::onAddingSetTooSoonConfirmed,
            onDismiss = viewModel::onAddingSetTooSoonCanceled,
        )
    }
}

/** Dialog for editing the optional session description / comment. */
@Composable
private fun EditCommentDialog(
    commentDraft: String,
    onCommentDraftChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Description") },
        text = {
            OutlinedTextField(
                value = commentDraft,
                onValueChange = onCommentDraftChanged,   // ViewModel enforces the 1 000-char cap
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Description") },
                placeholder = { Text("Add a description for this session…") },
                minLines = 3,
                maxLines = 8,
                supportingText = {
                    Text(
                        text = "${commentDraft.length} / 1000",
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
    scoreDraft: String,
    isValid: Boolean,
    onScoreDraftChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val showError = scoreDraft.isNotEmpty() && !isValid

    // A FocusRequester to programmatically focus the input field
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Score") },
        text = {
            OutlinedTextField(
                value = scoreDraft,
                onValueChange = { onScoreDraftChanged(it.take(3)) },  // hard cap at 3 chars
                modifier = Modifier.fillMaxWidth()
                                   .focusRequester(focusRequester),
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
                // Wire the Done IME action to the same callback as OK
                keyboardActions = KeyboardActions(
                    onDone = { if (isValid) onConfirm() }
                ),
            )
            // Request focus (and therefore the keyboard) when the dialog first appears
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
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
    buttonValueDraft: String,
    isValid: Boolean,
    onButtonValueDraftChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val showError = buttonValueDraft.isNotEmpty() && !isValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Button Value") },
        text = {
            OutlinedTextField(
                value = buttonValueDraft,
                onValueChange = { onButtonValueDraftChanged(it.take(3)) },  // hard cap at 3 chars
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

/** Dialog that lets the user change the arrow-count on one of the 12 grid buttons. */
@Composable
private fun AddingSetTooSoonDialog(
    secSinceLastAddSet: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm adding set") },
        text = { Text("Are you sure? The previous set was added only $secSinceLastAddSet seconds ago.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Yes") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Oops, Cancel") }
        },
    )
}


// ─────────────────────────────────────────────────────────────────────────────
// Date / Time Helper
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Formats a UTC epoch-millisecond value as  "Mon, 14/5/26 14:33"
 * using the device's local time zone.
 *
 * Note: `JavaTextStyle` is an alias for `java.time.format.TextStyle`
 * to avoid clashing with Compose's own `TextStyle`.
 */
fun formatSessionDateTime(utcMillis: Long): String {
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
    return "$dayName, $day/$month/$year $hour:$minute"
}
