package com.calypsan.listenup.client.features.admin.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.calypsan.listenup.client.design.components.FullScreenLoadingIndicator
import com.calypsan.listenup.client.design.components.ListenUpLoadingIndicatorSmall
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.domain.model.BackupInfo
import com.calypsan.listenup.client.domain.model.BackupValidation
import com.calypsan.listenup.client.presentation.admin.AdminBackupState
import com.calypsan.listenup.client.presentation.admin.AdminBackupViewModel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminBackupScreen(
    viewModel: AdminBackupViewModel = koinInject(),
    onBackClick: () -> Unit,
    onCreateClick: () -> Unit,
    onRestoreClick: (String) -> Unit,
    onABSImportClick: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backups") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            if (!state.isLoading) {
                FloatingActionButton(onClick = onCreateClick) {
                    Icon(Icons.Default.Add, contentDescription = "Create Backup")
                }
            }
        },
    ) { paddingValues ->
        AdminBackupContent(
            state = state,
            modifier = Modifier.padding(paddingValues),
            onRestoreClick = onRestoreClick,
            onDeleteClick = { viewModel.showDeleteConfirmation(it) },
            onValidateClick = { viewModel.validateBackup(it) },
            onABSImportClick = onABSImportClick,
        )
    }

    // Delete confirmation dialog
    state.deleteConfirmBackup?.let { backup ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteConfirmation() },
            title = { Text("Delete Backup") },
            text = { Text("Are you sure you want to delete ${backup.id}? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteBackup(backup) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteConfirmation() }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Validation result dialog
    state.validationResult?.let { validation ->
        ValidationResultDialog(
            validation = validation,
            onDismiss = { viewModel.dismissValidation() },
        )
    }
}

@Composable
private fun AdminBackupContent(
    state: AdminBackupState,
    modifier: Modifier = Modifier,
    onRestoreClick: (String) -> Unit,
    onDeleteClick: (BackupInfo) -> Unit,
    onValidateClick: (BackupInfo) -> Unit,
    onABSImportClick: () -> Unit,
) {
    when {
        state.isLoading -> {
            FullScreenLoadingIndicator()
        }
        state.backups.isEmpty() -> {
            EmptyBackupState(modifier = modifier, onABSImportClick = onABSImportClick)
        }
        else -> {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.backups, key = { it.id }) { backup ->
                    BackupCard(
                        backup = backup,
                        isValidating = state.validatingBackupId == backup.id,
                        onRestoreClick = { onRestoreClick(backup.id) },
                        onDeleteClick = { onDeleteClick(backup) },
                        onValidateClick = { onValidateClick(backup) },
                    )
                }
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                item { ABSImportCard(onClick = onABSImportClick) }
            }
        }
    }
}

@Composable
private fun BackupCard(
    backup: BackupInfo,
    isValidating: Boolean,
    onRestoreClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onValidateClick: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Archive, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = backup.id, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box {
                    if (isValidating) {
                        ListenUpLoadingIndicatorSmall()
                    } else {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Restore") }, onClick = { showMenu = false; onRestoreClick() }, leadingIcon = { Icon(Icons.Default.Restore, contentDescription = null) })
                        DropdownMenuItem(text = { Text("Validate") }, onClick = { showMenu = false; onValidateClick() }, leadingIcon = { Icon(Icons.Default.Verified, contentDescription = null) })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { showMenu = false; onDeleteClick() }, leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) })
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            val localDateTime = backup.createdAt.toLocalDateTime(TimeZone.currentSystemDefault())
            Text(text = "Created: ${localDateTime.date} at ${localDateTime.hour}:${localDateTime.minute.toString().padStart(2, '0')}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Size: ${backup.sizeFormatted}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ABSImportCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Upload, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("Import from Audiobookshelf", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text("Migrate your listening history", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun EmptyBackupState(modifier: Modifier = Modifier, onABSImportClick: () -> Unit) {
    Column(modifier = modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(16.dp))
        Text("No backups yet", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Create a backup to protect your data", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        Spacer(modifier = Modifier.height(32.dp))
        ABSImportCard(onClick = onABSImportClick)
    }
}

@Composable
private fun ValidationResultDialog(validation: BackupValidation, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (validation.valid) Icons.Default.CheckCircle else Icons.Default.Archive, contentDescription = null, tint = if (validation.valid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (validation.valid) "Backup Valid" else "Backup Invalid")
            }
        },
        text = {
            Column {
                validation.serverName?.let { Text("Server: $it", style = MaterialTheme.typography.bodyMedium) }
                validation.version?.let { Text("Version: $it", style = MaterialTheme.typography.bodyMedium) }
                if (validation.entityCounts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Contents:", style = MaterialTheme.typography.titleSmall)
                    validation.entityCounts.forEach { (type, count) -> Text("  $type: $count", style = MaterialTheme.typography.bodySmall) }
                }
                if (validation.warnings.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Warnings:", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.tertiary)
                    validation.warnings.forEach { Text("  $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
                }
                if (validation.errors.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Errors:", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                    validation.errors.forEach { Text("  $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
