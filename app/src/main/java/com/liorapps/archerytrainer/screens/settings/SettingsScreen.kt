@file:Suppress("AssignedValueIsNeverRead", "AssignedValueIsNeverRead", "AssignedValueIsNeverRead",
    "AssignedValueIsNeverRead"
)

package com.liorapps.archerytrainer.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liorapps.archerytrainer.ArcheryTrainerDefaults
import com.liorapps.archerytrainer.ui.theme.ArcheryTrainerTheme
import kotlin.math.roundToInt
import kotlin.text.format

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
) {
    val settings by viewModel.settingsFlow.collectAsStateWithLifecycle()

    SettingsScreenContent(
        settings = settings,
        onSettingsChange = viewModel::updateSettings,
        onNavigateBack = onNavigateBack,
    )
}

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
            // ---- Video ----------------------------------------------
            SettingsSectionHeader(title = "Video")

            SettingsStringItem(
                title = "Delay (Sec)",
                value = settings.delaySec.toString(),
                onClick = { openDialog = DialogType.DELAY_SEC },
            )

            SettingsStringItem(
                title = "Video Bitrate (Bit/Sec)",
                value = "%,d".format(settings.bitRate),
                onClick = { openDialog = DialogType.VIDEO_BITRATE },
            )
//            HorizontalDivider(modifier = Modifier.padding(start = 16.dp))

            SettingsStringItem(
                title = "Video Resolution",
                value = settings.videoResolution.displayName,
                onClick = { openDialog = DialogType.VIDEO_RESOLUTION },
            )

            // ---- Shooting Sessions ----------------------------------------------
            SettingsSectionHeader(title = "Shooting Sessions")

            SettingsBooleanItem(
                title = "Give Sets Scores",
                description = "Specify for each set a total score",
                checked = settings.shootingSetsHaveScores,
                onCheckedChange = { onSettingsChange(settings.copy(shootingSetsHaveScores = it)) },
            )

            SettingsStringItem(
                title = "Warn if Sets Too Close",
                value = "${settings.timeBetweenSetsForTooSoonWarn} Sec. - You'll get a warning if sets are less than ${settings.timeBetweenSetsForTooSoonWarn} seconds apart (0 to disable)",
                onClick = { openDialog = DialogType.SETS_TOO_CLOSE_WARN },
            )

            SettingsStringItem(
                title = "Set Buttons",
                value = settings.shootingSessionButtonValues.ifBlank { "(blank)" },
                onClick = { openDialog = DialogType.PARAM_C },
            )
//            HorizontalDivider(modifier = Modifier.padding(start = 16.dp))

//            SettingsStringItem(
//                title = "Dummy Float",
//                value = "%.2f".format(settings.dummyFloat),
//                onClick = { openDialog = DialogType.PARAM_D },
//            )
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

        DialogType.VIDEO_RESOLUTION -> VideoResolutionDialog(
            current = settings.videoResolution,
            onConfirm = { newValue ->
                onSettingsChange(settings.copy(videoResolution = newValue))
                openDialog = null
            },
            onDismiss = { openDialog = null },
        )

        DialogType.SETS_TOO_CLOSE_WARN -> SetsTooCloseWarnDialog(
            current = settings.timeBetweenSetsForTooSoonWarn,
            onConfirm = { newValue ->
                onSettingsChange(settings.copy(timeBetweenSetsForTooSoonWarn = newValue))
                openDialog = null
            },
            onDismiss = { openDialog = null },
        )

        DialogType.PARAM_C -> ParamCDialog(
            current = settings.shootingSessionButtonValues,
            onConfirm = { newValue ->
                onSettingsChange(settings.copy(shootingSessionButtonValues = newValue))
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

private enum class DialogType { SETS_TOO_CLOSE_WARN, DELAY_SEC, VIDEO_BITRATE, VIDEO_RESOLUTION, PARAM_C, PARAM_D }

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
fun SettingsBooleanItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Checkbox
            )
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
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
//        Checkbox(
//            checked = checked,
//            onCheckedChange = null // null because the Row handles the click logic
//        )
        Switch(
            checked = checked,
            onCheckedChange = null // null because the Row handles the click logic
        )
    }
}

@Composable
private fun SettingsStringItem(
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
    description: String,
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
            Column( modifier = Modifier.fillMaxWidth()) {
                if (description.isNotEmpty()) {
                    Text(description)
                    Spacer(Modifier.height(30.dp))
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(label) },
                    isError = !isLegalValue(text.toIntOrNull()),
                    supportingText = {
                        if (isError) Text(illegalValueMsg)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
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
    @Suppress("SameParameterValue", "SameParameterValue") title: String,
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
        description = "",
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
        description = "Video bitrate in bits/sec (note: bits not bytes!)",
        label = "Bitrate in bits/sec",
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
        current = current.displayName,
        options = listOf(
            ArcheryTrainerDefaults.VideoResolution.SD_640x480.displayName,
            ArcheryTrainerDefaults.VideoResolution.HD_1280x720.displayName,
            ArcheryTrainerDefaults.VideoResolution.FHD_1920x1080.displayName,
            ArcheryTrainerDefaults.VideoResolution.QHD_2560x1440.displayName,
            ArcheryTrainerDefaults.VideoResolution.UHD_3840x2160.displayName,
        ),
        onConfirm = { newResolution ->
            when (newResolution) {
                ArcheryTrainerDefaults.VideoResolution.SD_640x480.displayName ->
                    onConfirm(ArcheryTrainerDefaults.VideoResolution.SD_640x480)
                ArcheryTrainerDefaults.VideoResolution.HD_1280x720.displayName ->
                    onConfirm(ArcheryTrainerDefaults.VideoResolution.HD_1280x720)
                ArcheryTrainerDefaults.VideoResolution.FHD_1920x1080.displayName ->
                    onConfirm(ArcheryTrainerDefaults.VideoResolution.FHD_1920x1080)
                ArcheryTrainerDefaults.VideoResolution.QHD_2560x1440.displayName ->
                    onConfirm(ArcheryTrainerDefaults.VideoResolution.QHD_2560x1440)
                ArcheryTrainerDefaults.VideoResolution.UHD_3840x2160.displayName ->
                    onConfirm(ArcheryTrainerDefaults.VideoResolution.UHD_3840x2160)
            }
        },
        onDismiss = onDismiss,
    )
}

@Composable
private fun SetsTooCloseWarnDialog(
    current: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    SimpleIntDialog(
        current = current,
        title = "Sets Too Close Warn Threshold",
        description = "If a new set is added soon after a previous one, you'll get a warning (in order to prevent accidental additions). Define here the threshold (in Seconds) for that warning (e.g. 60). 0 to disable this warning.",
        label = "Threshold in Sec.",
        illegalValueMsg = "Please enter a positive number",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        isLegalValue = { value -> (value != null) && (value >= 0) },
    )
}

// todo: 2brm
//@Composable
//private fun ParamBDialog(
//    current: Int,
//    options: List<Int>,
//    onConfirm: (Int) -> Unit,
//    onDismiss: () -> Unit,
//) {
//    var selected by remember { mutableIntStateOf(current) }
//
//    AlertDialog(
//        onDismissRequest = onDismiss,
//        title = { Text("Parameter B") },
//        text = {
//            Column(modifier = Modifier.selectableGroup()) {
//                options.forEach { option ->
//                    Row(
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .selectable(
//                                selected = (option == selected),
//                                onClick = { selected = option },
//                                role = Role.RadioButton,
//                            )
//                            .padding(vertical = 4.dp),
//                        verticalAlignment = Alignment.CenterVertically,
//                    ) {
//                        RadioButton(
//                            selected = (option == selected),
//                            onClick = null, // handled by Row
//                        )
//                        Spacer(modifier = Modifier.width(8.dp))
//                        Text(
//                            text = option.toString(),
//                            style = MaterialTheme.typography.bodyLarge,
//                        )
//                    }
//                }
//            }
//        },
//        confirmButton = {
//            TextButton(onClick = { onConfirm(selected) }) { Text("OK") }
//        },
//        dismissButton = {
//            TextButton(onClick = onDismiss) { Text("Cancel") }
//        },
//    )
//}

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


@Preview(showBackground = true)
@Composable
fun SettingsScreenContentPreview() {
    ArcheryTrainerTheme {
        SettingsScreenContent(
            settings = SettingsRepository.Settings(
                delaySec = 25,
                videoResolution = ArcheryTrainerDefaults.VideoResolution.HD_1280x720,
                frameRate = 30,
                bitRate = 15_000_000,
                shootingSessionButtonValues = "1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12",
                dummyFloat = 0.73f,
                timeBetweenSetsForTooSoonWarn = 33,
            ),
            onSettingsChange = {},
            onNavigateBack = {},
        )
    }
}