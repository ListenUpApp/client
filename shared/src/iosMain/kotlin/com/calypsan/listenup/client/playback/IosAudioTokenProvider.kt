@file:OptIn(ExperimentalTime::class)

package com.calypsan.listenup.client.playback

import com.calypsan.listenup.client.core.AccessToken
import com.calypsan.listenup.client.core.RefreshToken
import com.calypsan.listenup.client.data.remote.AuthApi
import com.calypsan.listenup.client.domain.repository.SettingsRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

private val logger = KotlinLogging.logger {}

/**
 * iOS implementation of AudioTokenProvider.
 *
 * Design:
 * - Token is cached in a volatile field (non-blocking reads)
 * - Refresh happens proactively on a schedule AND reactively on 401
 * - Uses the existing auth infrastructure for actual refresh
 *
 * iOS-specific notes:
 * - URLSession delegates handle auth headers separately
 * - This class provides the token; iOS networking code reads it
 */
class IosAudioTokenProvider(
    private val settingsRepository: SettingsRepository,
    private val authApi: AuthApi,
    private val scope: CoroutineScope,
) : AudioTokenProvider {
    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var tokenExpiresAt: Long = 0L

    private val refreshMutex = Mutex()

    init {
        // Initial load
        scope.launch {
            refreshToken()
        }

        // Proactive refresh: check every 5 minutes, refresh if expiring within 10 minutes
        scope.launch {
            while (isActive) {
                delay(5.minutes)
                val now = Clock.System.now().toEpochMilliseconds()
                val expiresIn = tokenExpiresAt - now

                // Refresh if expiring within 10 minutes
                if (expiresIn < 10.minutes.inWholeMilliseconds) {
                    logger.debug { "Proactive token refresh: expires in ${expiresIn / 1000}s" }
                    refreshToken()
                }
            }
        }
    }

    /**
     * Non-blocking read for networking code.
     * Returns null if no token available.
     */
    override fun getToken(): String? = cachedToken

    /**
     * Called before starting playback to ensure fresh token.
     */
    override suspend fun prepareForPlayback() {
        refreshToken()
    }

    /**
     * Called on 401 response from network layer.
     * Triggers async refresh, returns immediately.
     */
    fun onUnauthorized() {
        scope.launch {
            logger.warn { "Token unauthorized, refreshing..." }
            refreshToken()
        }
    }

    private suspend fun refreshToken() {
        // Prevent concurrent refreshes
        refreshMutex.withLock {
            try {
                // Try to use existing token first
                val currentToken = settingsRepository.getAccessToken()
                if (currentToken != null) {
                    cachedToken = currentToken.value
                    // Estimate expiry (PASETO tokens are typically 15 min - 1 hour)
                    // Use 50 minutes as safe buffer
                    tokenExpiresAt = Clock.System.now().toEpochMilliseconds() + 50.minutes.inWholeMilliseconds
                    logger.debug { "Token loaded from storage" }
                    return
                }

                // No token in storage - try refresh
                val refreshToken = settingsRepository.getRefreshToken()
                if (refreshToken == null) {
                    logger.warn { "No refresh token available" }
                    cachedToken = null
                    return
                }

                try {
                    val response = authApi.refresh(refreshToken)

                    // Save new tokens
                    val sessionId = settingsRepository.getSessionId() ?: ""
                    val userId = settingsRepository.getUserId() ?: ""
                    settingsRepository.saveAuthTokens(
                        access = AccessToken(response.accessToken),
                        refresh = RefreshToken(response.refreshToken),
                        sessionId = sessionId,
                        userId = userId,
                    )

                    cachedToken = response.accessToken
                    tokenExpiresAt = Clock.System.now().toEpochMilliseconds() + 50.minutes.inWholeMilliseconds
                    logger.info { "Token refreshed successfully" }
                } catch (e: Exception) {
                    // Refresh failed - user needs to re-authenticate
                    logger.error(e) { "Token refresh failed" }
                    cachedToken = null
                }
            } catch (e: Exception) {
                logger.error(e) { "Error during token refresh" }
            }
        }
    }
}
