package com.calypsan.listenup.client.features.admin.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import com.calypsan.listenup.client.design.components.FullScreenLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.design.components.ListenUpButton
import com.calypsan.listenup.client.presentation.admin.AdminBackupViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateBackupScreen(
    viewModel: AdminBackupViewModel = koinInject(),
    onBackClick: () -> Unit,
    onSuccess: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var includeEvents by remember { mutableStateOf(true) }
    var includeImages by remember { mutableStateOf(false) }
    var hasStartedCreation by remember { mutableStateOf(false) }

    // Navigate back on success
    LaunchedEffect(state.isCreating, hasStartedCreation) {
        if (hasStartedCreation && !state.isCreating && state.error == null) {
            onSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Backup") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { paddingValues ->
        if (state.isCreating) {
            CreatingBackupContent(
                modifier = Modifier.padding(paddingValues),
            )
        } else {
            CreateBackupForm(
                includeEvents = includeEvents,
                includeImages = includeImages,
                onIncludeEventsChange = { includeEvents = it },
                onIncludeImagesChange = { includeImages = it },
                error = state.error,
                onCreateClick = {
                    hasStartedCreation = true
                    viewModel.createBackup(
                        includeImages = includeImages,
                        includeEvents = includeEvents,
                    )
                },
                modifier = Modifier.padding(paddingValues),
            )
        }
    }
}

@Composable
private fun CreateBackupForm(
    includeEvents: Boolean,
    includeImages: Boolean,
    onIncludeEventsChange: (Boolean) -> Unit,
    onIncludeImagesChange: (Boolean) -> Unit,
    error: String?,
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Create a backup of your ListenUp server data.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "What to include",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                // Events option
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Checkbox(
                        checked = includeEvents,
                        onCheckedChange = onIncludeEventsChange,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Timeline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "Listening history",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        Text(
                            text = "Events, sessions, and progress data",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.height(16.dp),
                            )
                            Text(
                                text = "Recommended for full restore capability",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Images option
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Checkbox(
                        checked = includeImages,
                        onCheckedChange = onIncludeImagesChange,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Image,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                            Text(
                                text = "Cover images",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        Text(
                            text = "Book covers and user avatars",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.height(16.dp),
                            )
                            Text(
                                text = "Significantly increases backup size",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }
            }
        }

        // Info card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "ℹ️",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (includeImages) {
                        "Backup will include images. This may take a while and result in a larger file."
                    } else {
                        "Backup will not include images. Estimated size depends on your library."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }

        // Error display
        error?.let {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        ListenUpButton(
            onClick = onCreateClick,
            text = "Create Backup",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CreatingBackupContent(
    modifier: Modifier = Modifier,
) {
    FullScreenLoadingIndicator(message = "Creating Backup...")
}
