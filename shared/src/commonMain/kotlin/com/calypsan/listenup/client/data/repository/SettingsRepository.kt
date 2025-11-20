package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.AccessToken
import com.calypsan.listenup.client.core.RefreshToken
import com.calypsan.listenup.client.core.SecureStorage
import com.calypsan.listenup.client.core.ServerUrl
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
    private val secureStorage: SecureStorage
) {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
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
     * This is persisted across app restarts.
     */
    suspend fun setServerUrl(url: ServerUrl) {
        secureStorage.save(KEY_SERVER_URL, url.value)
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
     * Updates auth state to Unauthenticated.
     */
    suspend fun clearAuthTokens() {
        secureStorage.delete(KEY_ACCESS_TOKEN)
        secureStorage.delete(KEY_REFRESH_TOKEN)
        secureStorage.delete(KEY_SESSION_ID)
        secureStorage.delete(KEY_USER_ID)

        _authState.value = AuthState.Unauthenticated
    }

    /**
     * Clear all settings including server URL (complete reset).
     * Updates auth state to Unauthenticated.
     */
    suspend fun clearAll() {
        secureStorage.clear()
        _authState.value = AuthState.Unauthenticated
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
     * Call this once during app initialization to restore auth state from storage.
     */
    suspend fun initializeAuthState() {
        val hasTokens = getAccessToken() != null
        val userId = getUserId()
        val sessionId = getSessionId()

        _authState.value = if (hasTokens && userId != null && sessionId != null) {
            AuthState.Authenticated(userId, sessionId)
        } else {
            AuthState.Unauthenticated
        }
    }
}
