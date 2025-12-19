package com.calypsan.listenup.client.navigation

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.calypsan.listenup.client.data.repository.AuthState
import com.calypsan.listenup.client.data.repository.DeepLinkManager
import com.calypsan.listenup.client.data.repository.SettingsRepository
import com.calypsan.listenup.client.design.components.FullScreenLoadingIndicator
import com.calypsan.listenup.client.design.components.LocalSnackbarHostState
import com.calypsan.listenup.client.features.admin.AdminScreen
import com.calypsan.listenup.client.features.admin.CreateInviteScreen
import com.calypsan.listenup.client.features.connect.ServerSelectScreen
import com.calypsan.listenup.client.features.connect.ServerSetupScreen
import com.calypsan.listenup.client.features.invite.InviteRegistrationScreen
import com.calypsan.listenup.client.features.nowplaying.NowPlayingHost
import com.calypsan.listenup.client.features.settings.SettingsScreen
import com.calypsan.listenup.client.features.shell.AppShell
import com.calypsan.listenup.client.features.shell.ShellDestination
import com.calypsan.listenup.client.presentation.admin.AdminViewModel
import com.calypsan.listenup.client.presentation.admin.CreateInviteViewModel
import com.calypsan.listenup.client.presentation.invite.InviteRegistrationViewModel
import com.calypsan.listenup.client.presentation.invite.InviteSubmissionStatus
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Root navigation composable for ListenUp Android app.
 *
 * Navigation priority:
 * 1. Pending invite deep link (shows invite registration)
 * 2. Auth state-driven routing (server setup → login → library)
 *
 * Navigation automatically adjusts when auth state changes.
 * Uses Navigation 3 stable for Android (will migrate to KMP when Desktop support needed).
 */
@Composable
fun ListenUpNavigation(
    settingsRepository: SettingsRepository = koinInject(),
    deepLinkManager: DeepLinkManager = koinInject(),
) {
    // Initialize auth state on first composition
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        scope.launch {
            settingsRepository.initializeAuthState()
        }
    }

    // Observe pending invite deep link
    val pendingInvite by deepLinkManager.pendingInvite.collectAsState()

    // Observe auth state changes
    val authState by settingsRepository.authState.collectAsState()

    // Check for pending invite BEFORE auth state routing
    // This allows invite registration even when already authenticated
    pendingInvite?.let { invite ->
        InviteRegistrationNavigation(
            serverUrl = invite.serverUrl,
            inviteCode = invite.code,
            onComplete = { deepLinkManager.consumeInvite() },
            onCancel = { deepLinkManager.consumeInvite() },
        )
        return
    }

    // Route to appropriate screen based on auth state
    when (authState) {
        AuthState.Initializing -> {
            // Show blank screen while determining auth state
            // Prevents flash of wrong screen on startup
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            )
        }
        AuthState.NeedsServerUrl -> ServerSetupNavigation()
        AuthState.CheckingServer -> LoadingScreen("Checking server...")
        AuthState.NeedsSetup -> SetupNavigation()
        AuthState.NeedsLogin -> LoginNavigation(settingsRepository)
        is AuthState.Authenticated -> AuthenticatedNavigation(settingsRepository)
    }
}

/**
 * Navigation for invite registration flow.
 *
 * Shows the invite registration screen and handles completion/cancellation.
 * On successful registration, auth tokens are stored and AuthState becomes Authenticated,
 * which will trigger navigation to the library after the invite is consumed.
 */
@Composable
private fun InviteRegistrationNavigation(
    serverUrl: String,
    inviteCode: String,
    onComplete: () -> Unit,
    onCancel: () -> Unit,
) {
    val viewModel: InviteRegistrationViewModel =
        koinInject {
            org.koin.core.parameter
                .parametersOf(serverUrl, inviteCode)
        }

    // Watch for successful registration to trigger completion
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.submissionStatus) {
        if (state.submissionStatus is InviteSubmissionStatus.Success) {
            onComplete()
        }
    }

    InviteRegistrationScreen(
        viewModel = viewModel,
        onCancel = onCancel,
    )
}

/**
 * Loading screen shown during auth state initialization.
 * Displayed briefly on app start while checking for stored credentials.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
private fun LoadingScreen(message: String = "Loading...") {
    FullScreenLoadingIndicator()
}

/**
 * Server setup navigation - shown when no server URL is configured.
 *
 * Flow:
 * 1. ServerSelectScreen - shows discovered servers + manual option
 * 2. ServerSetupScreen - manual URL entry (if user clicks "Add Manually")
 *
 * After successful selection/verification, AuthState changes trigger automatic navigation.
 */
@Composable
private fun ServerSetupNavigation() {
    val backStack = remember { mutableStateListOf<Route>(ServerSelect) }

    NavDisplay(
        backStack = backStack,
        onBack = {
            if (backStack.size > 1) {
                backStack.removeAt(backStack.lastIndex)
            }
        },
        entryProvider =
            entryProvider {
                entry<ServerSelect> {
                    ServerSelectScreen(
                        onServerActivated = {
                            // Server is selected, AuthState will change automatically
                        },
                        onManualEntryRequested = {
                            backStack.add(ServerSetup)
                        },
                    )
                }
                entry<ServerSetup> {
                    ServerSetupScreen(
                        onServerVerified = {
                            // URL is saved, AuthState will change automatically
                            // Pop back to select (will be replaced by auth screen)
                        },
                        onBack = {
                            backStack.removeAt(backStack.lastIndex)
                        },
                    )
                }
            },
    )
}

/**
 * Setup navigation - shown when server needs initial root user.
 * After successful setup, AuthState.Authenticated triggers automatic navigation.
 */
@Composable
private fun SetupNavigation() {
    val backStack = remember { mutableStateListOf<Route>(Setup) }

    NavDisplay(
        backStack = backStack,
        entryProvider =
            entryProvider {
                entry<Setup> {
                    com.calypsan.listenup.client.features.auth
                        .SetupScreen()
                }
            },
    )
}

/**
 * Login navigation - shown when server is configured but user needs to authenticate.
 * After successful login, AuthState.Authenticated triggers automatic navigation.
 */
@Composable
private fun LoginNavigation(settingsRepository: SettingsRepository) {
    val scope = rememberCoroutineScope()
    val backStack = remember { mutableStateListOf<Route>(Login) }

    NavDisplay(
        backStack = backStack,
        entryProvider =
            entryProvider {
                entry<Login> {
                    com.calypsan.listenup.client.features.auth
                        .LoginScreen(
                            onChangeServer = {
                                scope.launch {
                                    // Clear server URL to go back to server selection
                                    settingsRepository.disconnectFromServer()
                                }
                            },
                        )
                }
            },
    )
}

/**
 * Navigation graph for authenticated users.
 *
 * Entry point: AppShell (contains bottom nav with Home, Library, Discover)
 *
 * Predictive back behavior:
 * - Root screen (Shell): onBack doesn't pop, allowing system back-to-home animation
 * - Detail screens: Slide animations for in-app navigation
 *
 * NowPlayingHost is overlaid on top of all navigation, providing:
 * - Floating mini player above bottom nav
 * - Full screen player that expands from mini player
 *
 * When user logs out, SettingsRepository clears auth tokens,
 * triggering automatic switch to UnauthenticatedNavigation.
 */
@Suppress("LongMethod")
@Composable
private fun AuthenticatedNavigation(settingsRepository: SettingsRepository) {
    val scope = rememberCoroutineScope()
    val backStack = remember { mutableStateListOf<Route>(Shell) }

    // Track shell tab state here so it survives navigation to detail screens
    var currentShellDestination by remember { mutableStateOf<ShellDestination>(ShellDestination.Home) }

    // App-wide snackbar state - provided to all screens via CompositionLocal
    val snackbarHostState = remember { SnackbarHostState() }

    // Wrap navigation with NowPlayingHost for persistent mini player
    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        Box(modifier = Modifier.fillMaxSize()) {
            NavDisplay(
                backStack = backStack,
                // Only handle back if we're not at root - let system handle back-to-home
                onBack = {
                    if (backStack.size > 1) {
                        backStack.removeAt(backStack.lastIndex)
                    }
                    // When size == 1, don't pop - allows system back-to-home animation
                },
                // Global slide transitions for all navigation
                transitionSpec = {
                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                },
                popTransitionSpec = {
                    slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                },
                predictivePopTransitionSpec = {
                    slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                },
                entryProvider =
                    entryProvider {
                        entry<Shell> {
                            AppShell(
                                currentDestination = currentShellDestination,
                                onDestinationChange = { currentShellDestination = it },
                                onBookClick = { bookId ->
                                    backStack.add(BookDetail(bookId))
                                },
                                onSeriesClick = { seriesId ->
                                    backStack.add(SeriesDetail(seriesId))
                                },
                                onContributorClick = { contributorId ->
                                    backStack.add(ContributorDetail(contributorId))
                                },
                                onAdminClick = {
                                    backStack.add(Admin)
                                },
                                onSettingsClick = {
                                    backStack.add(Settings)
                                },
                                onSignOut = {
                                    scope.launch {
                                        settingsRepository.clearAuthTokens()
                                    }
                                },
                            )
                        }
                        entry<BookDetail> { args ->
                            com.calypsan.listenup.client.features.bookdetail.BookDetailScreen(
                                bookId = args.bookId,
                                onBackClick = {
                                    backStack.removeAt(backStack.lastIndex)
                                },
                                onEditClick = { bookId ->
                                    backStack.add(BookEdit(bookId))
                                },
                                onSeriesClick = { seriesId ->
                                    backStack.add(SeriesDetail(seriesId))
                                },
                                onContributorClick = { contributorId ->
                                    backStack.add(ContributorDetail(contributorId))
                                },
                            )
                        }
                        entry<BookEdit> { args ->
                            com.calypsan.listenup.client.features.bookedit.BookEditScreen(
                                bookId = args.bookId,
                                onBackClick = {
                                    backStack.removeAt(backStack.lastIndex)
                                },
                                onSaveSuccess = {
                                    // Navigate back after successful save
                                    backStack.removeAt(backStack.lastIndex)
                                },
                            )
                        }
                        entry<SeriesDetail> { args ->
                            com.calypsan.listenup.client.features.seriesdetail.SeriesDetailScreen(
                                seriesId = args.seriesId,
                                onBackClick = {
                                    backStack.removeAt(backStack.lastIndex)
                                },
                                onBookClick = { bookId ->
                                    backStack.add(BookDetail(bookId))
                                },
                                onEditClick = { seriesId ->
                                    backStack.add(SeriesEdit(seriesId))
                                },
                            )
                        }
                        entry<SeriesEdit> { args ->
                            com.calypsan.listenup.client.features.seriesedit.SeriesEditScreen(
                                seriesId = args.seriesId,
                                onBackClick = {
                                    backStack.removeAt(backStack.lastIndex)
                                },
                                onSaveSuccess = {
                                    // Navigate back after successful save
                                    backStack.removeAt(backStack.lastIndex)
                                },
                            )
                        }
                        entry<ContributorDetail> { args ->
                            com.calypsan.listenup.client.features.contributordetail.ContributorDetailScreen(
                                contributorId = args.contributorId,
                                onBackClick = {
                                    backStack.removeAt(backStack.lastIndex)
                                },
                                onBookClick = { bookId ->
                                    backStack.add(BookDetail(bookId))
                                },
                                onEditClick = { contributorId ->
                                    backStack.add(ContributorEdit(contributorId))
                                },
                                onViewAllClick = { contributorId, role ->
                                    backStack.add(ContributorBooks(contributorId, role))
                                },
                            )
                        }
                        entry<ContributorEdit> { args ->
                            com.calypsan.listenup.client.features.contributoredit.ContributorEditScreen(
                                contributorId = args.contributorId,
                                onBackClick = {
                                    backStack.removeAt(backStack.lastIndex)
                                },
                                onSaveSuccess = {
                                    // Navigate back after successful save
                                    backStack.removeAt(backStack.lastIndex)
                                },
                            )
                        }
                        entry<ContributorBooks> { args ->
                            com.calypsan.listenup.client.features.contributordetail.ContributorBooksScreen(
                                contributorId = args.contributorId,
                                role = args.role,
                                onBackClick = {
                                    backStack.removeAt(backStack.lastIndex)
                                },
                                onBookClick = { bookId ->
                                    backStack.add(BookDetail(bookId))
                                },
                            )
                        }
                        // Admin screens
                        entry<Admin> {
                            val viewModel: AdminViewModel = koinInject()
                            AdminScreen(
                                viewModel = viewModel,
                                onBackClick = {
                                    backStack.removeAt(backStack.lastIndex)
                                },
                                onInviteClick = {
                                    backStack.add(CreateInvite)
                                },
                            )
                        }
                        entry<CreateInvite> {
                            val viewModel: CreateInviteViewModel = koinInject()
                            CreateInviteScreen(
                                viewModel = viewModel,
                                onBackClick = {
                                    backStack.removeAt(backStack.lastIndex)
                                },
                                onSuccess = {
                                    backStack.removeAt(backStack.lastIndex)
                                },
                            )
                        }
                        entry<Settings> {
                            SettingsScreen(
                                onNavigateBack = {
                                    backStack.removeAt(backStack.lastIndex)
                                },
                            )
                        }
                    },
            )

            // Now Playing overlay - persistent across all navigation
            // Position adjusts based on whether bottom nav is visible (Shell vs detail screens)
            // Also animates up when snackbar is visible
            NowPlayingHost(
                hasBottomNav = backStack.lastOrNull() == Shell,
                snackbarHostState = snackbarHostState,
                onNavigateToBook = { bookId ->
                    backStack.add(BookDetail(bookId))
                },
                onNavigateToSeries = { seriesId ->
                    backStack.add(SeriesDetail(seriesId))
                },
                onNavigateToContributor = { contributorId ->
                    backStack.add(ContributorDetail(contributorId))
                },
            )

            // App-wide snackbar - positioned at bottom, mini player animates up when visible
            SnackbarHost(
                hostState = snackbarHostState,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
            )
        }
    }
}
