@file:Suppress("MagicNumber", "LongMethod", "CognitiveComplexMethod", "StringLiteralDuplication")

package com.calypsan.listenup.client.features.admin.collections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.calypsan.listenup.client.design.components.FullScreenLoadingIndicator
import com.calypsan.listenup.client.design.components.ListenUpLoadingIndicator
import com.calypsan.listenup.client.design.components.ListenUpLoadingIndicatorSmall
import com.calypsan.listenup.client.design.components.ListenUpDestructiveDialog
import com.calypsan.listenup.client.design.components.ListenUpTextField
import com.calypsan.listenup.client.domain.model.AdminUserInfo
import com.calypsan.listenup.client.presentation.admin.AdminCollectionDetailUiState
import com.calypsan.listenup.client.presentation.admin.AdminCollectionDetailViewModel
import com.calypsan.listenup.client.presentation.admin.CollectionBookItem
import com.calypsan.listenup.client.presentation.admin.CollectionShareItem
import org.jetbrains.compose.resources.stringResource
import listenup.composeapp.generated.resources.Res
import listenup.composeapp.generated.resources.common_add
import listenup.composeapp.generated.resources.admin_add_member
import listenup.composeapp.generated.resources.admin_add_members_to_share_this
import listenup.composeapp.generated.resources.admin_administrator
import listenup.composeapp.generated.resources.admin_all_users_are_already_members
import listenup.composeapp.generated.resources.admin_are_you_sure_you_want_6
import listenup.composeapp.generated.resources.admin_books_can_be_added_from
import listenup.composeapp.generated.resources.admin_books_in_collection
import listenup.composeapp.generated.resources.admin_collection_details
import listenup.composeapp.generated.resources.admin_collection_name
import listenup.composeapp.generated.resources.admin_collection_not_found
import listenup.composeapp.generated.resources.admin_collection_updated
import listenup.composeapp.generated.resources.admin_in_this_collection
import listenup.composeapp.generated.resources.admin_loading_users
import listenup.composeapp.generated.resources.common_members
import listenup.composeapp.generated.resources.admin_no_books_in_this_collection
import listenup.composeapp.generated.resources.admin_no_members
import listenup.composeapp.generated.resources.admin_no_users_available
import listenup.composeapp.generated.resources.common_remove
import listenup.composeapp.generated.resources.admin_remove_book
import listenup.composeapp.generated.resources.admin_remove_member
import listenup.composeapp.generated.resources.common_save_changes
import listenup.composeapp.generated.resources.admin_the_book_will_not_be
import listenup.composeapp.generated.resources.admin_the_display_name_for_this
import listenup.composeapp.generated.resources.admin_they_will_no_longer_have

/**
 * Admin screen for viewing and editing a single collection.
 *
 * Features:
 * - View and edit collection name
 * - List books in the collection (when available)
 * - Remove books from the collection
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminCollectionDetailScreen(
    viewModel: AdminCollectionDetailViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var bookToRemove by remember { mutableStateOf<CollectionBookItem?>(null) }
    var shareToRemove by remember { mutableStateOf<CollectionShareItem?>(null) }

    // Handle errors
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Handle save success
    LaunchedEffect(state.saveSuccess) {
        if (state.saveSuccess) {
            snackbarHostState.showSnackbar("Collection updated")
            viewModel.clearSaveSuccess()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.collection?.name ?: "Collection") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (state.isLoading) {
            FullScreenLoadingIndicator()
        } else if (state.collection == null) {
            ErrorContent(
                message = stringResource(Res.string.admin_collection_not_found),
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            CollectionDetailContent(
                state = state,
                onNameChange = viewModel::updateName,
                onSaveClick = viewModel::saveName,
                onRemoveBookClick = { bookToRemove = it },
                onAddMemberClick = viewModel::showAddMemberSheet,
                onRemoveMemberClick = { shareToRemove = it },
                modifier = Modifier.padding(innerPadding),
            )
        }
    }

    // Add member bottom sheet
    if (state.showAddMemberSheet) {
        AddMemberBottomSheet(
            sheetState = sheetState,
            isLoading = state.isLoadingUsers,
            isSharing = state.isSharing,
            users = state.availableUsers,
            onDismiss = viewModel::hideAddMemberSheet,
            onUserSelected = viewModel::shareWithUser,
        )
    }

    // Remove book confirmation dialog
    bookToRemove?.let { book ->
        ListenUpDestructiveDialog(
            onDismissRequest = { bookToRemove = null },
            title = stringResource(Res.string.admin_remove_book),
            text =
                "Are you sure you want to remove \"${book.title}\" from this collection? " +
                    stringResource(Res.string.admin_the_book_will_not_be),
            confirmText = stringResource(Res.string.common_remove),
            onConfirm = {
                viewModel.removeBook(book.id)
                bookToRemove = null
            },
            onDismiss = { bookToRemove = null },
        )
    }

    // Remove member confirmation dialog
    shareToRemove?.let { share ->
        ListenUpDestructiveDialog(
            onDismissRequest = { shareToRemove = null },
            title = stringResource(Res.string.admin_remove_member),
            text =
                stringResource(Res.string.admin_are_you_sure_you_want_6) +
                    stringResource(Res.string.admin_they_will_no_longer_have),
            confirmText = stringResource(Res.string.common_remove),
            onConfirm = {
                viewModel.removeShare(share.id)
                shareToRemove = null
            },
            onDismiss = { shareToRemove = null },
        )
    }
}

@Composable
private fun CollectionDetailContent(
    state: AdminCollectionDetailUiState,
    onNameChange: (String) -> Unit,
    onSaveClick: () -> Unit,
    onRemoveBookClick: (CollectionBookItem) -> Unit,
    onAddMemberClick: () -> Unit,
    onRemoveMemberClick: (CollectionShareItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
    ) {
        // Collection info section
        item {
            Text(
                text = stringResource(Res.string.admin_collection_details),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
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
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Editable name field
                    ListenUpTextField(
                        value = state.editedName,
                        onValueChange = onNameChange,
                        label = stringResource(Res.string.admin_collection_name),
                        enabled = !state.isSaving,
                        supportingText = stringResource(Res.string.admin_the_display_name_for_this),
                    )

                    // Save button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = onSaveClick,
                            enabled =
                                !state.isSaving &&
                                    state.editedName.isNotBlank() &&
                                    state.editedName != state.collection?.name,
                        ) {
                            if (state.isSaving) {
                                ListenUpLoadingIndicatorSmall()
                            } else {
                                Text(stringResource(Res.string.common_save_changes))
                            }
                        }
                    }

                    // Book count info
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Column {
                            val count = state.collection?.bookCount ?: 0
                            Text(
                                text = "$count book${if (count != 1) "s" else ""}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(Res.string.admin_in_this_collection),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // Books section
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(Res.string.admin_books_in_collection),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        if (state.books.isEmpty()) {
            item {
                EmptyBooksMessage()
            }
        } else {
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
                            BookRow(
                                book = book,
                                isRemoving = state.removingBookId == book.id,
                                onRemoveClick = { onRemoveBookClick(book) },
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
        }

        // Members section
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.common_members),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TextButton(onClick = onAddMemberClick) {
                    Icon(
                        imageVector = Icons.Outlined.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(Res.string.common_add),
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }

        if (state.shares.isEmpty()) {
            item {
                EmptyMembersMessage()
            }
        } else {
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
                        state.shares.forEachIndexed { index, share ->
                            MemberRow(
                                share = share,
                                isRemoving = state.removingShareId == share.id,
                                onRemoveClick = { onRemoveMemberClick(share) },
                            )
                            if (index < state.shares.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MemberRow(
    share: CollectionShareItem,
    isRemoving: Boolean,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = share.userName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (share.userEmail.isNotBlank()) {
                Text(
                    text = share.userEmail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (isRemoving) {
            ListenUpLoadingIndicatorSmall()
        } else {
            IconButton(onClick = onRemoveClick) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(Res.string.common_remove),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun EmptyMembersMessage(modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.admin_no_members),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(Res.string.admin_add_members_to_share_this),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMemberBottomSheet(
    sheetState: androidx.compose.material3.SheetState,
    isLoading: Boolean,
    isSharing: Boolean,
    users: List<AdminUserInfo>,
    onDismiss: () -> Unit,
    onUserSelected: (String) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
        ) {
            Text(
                text = stringResource(Res.string.admin_add_member),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )

            if (isLoading) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ListenUpLoadingIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(Res.string.admin_loading_users),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (users.isEmpty()) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(Res.string.admin_no_users_available),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(Res.string.admin_all_users_are_already_members),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            } else {
                users.forEach { user ->
                    ListItem(
                        headlineContent = { Text(user.displayName ?: user.email) },
                        supportingContent = {
                            if (user.displayName != null) {
                                Text(user.email)
                            } else if (user.isRoot) {
                                Text(stringResource(Res.string.admin_administrator))
                            }
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = null,
                            )
                        },
                        trailingContent = {
                            if (isSharing) {
                                ListenUpLoadingIndicatorSmall()
                            }
                        },
                        colors =
                            ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                        modifier =
                            Modifier.clickable(enabled = !isSharing) {
                                onUserSelected(user.id)
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun BookRow(
    book: CollectionBookItem,
    isRemoving: Boolean,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Book,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (book.authorNames.isNotBlank()) {
                Text(
                    text = book.authorNames,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (isRemoving) {
            ListenUpLoadingIndicatorSmall()
        } else {
            IconButton(onClick = onRemoveClick) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(Res.string.common_remove),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun EmptyBooksMessage(modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Book,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.admin_no_books_in_this_collection),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(Res.string.admin_books_can_be_added_from),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
