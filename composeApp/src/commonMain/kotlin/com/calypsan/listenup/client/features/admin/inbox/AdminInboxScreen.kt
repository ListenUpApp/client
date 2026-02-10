@file:Suppress("MagicNumber", "LongMethod", "CognitiveComplexMethod")

package com.calypsan.listenup.client.features.admin.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Publish
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import com.calypsan.listenup.client.design.components.ListenUpLoadingIndicatorSmall
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import com.calypsan.listenup.client.design.components.ListenUpExtendedFab
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import coil3.compose.AsyncImage
import com.calypsan.listenup.client.design.components.FullScreenLoadingIndicator
import com.calypsan.listenup.client.design.components.ListenUpDestructiveDialog
import com.calypsan.listenup.client.domain.model.InboxBook
import com.calypsan.listenup.client.presentation.admin.AdminInboxUiState
import com.calypsan.listenup.client.presentation.admin.AdminInboxViewModel
import org.jetbrains.compose.resources.stringResource
import listenup.composeapp.generated.resources.Res
import listenup.composeapp.generated.resources.common_inbox
import listenup.composeapp.generated.resources.common_inbox_empty
import listenup.composeapp.generated.resources.admin_newly_scanned_books_will_appear
import listenup.composeapp.generated.resources.admin_no_collections_will_be_public
import listenup.composeapp.generated.resources.common_release
import listenup.composeapp.generated.resources.common_release_anyway
import listenup.composeapp.generated.resources.common_release_without_collections
import listenup.composeapp.generated.resources.admin_these_books_will_become_visible
import listenup.composeapp.generated.resources.admin_will_be_released_without_any

/**
 * Admin screen for managing the inbox staging workflow.
 *
 * Displays newly scanned books that need admin review before becoming
 * visible to users. Supports batch selection and release operations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminInboxScreen(
    viewModel: AdminInboxViewModel,
    onBackClick: () -> Unit,
    onBookClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showReleaseConfirmation by remember { mutableStateOf(false) }

    // Handle errors
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Handle release success
    LaunchedEffect(state.lastReleaseResult) {
        state.lastReleaseResult?.let { result ->
            val message =
                buildString {
                    append("Released ${result.released} book${if (result.released != 1) "s" else ""}")
                    if (result.publicCount > 0) {
                        append(" (${result.publicCount} public)")
                    }
                }
            snackbarHostState.showSnackbar(message)
            viewModel.clearReleaseResult()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    if (state.hasSelection) {
                        Text("${state.selectedCount} selected")
                    } else {
                        Text(stringResource(Res.string.common_inbox))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.hasSelection) {
                            viewModel.clearSelection()
                        } else {
                            onBackClick()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (state.hasBooks) {
                        IconButton(onClick = {
                            if (state.allSelected) {
                                viewModel.clearSelection()
                            } else {
                                viewModel.selectAll()
                            }
                        }) {
                            Icon(
                                imageVector =
                                    if (state.allSelected) {
                                        Icons.Outlined.CheckBox
                                    } else {
                                        Icons.Outlined.SelectAll
                                    },
                                contentDescription = if (state.allSelected) "Deselect all" else "Select all",
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (state.hasSelection) {
                ListenUpExtendedFab(
                    onClick = {
                        if (viewModel.hasSelectedBooksWithoutCollections()) {
                            showReleaseConfirmation = true
                        } else {
                            viewModel.releaseBooks(state.selectedBookIds.toList())
                        }
                    },
                    icon = Icons.Outlined.Publish,
                    text = stringResource(Res.string.common_release),
                    isLoading = state.isReleasing,
                )
            }
        },
    ) { innerPadding ->
        if (state.isLoading && state.books.isEmpty()) {
            FullScreenLoadingIndicator()
        } else {
            InboxContent(
                state = state,
                onBookClick = onBookClick,
                onBookSelectionToggle = { viewModel.toggleBookSelection(it) },
                modifier = Modifier.padding(innerPadding),
            )
        }
    }

    // Release confirmation dialog (shown when releasing books without collections)
    if (showReleaseConfirmation) {
        val booksWithoutCollections =
            state.books
                .filter { it.id in state.selectedBookIds && it.stagedCollectionIds.isEmpty() }
                .size

        ListenUpDestructiveDialog(
            onDismissRequest = { showReleaseConfirmation = false },
            title = stringResource(Res.string.common_release_without_collections),
            text =
                "$booksWithoutCollections book${if (booksWithoutCollections != 1) "s" else ""} " +
                    stringResource(Res.string.admin_will_be_released_without_any) +
                    stringResource(Res.string.admin_these_books_will_become_visible),
            confirmText = stringResource(Res.string.common_release_anyway),
            onConfirm = {
                showReleaseConfirmation = false
                viewModel.releaseBooks(state.selectedBookIds.toList())
            },
            onDismiss = { showReleaseConfirmation = false },
        )
    }
}

@Composable
private fun InboxContent(
    state: AdminInboxUiState,
    onBookClick: (String) -> Unit,
    onBookSelectionToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.books.isEmpty()) {
        EmptyInboxMessage(modifier = modifier)
    } else {
        LazyColumn(
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
        ) {
            item {
                Text(
                    text = "${state.books.size} book${if (state.books.size != 1) "s" else ""} awaiting review",
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
                        state.books.forEachIndexed { index, book ->
                            InboxBookRow(
                                book = book,
                                isSelected = book.id in state.selectedBookIds,
                                isReleasing = book.id in state.releasingBookIds,
                                onClick = { onBookClick(book.id) },
                                onSelectionToggle = { onBookSelectionToggle(book.id) },
                            )
                            if (index < state.books.lastIndex) {
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

@Composable
private fun InboxBookRow(
    book: InboxBook,
    isSelected: Boolean,
    isReleasing: Boolean,
    onClick: () -> Unit,
    onSelectionToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                ).clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Selection checkbox
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onSelectionToggle() },
            enabled = !isReleasing,
        )

        // Book cover
        AsyncImage(
            model = book.coverUrl,
            contentDescription = book.title,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp)),
        )

        // Book info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            book.author?.let { author ->
                Text(
                    text = author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Show staged collections count
            if (book.stagedCollectionIds.isNotEmpty()) {
                val count = book.stagedCollectionIds.size
                Text(
                    text = "$count collection${if (count != 1) "s" else ""} staged",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(
                    text = stringResource(Res.string.admin_no_collections_will_be_public),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                )
            }
        }

        // Loading indicator
        if (isReleasing) {
            ListenUpLoadingIndicatorSmall()
        }
    }
}

@Composable
private fun EmptyInboxMessage(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Inbox,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.common_inbox_empty),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(Res.string.admin_newly_scanned_books_will_appear),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
