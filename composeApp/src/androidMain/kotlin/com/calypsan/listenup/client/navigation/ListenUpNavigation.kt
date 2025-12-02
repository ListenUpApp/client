package com.calypsan.listenup.client.navigation

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.calypsan.listenup.client.features.shell.ShellDestination
import androidx.navigation3.runtime.entryProvider
import com.calypsan.listenup.client.design.components.FullScreenLoadingIndicator
import androidx.navigation3.ui.NavDisplay
import com.calypsan.listenup.client.data.repository.AuthState
import com.calypsan.listenup.client.data.repository.SettingsRepository
import com.calypsan.listenup.client.features.connect.ServerSetupScreen
import com.calypsan.listenup.client.features.nowplaying.NowPlayingHost
import com.calypsan.listenup.client.features.shell.AppShell
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Root navigation composable for ListenUp Android app.
 *
 * Auth-driven navigation pattern:
 * - Observes auth state from SettingsRepository
 * - Shows loading screen during initialization
 * - Routes to unauthenticated flow (server setup → login)
 * - Routes to authenticated flow (library)
 *
 * Navigation automatically adjusts when auth state changes.
 * Uses Navigation 3 stable for Android (will migrate to KMP when Desktop support needed).
 */
@Composable
fun ListenUpNavigation(
    settingsRepository: SettingsRepository = koinInject()
) {
    // Initialize auth state on first composition
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        scope.launch {
            settingsRepository.initializeAuthState()
        }
    }

    // Observe auth state changes
    val authState by settingsRepository.authState.collectAsState()

    // Route to appropriate screen based on auth state
    when (authState) {
        AuthState.NeedsServerUrl -> ServerSetupNavigation()
        AuthState.CheckingServer -> LoadingScreen("Checking server...")
        AuthState.NeedsSetup -> SetupNavigation()
        AuthState.NeedsLogin -> LoginNavigation()
        is AuthState.Authenticated -> AuthenticatedNavigation(settingsRepository)
    }
}

/**
 * Loading screen shown during auth state initialization.
 * Displayed briefly on app start while checking for stored credentials.
 */
@Composable
private fun LoadingScreen(message: String = "Loading...") {
    FullScreenLoadingIndicator()
}

/**
 * Server setup navigation - shown when no server URL is configured.
 * After successful verification, AuthState changes trigger automatic navigation.
 */
@Composable
private fun ServerSetupNavigation() {
    val backStack = remember { mutableStateListOf<Route>(ServerSetup) }

    NavDisplay(
        backStack = backStack,
        entryProvider = entryProvider {
            entry<ServerSetup> {
                ServerSetupScreen(
                    onServerVerified = {
                        // URL is saved, AuthState will change automatically
                        // No manual navigation needed
                    }
                )
            }
        }
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
        entryProvider = entryProvider {
            entry<Setup> {
                com.calypsan.listenup.client.features.auth.SetupScreen()
            }
        }
    )
}

/**
 * Login navigation - shown when server is configured but user needs to authenticate.
 * After successful login, AuthState.Authenticated triggers automatic navigation.
 */
@Composable
private fun LoginNavigation() {
    val backStack = remember { mutableStateListOf<Route>(Login) }

    NavDisplay(
        backStack = backStack,
        entryProvider = entryProvider {
            entry<Login> {
                com.calypsan.listenup.client.features.auth.LoginScreen()
            }
        }
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
@Composable
private fun AuthenticatedNavigation(
    settingsRepository: SettingsRepository
) {
    val scope = rememberCoroutineScope()
    val backStack = remember { mutableStateListOf<Route>(Shell) }

    // Track shell tab state here so it survives navigation to detail screens
    var currentShellDestination by remember { mutableStateOf<ShellDestination>(ShellDestination.Home) }

    // Wrap navigation with NowPlayingHost for persistent mini player
    Box(modifier = Modifier.fillMaxSize()) {
        NavDisplay(
        backStack = backStack,
        // Only handle back if we're not at root - let system handle back-to-home
        onBack = {
            if (backStack.size > 1) {
                backStack.removeLast()
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
        entryProvider = entryProvider {
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
                    onSignOut = {
                        scope.launch {
                            settingsRepository.clearAuthTokens()
                        }
                    }
                )
            }
            entry<BookDetail> { args ->
                com.calypsan.listenup.client.features.book_detail.BookDetailScreen(
                    bookId = args.bookId,
                    onBackClick = {
                        backStack.removeLast()
                    },
                    onSeriesClick = { seriesId ->
                        backStack.add(SeriesDetail(seriesId))
                    },
                    onContributorClick = { contributorId ->
                        backStack.add(ContributorDetail(contributorId))
                    }
                )
            }
            entry<SeriesDetail> { args ->
                com.calypsan.listenup.client.features.series_detail.SeriesDetailScreen(
                    seriesId = args.seriesId,
                    onBackClick = {
                        backStack.removeLast()
                    },
                    onBookClick = { bookId ->
                        backStack.add(BookDetail(bookId))
                    }
                )
            }
            entry<ContributorDetail> { args ->
                com.calypsan.listenup.client.features.contributor_detail.ContributorDetailScreen(
                    contributorId = args.contributorId,
                    onBackClick = {
                        backStack.removeLast()
                    },
                    onBookClick = { bookId ->
                        backStack.add(BookDetail(bookId))
                    },
                    onViewAllClick = { contributorId, role ->
                        backStack.add(ContributorBooks(contributorId, role))
                    }
                )
            }
            entry<ContributorBooks> { args ->
                com.calypsan.listenup.client.features.contributor_detail.ContributorBooksScreen(
                    contributorId = args.contributorId,
                    role = args.role,
                    onBackClick = {
                        backStack.removeLast()
                    },
                    onBookClick = { bookId ->
                        backStack.add(BookDetail(bookId))
                    }
                )
            }
        }
    )

        // Now Playing overlay - persistent across all navigation
        // Position adjusts based on whether bottom nav is visible (Shell vs detail screens)
        NowPlayingHost(
            hasBottomNav = backStack.lastOrNull() == Shell,
            onNavigateToBook = { bookId ->
                backStack.add(BookDetail(bookId))
            },
            onNavigateToSeries = { seriesId ->
                backStack.add(SeriesDetail(seriesId))
            },
            onNavigateToContributor = { contributorId ->
                backStack.add(ContributorDetail(contributorId))
            }
        )
    }
}
