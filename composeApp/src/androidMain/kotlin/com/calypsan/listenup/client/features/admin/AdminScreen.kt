@file:Suppress("MagicNumber")

package com.calypsan.listenup.client.features.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calypsan.listenup.client.data.remote.AdminInvite
import com.calypsan.listenup.client.data.remote.AdminUser
import com.calypsan.listenup.client.design.components.FullScreenLoadingIndicator
import com.calypsan.listenup.client.design.components.ListenUpDestructiveDialog
import com.calypsan.listenup.client.presentation.admin.AdminUiState
import com.calypsan.listenup.client.presentation.admin.AdminViewModel
import kotlinx.coroutines.launch

/**
 * Combined admin screen showing users, pending invites, and invite action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    viewModel: AdminViewModel,
    onBackClick: () -> Unit,
    onInviteClick: () -> Unit,
    onCollectionsClick: () -> Unit = {},
    onInboxClick: () -> Unit = {},
    inboxEnabled: Boolean = false,
    inboxCount: Int = 0,
    isTogglingInbox: Boolean = false,
    onInboxEnabledChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var userToDelete by remember { mutableStateOf<AdminUser?>(null) }
    var inviteToRevoke by remember { mutableStateOf<AdminInvite?>(null) }
    var userToDeny by remember { mutableStateOf<AdminUser?>(null) }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Administration") },
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
        } else {
            AdminContent(
                state = state,
                onOpenRegistrationChange = { viewModel.setOpenRegistration(it) },
                onApproveUserClick = { viewModel.approveUser(it.id) },
                onDenyUserClick = { userToDeny = it },
                onDeleteUserClick = { userToDelete = it },
                onCopyInviteClick = { invite ->
                    copyToClipboard(context, invite.url)
                    scope.launch {
                        snackbarHostState.showSnackbar("Link copied!")
                    }
                },
                onRevokeInviteClick = { inviteToRevoke = it },
                onInviteClick = onInviteClick,
                onCollectionsClick = onCollectionsClick,
                onInboxClick = onInboxClick,
                inboxEnabled = inboxEnabled,
                inboxCount = inboxCount,
                isTogglingInbox = isTogglingInbox,
                onInboxEnabledChange = onInboxEnabledChange,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }

    // Delete user confirmation dialog
    userToDelete?.let { user ->
        ListenUpDestructiveDialog(
            onDismissRequest = { userToDelete = null },
            title = "Delete User",
            text = "Are you sure you want to delete ${user.displayName ?: user.email}? This action cannot be undone.",
            confirmText = "Delete",
            onConfirm = {
                viewModel.deleteUser(user.id)
                userToDelete = null
            },
            onDismiss = { userToDelete = null },
        )
    }

    // Revoke invite confirmation dialog
    inviteToRevoke?.let { invite ->
        ListenUpDestructiveDialog(
            onDismissRequest = { inviteToRevoke = null },
            title = "Revoke Invite",
            text =
                "Are you sure you want to revoke the invite for ${invite.name}? " +
                    "They won't be able to use this link anymore.",
            confirmText = "Revoke",
            onConfirm = {
                viewModel.revokeInvite(invite.id)
                inviteToRevoke = null
            },
            onDismiss = { inviteToRevoke = null },
        )
    }

    // Deny user confirmation dialog
    userToDeny?.let { user ->
        ListenUpDestructiveDialog(
            onDismissRequest = { userToDeny = null },
            title = "Deny Registration",
            text =
                "Are you sure you want to deny the registration request from " +
                    "${user.displayName ?: user.email}? They will need to register again.",
            confirmText = "Deny",
            onConfirm = {
                viewModel.denyUser(user.id)
                userToDeny = null
            },
            onDismiss = { userToDeny = null },
        )
    }
}

private fun copyToClipboard(
    context: Context,
    text: String,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Invite Link", text)
    clipboard.setPrimaryClip(clip)
}

@Composable
private fun AdminContent(
    state: AdminUiState,
    onOpenRegistrationChange: (Boolean) -> Unit,
    onApproveUserClick: (AdminUser) -> Unit,
    onDenyUserClick: (AdminUser) -> Unit,
    onDeleteUserClick: (AdminUser) -> Unit,
    onCopyInviteClick: (AdminInvite) -> Unit,
    onRevokeInviteClick: (AdminInvite) -> Unit,
    onInviteClick: () -> Unit,
    onCollectionsClick: () -> Unit,
    onInboxClick: () -> Unit,
    inboxEnabled: Boolean,
    inboxCount: Int,
    isTogglingInbox: Boolean,
    onInboxEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
    ) {
        // Settings section
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            )
        }

        item {
            SettingsCard(
                openRegistration = state.openRegistration,
                isTogglingOpenRegistration = state.isTogglingOpenRegistration,
                onOpenRegistrationChange = onOpenRegistrationChange,
                inboxEnabled = inboxEnabled,
                isTogglingInbox = isTogglingInbox,
                onInboxEnabledChange = onInboxEnabledChange,
            )
        }

        // Pending users section (only shown when open registration is enabled)
        if (state.openRegistration) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Pending Registrations",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors =
                        CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                ) {
                    if (state.pendingUsers.isEmpty()) {
                        Text(
                            text = "No pending registrations",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.padding(16.dp),
                        )
                    } else {
                        Column {
                            state.pendingUsers.forEachIndexed { index, user ->
                                PendingUserRow(
                                    user = user,
                                    isApproving = state.approvingUserId == user.id,
                                    isDenying = state.denyingUserId == user.id,
                                    onApproveClick = { onApproveUserClick(user) },
                                    onDenyClick = { onDenyUserClick(user) },
                                )
                                if (index < state.pendingUsers.lastIndex) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Users section header
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Users",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        // Users table
        item {
            val cardShape = MaterialTheme.shapes.large
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = cardShape,
                colors =
                    CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
            ) {
                Column {
                    // Table header - clip top corners to match card shape
                    UserTableHeader(
                        modifier =
                            Modifier.clip(
                                RoundedCornerShape(
                                    topStart = 28.dp,
                                    topEnd = 28.dp,
                                ),
                            ),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // User rows
                    if (state.users.isEmpty()) {
                        Text(
                            text = "No users found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    } else {
                        state.users.forEachIndexed { index, user ->
                            UserTableRow(
                                user = user,
                                isDeleting = state.deletingUserId == user.id,
                                onDeleteClick = { onDeleteUserClick(user) },
                            )
                            if (index < state.users.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Pending invites section (only if there are any)
        if (state.pendingInvites.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Pending Invites",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors =
                        CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                ) {
                    Column {
                        state.pendingInvites.forEachIndexed { index, invite ->
                            InviteRow(
                                invite = invite,
                                isRevoking = state.revokingInviteId == invite.id,
                                onCopyClick = { onCopyInviteClick(invite) },
                                onRevokeClick = { onRevokeInviteClick(invite) },
                            )
                            if (index < state.pendingInvites.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Invite someone button
        item {
            Spacer(modifier = Modifier.height(24.dp))
            InviteSomeoneCard(onClick = onInviteClick)
        }

        // Collections button
        item {
            Spacer(modifier = Modifier.height(12.dp))
            CollectionsCard(onClick = onCollectionsClick)
        }

        // Inbox button (only shown when inbox workflow is enabled)
        if (inboxEnabled) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
                InboxCard(
                    inboxCount = inboxCount,
                    onClick = onInboxClick,
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsCard(
    openRegistration: Boolean,
    isTogglingOpenRegistration: Boolean,
    onOpenRegistrationChange: (Boolean) -> Unit,
    inboxEnabled: Boolean,
    isTogglingInbox: Boolean,
    onInboxEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    ) {
        Column {
            // Open Registration toggle
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.HowToReg,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Open Registration",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Allow anyone to request an account",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isTogglingOpenRegistration) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Switch(
                        checked = openRegistration,
                        onCheckedChange = onOpenRegistrationChange,
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )

            // Inbox Workflow toggle
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Inbox,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Inbox Workflow",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Review new books before they appear in library",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isTogglingInbox) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Switch(
                        checked = inboxEnabled,
                        onCheckedChange = onInboxEnabledChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingUserRow(
    user: AdminUser,
    isApproving: Boolean,
    isDenying: Boolean,
    onApproveClick: () -> Unit,
    onDenyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Name and email
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text =
                    user.displayName ?: "${user.firstName ?: ""} ${user.lastName ?: ""}".trim().ifEmpty { user.email },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = user.email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Action buttons
        if (isApproving || isDenying) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
            )
        } else {
            OutlinedButton(
                onClick = onDenyClick,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Deny")
            }
            FilledTonalButton(
                onClick = onApproveClick,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Approve")
            }
        }
    }
}

@Composable
private fun UserTableHeader(modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Name",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "Email",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "Role",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.5f),
        )
        // Space for delete button
        Spacer(modifier = Modifier.size(48.dp))
    }
}

@Composable
private fun UserTableRow(
    user: AdminUser,
    isDeleting: Boolean,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Name with admin indicator
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = user.displayName ?: user.email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (user.isRoot || user.role == "admin") {
                Icon(
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = "Admin",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        // Email
        Text(
            text = user.email,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        // Role
        Text(
            text = if (user.isRoot) "Root" else user.role.replaceFirstChar { it.uppercase() }.ifEmpty { "Member" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(0.5f),
        )

        // Delete button
        if (!user.isProtected) {
            if (isDeleting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.size(48.dp))
        }
    }
}

@Composable
private fun InviteRow(
    invite: AdminInvite,
    isRevoking: Boolean,
    onCopyClick: () -> Unit,
    onRevokeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Name and email
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = invite.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = invite.email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Role badge
        Text(
            text = invite.role.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
        )

        // Copy link button
        IconButton(onClick = onCopyClick) {
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = "Copy Link",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }

        // Revoke button
        if (isRevoking) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
            )
        } else {
            IconButton(onClick = onRevokeClick) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Revoke",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun InviteSomeoneCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.PersonAdd,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Invite Someone",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "Share your audiobook library with others",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun CollectionsCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Collections",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = "Organize books into collections for access control",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun InboxCard(
    inboxCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Inbox,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Inbox",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text =
                        if (inboxCount > 0) {
                            "$inboxCount book${if (inboxCount != 1) "s" else ""} awaiting review"
                        } else {
                            "Review newly scanned books before publishing"
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                )
            }
        }
    }
}
