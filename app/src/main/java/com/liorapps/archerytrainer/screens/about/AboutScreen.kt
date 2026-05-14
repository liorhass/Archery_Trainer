package com.liorapps.archerytrainer.screens.about

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.liorapps.archerytrainer.ui.theme.ArcheryTrainerTheme
import com.liorapps.archerytrainer.R
import com.liorapps.archerytrainer.ui.theme.AppTheme

@Composable
fun AboutScreen(
    viewModel: AboutViewModel,
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    AboutScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreenContent(
    uiState: AboutUiState,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Open Navigation Drawer"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(20.dp),
//            horizontalAlignment = Alignment.CenterHorizontally,
//            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(30.dp))

//            Text(
//                text = "Archery Trainer",
//                style = MaterialTheme.typography.headlineMedium,
//                fontWeight = FontWeight.Bold
//            )
            AboutHeader()

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Copyright © Lior Hass    2026",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Version ${uiState.versionName} (${uiState.versionCode})\n${uiState.buildDateTime}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

//            if (uiState.buildDateTime.isNotEmpty()) {
//                Spacer(modifier = Modifier.height(4.dp))
//                Text(
//                    text = "Built on ${uiState.buildDateTime}",
//                    style = MaterialTheme.typography.bodyMedium,
//                    color = MaterialTheme.colorScheme.onSurfaceVariant
//                )
//            }

            Spacer(modifier = Modifier.height(32.dp))

            HtmlLikeLink(
                onClick = {
                    if (uiState.githubUrl.isNotEmpty()) {
                        val intent = Intent(Intent.ACTION_VIEW, uiState.githubUrl.toUri())
                        context.startActivity(intent)
                    }
                },
                text = "View Source on GitHub",
            )

        }
    }
}

@Composable
fun AboutHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically // Keeps the logo and text aligned in the middle
    ) {
        Image(
            painter = painterResource(id = R.drawable.archery_trainer_logo_foreground), // Replace with your actual drawable resource
            contentDescription = null,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = "Archery Trainer",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun HtmlLikeLink(
    onClick: () -> Unit,
    text: String,
) {
    val annotatedString = buildAnnotatedString {
        val linkAnnotation = LinkAnnotation.Clickable(
            tag = "policy",
            linkInteractionListener = { onClick() }
        )

        withLink(linkAnnotation) {
            withStyle(
                style = SpanStyle(
                    color = AppTheme.colors.webLink,
                    textDecoration = TextDecoration.Underline
                )
            ) {
                append(text)
            }
        }
    }

    BasicText(text = annotatedString)
}

@PreviewLightDark()
@Composable
fun AboutScreenPreview() {
    ArcheryTrainerTheme() {
        AboutScreenContent(
            uiState = AboutUiState().copy(
                versionName = "1.0.17-personal",
                versionCode = "24",
                buildDateTime = "2023-10-27 10:00",
                githubUrl = "https://github.com/liorapps/ArcheryTrainer"
            ),
            onNavigateBack = {}
        )
    }
}
