package com.calypsan.listenup.client.data.remote.api

import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.core.suspendRunCatching
import com.calypsan.listenup.client.data.remote.ApiClientFactory
import com.calypsan.listenup.client.data.remote.model.ApiResponse
import com.calypsan.listenup.client.data.remote.model.PlaybackProgressResponse
import com.calypsan.listenup.client.domain.model.Instance
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

/**
 * HTTP client for the ListenUp audiobook server API.
 *
 * Handles both public and authenticated API endpoints:
 * - Public endpoints (like getInstance) use a simple unauthenticated client
 * - Authenticated endpoints use ApiClientFactory for automatic token refresh
 *
 * This separation ensures clean architecture - public endpoints don't carry
 * unnecessary auth overhead, while authenticated endpoints get automatic
 * token management.
 *
 * Uses Ktor 3 for modern, multiplatform HTTP client functionality.
 */
class ListenUpApi(
    private val baseUrl: String,
    private val apiClientFactory: ApiClientFactory? = null
) {
    /**
     * Simple HTTP client for public endpoints (no authentication).
     * Used for endpoints like getInstance that don't require credentials.
     */
    private val publicClient = HttpClient {
        // JSON content negotiation for request/response serialization
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = false
                isLenient = false
                ignoreUnknownKeys = true
            })
        }

        // HTTP logging for debugging
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    com.calypsan.listenup.client.data.remote.api.logger.debug { message }
                }
            }
            level = LogLevel.HEADERS
        }

        // Request timeout configuration
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
        }

        // Default request configuration
        defaultRequest {
            url(baseUrl)
        }
    }

    /**
     * Get authenticated HTTP client from factory.
     * Used for endpoints that require Bearer token authentication.
     *
     * @throws IllegalStateException if factory not provided
     */
    private suspend fun getAuthenticatedClient(): HttpClient {
        return apiClientFactory?.getClient()
            ?: throw IllegalStateException("ApiClientFactory required for authenticated endpoints")
    }

    /**
     * Fetch the server instance information.
     *
     * This is a public endpoint - no authentication required.
     *
     * @return Result containing the Instance on success, or an error on failure
     */
    suspend fun getInstance(): Result<Instance> = suspendRunCatching {
        logger.debug { "Fetching instance information from $baseUrl/api/v1/instance" }

        val response: ApiResponse<Instance> = publicClient.get("/api/v1/instance").body()

        logger.debug { "Received response: success=${response.success}" }

        // Convert API response to Result and extract data
        when (val result = response.toResult()) {
            is Success -> result.data
            is Failure -> throw result.exception
        }
    }

    /**
     * Fetch books the user is currently listening to.
     *
     * This is an authenticated endpoint - requires valid access token.
     * Returns playback progress for books sorted by last played time.
     *
     * @param limit Maximum number of books to return (default 10)
     * @return Result containing list of PlaybackProgressResponse on success
     */
    suspend fun getContinueListening(limit: Int = 10): Result<List<PlaybackProgressResponse>> = suspendRunCatching {
        logger.debug { "Fetching continue listening from $baseUrl/api/v1/listening/continue" }

        val client = getAuthenticatedClient()
        val response: ApiResponse<List<PlaybackProgressResponse>> = client.get("/api/v1/listening/continue") {
            parameter("limit", limit)
        }.body()

        logger.debug { "Received continue listening response: success=${response.success}" }

        when (val result = response.toResult()) {
            is Success -> result.data
            is Failure -> throw result.exception
        }
    }

    /**
     * Clean up resources when the API client is no longer needed.
     */
    fun close() {
        publicClient.close()
    }
}
