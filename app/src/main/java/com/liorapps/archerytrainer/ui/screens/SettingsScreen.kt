package com.liorapps.archerytrainer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.liorapps.archerytrainer.MainViewModel
import com.liorapps.archerytrainer.SettingsRepository
import com.liorapps.archerytrainer.ArcheryTrainerDefaults
import com.liorapps.archerytrainer.ui.theme.ArcheryTrainerTheme
import kotlin.math.roundToInt


@Composable
fun SettingsScreen(
    viewModel: MainViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onOpenDrawer: () -> Unit,
) {
    val settings by viewModel.settingsFlow.collectAsStateWithLifecycle()

    SettingsScreenContent(
        settings = settings,
        onSettingsChange = viewModel::updateSettings,
        onNavigateBack = onNavigateBack,
    )
}

// ---------------------------------------------------------------------------
// Pure-Compose content (easy to preview / test independently)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    settings: SettingsRepository.Settings,
    onSettingsChange: (SettingsRepository.Settings) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        val modifier = modifier.then(Modifier.padding(innerPadding))
        SettingsSection(settings, onSettingsChange, modifier)
    }
}

// ---------------------------------------------------------------------------
// Public entry point — collects state from the ViewModel
// ---------------------------------------------------------------------------

//@Composable
//fun SettingsScreen(viewModel: SettingsViewModel) {
//    val settings by viewModel.settings.collectAsStateWithLifecycle()
//    SettingsSection(
//        settings = settings,
//        onSettingsChange = viewModel::updateSettings,
//    )
//}

// ---------------------------------------------------------------------------
// Top-level composable (as specified)
// ---------------------------------------------------------------------------

@Composable
private fun SettingsSection(
    settings: SettingsRepository.Settings,
    onSettingsChange: (SettingsRepository.Settings) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Which dialog is currently open
    var openDialog by remember { mutableStateOf<DialogType?>(null) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // ---- Section 1 ----------------------------------------------
            SettingsSectionHeader(title = "Video")

            SettingsItem(
                title = "Delay (Sec)",
                value = settings.delaySec.toString(),
                onClick = { openDialog = DialogType.DELAY_SEC },
            )

            SettingsItem(
                title = "Video Bitrate (Bit/Sec)",
                value = "%,d".format(settings.bitRate),
                onClick = { openDialog = DialogType.VIDEO_BITRATE },
            )
//            HorizontalDivider(modifier = Modifier.padding(start = 16.dp))

            SettingsItem(
                title = "Video Resolution",
                value = settings.videoResolution.toString(),
                onClick = { openDialog = DialogType.VIDEO_RESOLUTION },
            )

            // ---- Section 2 ----------------------------------------------
            SettingsSectionHeader(title = "Section-2")

            SettingsItem(
                title = "Dummy String",
                value = settings.dummyString.ifBlank { "(blank)" },
                onClick = { openDialog = DialogType.PARAM_C },
            )
//            HorizontalDivider(modifier = Modifier.padding(start = 16.dp))

            SettingsItem(
                title = "Dummy Float",
                value = "%.2f".format(settings.dummyFloat),
                onClick = { openDialog = DialogType.PARAM_D },
            )
        }
    }

    // ── Dialogs ─────────────────────────────────────────────────────────────
    when (openDialog) {
        DialogType.DELAY_SEC -> DelaySecDialog(
            current = settings.delaySec,
            onConfirm = { newValue ->
                onSettingsChange(settings.copy(delaySec = newValue))
                openDialog = null
            },
            onDismiss = { openDialog = null },
        )

        DialogType.VIDEO_BITRATE -> VideoBitrateDialog(
            current = settings.bitRate,
            onConfirm = { newValue ->
                onSettingsChange(settings.copy(bitRate = newValue))
                openDialog = null
            },
            onDismiss = { openDialog = null },
        )

        DialogType.VIDEO_RESOLUTION -> VideoResolutionDialog (
            current = settings.videoResolution,
            onConfirm = { newValue ->
                onSettingsChange(settings.copy(videoResolution = newValue))
                openDialog = null
            },
            onDismiss = { openDialog = null },
        )

        DialogType.PARAM_C -> ParamCDialog(
            current = settings.dummyString,
            onConfirm = { newValue ->
                onSettingsChange(settings.copy(dummyString = newValue))
                openDialog = null
            },
            onDismiss = { openDialog = null },
        )

        DialogType.PARAM_D -> ParamDDialog(
            current = settings.dummyFloat,
            onConfirm = { newValue ->
                onSettingsChange(settings.copy(dummyFloat = newValue))
                openDialog = null
            },
            onDismiss = { openDialog = null },
        )

        null -> Unit
    }
}

// ---------------------------------------------------------------------------
// Shared UI building blocks
// ---------------------------------------------------------------------------

private enum class DialogType { DELAY_SEC, VIDEO_BITRATE, VIDEO_RESOLUTION, PARAM_C, PARAM_D }

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
    )
    HorizontalDivider()
}

@Composable
private fun SettingsItem(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SimpleIntDialog(
    current: Int,
    title: String,
    label: String,
    illegalValueMsg: String,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
    isLegalValue: (Int?) -> Boolean,
) {
    var text by remember { mutableStateOf(current.toString()) }
    var isError = false

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                isError = ! isLegalValue(text.toIntOrNull()),
                supportingText = {
                    if (isError) Text(illegalValueMsg)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { text.toIntOrNull()?.let(onConfirm) },
                enabled = !isError,
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun SingleSelectionStringDialog(
    title: String,
    current: String,
    options: List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(current) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (option == selected),
                                onClick = { selected = option },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = (option == selected),
                            onClick = null, // handled by Row
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = option,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ---------------------------------------------------------------------------
// Dialogs for specific config parameters
// ---------------------------------------------------------------------------
@Composable
private fun DelaySecDialog(
    current: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    SimpleIntDialog(
        current = current,
        title = "Delay (Sec)",
        label = "Video delay in seconds",
        illegalValueMsg = "Please enter a number between 0 and ${ArcheryTrainerDefaults.MAX_DELAY_SEC}",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        isLegalValue = { value -> (value != null) && (value >= 0) && (value <= ArcheryTrainerDefaults.MAX_DELAY_SEC) },
    )
}

@Composable
private fun VideoBitrateDialog(
    current: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    SimpleIntDialog(
        current = current,
        title = "Video Bitrate (Bits/Sec)",
        label = "Video bitrate in bits/sec (note: bits not bytes!)",
        illegalValueMsg = "Please enter a number between 0 and ${ArcheryTrainerDefaults.MAX_BIT_RATE}",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        isLegalValue = { value -> (value != null) && (value >= 0) && (value <= ArcheryTrainerDefaults.MAX_BIT_RATE) },
    )
}

@Composable
private fun VideoResolutionDialog(
    current: ArcheryTrainerDefaults.VideoResolution,
    onConfirm: (ArcheryTrainerDefaults.VideoResolution) -> Unit,
    onDismiss: () -> Unit,
) {
    SingleSelectionStringDialog(
        title = "Video Resolution",
        current = current.toString(),
        options = listOf(
            ArcheryTrainerDefaults.VideoResolution.SD_640x480.toString(),
            ArcheryTrainerDefaults.VideoResolution.HD_1280x720.toString(),
            ArcheryTrainerDefaults.VideoResolution.FHD_1920x1080.toString(),
            ArcheryTrainerDefaults.VideoResolution.QHD_2560x1440.toString(),
            ArcheryTrainerDefaults.VideoResolution.UHD_3840x2160.toString(),
        ),
        onConfirm = { newResolution ->
            when (newResolution) {
                ArcheryTrainerDefaults.VideoResolution.SD_640x480.toString() -> onConfirm(ArcheryTrainerDefaults.VideoResolution.SD_640x480())
                ArcheryTrainerDefaults.VideoResolution.HD_1280x720.toString() -> onConfirm(ArcheryTrainerDefaults.VideoResolution.HD_1280x720())
                ArcheryTrainerDefaults.VideoResolution.FHD_1920x1080.toString() -> onConfirm(ArcheryTrainerDefaults.VideoResolution.FHD_1920x1080())
                ArcheryTrainerDefaults.VideoResolution.QHD_2560x1440.toString() -> onConfirm(ArcheryTrainerDefaults.VideoResolution.QHD_2560x1440())
                ArcheryTrainerDefaults.VideoResolution.UHD_3840x2160.toString() -> onConfirm(ArcheryTrainerDefaults.VideoResolution.UHD_3840x2160())
            }
        },
        onDismiss = onDismiss,
    )
}






@Composable
private fun DelaySecDialog2brm(
    current: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(current.toString()) }
    val isError = (text.toIntOrNull() == null) && (text.toInt() >= 0) && (text.toInt() <= ArcheryTrainerDefaults.MAX_DELAY_SEC)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delay (Sec)") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Video delay in seconds") },
                isError = isError,
                supportingText = {
                    if (isError) Text("Please enter a number between 0 and $ArcheryTrainerDefaults.MAX_DELAY_SEC")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { text.toIntOrNull()?.let(onConfirm) },
                enabled = !isError,
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ---------------------------------------------------------------------------
// Dialog: paramB — single-choice from predefined list
// ---------------------------------------------------------------------------

@Composable
private fun ParamBDialog(
    current: Int,
    options: List<Int>,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(current) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Parameter B") },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (option == selected),
                                onClick = { selected = option },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = (option == selected),
                            onClick = null, // handled by Row
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = option.toString(),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ---------------------------------------------------------------------------
// Dialog: paramC — free string input
// ---------------------------------------------------------------------------

@Composable
private fun ParamCDialog(
    current: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(current) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Parameter C") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Enter a value") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ---------------------------------------------------------------------------
// Dialog: paramD — slider in [0, 1]
// ---------------------------------------------------------------------------

@Composable
private fun ParamDDialog(
    current: Float,
    onConfirm: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    // Work in integer steps of 1 % for a smooth but discrete slider
    var sliderValue by remember { mutableFloatStateOf(current) }
    val displayValue = "%.2f".format(sliderValue)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Parameter D") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = (it * 100).roundToInt() / 100f },
                    valueRange = 0f..1f,
                    steps = 98, // 100 intervals → 99 intermediate stops (0.01 each)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("0.00", style = MaterialTheme.typography.labelSmall)
                    Text("1.00", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(sliderValue) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ---------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------
data class MySettings(
    val paramA: Int = 0,
    val paramB: Int = PARAM_B_OPTIONS.first(),
    val paramC: String = "",
    val paramD: Float = 0.5f,
) {
    companion object {
        val PARAM_B_OPTIONS = listOf(10, 20, 50, 100, 200)
    }
}
@Preview(showBackground = true)
@Composable
fun SettingsScreenContentPreview() {
    ArcheryTrainerTheme {
        SettingsScreenContent(
            settings = SettingsRepository.Settings(
                delaySec = 25,
                videoResolution = ArcheryTrainerDefaults.VideoResolution.HD_1280x720(),
                frameRate = 30,
                bitRate = 15_000_000,
                dummyString = "Hello, World!",
                dummyFloat = 0.73f,
            ),
            onSettingsChange = {},
            onNavigateBack = {},
        )
    }
}
//@Preview(showBackground = true)
//@Composable
//fun SettingsScreenPreview() {
//    VideoTrainerTheme {
//        SettingsScreenContent(
//            settings = MySettings(paramA = 3, paramB = 7, paramC = "lama", paramD = 0.314f),
//            onSettingsChange = {},
//            onNavigateBack = {},
//        )
//    }
//}












//@Composable
//private fun SettingsSection(
//    settings: MySettings,
//    onSettingsChange: (MySettings) -> Unit,
//    modifier: Modifier = Modifier,
//) {
//    // ---- local draft state ------------------------------------------------
//    // We keep a mutable local copy so the UI feels instant while the
//    // ViewModel / repository persists the value asynchronously.
//
//    var paramAText by remember(settings.paramA) {
//        mutableStateOf(settings.paramA.toString())
//    }
//    var paramB by remember(settings.paramB) {
//        mutableIntStateOf(settings.paramB)
//    }
//    var paramCText by remember(settings.paramC) {
//        mutableStateOf(settings.paramC)
//    }
//    var paramD by remember(settings.paramD) {
//        mutableFloatStateOf(settings.paramD)
//    }
//
//    // Helper: push current draft back to the ViewModel
//    fun commit(
//        newParamA: Int = paramAText.toIntOrNull() ?: settings.paramA,
//        newParamB: Int = paramB,
//        newParamC: String = paramCText,
//        newParamD: Float = paramD,
//    ) {
//        onSettingsChange(
//            settings.copy(
//                paramA = newParamA,
//                paramB = newParamB,
//                paramC = newParamC,
//                paramD = newParamD,
//            )
//        )
//    }
//
//    // ---- layout -----------------------------------------------------------
//    Column(
//        modifier = modifier
//            .fillMaxSize()
//            .verticalScroll(rememberScrollState())
//            .padding(vertical = 16.dp),
//        verticalArrangement = Arrangement.spacedBy(8.dp),
//    ) {
//
//        // ── Section 1 ──────────────────────────────────────────────────────
//        SettingsSectionHeader(title = "Section-1")
//
//        // paramA – free integer input
//        SettingsItemCard {
//            OutlinedTextField(
//                value = paramAText,
//                onValueChange = { raw ->
//                    // Accept only digit / minus characters while typing
//                    if (raw.isEmpty() || raw == "-" || raw.toIntOrNull() != null) {
//                        paramAText = raw
//                    }
//                },
//                label = { Text("Param A") },
//                supportingText = { Text("Any integer value") },
//                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
//                singleLine = true,
//                modifier = Modifier.fillMaxWidth(),
//            )
//
//            Spacer(Modifier.height(4.dp))
//
//            Button(
//                onClick = { commit(newParamA = paramAText.toIntOrNull() ?: settings.paramA) },
//                modifier = Modifier.align(Alignment.End),
//            ) {
//                Text("Apply")
//            }
//        }
//
//        // paramB – picker from pre-defined set
//        SettingsItemCard {
//            Text(
//                text = "Param B",
//                style = MaterialTheme.typography.labelLarge,
//            )
//            Text(
//                text = "Select a value from the list",
//                style = MaterialTheme.typography.bodySmall,
//                color = MaterialTheme.colorScheme.onSurfaceVariant,
//            )
//            Spacer(Modifier.height(8.dp))
//            ParamBPicker(
//                options = MySettings.PARAM_B_OPTIONS,
//                selected = paramB,
//                onSelected = { newValue ->
//                    paramB = newValue
//                    commit(newParamB = newValue)
//                },
//            )
//        }
//
//        // ── Section 2 ──────────────────────────────────────────────────────
//        SettingsSectionHeader(title = "Section-2")
//
//        // paramC – free string input
//        SettingsItemCard {
//            OutlinedTextField(
//                value = paramCText,
//                onValueChange = { paramCText = it },
//                label = { Text("Param C") },
//                supportingText = { Text("Any text value") },
//                singleLine = true,
//                modifier = Modifier.fillMaxWidth(),
//            )
//
//            Spacer(Modifier.height(4.dp))
//
//            Button(
//                onClick = { commit(newParamC = paramCText) },
//                modifier = Modifier.align(Alignment.End),
//            ) {
//                Text("Apply")
//            }
//        }
//
//        // paramD – slider 0..1
//        SettingsItemCard {
//            Row(
//                modifier = Modifier.fillMaxWidth(),
//                horizontalArrangement = Arrangement.SpaceBetween,
//                verticalAlignment = Alignment.CenterVertically,
//            ) {
//                Column {
//                    Text(
//                        text = "Param D",
//                        style = MaterialTheme.typography.labelLarge,
//                    )
//                    Text(
//                        text = "Value between 0 and 1",
//                        style = MaterialTheme.typography.bodySmall,
//                        color = MaterialTheme.colorScheme.onSurfaceVariant,
//                    )
//                }
//                // Show current value rounded to 2 decimal places
//                Text(
//                    text = "%.2f".format(paramD),
//                    style = MaterialTheme.typography.bodyLarge,
//                    color = MaterialTheme.colorScheme.primary,
//                )
//            }
//            Spacer(Modifier.height(8.dp))
//            Slider(
//                value = paramD,
//                onValueChange = { paramD = it },
//                onValueChangeFinished = { commit(newParamD = paramD) },
//                valueRange = 0f..1f,
//                modifier = Modifier.fillMaxWidth(),
//            )
//            Row(
//                modifier = Modifier.fillMaxWidth(),
//                horizontalArrangement = Arrangement.SpaceBetween,
//            ) {
//                Text(
//                    text = "0.00",
//                    style = MaterialTheme.typography.labelSmall,
//                    color = MaterialTheme.colorScheme.onSurfaceVariant,
//                )
//                Text(
//                    text = "1.00",
//                    style = MaterialTheme.typography.labelSmall,
//                    color = MaterialTheme.colorScheme.onSurfaceVariant,
//                )
//            }
//        }
//
//        Spacer(Modifier.height(16.dp))
//    }
//}
//
//// ---------------------------------------------------------------------------
//// Reusable sub-components
//// ---------------------------------------------------------------------------
//
//@Composable
//private fun SettingsSectionHeader(
//    title: String,
//    modifier: Modifier = Modifier,
//) {
//    Column(modifier = modifier.padding(horizontal = 16.dp)) {
//        Text(
//            text = title,
//            style = MaterialTheme.typography.titleSmall,
//            color = MaterialTheme.colorScheme.primary,
//        )
//        HorizontalDivider(
//            modifier = Modifier.padding(top = 4.dp),
//            color = MaterialTheme.colorScheme.outlineVariant,
//        )
//    }
//}
//
///**
// * A card-like container for a single settings row / group.
// */
//@Composable
//private fun SettingsItemCard(
//    modifier: Modifier = Modifier,
//    content: @Composable ColumnScope.() -> Unit,
//) {
//    Card(
//        modifier = modifier
//            .fillMaxWidth()
//            .padding(horizontal = 16.dp),
//        colors = CardDefaults.cardColors(
//            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
//        ),
//    ) {
//        Column(
//            modifier = Modifier.padding(16.dp),
//            content = content,
//        )
//    }
//}
//
///**
// * Renders [options] as a row of Filter-chip-style buttons.
// * The currently [selected] value is highlighted.
// */
//@Composable
//private fun ParamBPicker(
//    options: List<Int>,
//    selected: Int,
//    onSelected: (Int) -> Unit,
//    modifier: Modifier = Modifier,
//) {
//    // Wrap in a horizontal scroll in case many options don't fit in one line
//    val scrollState = rememberScrollState()
//    Row(
//        modifier = modifier
//            .fillMaxWidth()
//            .horizontalScroll(scrollState),
//        horizontalArrangement = Arrangement.spacedBy(8.dp),
//    ) {
//        options.forEach { option ->
//            FilterChip(
//                selected = (option == selected),
//                onClick = { onSelected(option) },
//                label = { Text(option.toString()) },
//            )
//        }
//    }
//}
//
//// ---------------------------------------------------------------------------
//// ViewModel stub – replace with your real implementation
//// ---------------------------------------------------------------------------
//
///**
// * Stub interface. Replace with your actual ViewModel class that extends
// * [androidx.lifecycle.ViewModel] and injects your repository.
// */
//interface SettingsViewModel {
//    val settings: kotlinx.coroutines.flow.StateFlow<MySettings>
//    fun updateSettings(settings: MySettings)
//}
//
//@Preview(showBackground = true)
//@Composable
//fun SettingsScreenPreview() {
//    VideoTrainerTheme {
//        SettingsScreenContent(
//            settings = MySettings(paramA = 3, paramB = 7, paramC = "lama", paramD = 0.314f),
//            onSettingsChange = {},
//            onNavigateBack = {},
//        )
//    }
//}




//import androidx.compose.foundation.layout.*
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.automirrored.rounded.ArrowBack
//import androidx.compose.material3.*
//import androidx.compose.runtime.Composable
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.tooling.preview.Preview
//import androidx.compose.ui.unit.dp
//import com.liorapps.videotrainer.ui.theme.VideoTrainerTheme
//
//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun SettingsScreen(
//    onNavigateBack: () -> Unit
//) {
//    Scaffold(
//        topBar = {
//            TopAppBar(
//                title = { Text("Settings") },
//                navigationIcon = {
//                    IconButton(onClick = onNavigateBack) {
//                        Icon(
//                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
//                            contentDescription = "Back"
//                        )
//                    }
//                }
//            )
//        }
//    ) { innerPadding ->
//        Column(
//            modifier = Modifier
//                .fillMaxSize()
//                .padding(innerPadding)
//                .padding(16.dp),
//            verticalArrangement = Arrangement.Top,
//            horizontalAlignment = Alignment.Start
//        ) {
//            Text(
//                text = "Video Trainer Settings",
//                style = MaterialTheme.typography.headlineSmall
//            )
//            Spacer(modifier = Modifier.height(16.dp))
//            Text("Dummy settings placeholder...")
//        }
//    }
//}

//@Preview(showBackground = true)
//@Composable
//fun SettingsScreenPreview() {
//    VideoTrainerTheme {
//        SettingsScreen(onNavigateBack = {})
//    }
//}
