package com.calypsan.listenup.client.data.repository

/**
 * Represents the authentication state of the application.
 *
 * This sealed interface provides a type-safe way to handle different authentication
 * states throughout the app. Emitted via StateFlow from SettingsRepository.
 *
 * State flow progression:
 * ```
 * No URL → NeedsServerUrl
 * URL + Token → Authenticated (optimistic, skip network check)
 * URL + No Token → CheckingServer
 *   ├─ setup_required=true  → NeedsSetup
 *   ├─ setup_required=false → NeedsLogin
 *   └─ Network failure      → clear URL → NeedsServerUrl
 * ```
 *
 * Usage:
 * ```kotlin
 * settingsRepository.authState.collect { state ->
 *     when (state) {
 *         AuthState.NeedsServerUrl -> showServerSetup()
 *         AuthState.CheckingServer -> showLoading()
 *         AuthState.NeedsSetup -> showRootUserSetup()
 *         AuthState.NeedsLogin -> showLogin()
 *         is AuthState.Authenticated -> showLibrary(state.userId)
 *     }
 * }
 * ```
 */
sealed interface AuthState {
    /**
     * No server URL configured.
     * User must enter and validate a server URL before proceeding.
     */
    data object NeedsServerUrl : AuthState

    /**
     * Server URL configured, checking server status.
     * Temporary state while fetching instance info to determine if setup is required.
     */
    data object CheckingServer : AuthState

    /**
     * Server needs initial setup (no root user configured).
     * User must create the first admin account via the setup flow.
     */
    data object NeedsSetup : AuthState

    /**
     * Server is configured, but user needs to log in.
     * Show login screen for authentication.
     */
    data object NeedsLogin : AuthState

    /**
     * User is authenticated with valid tokens.
     *
     * Note: Tokens are assumed valid (optimistic). First API call will validate,
     * and 401 response triggers automatic refresh via ApiClientFactory interceptor.
     *
     * @property userId The authenticated user's ID
     * @property sessionId The active session ID from the server
     */
    data class Authenticated(
        val userId: String,
        val sessionId: String,
    ) : AuthState
}
