package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.AccessToken
import com.calypsan.listenup.client.core.RefreshToken
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.SecureStorage
import com.calypsan.listenup.client.core.ServerUrl
import com.calypsan.listenup.client.domain.repository.InstanceRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Events emitted when user preferences change.
 * Observed by the sync layer to queue operations without creating circular dependencies.
 */
sealed interface PreferenceChangeEvent {
    /**
     * Default playback speed was changed.
     */
    data class PlaybackSpeedChanged(
        val speed: Float,
    ) : PreferenceChangeEvent
}

// region Segregated Interfaces (ISP)

/**
 * Contract for authentication and session management.
 *
 * Used by components that need to manage auth tokens, session state,
 * or observe authentication changes (ApiClientFactory, navigation, token providers).
 */
interface AuthSessionContract {
    /** Reactive authentication state. */
    val authState: StateFlow<AuthState>

    /** Save authentication tokens after successful login or token refresh. */
    suspend fun saveAuthTokens(
        access: AccessToken,
        refresh: RefreshToken,
        sessionId: String,
        userId: String,
    )

    suspend fun getAccessToken(): AccessToken?

    suspend fun getRefreshToken(): RefreshToken?

    suspend fun getSessionId(): String?

    suspend fun getUserId(): String?

    /** Update only the access token (used during automatic token refresh). */
    suspend fun updateAccessToken(token: AccessToken)

    /** Clear authentication tokens (soft logout). */
    suspend fun clearAuthTokens()

    /** Check if user has stored authentication tokens. */
    suspend fun isAuthenticated(): Boolean

    /** Initialize authentication state on app startup. */
    suspend fun initializeAuthState()

    /** Check server status to determine if setup is required. */
    suspend fun checkServerStatus(): AuthState
}

/**
 * Contract for server URL configuration.
 *
 * Used by components that need to know the server URL
 * (SSEManager, DownloadWorker, ImageApi, API clients).
 */
interface ServerConfigContract {
    suspend fun setServerUrl(url: ServerUrl)

    suspend fun getServerUrl(): ServerUrl?

    suspend fun hasServerConfigured(): Boolean

    /** Disconnect from current server (clears URL and auth data). */
    suspend fun disconnectFromServer()

    /** Clear all settings including server URL (complete reset). */
    suspend fun clearAll()
}

/**
 * Contract for library display and sort preferences.
 *
 * Used by LibraryViewModel and SettingsViewModel for managing
 * how books, series, and contributors are displayed and sorted.
 */
interface LibraryPreferencesContract {
    // Sort state per tab
    suspend fun getBooksSortState(): String?

    suspend fun setBooksSortState(persistenceKey: String)

    suspend fun getSeriesSortState(): String?

    suspend fun setSeriesSortState(persistenceKey: String)

    suspend fun getAuthorsSortState(): String?

    suspend fun setAuthorsSortState(persistenceKey: String)

    suspend fun getNarratorsSortState(): String?

    suspend fun setNarratorsSortState(persistenceKey: String)

    // Display options
    suspend fun getIgnoreTitleArticles(): Boolean

    suspend fun setIgnoreTitleArticles(ignore: Boolean)

    suspend fun getHideSingleBookSeries(): Boolean

    suspend fun setHideSingleBookSeries(hide: Boolean)
}

/**
 * Contract for playback preferences.
 *
 * Used by SettingsViewModel, NowPlayingViewModel, DownloadWorker,
 * and SyncManager for managing playback-related settings.
 */
interface PlaybackPreferencesContract {
    /** Flow of preference change events for sync layer. */
    val preferenceChanges: SharedFlow<PreferenceChangeEvent>

    /**
     * Get the default playback speed for new books.
     * @return Playback speed multiplier (e.g., 1.0, 1.25, 1.5). Default is 1.0.
     */
    suspend fun getDefaultPlaybackSpeed(): Float

    /**
     * Set the default playback speed for new books.
     * This is a synced setting - will be pushed to server.
     */
    suspend fun setDefaultPlaybackSpeed(speed: Float)

    /** Get whether spatial (5.1 surround) audio is preferred. */
    suspend fun getSpatialPlayback(): Boolean

    /** Set spatial audio preference (per-device setting). */
    suspend fun setSpatialPlayback(enabled: Boolean)
}

// endregion

/**
 * Aggregate contract for backward compatibility.
 *
 * New code should depend on specific interfaces (AuthSessionContract,
 * ServerConfigContract, etc.) rather than this aggregate.
 */
interface SettingsRepositoryContract :
    AuthSessionContract,
    ServerConfigContract,
    LibraryPreferencesContract,
    PlaybackPreferencesContract

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
    private val instanceRepository: InstanceRepository,
) : SettingsRepositoryContract {
    // Override properties can't use explicit backing fields - must use traditional pattern
    private val _authState = MutableStateFlow<AuthState>(AuthState.Initializing)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // Buffer of 1 ensures emit() doesn't suspend when no collectors are active.
    // This is appropriate for preference sync since we don't want settings changes
    // to block waiting for the sync layer.
    private val _preferenceChanges = MutableSharedFlow<PreferenceChangeEvent>(extraBufferCapacity = 1)
    override val preferenceChanges: SharedFlow<PreferenceChangeEvent> = _preferenceChanges.asSharedFlow()

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_USER_ID = "user_id"

        // Library sort preferences (per-tab)
        private const val KEY_SORT_BOOKS = "sort_books"
        private const val KEY_SORT_SERIES = "sort_series"
        private const val KEY_SORT_AUTHORS = "sort_authors"
        private const val KEY_SORT_NARRATORS = "sort_narrators"

        // Title sort article handling
        private const val KEY_IGNORE_TITLE_ARTICLES = "ignore_title_articles"

        // Series display preferences
        private const val KEY_HIDE_SINGLE_BOOK_SERIES = "hide_single_book_series"

        // Playback preferences
        private const val KEY_SPATIAL_PLAYBACK = "spatial_playback"
        private const val KEY_DEFAULT_PLAYBACK_SPEED = "default_playback_speed"

        // Default values
        const val DEFAULT_PLAYBACK_SPEED = 1.0f
    }

    // Server configuration

    /**
     * Set the server URL for API requests.
     * This is persisted across app restarts and checks server status to determine
     * if setup (root user creation) is needed.
     */
    override suspend fun setServerUrl(url: ServerUrl) {
        secureStorage.save(KEY_SERVER_URL, url.value)
        // Check if we already have tokens (returning user)
        val hasToken = getAccessToken() != null
        if (hasToken) {
            // Trust local tokens - derive state from stored data
            _authState.value = deriveAuthState()
        } else {
            // No tokens - need to check server to determine if setup is required
            // This is the first time connecting, so check if server has users
            checkServerStatus()
        }
    }

    /**
     * Get the configured server URL.
     * @return ServerUrl if configured, null otherwise
     */
    override suspend fun getServerUrl(): ServerUrl? = secureStorage.read(KEY_SERVER_URL)?.let { ServerUrl(it) }

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
    override suspend fun saveAuthTokens(
        access: AccessToken,
        refresh: RefreshToken,
        sessionId: String,
        userId: String,
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
    override suspend fun getAccessToken(): AccessToken? = secureStorage.read(KEY_ACCESS_TOKEN)?.let { AccessToken(it) }

    /**
     * Get the current refresh token.
     * @return RefreshToken if authenticated, null otherwise
     */
    override suspend fun getRefreshToken(): RefreshToken? =
        secureStorage.read(KEY_REFRESH_TOKEN)?.let { RefreshToken(it) }

    /**
     * Get the current session ID.
     * @return Session ID if authenticated, null otherwise
     */
    override suspend fun getSessionId(): String? = secureStorage.read(KEY_SESSION_ID)

    /**
     * Get the current user ID.
     * @return User ID if authenticated, null otherwise
     */
    override suspend fun getUserId(): String? = secureStorage.read(KEY_USER_ID)

    /**
     * Update only the access token (used during automatic token refresh).
     * Does not change auth state - assumes user is still authenticated.
     */
    override suspend fun updateAccessToken(token: AccessToken) {
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
    override suspend fun clearAuthTokens() {
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
    override suspend fun clearAll() {
        secureStorage.clear()
        _authState.value = AuthState.NeedsServerUrl
    }

    // State queries

    /**
     * Check if user has stored authentication tokens.
     * Note: Tokens may be expired - this only checks for presence.
     */
    override suspend fun isAuthenticated(): Boolean = getAccessToken() != null

    /**
     * Check if server URL has been configured.
     */
    override suspend fun hasServerConfigured(): Boolean = getServerUrl() != null

    /**
     * Initialize authentication state on app startup.
     * Call this once during app initialization to derive auth state from stored data.
     *
     * State derivation logic (offline-first):
     * ```
     * No URL               → NeedsServerUrl
     * URL + Token          → Authenticated (trust local tokens)
     * URL + No Token       → NeedsLogin (don't check network on startup)
     * ```
     *
     * We deliberately skip network checks on startup. If tokens are invalid,
     * they'll fail when used and trigger re-auth via 401 handling.
     * This ensures the app works offline with cached data.
     */
    override suspend fun initializeAuthState() {
        _authState.value = deriveAuthState()
    }

    /**
     * Derives the current authentication state based on stored data.
     *
     * Offline-first: We never make network calls during state derivation.
     * If tokens exist, we trust them. If they're invalid, API calls will
     * fail with 401 and trigger re-auth at that point.
     */
    private suspend fun deriveAuthState(): AuthState {
        val serverUrl = getServerUrl()

        // No URL configured → need server setup
        if (serverUrl == null) {
            return AuthState.NeedsServerUrl
        }

        // URL + Token = Authenticated (trust local state)
        val hasToken = getAccessToken() != null
        val userId = getUserId()
        val sessionId = getSessionId()

        if (hasToken && userId != null && sessionId != null) {
            return AuthState.Authenticated(userId, sessionId)
        }

        // URL + Token but missing userId/sessionId → still trust it
        // This handles edge cases where session data wasn't fully persisted
        if (hasToken) {
            // Use placeholder values - will be refreshed on first successful API call
            return AuthState.Authenticated(
                userId = userId ?: "pending",
                sessionId = sessionId ?: "pending",
            )
        }

        // URL but no token → user needs to log in
        // Don't check server status - that's a network call
        return AuthState.NeedsLogin
    }

    /**
     * Checks the server's instance status to determine if setup is required.
     * Returns NeedsSetup if server has no root user, NeedsLogin otherwise.
     *
     * Note: This is only called explicitly (e.g., from login screen), never on startup.
     * On network failure, we stay in NeedsLogin - the server URL is never cleared
     * automatically. Users must explicitly change servers via settings.
     */
    override suspend fun checkServerStatus(): AuthState {
        _authState.value = AuthState.CheckingServer

        return when (val result = instanceRepository.getInstance(forceRefresh = true)) {
            is Result.Success -> {
                val newState =
                    if (result.data.setupRequired) {
                        AuthState.NeedsSetup
                    } else {
                        AuthState.NeedsLogin
                    }
                _authState.value = newState
                newState
            }

            is Result.Failure -> {
                // Server unreachable - stay in NeedsLogin, don't clear URL
                // User can retry or check their connection
                _authState.value = AuthState.NeedsLogin
                AuthState.NeedsLogin
            }
        }
    }

    /**
     * Explicitly disconnect from the current server.
     *
     * This clears the server URL and all auth data, returning to the
     * initial server setup screen. Only call this when the user
     * explicitly wants to change servers (e.g., from a "Change Server" button).
     *
     * For network errors, do NOT call this - let the user retry or work offline.
     */
    override suspend fun disconnectFromServer() {
        secureStorage.delete(KEY_SERVER_URL)
        secureStorage.delete(KEY_ACCESS_TOKEN)
        secureStorage.delete(KEY_REFRESH_TOKEN)
        secureStorage.delete(KEY_SESSION_ID)
        secureStorage.delete(KEY_USER_ID)
        _authState.value = AuthState.NeedsServerUrl
    }

    // Library sort preferences
    // Stored as "category:direction" (e.g., "title:ascending")

    /**
     * Get the sort state for the Books tab.
     * @return Persistence key (category:direction), or null if not set
     */
    override suspend fun getBooksSortState(): String? = secureStorage.read(KEY_SORT_BOOKS)

    /**
     * Set the sort state for the Books tab.
     */
    override suspend fun setBooksSortState(persistenceKey: String) {
        secureStorage.save(KEY_SORT_BOOKS, persistenceKey)
    }

    /**
     * Get the sort state for the Series tab.
     * @return Persistence key (category:direction), or null if not set
     */
    override suspend fun getSeriesSortState(): String? = secureStorage.read(KEY_SORT_SERIES)

    /**
     * Set the sort state for the Series tab.
     */
    override suspend fun setSeriesSortState(persistenceKey: String) {
        secureStorage.save(KEY_SORT_SERIES, persistenceKey)
    }

    /**
     * Get the sort state for the Authors tab.
     * @return Persistence key (category:direction), or null if not set
     */
    override suspend fun getAuthorsSortState(): String? = secureStorage.read(KEY_SORT_AUTHORS)

    /**
     * Set the sort state for the Authors tab.
     */
    override suspend fun setAuthorsSortState(persistenceKey: String) {
        secureStorage.save(KEY_SORT_AUTHORS, persistenceKey)
    }

    /**
     * Get the sort state for the Narrators tab.
     * @return Persistence key (category:direction), or null if not set
     */
    override suspend fun getNarratorsSortState(): String? = secureStorage.read(KEY_SORT_NARRATORS)

    /**
     * Set the sort state for the Narrators tab.
     */
    override suspend fun setNarratorsSortState(persistenceKey: String) {
        secureStorage.save(KEY_SORT_NARRATORS, persistenceKey)
    }

    // Title sort article handling

    /**
     * Get whether to ignore leading articles (A, An, The) when sorting by title.
     * @return true to ignore articles (default), false for literal sort
     */
    override suspend fun getIgnoreTitleArticles(): Boolean =
        secureStorage.read(KEY_IGNORE_TITLE_ARTICLES)?.toBooleanStrictOrNull() ?: true

    /**
     * Set whether to ignore leading articles when sorting by title.
     */
    override suspend fun setIgnoreTitleArticles(ignore: Boolean) {
        secureStorage.save(KEY_IGNORE_TITLE_ARTICLES, ignore.toString())
    }

    // Series display preferences

    /**
     * Get whether to hide series with only one book.
     * @return true to hide single-book series (default), false to show all
     */
    override suspend fun getHideSingleBookSeries(): Boolean =
        secureStorage.read(KEY_HIDE_SINGLE_BOOK_SERIES)?.toBooleanStrictOrNull() ?: true

    /**
     * Set whether to hide series with only one book.
     */
    override suspend fun setHideSingleBookSeries(hide: Boolean) {
        secureStorage.save(KEY_HIDE_SINGLE_BOOK_SERIES, hide.toString())
    }

    // Playback preferences

    /**
     * Get whether spatial (5.1 surround) audio is preferred.
     * When enabled, transcoded audio uses 5.1 surround for spatial audio support.
     * When disabled, uses stereo for faster transcoding and universal compatibility.
     * @return true for 5.1 spatial (default), false for stereo
     */
    override suspend fun getSpatialPlayback(): Boolean =
        secureStorage.read(KEY_SPATIAL_PLAYBACK)?.toBooleanStrictOrNull() ?: true

    /**
     * Set spatial audio preference.
     * This is a per-device setting - different devices can have different preferences.
     */
    override suspend fun setSpatialPlayback(enabled: Boolean) {
        secureStorage.save(KEY_SPATIAL_PLAYBACK, enabled.toString())
    }

    // Universal playback speed (synced across devices)

    /**
     * Get the default playback speed for new books.
     * This is a synced setting - the value may be updated when preferences sync.
     * @return Playback speed multiplier (e.g., 1.0, 1.25, 1.5). Default is 1.0.
     */
    override suspend fun getDefaultPlaybackSpeed(): Float =
        secureStorage.read(KEY_DEFAULT_PLAYBACK_SPEED)?.toFloatOrNull() ?: DEFAULT_PLAYBACK_SPEED

    /**
     * Set the default playback speed for new books.
     * This is a synced setting - emits an event for the sync layer to queue.
     * @param speed Playback speed multiplier (e.g., 1.0, 1.25, 1.5)
     */
    override suspend fun setDefaultPlaybackSpeed(speed: Float) {
        // Save locally
        secureStorage.save(KEY_DEFAULT_PLAYBACK_SPEED, speed.toString())

        // Emit event for sync layer to observe and queue
        _preferenceChanges.emit(PreferenceChangeEvent.PlaybackSpeedChanged(speed))
    }
}
