@file:Suppress("LongMethod")

package com.calypsan.listenup.client.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.domain.model.ThemeMode
import com.calypsan.listenup.client.presentation.settings.SettingsViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Preset playback speeds.
 */
object PlaybackSpeedPresets {
    val presets = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)

    fun format(speed: Float): String =
        if (speed == speed.toInt().toFloat()) {
            "${speed.toInt()}.0x"
        } else {
            val formatted = "%.2f".format(speed).trimEnd('0').trimEnd('.')
            "${formatted}x"
        }
}

/**
 * Preset durations for skip forward button (in seconds).
 */
@Suppress("MagicNumber")
object SkipForwardPresets {
    val presets = listOf(10, 15, 20, 30, 45, 60, 90, 120)

    fun format(seconds: Int): String =
        when {
            seconds >= 60 && seconds % 60 == 0 -> "${seconds / 60} min"
            seconds >= 60 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
}

/**
 * Preset durations for skip backward button (in seconds).
 */
@Suppress("MagicNumber")
object SkipBackwardPresets {
    val presets = listOf(5, 10, 15, 20, 30, 45, 60)

    fun format(seconds: Int): String =
        when {
            seconds >= 60 && seconds % 60 == 0 -> "${seconds / 60} min"
            seconds >= 60 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
}

/**
 * Preset durations for sleep timer (in minutes).
 * Includes "Off" option represented as null.
 */
@Suppress("MagicNumber")
object SleepTimerPresets {
    val presets: List<Int?> = listOf(null, 5, 10, 15, 20, 30, 45, 60, 90, 120)

    fun format(minutes: Int?): String =
        when (minutes) {
            null -> "Off"
            60 -> "1 hour"
            90 -> "1.5 hours"
            120 -> "2 hours"
            else -> "$minutes min"
        }
}

/**
 * Settings screen for desktop.
 *
 * Displays user-configurable settings organized by category:
 * - Appearance: Theme
 * - Playback: Speed, skip intervals, auto-rewind, spatial audio
 * - Sleep Timer: Default duration
 * - Library: Sorting and display options
 * - Account: Server info and sign out
 * - About: Version information
 *
 * @param onNavigateBack Callback to navigate back
 * @param onNavigateToLicenses Optional callback to navigate to licenses screen
 * @param viewModel SettingsViewModel injected via Koin
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToStorage: (() -> Unit)? = null,
    onNavigateToLicenses: (() -> Unit)? = null,
    showDynamicColors: Boolean = false,
    showSleepTimer: Boolean = true,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showSignOutDialog by remember { mutableStateOf(false) }

    // Sign out confirmation dialog
    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            shape = MaterialTheme.shapes.large,
            title = { Text("Sign Out") },
            text = { Text("Are you sure you want to sign out? You'll need to sign in again to access your library.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.signOut()
                        showSignOutDialog = false
                    },
                ) {
                    Text("Sign Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
        ) {
            // Appearance section
            SettingsSection(title = "Appearance") {
                SettingsDropdownItem(
                    title = "Theme",
                    description = "Choose light, dark, or follow system",
                    selectedValue = state.themeMode,
                    options = ThemeMode.entries.toList(),
                    formatValue = { mode ->
                        when (mode) {
                            ThemeMode.SYSTEM -> "System"
                            ThemeMode.LIGHT -> "Light"
                            ThemeMode.DARK -> "Dark"
                        }
                    },
                    onValueSelected = viewModel::setThemeMode,
                )
                if (showDynamicColors) {
                    SettingsToggleItem(
                        title = "Dynamic colors",
                        description = "Use colors from your wallpaper (Material You)",
                        checked = state.dynamicColorsEnabled,
                        onCheckedChange = viewModel::setDynamicColorsEnabled,
                    )
                }
            }

            SettingsDivider()

            // Playback section
            SettingsSection(title = "Playback") {
                SettingsDropdownItem(
                    title = "Default speed",
                    description = "Speed used for new books",
                    selectedValue = state.defaultPlaybackSpeed,
                    options = PlaybackSpeedPresets.presets,
                    formatValue = { PlaybackSpeedPresets.format(it) },
                    onValueSelected = viewModel::setDefaultPlaybackSpeed,
                )
                SettingsDropdownItem(
                    title = "Skip forward",
                    description = "Duration when pressing skip forward",
                    selectedValue = state.defaultSkipForwardSec,
                    options = SkipForwardPresets.presets,
                    formatValue = { SkipForwardPresets.format(it) },
                    onValueSelected = viewModel::setDefaultSkipForwardSec,
                )
                SettingsDropdownItem(
                    title = "Skip backward",
                    description = "Duration when pressing skip backward",
                    selectedValue = state.defaultSkipBackwardSec,
                    options = SkipBackwardPresets.presets,
                    formatValue = { SkipBackwardPresets.format(it) },
                    onValueSelected = viewModel::setDefaultSkipBackwardSec,
                )
                SettingsToggleItem(
                    title = "Auto-rewind on resume",
                    description = "Rewind a few seconds when resuming playback",
                    checked = state.autoRewindEnabled,
                    onCheckedChange = viewModel::setAutoRewindEnabled,
                )
                SettingsToggleItem(
                    title = "Spatial audio",
                    description = "5.1 surround sound for immersive listening",
                    checked = state.spatialPlayback,
                    onCheckedChange = viewModel::setSpatialPlayback,
                )
            }

            if (showSleepTimer) {
                SettingsDivider()

                // Sleep timer section
                SettingsSection(title = "Sleep Timer") {
                    SettingsDropdownItem(
                        title = "Default timer",
                        description = "Auto-start sleep timer when playing",
                        selectedValue = state.defaultSleepTimerMin,
                        options = SleepTimerPresets.presets,
                        formatValue = { SleepTimerPresets.format(it) },
                        onValueSelected = viewModel::setDefaultSleepTimerMin,
                    )
                }
            }

            SettingsDivider()

            // Library section
            SettingsSection(title = "Library") {
                SettingsToggleItem(
                    title = "Ignore articles when sorting",
                    description = "Sort ignoring leading articles (A, An, The)",
                    checked = state.ignoreTitleArticles,
                    onCheckedChange = viewModel::setIgnoreTitleArticles,
                )
                SettingsToggleItem(
                    title = "Hide single-book series",
                    description = "Hide series with only one book",
                    checked = state.hideSingleBookSeries,
                    onCheckedChange = viewModel::setHideSingleBookSeries,
                )
            }

            SettingsDivider()

            // Account section
            SettingsSection(title = "Account") {
                state.serverUrl?.let { url ->
                    SettingsInfoItem(
                        title = "Server",
                        value = url.removePrefix("https://").removePrefix("http://"),
                    )
                }
                SettingsActionItem(
                    title = "Sign out",
                    icon = Icons.AutoMirrored.Filled.Logout,
                    onClick = { showSignOutDialog = true },
                    destructive = true,
                )
            }

            if (onNavigateToStorage != null) {
                SettingsDivider()

                SettingsSection(title = "Storage") {
                    SettingsNavigationItem(
                        title = "Manage storage",
                        description = "View and manage downloaded audiobooks",
                        onClick = onNavigateToStorage,
                    )
                }
            }

            SettingsDivider()

            // About section
            SettingsSection(title = "About") {
                SettingsInfoItem(
                    title = "App version",
                    value = "Desktop",
                )
                state.serverVersion?.let { version ->
                    SettingsInfoItem(
                        title = "Server version",
                        value = version,
                    )
                }
                if (onNavigateToLicenses != null) {
                    SettingsNavigationItem(
                        title = "Open source licenses",
                        description = "View third-party licenses",
                        onClick = onNavigateToLicenses,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        content()
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun SettingsToggleItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

/**
 * Settings item that displays info without interaction.
 */
@Composable
private fun SettingsInfoItem(
    title: String,
    value: String,
) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

/**
 * Settings item that navigates to another screen.
 */
@Composable
private fun SettingsNavigationItem(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier =
            Modifier
                .padding(horizontal = 8.dp)
                .clickable(onClick = onClick),
    )
}

/**
 * Settings item for actions (like sign out) with optional icon.
 */
@Composable
private fun SettingsActionItem(
    title: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val contentColor =
        if (destructive) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurface
        }

    ListItem(
        headlineContent = {
            Text(
                text = title,
                color = contentColor,
            )
        },
        leadingContent =
            icon?.let {
                {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = contentColor,
                    )
                }
            },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier =
            Modifier
                .padding(horizontal = 8.dp)
                .clickable(onClick = onClick),
    )
}

/**
 * Settings item with dropdown selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SettingsDropdownItem(
    title: String,
    description: String,
    selectedValue: T,
    options: List<T>,
    formatValue: (T) -> String,
    onValueSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
            ) {
                OutlinedTextField(
                    value = formatValue(selectedValue),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    modifier =
                        Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .width(100.dp),
                    singleLine = true,
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(formatValue(option)) },
                            onClick = {
                                onValueSelected(option)
                                expanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                        )
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}
