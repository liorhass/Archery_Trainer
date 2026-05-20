package com.liorapps.archerytrainer.screens.importexport

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liorapps.archerytrainer.ui.theme.ArcheryTrainerTheme
import kotlin.String


/* A few notes worth calling out
  LaunchedEffect(uiState.shouldOpenFilePicker) - keying on the boolean means the effect re-runs
    only when the flag changes value (false → true), not on every recomposition.
    Calling onFilePickerLaunched() immediately after launch() resets it before the picker even
    returns, which is safe because the picker result comes back asynchronously via the
    launcher callback.
  AnimatedVisibility - wrapping the result rows in it gives a smooth fade-in/out when results
    appear and are dismissed, with zero extra work.
  Error color - I used MaterialTheme.colorScheme.error rather than a hardcoded red, so it
    automatically respects the app's theme and dark mode. For success I used
    colorScheme.primary; if you'd prefer a dedicated green you can swap it for Color(0xFF2E7D32).
  allowedMimeTypes is an Array<String> (not List) because that's what OpenDocument.launch()
    expects directly — no conversion needed at the call site.
*/

@Composable
fun ImportExportScreen(
    viewModel: ImportExportViewModel,
    allowedMimeTypes: Array<String> = arrayOf("application/json"),
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ImportExportScreenContent(
        uiState = uiState,
        allowedMimeTypes = allowedMimeTypes,
        onImportFileSelected = viewModel::onImportFileSelected,
        onFilePickerLaunched = viewModel::onFilePickerLaunched,
        onExportClicked = viewModel::onExportClicked,
        onExportResultDismissed = viewModel::onExportResultDismissed,
        onImportClicked = viewModel::onImportClicked,
        onImportResultDismissed = viewModel::onImportResultDismissed,
        onNavigateBack = onNavigateBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportExportScreenContent(
    uiState: ImportExportUiState,
    allowedMimeTypes: Array<String>,
    onImportFileSelected: (uri: Uri) -> Unit,
    onFilePickerLaunched: () -> Unit,
    onExportClicked: () -> Unit,
    onExportResultDismissed: () -> Unit,
    onImportClicked: () -> Unit,
    onImportResultDismissed: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { onImportFileSelected(it) }
    }

    // Consume the one-shot flag: launch picker, then immediately reset.
    LaunchedEffect(uiState.shouldOpenFilePicker) {
        if (uiState.shouldOpenFilePicker) {
            filePickerLauncher.launch(allowedMimeTypes)
            onFilePickerLaunched()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import/Export") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Go back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 40.dp, bottom = 20.dp, start = 32.dp, end = 32.dp),
//                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // ── Export section ──────────────────────────────────────────────────
                Button(
                    onClick = onExportClicked,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Export to File")
                }

                AnimatedVisibility(visible = uiState.exportResult != null) {
                    uiState.exportResult?.let { result ->
                        OperationResultRow(
                            result = result,
                            onDismiss = onExportResultDismissed,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 32.dp))

                // ── Import section ──────────────────────────────────────────────────
                Button(
                    onClick = onImportClicked,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Import from File")
                }

                AnimatedVisibility(visible = uiState.importResult != null) {
                    uiState.importResult?.let { result ->
                        OperationResultRow(
                            result = result,
                            onDismiss = onImportResultDismissed,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}


// ─── Shared result row ───────────────────────────────────────────────────────

@Composable
private fun OperationResultRow(
    result: OperationResult,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isFailure = result is OperationResult.Failure
    val message = when (result) {
        is OperationResult.Success -> result.message
        is OperationResult.Failure   -> result.message
    }
    val color = if (isFailure) MaterialTheme.colorScheme.error
    else         MaterialTheme.colorScheme.primary
    val icon  = if (isFailure) Icons.Filled.Warning
    else         Icons.Filled.CheckCircle

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector      = icon,
            contentDescription = null,
            tint             = color,
            modifier         = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text     = message,
            color    = color,
            style    = if (isFailure) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            else         MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick  = onDismiss,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector      = Icons.Filled.Close,
                contentDescription = "Dismiss",
                tint             = color,
                modifier         = Modifier.size(16.dp),
            )
        }
    }
}

@PreviewLightDark
@Composable
fun ImportExportScreenPreview() {
    ArcheryTrainerTheme {
        ImportExportScreenContent(
            uiState = ImportExportUiState().copy(
                exportResult = OperationResult.Success("Database exported"),
                importResult = OperationResult.Failure("Import failed!")
            ),
            allowedMimeTypes = arrayOf(""),
            onImportFileSelected = {},
            onFilePickerLaunched = {},
            onExportClicked = {},
            onExportResultDismissed = {},
            onImportClicked = {},
            onImportResultDismissed = {},
            onNavigateBack = {}
        )
    }
}

