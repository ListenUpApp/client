package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.AccessToken
import com.calypsan.listenup.client.core.RefreshToken
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.SecureStorage
import com.calypsan.listenup.client.core.ServerUrl
import com.calypsan.listenup.client.domain.repository.InstanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository for managing application settings and authentication state.
 *
 * This is the single source of truth for:
 * - Server URL configuration
 * - Authentication tokens (access + refresh)
 * - Session information (user ID, session ID)
 * - Reactive authentication state via StateFlow
 *
 * All sensitive data is stored via SecureStorage (encrypted at rest).
 */
class SettingsRepository(
    private val secureStorage: SecureStorage,
    private val instanceRepository: InstanceRepository
) {
    private val _authState = MutableStateFlow<AuthState>(AuthState.NeedsServerUrl)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_USER_ID = "user_id"
    }

    // Server configuration

    /**
     * Set the server URL for API requests.
     * This is persisted across app restarts and triggers auth state re-derivation.
     */
    suspend fun setServerUrl(url: ServerUrl) {
        secureStorage.save(KEY_SERVER_URL, url.value)
        // Re-derive state after URL change
        _authState.value = deriveAuthState()
    }

    /**
     * Get the configured server URL.
     * @return ServerUrl if configured, null otherwise
     */
    suspend fun getServerUrl(): ServerUrl? {
        return secureStorage.read(KEY_SERVER_URL)?.let { ServerUrl(it) }
    }

    // Authentication state management

    /**
     * Save authentication tokens after successful login or token refresh.
     * This updates the auth state to Authenticated.
     *
     * @param access PASETO access token (15min lifetime)
     * @param refresh Opaque refresh token (30d lifetime)
     * @param sessionId Server session ID
     * @param userId Authenticated user's ID
     */
    suspend fun saveAuthTokens(
        access: AccessToken,
        refresh: RefreshToken,
        sessionId: String,
        userId: String
    ) {
        secureStorage.save(KEY_ACCESS_TOKEN, access.value)
        secureStorage.save(KEY_REFRESH_TOKEN, refresh.value)
        secureStorage.save(KEY_SESSION_ID, sessionId)
        secureStorage.save(KEY_USER_ID, userId)

        _authState.value = AuthState.Authenticated(userId, sessionId)
    }

    /**
     * Get the current access token.
     * @return AccessToken if authenticated, null otherwise
     */
    suspend fun getAccessToken(): AccessToken? {
        return secureStorage.read(KEY_ACCESS_TOKEN)?.let { AccessToken(it) }
    }

    /**
     * Get the current refresh token.
     * @return RefreshToken if authenticated, null otherwise
     */
    suspend fun getRefreshToken(): RefreshToken? {
        return secureStorage.read(KEY_REFRESH_TOKEN)?.let { RefreshToken(it) }
    }

    /**
     * Get the current session ID.
     * @return Session ID if authenticated, null otherwise
     */
    suspend fun getSessionId(): String? {
        return secureStorage.read(KEY_SESSION_ID)
    }

    /**
     * Get the current user ID.
     * @return User ID if authenticated, null otherwise
     */
    suspend fun getUserId(): String? {
        return secureStorage.read(KEY_USER_ID)
    }

    /**
     * Update only the access token (used during automatic token refresh).
     * Does not change auth state - assumes user is still authenticated.
     */
    suspend fun updateAccessToken(token: AccessToken) {
        secureStorage.save(KEY_ACCESS_TOKEN, token.value)
    }

    // Session management

    /**
     * Clear authentication tokens (soft logout).
     * Keeps server URL and cached data intact.
     * Sets state directly to NeedsLogin without making network calls.
     *
     * This is called when:
     * - User explicitly logs out
     * - Token refresh fails (401 from server)
     *
     * We go directly to NeedsLogin (not CheckingServer) because:
     * 1. We know the server was reachable (we just got a 401)
     * 2. Making another HTTP call during auth failure can cause issues
     * 3. Server setup status rarely changes during a session
     *
     * TODO: Add playback resilience - check if audio is playing before clearing.
     * If playing, show banner instead of redirecting to login.
     */
    suspend fun clearAuthTokens() {
        secureStorage.delete(KEY_ACCESS_TOKEN)
        secureStorage.delete(KEY_REFRESH_TOKEN)
        secureStorage.delete(KEY_SESSION_ID)
        secureStorage.delete(KEY_USER_ID)

        // Go directly to NeedsLogin - don't make HTTP calls during auth failure
        // The server was reachable (we got a 401), so setup isn't required
        _authState.value = AuthState.NeedsLogin
    }

    /**
     * Clear all settings including server URL (complete reset).
     * Updates auth state to NeedsServerUrl.
     */
    suspend fun clearAll() {
        secureStorage.clear()
        _authState.value = AuthState.NeedsServerUrl
    }

    // State queries

    /**
     * Check if user has stored authentication tokens.
     * Note: Tokens may be expired - this only checks for presence.
     */
    suspend fun isAuthenticated(): Boolean = getAccessToken() != null

    /**
     * Check if server URL has been configured.
     */
    suspend fun hasServerConfigured(): Boolean = getServerUrl() != null

    /**
     * Initialize authentication state on app startup.
     * Call this once during app initialization to derive auth state from stored data.
     *
     * State derivation logic:
     * ```
     * No URL               → NeedsServerUrl
     * URL + Token          → Authenticated (optimistic, skip network check)
     * URL + No Token       → CheckingServer → check instance.setupRequired
     *   ├─ setup_required=true  → NeedsSetup
     *   ├─ setup_required=false → NeedsLogin
     *   └─ Network failure      → clear URL → NeedsServerUrl
     * ```
     */
    suspend fun initializeAuthState() {
        _authState.value = deriveAuthState()
    }

    /**
     * Derives the current authentication state based on stored data and server status.
     * Internal method called by initializeAuthState() and after state-changing operations.
     */
    private suspend fun deriveAuthState(): AuthState {
        val serverUrl = getServerUrl()

        // No URL configured
        if (serverUrl == null) {
            return AuthState.NeedsServerUrl
        }

        // URL + Token = Authenticated (optimistic)
        val hasToken = getAccessToken() != null
        val userId = getUserId()
        val sessionId = getSessionId()

        if (hasToken && userId != null && sessionId != null) {
            return AuthState.Authenticated(userId, sessionId)
        }

        // URL but no token → check server status
        return checkServerStatus()
    }

    /**
     * Checks the server's instance status to determine if setup is required.
     * Returns NeedsSetup if server has no root user, NeedsLogin otherwise.
     * On network failure, clears the URL and returns NeedsServerUrl.
     */
    private suspend fun checkServerStatus(): AuthState {
        // Emit checking state
        _authState.value = AuthState.CheckingServer

        return when (val result = instanceRepository.getInstance(forceRefresh = true)) {
            is Result.Success -> {
                if (result.data.setupRequired) {
                    AuthState.NeedsSetup
                } else {
                    AuthState.NeedsLogin
                }
            }
            is Result.Failure -> {
                // Server unreachable → clear URL and restart flow
                clearServerUrl()
                AuthState.NeedsServerUrl
            }
        }
    }

    /**
     * Handle server unreachable error (e.g., connection refused).
     *
     * Called when network operations fail with connection errors,
     * indicating the server is not running or URL is incorrect.
     *
     * This clears the stored server URL and transitions to NeedsServerUrl
     * state, allowing the user to re-enter/verify the server URL.
     */
    suspend fun handleServerUnreachable() {
        clearServerUrl()
        _authState.value = AuthState.NeedsServerUrl
    }

    /**
     * Clears the stored server URL.
     * Used when server becomes unreachable to force user to re-validate connection.
     */
    private suspend fun clearServerUrl() {
        secureStorage.delete(KEY_SERVER_URL)
    }
}
