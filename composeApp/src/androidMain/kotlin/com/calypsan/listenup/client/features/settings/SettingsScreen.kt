package com.calypsan.listenup.client.features.settings

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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calypsan.listenup.client.features.nowplaying.PlaybackSpeedPresets
import com.calypsan.listenup.client.presentation.settings.SettingsViewModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

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
            // Playback section
            SettingsSection(title = "Playback") {
                SettingsDropdownItem(
                    title = "Default Playback Speed",
                    description = "Speed used for new books",
                    selectedValue = state.defaultPlaybackSpeed,
                    options = PlaybackSpeedPresets.presets,
                    formatValue = { PlaybackSpeedPresets.format(it) },
                    onValueSelected = viewModel::setDefaultPlaybackSpeed,
                )
                SettingsToggleItem(
                    title = "Spatial Audio",
                    description =
                        "Enable 5.1 surround sound for immersive listening. " +
                            "Requires longer initial load for incompatible formats.",
                    checked = state.spatialPlayback,
                    onCheckedChange = viewModel::setSpatialPlayback,
                )
            }

            // Library section
            SettingsSection(title = "Library") {
                SettingsToggleItem(
                    title = "Ignore articles when sorting",
                    description = "Sort titles ignoring leading articles (A, An, The)",
                    checked = state.ignoreTitleArticles,
                    onCheckedChange = viewModel::setIgnoreTitleArticles,
                )
                SettingsToggleItem(
                    title = "Hide single-book series",
                    description = "Hide series with only one book from the Series tab",
                    checked = state.hideSingleBookSeries,
                    onCheckedChange = viewModel::setHideSingleBookSeries,
                )
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
        modifier = Modifier.padding(horizontal = 8.dp),
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
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}
