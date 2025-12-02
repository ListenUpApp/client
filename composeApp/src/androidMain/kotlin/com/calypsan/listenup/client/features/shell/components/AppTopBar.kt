package com.calypsan.listenup.client.features.shell.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.calypsan.listenup.client.data.local.db.UserEntity
import com.calypsan.listenup.client.data.sync.SyncStatus
import com.calypsan.listenup.client.design.components.UserAvatar
import com.calypsan.listenup.client.features.shell.ShellDestination

/**
 * Top app bar for the main shell with collapsible search.
 *
 * Collapsed state: Title, search icon, sync indicator, avatar
 * Expanded state: Back arrow, search field, avatar
 *
 * @param currentDestination Current shell destination (for title)
 * @param syncState Current sync status
 * @param user Current user entity
 * @param isSearchExpanded Whether search is expanded
 * @param searchQuery Current search query
 * @param onSearchExpandedChange Callback when search expand state changes
 * @param onSearchQueryChange Callback when search query changes
 * @param isAvatarMenuExpanded Whether avatar dropdown is expanded
 * @param onAvatarMenuExpandedChange Callback when avatar menu expand state changes
 * @param onSettingsClick Callback when settings is clicked
 * @param onSignOutClick Callback when sign out is clicked
 * @param scrollBehavior Scroll behavior for collapsing on scroll
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    currentDestination: ShellDestination?,
    syncState: SyncStatus,
    user: UserEntity?,
    isSearchExpanded: Boolean,
    searchQuery: String,
    onSearchExpandedChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    isAvatarMenuExpanded: Boolean,
    onAvatarMenuExpandedChange: (Boolean) -> Unit,
    onSettingsClick: () -> Unit,
    onSignOutClick: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    // Guard against null destination during recomposition transitions
    val safeDestination = currentDestination ?: ShellDestination.Home

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface
        ),
        navigationIcon = {
            if (isSearchExpanded) {
                IconButton(onClick = { onSearchExpandedChange(false) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close search"
                    )
                }
            }
        },
        title = {
            AnimatedContent(
                targetState = isSearchExpanded,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "search_animation"
            ) { expanded ->
                if (expanded) {
                    SearchField(
                        query = searchQuery,
                        onQueryChange = onSearchQueryChange,
                        onClose = { onSearchExpandedChange(false) }
                    )
                } else {
                    Text(safeDestination.title)
                }
            }
        },
        actions = {
            // Search icon (hidden when expanded)
            if (!isSearchExpanded) {
                IconButton(onClick = { onSearchExpandedChange(true) }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search"
                    )
                }
            }

            // Sync indicator (hidden when search expanded)
            if (!isSearchExpanded) {
                SyncIndicator(syncState = syncState)
            }

            // Avatar (always visible)
            UserAvatar(
                user = user,
                expanded = isAvatarMenuExpanded,
                onExpandedChange = onAvatarMenuExpandedChange,
                onSettingsClick = onSettingsClick,
                onSignOutClick = onSignOutClick,
                modifier = Modifier.padding(end = 8.dp)
            )
        },
        scrollBehavior = scrollBehavior
    )
}

/**
 * Search text field for the expanded search state.
 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search audiobooks...") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = { keyboardController?.hide() }
        ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
    )
}

/**
 * Sync status indicator.
 *
 * Shows:
 * - Spinner during Syncing/Progress/Retrying
 * - Error icon on Error
 * - Nothing for Idle/Success
 */
@Composable
private fun SyncIndicator(syncState: SyncStatus) {
    when (syncState) {
        is SyncStatus.Syncing,
        is SyncStatus.Progress,
        is SyncStatus.Retrying -> {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(24.dp)
                    .padding(end = 4.dp),
                strokeWidth = 2.dp
            )
        }
        is SyncStatus.Error -> {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = "Sync error",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(end = 4.dp)
            )
        }
        else -> {
            // Idle, Success - show nothing
        }
    }
}
