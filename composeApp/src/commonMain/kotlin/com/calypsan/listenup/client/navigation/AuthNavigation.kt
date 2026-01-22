package com.calypsan.listenup.client.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.calypsan.listenup.client.design.components.FullScreenLoadingIndicator
import com.calypsan.listenup.client.domain.repository.AuthSession
import com.calypsan.listenup.client.domain.repository.AuthState
import com.calypsan.listenup.client.features.auth.LoginScreen
import com.calypsan.listenup.client.features.auth.PendingApprovalScreen
import com.calypsan.listenup.client.features.auth.RegisterScreen
import com.calypsan.listenup.client.features.auth.SetupScreen
import com.calypsan.listenup.client.features.connect.ServerSetupScreen
import com.calypsan.listenup.client.presentation.auth.PendingApprovalViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Auth-only navigation for initial authentication flow.
 *
 * This handles:
 * 1. Server setup (when no server URL configured)
 * 2. Root user setup (when server has no users)
 * 3. Login (when server is configured)
 * 4. Registration (when open registration is enabled)
 * 5. Pending approval (after registration)
 *
 * After successful authentication, calls [onAuthenticated] to proceed to main app.
 */
@Composable
fun AuthNavigation(
    onAuthenticated: () -> Unit,
    authSession: AuthSession = koinInject(),
) {
    val scope = rememberCoroutineScope()

    // Initialize auth state on first composition
    LaunchedEffect(Unit) {
        scope.launch {
            authSession.initializeAuthState()
        }
    }

    // Observe auth state changes
    val authState by authSession.authState.collectAsState()

    // Route to appropriate screen based on auth state
    val currentAuthState = authState
    when (currentAuthState) {
        AuthState.Initializing -> {
            // Show blank screen while determining auth state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            )
        }

        AuthState.NeedsServerUrl -> {
            ServerSetupNavigation()
        }

        AuthState.CheckingServer -> {
            FullScreenLoadingIndicator(message = "Checking server...")
        }

        AuthState.NeedsSetup -> {
            SetupNavigation()
        }

        is AuthState.NeedsLogin -> {
            LoginNavigation(authSession, currentAuthState.openRegistration)
        }

        is AuthState.PendingApproval -> {
            PendingApprovalNavigation(
                userId = currentAuthState.userId,
                email = currentAuthState.email,
                password = currentAuthState.encryptedPassword,
            )
        }

        is AuthState.Authenticated -> {
            // Auth complete, notify parent
            LaunchedEffect(Unit) {
                onAuthenticated()
            }
        }
    }
}

/**
 * Navigation for pending approval screen.
 */
@Composable
private fun PendingApprovalNavigation(
    userId: String,
    email: String,
    password: String,
) {
    val viewModel: PendingApprovalViewModel = koinInject {
        org.koin.core.parameter.parametersOf(userId, email, password)
    }

    PendingApprovalScreen(
        viewModel = viewModel,
        onNavigateToLogin = {
            // Auth state will automatically update from clearPendingRegistration
        },
    )
}

/**
 * Server setup navigation - shown when no server URL is configured.
 *
 * Shows manual server URL entry screen.
 * TODO: Add server discovery (mDNS) for desktop
 */
@Composable
private fun ServerSetupNavigation() {
    val backStack = remember { mutableStateListOf<AuthRoute>(ServerSetup) }

    NavDisplay(
        backStack = backStack,
        onBack = {
            if (backStack.size > 1) {
                backStack.removeAt(backStack.lastIndex)
            }
        },
        entryProvider = entryProvider {
            entry<ServerSetup> {
                ServerSetupScreen(
                    onServerVerified = {
                        // Server is saved, AuthState will change automatically
                    },
                    onBack = null, // No back button on initial setup
                )
            }
        },
    )
}

/**
 * Setup navigation - shown when server needs initial root user.
 */
@Composable
private fun SetupNavigation() {
    val backStack = remember { mutableStateListOf<AuthRoute>(Setup) }

    NavDisplay(
        backStack = backStack,
        entryProvider = entryProvider {
            entry<Setup> {
                SetupScreen()
            }
        },
    )
}

/**
 * Login navigation - shown when server is configured but user needs to authenticate.
 */
@Composable
private fun LoginNavigation(
    authSession: AuthSession,
    openRegistration: Boolean,
) {
    val scope = rememberCoroutineScope()
    val backStack = remember { mutableStateListOf<AuthRoute>(Login) }
    val serverConfig: com.calypsan.listenup.client.domain.repository.ServerConfig = koinInject()

    // Refresh open registration value from server
    LaunchedEffect(Unit) {
        authSession.refreshOpenRegistration()
    }

    NavDisplay(
        backStack = backStack,
        onBack = {
            if (backStack.size > 1) {
                backStack.removeAt(backStack.lastIndex)
            }
        },
        entryProvider = entryProvider {
            entry<Login> {
                LoginScreen(
                    openRegistration = openRegistration,
                    onChangeServer = {
                        scope.launch {
                            serverConfig.disconnectFromServer()
                        }
                    },
                    onRegister = {
                        backStack.add(Register)
                    },
                )
            }
            entry<Register> {
                RegisterScreen(
                    onBackClick = {
                        backStack.removeAt(backStack.lastIndex)
                    },
                )
            }
        },
    )
}
