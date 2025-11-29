package com.calypsan.listenup.client.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.calypsan.listenup.client.features.shell.ShellDestination
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.calypsan.listenup.client.data.repository.AuthState
import com.calypsan.listenup.client.data.repository.SettingsRepository
import com.calypsan.listenup.client.features.connect.ServerSetupScreen
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
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
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

    NavDisplay(
        backStack = backStack,
        transitionSpec = {
            slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
        },
        popTransitionSpec = {
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
        }
    )
}
