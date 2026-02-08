package com.calypsan.listenup.client.data.remote

import com.calypsan.listenup.client.core.AccessToken
import com.calypsan.listenup.client.core.RefreshToken
import com.calypsan.listenup.client.core.ServerUrl
import com.calypsan.listenup.client.domain.repository.AuthSession
import com.calypsan.listenup.client.domain.repository.ServerConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpSend
import kotlinx.io.IOException
import io.ktor.client.plugins.plugin
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
    private val serverConfig: ServerConfig,
    private val authSession: AuthSession,
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
            serverConfig.getActiveUrl()
                ?: error("Server URL not configured")

        return createStreamingHttpClient(
            serverUrl = serverUrl,
            authSession = authSession,
            authApi = authApi,
        )
    }

    /**
     * Create an unauthenticated streaming HTTP client.
     *
     * Used for SSE endpoints that don't require authentication, such as
     * the registration status stream for pending users.
     *
     * NOT cached - creates a new client each time.
     *
     * @return Configured HttpClient without auth, suitable for streaming
     */
    suspend fun getUnauthenticatedStreamingClient(): HttpClient {
        val serverUrl =
            serverConfig.getActiveUrl()
                ?: error("Server URL not configured")

        return createUnauthenticatedStreamingHttpClient(serverUrl)
    }

    private suspend fun createClient(): HttpClient {
        val initialUrl =
            serverConfig.getActiveUrl()
                ?: error("Server URL not configured")

        logger.info { "Creating HTTP client for server: ${initialUrl.value}" }

        val client =
            HttpClient {
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
                            val access = authSession.getAccessToken()?.value
                            val refresh = authSession.getRefreshToken()?.value

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
                                authSession.getRefreshToken()
                                    ?: error("No refresh token available")

                            try {
                                val response = authApi.refresh(currentRefreshToken)

                                // Save new tokens to storage
                                authSession.saveAuthTokens(
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
                                authSession.clearAuthTokens()
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
                    url(initialUrl.value)
                    contentType(ContentType.Application.Json)
                }
            }

        // Install HttpSend interceptor for dynamic URL resolution and fallback
        client.plugin(HttpSend).intercept { request ->
            // Resolve the current active URL dynamically for each request
            val activeUrl = serverConfig.getActiveUrl()
            if (activeUrl != null) {
                val parsed = Url(activeUrl.value)
                request.url.protocol = parsed.protocol
                request.url.host = parsed.host
                request.url.port = parsed.port
            }

            try {
                execute(request)
            } catch (cause: Exception) {
                if (isNetworkError(cause)) {
                    // Try fallback URL
                    val fallbackUrl = serverConfig.switchToFallbackUrl()
                    if (fallbackUrl != null) {
                        logger.info { "Network error, retrying with fallback URL: ${fallbackUrl.value}" }
                        val parsed = Url(fallbackUrl.value)
                        request.url.protocol = parsed.protocol
                        request.url.host = parsed.host
                        request.url.port = parsed.port
                        try {
                            execute(request)
                        } catch (retryError: Exception) {
                            // Fallback also failed, throw original error
                            throw cause
                        }
                    } else {
                        throw cause
                    }
                } else {
                    throw cause
                }
            }
        }

        return client
    }

    /**
     * Check if an exception is a network/connection error (not an HTTP error).
     * Only these should trigger URL fallback.
     */
    private fun isNetworkError(cause: Exception): Boolean =
        cause is java.net.ConnectException ||
            cause is java.net.SocketTimeoutException ||
            cause is java.net.UnknownHostException ||
            cause is java.net.NoRouteToHostException ||
            cause is io.ktor.client.plugins.HttpRequestTimeoutException ||
            cause.cause?.let { it is java.net.ConnectException || it is java.net.SocketTimeoutException } == true

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
 * @param authSession For loading auth tokens
 * @param authApi For refreshing tokens
 * @return HttpClient with streaming configuration and infinite timeouts
 */
internal expect suspend fun createStreamingHttpClient(
    serverUrl: ServerUrl,
    authSession: AuthSession,
    authApi: AuthApiContract,
): HttpClient

/**
 * Platform-specific unauthenticated streaming HTTP client factory.
 *
 * Creates an HttpClient configured for long-lived SSE connections
 * without authentication. Used for endpoints that don't require auth,
 * such as registration status streaming for pending users.
 *
 * @param serverUrl Base server URL
 * @return HttpClient with streaming configuration, no auth
 */
internal expect fun createUnauthenticatedStreamingHttpClient(serverUrl: ServerUrl): HttpClient
