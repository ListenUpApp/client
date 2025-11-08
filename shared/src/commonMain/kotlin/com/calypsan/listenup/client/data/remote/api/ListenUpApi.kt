package com.calypsan.listenup.client.data.remote.api

import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.core.suspendRunCatching
import com.calypsan.listenup.client.data.remote.model.ApiResponse
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
 * This class handles all network communication with the server,
 * including request configuration, response deserialization, and error handling.
 *
 * Uses Ktor 3 for modern, multiplatform HTTP client functionality.
 */
class ListenUpApi(private val baseUrl: String) {
    /**
     * Configured HTTP client with JSON serialization, logging, and timeouts.
     */
    private val client = HttpClient {
        // JSON content negotiation for request/response serialization
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
                encodeDefaults = true
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
     * Fetch the server instance information.
     *
     * @return Result containing the Instance on success, or an error on failure
     */
    suspend fun getInstance(): Result<Instance> = suspendRunCatching {
        logger.debug { "Fetching instance information from $baseUrl/api/v1/instance" }

        val response: ApiResponse<Instance> = client.get("/api/v1/instance").body()

        logger.debug { "Received response: success=${response.success}" }

        // Convert API response to Result and extract data
        when (val result = response.toResult()) {
            is Success -> result.data
            is Failure -> throw result.exception
        }
    }

    /**
     * Clean up resources when the API client is no longer needed.
     */
    fun close() {
        client.close()
    }
}
