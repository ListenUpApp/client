package com.calypsan.listenup.client.features.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
 * @param onContributorClick Callback when a contributor is clicked (author or narrator)
 * @param onSignOut Callback when sign out is triggered
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(
    currentDestination: ShellDestination,
    onDestinationChange: (ShellDestination) -> Unit,
    onBookClick: (String) -> Unit,
    onSeriesClick: (String) -> Unit,
    onContributorClick: (String) -> Unit,
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

    // Scroll behavior for collapsing top bar
    // enterAlwaysScrollBehavior: hides on scroll down, shows immediately on scroll up
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    // Derive collapse fraction for child components (0 = expanded, 1 = fully collapsed)
    val topBarCollapseFraction by remember {
        derivedStateOf {
            val limit = scrollBehavior.state.heightOffsetLimit
            if (limit != 0f) {
                scrollBehavior.state.heightOffset / limit
            } else {
                0f
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
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
                onSignOutClick = handleSignOut,
                scrollBehavior = scrollBehavior
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
                    onAuthorClick = onContributorClick,
                    onNarratorClick = onContributorClick,
                    topBarCollapseFraction = topBarCollapseFraction,
                    modifier = Modifier.padding(padding)
                )
            }
            ShellDestination.Discover -> {
                DiscoverScreen(modifier = Modifier.padding(padding))
            }
        }
    }
}
