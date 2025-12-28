package com.calypsan.listenup.client.features.shell.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.design.components.ListenUpLoadingIndicatorSmall
import com.calypsan.listenup.client.presentation.sync.PendingOperationUi
import com.calypsan.listenup.client.presentation.sync.SyncIndicatorUiState

/**
 * Bottom sheet showing sync status details.
 *
 * Displays:
 * - Current sync status (syncing/idle)
 * - Pending operations count
 * - Failed operations with retry/dismiss actions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncDetailsSheet(
    state: SyncIndicatorUiState,
    onRetryOperation: (String) -> Unit,
    onDismissOperation: (String) -> Unit,
    onRetryAll: () -> Unit,
    onDismissAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            // Header
            Text(
                text = "Sync Status",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 16.dp),
            )

            // Current status
            SyncStatusSection(
                isSyncing = state.isSyncing,
                currentOperation = state.currentOperationDescription,
                pendingCount = state.pendingCount,
            )

            // Failed operations section
            if (state.failedOperations.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                FailedOperationsSection(
                    failedOperations = state.failedOperations,
                    onRetryOperation = onRetryOperation,
                    onDismissOperation = onDismissOperation,
                    onRetryAll = onRetryAll,
                    onDismissAll = onDismissAll,
                )
            }

            // Success state when nothing pending and no errors
            if (!state.isSyncing && state.pendingCount == 0 && state.failedOperations.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                SyncCompleteSection()
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SyncStatusSection(
    isSyncing: Boolean,
    currentOperation: String?,
    pendingCount: Int,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSyncing) {
                ListenUpLoadingIndicatorSmall()
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Syncing...",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    currentOperation?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else if (pendingCount > 0) {
                Icon(
                    imageVector = Icons.Default.Pending,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "$pendingCount pending",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Changes waiting to sync",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "No pending changes",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun SyncCompleteSection() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.CloudDone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "All synced",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "Your library is up to date",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun FailedOperationsSection(
    failedOperations: List<PendingOperationUi>,
    onRetryOperation: (String) -> Unit,
    onDismissOperation: (String) -> Unit,
    onRetryAll: () -> Unit,
    onDismissAll: () -> Unit,
) {
    // Section header with bulk actions
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${failedOperations.size} failed",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Row {
            TextButton(onClick = onRetryAll) {
                Text("Retry all")
            }
            TextButton(onClick = onDismissAll) {
                Text("Dismiss all")
            }
        }
    }

    Spacer(Modifier.height(8.dp))

    // List of failed operations
    LazyColumn(
        modifier = Modifier.height((failedOperations.size.coerceAtMost(4) * 72).dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(failedOperations, key = { it.id }) { operation ->
            FailedOperationItem(
                operation = operation,
                onRetry = { onRetryOperation(operation.id) },
                onDismiss = { onDismissOperation(operation.id) },
            )
        }
    }
}

@Composable
private fun FailedOperationItem(
    operation: PendingOperationUi,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = operation.description,
                    style = MaterialTheme.typography.bodyMedium,
                )
                operation.error?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                    )
                }
            }

            IconButton(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Retry",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
