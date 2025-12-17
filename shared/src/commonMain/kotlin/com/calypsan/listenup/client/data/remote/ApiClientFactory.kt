package com.calypsan.listenup.client.data.remote

import com.calypsan.listenup.client.core.AccessToken
import com.calypsan.listenup.client.core.RefreshToken
import com.calypsan.listenup.client.core.ServerUrl
import com.calypsan.listenup.client.data.repository.SettingsRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

/**
 * Factory for creating authenticated HTTP clients with automatic token refresh.
 *
 * Provides a single cached client instance that:
 * - Automatically adds Bearer auth headers
 * - Refreshes expired tokens on 401 responses
 * - Updates SettingsRepository with new tokens
 * - Handles concurrent refresh requests safely
 *
 * The client is lazy-initialized and cached for the lifetime of the factory.
 * Call [close] to release resources when no longer needed.
 */
class ApiClientFactory(
    private val settingsRepository: SettingsRepository,
    private val authApi: AuthApiContract,
) {
    private val mutex = Mutex()
    private var cachedClient: HttpClient? = null

    /**
     * Get or create the authenticated HTTP client for regular API calls.
     *
     * Client is cached after first creation. All requests through this client
     * will automatically include Bearer tokens and handle token refresh.
     *
     * Includes timeouts suitable for request/response API calls (30s).
     *
     * @return Configured HttpClient with auth plugin and timeouts
     */
    suspend fun getClient(): HttpClient =
        mutex.withLock {
            cachedClient ?: createClient().also { cachedClient = it }
        }

    /**
     * Create a streaming HTTP client for long-lived connections (SSE, WebSocket).
     *
     * Similar to getClient() but WITHOUT timeouts, suitable for streaming responses
     * that may stay open indefinitely.
     *
     * Platform-specific implementations configure engine-level timeouts to infinity:
     * - Android: OkHttp connect/read/write timeouts = 0 (infinite)
     * - iOS: URLSession timeouts = infinity
     *
     * NOT cached - creates a new client each time.
     *
     * @return Configured HttpClient with auth but no timeouts
     */
    suspend fun getStreamingClient(): HttpClient {
        val serverUrl =
            settingsRepository.getServerUrl()
                ?: error("Server URL not configured")

        return createStreamingHttpClient(
            serverUrl = serverUrl,
            settingsRepository = settingsRepository,
            authApi = authApi,
        )
    }

    private suspend fun createClient(): HttpClient {
        val serverUrl =
            settingsRepository.getServerUrl()
                ?: error("Server URL not configured")

        return HttpClient {
            install(ContentNegotiation) {
                json(
                    Json {
                        prettyPrint = false
                        isLenient = false
                        ignoreUnknownKeys = true
                    },
                )
            }

            // Install HttpTimeout plugin to allow per-request timeout configuration
            // Default timeouts for regular API calls (SSE uses separate client)
            @Suppress("MagicNumber")
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 30_000
            }

            install(Auth) {
                bearer {
                    // Load initial tokens from storage
                    loadTokens {
                        val access = settingsRepository.getAccessToken()?.value
                        val refresh = settingsRepository.getRefreshToken()?.value

                        if (access != null && refresh != null) {
                            BearerTokens(
                                accessToken = access,
                                refreshToken = refresh,
                            )
                        } else {
                            null
                        }
                    }

                    // Refresh tokens when receiving 401 Unauthorized
                    refreshTokens {
                        val currentRefreshToken =
                            settingsRepository.getRefreshToken()
                                ?: error("No refresh token available")

                        try {
                            val response = authApi.refresh(currentRefreshToken)

                            // Save new tokens to storage
                            settingsRepository.saveAuthTokens(
                                access = AccessToken(response.accessToken),
                                refresh = RefreshToken(response.refreshToken),
                                sessionId = response.sessionId,
                                userId = response.userId,
                            )

                            BearerTokens(
                                accessToken = response.accessToken,
                                refreshToken = response.refreshToken,
                            )
                        } catch (e: Exception) {
                            // Refresh failed - clear auth state and force re-login
                            logger.warn(e) { "Token refresh failed, clearing auth state" }
                            settingsRepository.clearAuthTokens()
                            null
                        }
                    }

                    // Control when to send bearer token
                    sendWithoutRequest { request ->
                        // Send auth header for all requests except auth endpoints
                        // (auth endpoints handle their own credentials in request body)
                        val urlString = request.url.toString()
                        // Match /api/v1/auth/ paths (login, refresh, logout, etc.)
                        !urlString.contains("/auth/")
                    }
                }
            }

            defaultRequest {
                url(serverUrl.value)
                contentType(ContentType.Application.Json)
            }
        }
    }

    /**
     * Invalidate the cached client and create a new one.
     * Useful when server URL changes or manual client reset is needed.
     */
    suspend fun invalidate() {
        mutex.withLock {
            cachedClient?.close()
            cachedClient = null
        }
    }

    /**
     * Close the cached client and release resources.
     * Call this when the factory is no longer needed.
     */
    suspend fun close() {
        mutex.withLock {
            cachedClient?.close()
            cachedClient = null
        }
    }
}

/**
 * Platform-specific streaming HTTP client factory.
 *
 * Creates an HttpClient configured for long-lived SSE/WebSocket connections
 * with infinite timeouts at the engine level.
 *
 * @param serverUrl Base server URL
 * @param settingsRepository For loading auth tokens
 * @param authApi For refreshing tokens
 * @return HttpClient with streaming configuration and infinite timeouts
 */
internal expect suspend fun createStreamingHttpClient(
    serverUrl: ServerUrl,
    settingsRepository: SettingsRepository,
    authApi: AuthApiContract,
): HttpClient
