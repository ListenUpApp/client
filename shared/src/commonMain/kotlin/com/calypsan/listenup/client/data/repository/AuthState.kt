package com.calypsan.listenup.client.data.repository

/**
 * Represents the authentication state of the application.
 *
 * This sealed interface provides a type-safe way to handle different authentication
 * states throughout the app. Emitted via StateFlow from SettingsRepository.
 *
 * Usage:
 * ```kotlin
 * settingsRepository.authState.collect { state ->
 *     when (state) {
 *         is AuthState.Loading -> showLoading()
 *         is AuthState.Unauthenticated -> showLogin()
 *         is AuthState.Authenticated -> showMainScreen(state.userId)
 *     }
 * }
 * ```
 */
sealed interface AuthState {
    /**
     * User is authenticated with valid tokens.
     *
     * @property userId The authenticated user's ID
     * @property sessionId The active session ID from the server
     */
    data class Authenticated(
        val userId: String,
        val sessionId: String
    ) : AuthState

    /**
     * User is not authenticated.
     * This state is entered after:
     * - App start with no stored tokens
     * - Explicit logout
     * - Token refresh failure
     */
    data object Unauthenticated : AuthState

    /**
     * Authentication state is being initialized.
     * This is the initial state before checking for stored credentials.
     */
    data object Loading : AuthState
}
