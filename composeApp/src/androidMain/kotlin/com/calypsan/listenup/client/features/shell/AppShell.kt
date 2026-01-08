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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import com.calypsan.listenup.client.domain.model.SyncState
import com.calypsan.listenup.client.domain.repository.SyncRepository
import com.calypsan.listenup.client.domain.repository.SyncStatusRepository
import com.calypsan.listenup.client.domain.repository.UserRepository
import com.calypsan.listenup.client.design.components.ListenUpDestructiveDialog
import com.calypsan.listenup.client.features.discover.DiscoverScreen
import com.calypsan.listenup.client.features.home.HomeScreen
import com.calypsan.listenup.client.features.library.LibraryScreen
import com.calypsan.listenup.client.features.search.SearchResultsOverlay
import com.calypsan.listenup.client.features.shell.components.AppNavigationBar
import com.calypsan.listenup.client.features.shell.components.AppNavigationDrawer
import com.calypsan.listenup.client.features.shell.components.AppNavigationRail
import com.calypsan.listenup.client.features.shell.components.AppTopBar
import com.calypsan.listenup.client.features.shell.components.SyncDetailsSheet
import com.calypsan.listenup.client.presentation.library.LibraryViewModel
import com.calypsan.listenup.client.presentation.search.SearchNavAction
import com.calypsan.listenup.client.presentation.search.SearchUiEvent
import com.calypsan.listenup.client.presentation.search.SearchViewModel
import com.calypsan.listenup.client.presentation.sync.SyncIndicatorUiEvent
import com.calypsan.listenup.client.presentation.sync.SyncIndicatorViewModel
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private val logger = KotlinLogging.logger {}

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
 * @param onAdminClick Callback when administration is clicked (only shown for admin users)
 * @param onSettingsClick Callback when settings is clicked
 * @param onSignOut Callback when sign out is triggered
 */
@Suppress("LongMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(
    currentDestination: ShellDestination,
    onDestinationChange: (ShellDestination) -> Unit,
    onBookClick: (String) -> Unit,
    onSeriesClick: (String) -> Unit,
    onContributorClick: (String) -> Unit,
    onLensClick: (String) -> Unit,
    onTagClick: (String) -> Unit,
    onAdminClick: (() -> Unit)? = null,
    onSettingsClick: () -> Unit,
    onSignOut: () -> Unit,
    onUserProfileClick: (userId: String) -> Unit,
) {
    // Inject dependencies
    val syncRepository: SyncRepository = koinInject()
    val userRepository: UserRepository = koinInject()
    val syncStatusRepository: SyncStatusRepository = koinInject()
    val authSession: com.calypsan.listenup.client.domain.repository.AuthSession = koinInject()
    val searchViewModel: SearchViewModel = koinViewModel()
    val syncIndicatorViewModel: SyncIndicatorViewModel = koinViewModel()

    // Preload library data by injecting LibraryViewModel (singleton) early.
    // This starts Room database queries immediately, so Library tab loads instantly.
    // The @Suppress is needed because we're intentionally not using the value directly.
    @Suppress("UNUSED_VARIABLE")
    val libraryViewModel: LibraryViewModel = koinInject()

    // Trigger sync on shell entry (not just when Library is visible)
    LaunchedEffect(Unit) {
        val isAuthenticated = authSession.getAccessToken() != null
        val lastSyncTime = syncStatusRepository.getLastSyncTime()
        if (isAuthenticated && lastSyncTime == null) {
            syncRepository.sync()
        }
    }

    // Fetch user data if missing from database but authenticated
    // This handles the case where database was cleared but tokens remain
    LaunchedEffect(Unit) {
        val hasTokens = authSession.getAccessToken() != null
        val existingUser = userRepository.getCurrentUser()

        if (hasTokens && existingUser == null) {
            logger.info { "User data missing but authenticated, fetching from server..." }
            userRepository.refreshCurrentUser()
        }
    }

    // Collect reactive state
    val syncState by syncRepository.syncState.collectAsStateWithLifecycle()
    val user by userRepository.observeCurrentUser().collectAsStateWithLifecycle(initialValue = null)
    val searchState by searchViewModel.state.collectAsStateWithLifecycle()
    val searchNavAction by searchViewModel.navActions.collectAsStateWithLifecycle()
    val syncIndicatorState by syncIndicatorViewModel.state.collectAsStateWithLifecycle()
    val isSyncDetailsExpanded by syncIndicatorViewModel.isExpanded.collectAsStateWithLifecycle()

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

            is SearchNavAction.NavigateToTag -> {
                onTagClick(action.tagId)
                searchViewModel.clearNavAction()
            }

            null -> {}
        }
    }

    // Coroutine scope for async operations
    val scope = rememberCoroutineScope()

    // Local UI state
    var isAvatarMenuExpanded by remember { mutableStateOf(false) }

    // Library mismatch dialog state
    var libraryMismatchToShow by remember { mutableStateOf<SyncState.LibraryMismatch?>(null) }

    // Detect library mismatch from sync state
    LaunchedEffect(syncState) {
        if (syncState is SyncState.LibraryMismatch) {
            libraryMismatchToShow = syncState as SyncState.LibraryMismatch
        }
    }

    // Library mismatch dialog
    libraryMismatchToShow?.let { mismatch ->
        ListenUpDestructiveDialog(
            onDismissRequest = { libraryMismatchToShow = null },
            title = "Library Changed",
            text =
                if (mismatch.hasPendingChanges) {
                    "The server's library has changed. You have unsaved changes that will be lost. " +
                        "Would you like to resync with the new library?"
                } else {
                    "The server's library has changed. Your local data will be refreshed to match."
                },
            confirmText = if (mismatch.hasPendingChanges) "Discard & Resync" else "Resync",
            onConfirm = {
                libraryMismatchToShow = null
                scope.launch {
                    syncRepository.resetForNewLibrary(mismatch.actualLibraryId)
                }
            },
            dismissText = "Cancel",
            onDismiss = { libraryMismatchToShow = null },
        )
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
            onAdminClick = onAdminClick,
            onSettingsClick = onSettingsClick,
            onSignOutClick = onSignOut,
            onMyProfileClick = { user?.id?.let(onUserProfileClick) },
            onSyncIndicatorClick = { syncIndicatorViewModel.toggleExpanded() },
            scrollBehavior = scrollBehavior,
            showAvatar = showAvatarInTopBar,
        )
    }

    // Sync details sheet
    if (isSyncDetailsExpanded) {
        SyncDetailsSheet(
            state = syncIndicatorState,
            onRetryOperation = { id ->
                syncIndicatorViewModel.onEvent(SyncIndicatorUiEvent.RetryOperation(id))
            },
            onDismissOperation = { id ->
                syncIndicatorViewModel.onEvent(SyncIndicatorUiEvent.DismissOperation(id))
            },
            onRetryAll = {
                syncIndicatorViewModel.onEvent(SyncIndicatorUiEvent.RetryAll)
            },
            onDismissAll = {
                syncIndicatorViewModel.onEvent(SyncIndicatorUiEvent.DismissAll)
            },
            onDismiss = { syncIndicatorViewModel.toggleExpanded() },
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
                        onLensClick = onLensClick,
                        // TODO: Navigate to lenses tab
                        onSeeAllLenses = { onDestinationChange(ShellDestination.Library) },
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
                    DiscoverScreen(
                        onLensClick = onLensClick,
                        onBookClick = onBookClick,
                        onUserProfileClick = onUserProfileClick,
                        modifier = Modifier.padding(padding),
                    )
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
                    onAdminClick = onAdminClick,
                    onSettingsClick = onSettingsClick,
                    onSignOutClick = onSignOut,
                    onMyProfileClick = { user?.id?.let(onUserProfileClick) },
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
                onAdminClick = onAdminClick,
                onSettingsClick = onSettingsClick,
                onSignOutClick = onSignOut,
                onMyProfileClick = { user?.id?.let(onUserProfileClick) },
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
