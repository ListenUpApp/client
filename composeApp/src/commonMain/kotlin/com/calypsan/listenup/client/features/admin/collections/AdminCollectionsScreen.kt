@file:Suppress("MagicNumber")

package com.calypsan.listenup.client.features.admin.collections

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.calypsan.listenup.client.design.components.FullScreenLoadingIndicator
import com.calypsan.listenup.client.design.components.ListenUpLoadingIndicatorSmall
import com.calypsan.listenup.client.design.components.ListenUpDestructiveDialog
import com.calypsan.listenup.client.design.components.ListenUpTextField
import com.calypsan.listenup.client.domain.model.Collection
import com.calypsan.listenup.client.presentation.admin.AdminCollectionsUiState
import com.calypsan.listenup.client.presentation.admin.AdminCollectionsViewModel

/**
 * Admin screen for managing collections.
 *
 * Displays a list of collections with create and delete functionality.
 * Tap a collection to navigate to its detail screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminCollectionsScreen(
    viewModel: AdminCollectionsViewModel,
    onBackClick: () -> Unit,
    onCollectionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showCreateDialog by remember { mutableStateOf(false) }
    var collectionToDelete by remember { mutableStateOf<Collection?>(null) }

    // Handle errors
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Handle create success
    LaunchedEffect(state.createSuccess) {
        if (state.createSuccess) {
            showCreateDialog = false // Dismiss dialog FIRST (before suspend)
            viewModel.clearCreateSuccess()
            snackbarHostState.showSnackbar("Collection created") // This suspends
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Collections") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "Create Collection",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        },
    ) { innerPadding ->
        if (state.isLoading && state.collections.isEmpty()) {
            FullScreenLoadingIndicator()
        } else {
            CollectionsContent(
                state = state,
                onCollectionClick = onCollectionClick,
                onDeleteClick = { collectionToDelete = it },
                modifier = Modifier.padding(innerPadding),
            )
        }
    }

    // Create collection dialog
    if (showCreateDialog) {
        CreateCollectionDialog(
            isCreating = state.isCreating,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name -> viewModel.createCollection(name) },
        )
    }

    // Delete confirmation dialog
    collectionToDelete?.let { collection ->
        val warningText =
            if (collection.bookCount > 0) {
                """
                    |Are you sure you want to delete "${collection.name}"?
                    |
                    |The ${collection.bookCount} book${if (collection.bookCount != 1) "s" else ""} in this collection will become visible to all users.
                """.trimMargin()
            } else {
                "Are you sure you want to delete \"${collection.name}\"?"
            }

        ListenUpDestructiveDialog(
            onDismissRequest = { collectionToDelete = null },
            title = "Delete Collection",
            text = warningText,
            confirmText = "Delete",
            onConfirm = {
                viewModel.deleteCollection(collection.id)
                collectionToDelete = null
            },
            onDismiss = { collectionToDelete = null },
        )
    }
}

@Composable
private fun CollectionsContent(
    state: AdminCollectionsUiState,
    onCollectionClick: (String) -> Unit,
    onDeleteClick: (Collection) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.collections.isEmpty()) {
        EmptyCollectionsMessage(modifier = modifier)
    } else {
        LazyColumn(
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
        ) {
            item {
                Text(
                    text = "${state.collections.size} collection${if (state.collections.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors =
                        CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                ) {
                    Column {
                        state.collections.forEachIndexed { index, collection ->
                            CollectionRow(
                                collection = collection,
                                isDeleting = state.deletingCollectionId == collection.id,
                                onClick = { onCollectionClick(collection.id) },
                                onDeleteClick = { onDeleteClick(collection) },
                            )
                            if (index < state.collections.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(88.dp)) // Space for FAB
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionRow(
    collection: Collection,
    isDeleting: Boolean,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState =
        rememberSwipeToDismissBoxState(
            initialValue = SwipeToDismissBoxValue.Settled,
            confirmValueChange = { dismissValue ->
                if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                    onDeleteClick()
                    false // Don't actually dismiss, let the dialog handle it
                } else {
                    false
                }
            },
        )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color by animateColorAsState(
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    Color.Transparent
                },
                label = "SwipeBackground",
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(color)
                        .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        },
        enableDismissFromStartToEnd = false,
        modifier = modifier,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = collection.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${collection.bookCount} book${if (collection.bookCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (isDeleting) {
                ListenUpLoadingIndicatorSmall()
            }
        }
    }
}

@Composable
private fun EmptyCollectionsMessage(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Collections",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Create a collection to organize your audiobooks",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CreateCollectionDialog(
    isCreating: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Create Collection") },
        text = {
            ListenUpTextField(
                value = name,
                onValueChange = { name = it },
                label = "Collection Name",
                enabled = !isCreating,
                supportingText = "Enter a name for the collection",
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                enabled = !isCreating && name.isNotBlank(),
            ) {
                if (isCreating) {
                    ListenUpLoadingIndicatorSmall()
                } else {
                    Text("Create")
                }
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(
                onClick = onDismiss,
                enabled = !isCreating,
            ) {
                Text("Cancel")
            }
        },
    )
}
