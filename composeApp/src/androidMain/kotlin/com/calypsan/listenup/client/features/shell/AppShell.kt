package com.calypsan.listenup.client.features.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import com.calypsan.listenup.client.data.local.db.SyncDao
import com.calypsan.listenup.client.data.local.db.UserDao
import com.calypsan.listenup.client.data.local.db.getLastSyncTime
import com.calypsan.listenup.client.data.repository.SettingsRepository
import com.calypsan.listenup.client.data.sync.SyncManager
import com.calypsan.listenup.client.features.discover.DiscoverScreen
import com.calypsan.listenup.client.features.home.HomeScreen
import com.calypsan.listenup.client.features.library.LibraryScreen
import com.calypsan.listenup.client.features.search.SearchResultsOverlay
import com.calypsan.listenup.client.features.shell.components.AppNavigationBar
import com.calypsan.listenup.client.features.shell.components.AppNavigationDrawer
import com.calypsan.listenup.client.features.shell.components.AppNavigationRail
import com.calypsan.listenup.client.features.shell.components.AppTopBar
import com.calypsan.listenup.client.presentation.search.SearchNavAction
import com.calypsan.listenup.client.presentation.search.SearchUiEvent
import com.calypsan.listenup.client.presentation.search.SearchViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

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
    onSignOut: () -> Unit,
) {
    // Inject dependencies
    val syncManager: SyncManager = koinInject()
    val userDao: UserDao = koinInject()
    val settingsRepository: SettingsRepository = koinInject()
    val syncDao: SyncDao = koinInject()
    val searchViewModel: SearchViewModel = koinViewModel()

    // Trigger sync on shell entry (not just when Library is visible)
    LaunchedEffect(Unit) {
        val isAuthenticated = settingsRepository.getAccessToken() != null
        val lastSyncTime = syncDao.getLastSyncTime()
        if (isAuthenticated && lastSyncTime == null) {
            syncManager.sync()
        }
    }

    // Collect reactive state
    val syncState by syncManager.syncState.collectAsStateWithLifecycle()
    val user by userDao.observeCurrentUser().collectAsStateWithLifecycle(initialValue = null)
    val searchState by searchViewModel.state.collectAsStateWithLifecycle()
    val searchNavAction by searchViewModel.navActions.collectAsStateWithLifecycle()

    // Handle search navigation
    LaunchedEffect(searchNavAction) {
        when (val action = searchNavAction) {
            is SearchNavAction.NavigateToBook -> {
                onBookClick(action.bookId)
                searchViewModel.clearNavAction()
            }

            is SearchNavAction.NavigateToContributor -> {
                onContributorClick(action.contributorId)
                searchViewModel.clearNavAction()
            }

            is SearchNavAction.NavigateToSeries -> {
                onSeriesClick(action.seriesId)
                searchViewModel.clearNavAction()
            }

            null -> {}
        }
    }

    // Local UI state
    var isAvatarMenuExpanded by remember { mutableStateOf(false) }

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

    // Get window size class for adaptive layout
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass

    // Determine layout based on width breakpoints
    // Use 1000dp for expanded threshold to keep foldables (Pixel Fold ~930dp) on rail
    val expandedThreshold = 1000
    val isExpanded = windowSizeClass.isWidthAtLeastBreakpoint(expandedThreshold)
    val isMedium = !isExpanded && windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    val isCompact = !isExpanded && !isMedium

    // Determine if avatar should show in top bar (only on compact screens)
    val showAvatarInTopBar = isCompact

    // Common top bar configuration
    val topBar: @Composable () -> Unit = {
        AppTopBar(
            currentDestination = currentDestination,
            syncState = syncState,
            user = user,
            isSearchExpanded = searchState.isExpanded,
            searchQuery = searchState.query,
            onSearchExpandedChange = { expanded ->
                if (expanded) {
                    searchViewModel.onEvent(SearchUiEvent.ExpandSearch)
                } else {
                    searchViewModel.onEvent(SearchUiEvent.CollapseSearch)
                }
            },
            onSearchQueryChange = { query ->
                searchViewModel.onEvent(SearchUiEvent.QueryChanged(query))
            },
            isAvatarMenuExpanded = isAvatarMenuExpanded,
            onAvatarMenuExpandedChange = { isAvatarMenuExpanded = it },
            onSettingsClick = { /* TODO: Navigate to settings */ },
            onSignOutClick = onSignOut,
            scrollBehavior = scrollBehavior,
            showAvatar = showAvatarInTopBar,
        )
    }

    // Common content configuration
    val shellContent: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit = { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Content based on current destination
            when (currentDestination) {
                ShellDestination.Home -> {
                    HomeScreen(
                        onBookClick = onBookClick,
                        onNavigateToLibrary = { onDestinationChange(ShellDestination.Library) },
                        modifier = Modifier.padding(padding),
                    )
                }

                ShellDestination.Library -> {
                    LibraryScreen(
                        onBookClick = onBookClick,
                        onSeriesClick = onSeriesClick,
                        onAuthorClick = onContributorClick,
                        onNarratorClick = onContributorClick,
                        topBarCollapseFraction = topBarCollapseFraction,
                        modifier = Modifier.padding(padding),
                    )
                }

                ShellDestination.Discover -> {
                    DiscoverScreen(modifier = Modifier.padding(padding))
                }
            }

            // Search results overlay (floats above content when search is active)
            SearchResultsOverlay(
                state = searchState,
                onResultClick = { hit ->
                    searchViewModel.onEvent(SearchUiEvent.ResultClicked(hit))
                },
                onTypeFilterToggle = { type ->
                    searchViewModel.onEvent(SearchUiEvent.ToggleTypeFilter(type))
                },
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
            )
        }
    }

    // Adaptive layout based on window width
    when {
        isCompact -> {
            // Phone layout: Bottom navigation
            Scaffold(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                topBar = topBar,
                bottomBar = {
                    Column {
                        // TODO: NowPlayingBar will be inserted here
                        AppNavigationBar(
                            currentDestination = currentDestination,
                            onDestinationSelected = onDestinationChange,
                        )
                    }
                },
                content = shellContent,
            )
        }

        isMedium -> {
            // Tablet portrait layout: Navigation rail on left
            Row(modifier = Modifier.fillMaxSize()) {
                AppNavigationRail(
                    currentDestination = currentDestination,
                    onDestinationSelected = onDestinationChange,
                    user = user,
                    isAvatarMenuExpanded = isAvatarMenuExpanded,
                    onAvatarMenuExpandedChange = { isAvatarMenuExpanded = it },
                    onSettingsClick = { /* TODO: Navigate to settings */ },
                    onSignOutClick = onSignOut,
                )
                Scaffold(
                    modifier =
                        Modifier
                            .weight(1f)
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                    topBar = topBar,
                    content = shellContent,
                )
            }
        }

        else -> {
            // Expanded layout (tablets landscape, desktop): Permanent navigation drawer
            AppNavigationDrawer(
                currentDestination = currentDestination,
                onDestinationSelected = onDestinationChange,
                user = user,
                isAvatarMenuExpanded = isAvatarMenuExpanded,
                onAvatarMenuExpandedChange = { isAvatarMenuExpanded = it },
                onSettingsClick = { /* TODO: Navigate to settings */ },
                onSignOutClick = onSignOut,
            ) {
                Scaffold(
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    topBar = topBar,
                    content = shellContent,
                )
            }
        }
    }
}
