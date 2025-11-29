package com.calypsan.listenup.client.features.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calypsan.listenup.client.data.local.db.UserDao
import com.calypsan.listenup.client.data.repository.SettingsRepository
import com.calypsan.listenup.client.data.sync.SyncManager
import com.calypsan.listenup.client.data.sync.SyncStatus
import com.calypsan.listenup.client.features.discover.DiscoverScreen
import com.calypsan.listenup.client.features.home.HomeScreen
import com.calypsan.listenup.client.features.library.LibraryScreen
import com.calypsan.listenup.client.features.shell.components.AppNavigationBar
import com.calypsan.listenup.client.features.shell.components.AppTopBar
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Main app shell providing persistent navigation frame.
 *
 * Contains:
 * - Top bar with collapsible search, sync indicator, user avatar
 * - Content area switching between Home, Library, Discover
 * - Reserved slot for future Now Playing bar
 * - Bottom navigation bar
 *
 * @param currentDestination Current bottom nav tab (state lifted to survive navigation)
 * @param onDestinationChange Callback when bottom nav tab changes
 * @param onBookClick Callback when a book is clicked (navigates to detail)
 * @param onSeriesClick Callback when a series is clicked (navigates to detail)
 * @param onSignOut Callback when sign out is triggered
 */
@Composable
fun AppShell(
    currentDestination: ShellDestination,
    onDestinationChange: (ShellDestination) -> Unit,
    onBookClick: (String) -> Unit,
    onSeriesClick: (String) -> Unit,
    onSignOut: () -> Unit
) {
    // Inject dependencies
    val syncManager: SyncManager = koinInject()
    val userDao: UserDao = koinInject()
    val settingsRepository: SettingsRepository = koinInject()

    // Collect reactive state
    val syncState by syncManager.syncState.collectAsStateWithLifecycle()
    val user by userDao.observeCurrentUser().collectAsStateWithLifecycle(initialValue = null)

    // Local UI state
    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isAvatarMenuExpanded by remember { mutableStateOf(false) }

    // Sign out handler
    val scope = rememberCoroutineScope()
    val handleSignOut: () -> Unit = {
        scope.launch {
            settingsRepository.clearAuthTokens()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                currentDestination = currentDestination,
                syncState = syncState,
                user = user,
                isSearchExpanded = isSearchExpanded,
                searchQuery = searchQuery,
                onSearchExpandedChange = { isSearchExpanded = it },
                onSearchQueryChange = { searchQuery = it },
                isAvatarMenuExpanded = isAvatarMenuExpanded,
                onAvatarMenuExpandedChange = { isAvatarMenuExpanded = it },
                onSettingsClick = { /* TODO: Navigate to settings */ },
                onSignOutClick = handleSignOut
            )
        },
        bottomBar = {
            Column {
                // TODO: NowPlayingBar will be inserted here
                // It floats above the navigation bar when audio is playing
                // Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                //     NowPlayingBar(...)
                // }

                AppNavigationBar(
                    currentDestination = currentDestination,
                    onDestinationSelected = onDestinationChange
                )
            }
        }
    ) { padding ->
        // Content based on current destination
        when (currentDestination) {
            ShellDestination.Home -> {
                HomeScreen(modifier = Modifier.padding(padding))
            }
            ShellDestination.Library -> {
                LibraryScreen(
                    onBookClick = onBookClick,
                    onSeriesClick = onSeriesClick,
                    onAuthorClick = { /* TODO: Author detail */ },
                    onNarratorClick = { /* TODO: Narrator detail */ },
                    modifier = Modifier.padding(padding)
                )
            }
            ShellDestination.Discover -> {
                DiscoverScreen(modifier = Modifier.padding(padding))
            }
        }
    }
}
