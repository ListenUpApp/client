package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.ServerUrl
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.core.suspendRunCatching
import com.calypsan.listenup.client.data.remote.model.ApiResponse
import com.calypsan.listenup.client.domain.model.Instance
import com.calypsan.listenup.client.domain.repository.InstanceRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

/**
 * Implementation of InstanceRepository that fetches data from the ListenUp API.
 *
 * Uses dynamic server URL resolution to support runtime URL changes (e.g., user
 * selecting a different server via MDNS discovery).
 *
 * This repository acts as a single source of truth for instance data.
 * Future enhancements could include:
 * - In-memory caching with cache invalidation
 * - Offline support with local persistence
 * - Automatic refresh on stale data
 */
class InstanceRepositoryImpl(
    private val getServerUrl: suspend () -> ServerUrl?,
) : InstanceRepository {
    private val json =
        Json {
            prettyPrint = false
            isLenient = false
            ignoreUnknownKeys = true
        }

    /**
     * Cached instance data.
     * TODO: Add proper cache invalidation strategy when requirements are clearer.
     */
    private var cachedInstance: Instance? = null

    /**
     * Creates an HTTP client for the given server URL.
     * Each API call creates a fresh client to ensure we use the current URL.
     */
    private fun createClient(serverUrl: ServerUrl): HttpClient =
        HttpClient {
            install(ContentNegotiation) {
                json(this@InstanceRepositoryImpl.json)
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 30_000
            }

            defaultRequest {
                url(serverUrl.value)
            }
        }

    override suspend fun getInstance(forceRefresh: Boolean): Result<Instance> {
        // Return cached data if available and refresh not forced
        if (!forceRefresh && cachedInstance != null) {
            return Result.Success(cachedInstance!!)
        }

        val serverUrl = getServerUrl()
        if (serverUrl == null) {
            logger.warn { "Cannot fetch instance: server URL not configured" }
            return Result.Failure(IllegalStateException("Server URL not configured"))
        }

        logger.debug { "Fetching instance from ${serverUrl.value}/api/v1/instance" }

        return suspendRunCatching {
            val client = createClient(serverUrl)
            try {
                val response: ApiResponse<Instance> = client.get("/api/v1/instance").body()

                logger.debug { "Received instance response: success=${response.success}" }

                when (val result = response.toResult()) {
                    is Success -> result.data
                    is Failure -> throw result.exception
                }
            } finally {
                client.close()
            }
        }.also { result ->
            // Cache successful results
            if (result is Result.Success) {
                cachedInstance = result.data
            }
        }
    }
}
