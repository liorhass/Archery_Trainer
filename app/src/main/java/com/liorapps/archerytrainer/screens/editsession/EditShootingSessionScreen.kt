package com.liorapps.archerytrainer.screens.editsession

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liorapps.archerytrainer.screens.video.ui.visible


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditShootingSessionScreen(
    viewModel: EditShootingSessionViewModel,
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Show dialogs (when necessary) above the scaffold
    EditShootingSessionTabDialogs(viewModel = viewModel, uiState = uiState,)

    Scaffold(
        topBar = {
            EditShootingSessionTopBar(
            viewModel = viewModel,
            uiState = uiState,
            onNavigateBack = onNavigateBack,
        )},
        bottomBar = {
            EditShootingSessionBottomBar(
                viewModel = viewModel,
                uiState = uiState,
            )
        },
    ) { innerPadding ->
        if (uiState.activeTab == ActiveTab.EDIT_SESSION) {
            EditShootingSessionTab(
                viewModel = viewModel,
                uiState = uiState,
                innerPadding = innerPadding,
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "I'm still empty"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditShootingSessionTopBar(
    viewModel: EditShootingSessionViewModel,
    uiState: EditShootingSessionState,
    onNavigateBack: () -> Unit,
) {
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
}

@Composable
private fun EditShootingSessionBottomBar(
    viewModel: EditShootingSessionViewModel,
    uiState: EditShootingSessionState,
) {
    NavigationBar {
        NavigationBarItem(
            selected = uiState.activeTab == ActiveTab.EDIT_SESSION,
            onClick = { viewModel.onSetActiveTab(ActiveTab.EDIT_SESSION) },
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
            selected = uiState.activeTab == ActiveTab.SETS_LIST,
            onClick = { viewModel.onSetActiveTab(ActiveTab.SETS_LIST) },
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = null,
                )
            },
            label = { Text("Sets") },
        )
    }
}
