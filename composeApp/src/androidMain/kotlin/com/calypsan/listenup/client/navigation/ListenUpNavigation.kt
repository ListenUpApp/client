package com.calypsan.listenup.client.navigation

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.calypsan.listenup.client.data.repository.AuthState
import com.calypsan.listenup.client.data.repository.SettingsRepository
import com.calypsan.listenup.client.features.connect.ServerSetupScreen
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

    // Route to appropriate navigation graph based on auth state
    when (authState) {
        is AuthState.Loading -> LoadingScreen()
        is AuthState.Unauthenticated -> UnauthenticatedNavigation(settingsRepository)
        is AuthState.Authenticated -> AuthenticatedNavigation(settingsRepository)
    }
}

/**
 * Loading screen shown during auth state initialization.
 * Displayed briefly on app start while checking for stored credentials.
 */
@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

/**
 * Navigation graph for unauthenticated users.
 *
 * Flow:
 * 1. ServerSetup - User enters and verifies server URL
 * 2. Login - User enters credentials (TODO: not implemented yet)
 *
 * When login succeeds, SettingsRepository updates auth state,
 * triggering automatic switch to AuthenticatedNavigation.
 */
@Composable
private fun UnauthenticatedNavigation(
    settingsRepository: SettingsRepository
) {
    val backStack = remember { mutableStateListOf<Any>(ServerSetup) }
    val scope = rememberCoroutineScope()

    NavDisplay(
        backStack = backStack,
        entryProvider = entryProvider {
            entry<ServerSetup> {
                ServerSetupScreen(
                    onServerVerified = {
                        // Navigate to login after successful verification
                        // ViewModel saves server URL to SettingsRepository
                        backStack.add(Login)
                    }
                )
            }

            entry<Login> {
                // TODO: Implement LoginScreen
                // For now, show placeholder
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Login Screen - Not Implemented Yet")
                }
            }
        }
    )
}

/**
 * Navigation graph for authenticated users.
 *
 * Entry point: Library screen (audiobook collection)
 *
 * When user logs out, SettingsRepository clears auth tokens,
 * triggering automatic switch to UnauthenticatedNavigation.
 */
@Composable
private fun AuthenticatedNavigation(
    settingsRepository: SettingsRepository
) {
    val backStack = remember { mutableStateListOf<Any>(Library) }

    NavDisplay(
        backStack = backStack,
        entryProvider = entryProvider {
            entry<Library> {
                // TODO: Implement LibraryScreen
                // For now, show placeholder
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Library Screen - Not Implemented Yet")
                }
            }
        }
    )
}
