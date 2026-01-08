package com.calypsan.listenup.client.presentation.auth

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.http.HttpStatusCode

private val logger = KotlinLogging.logger {}

/**
 * Maps exceptions to user-friendly [LoginErrorType] values.
 *
 * Provides type-safe exception handling using Ktor exception types and HTTP status codes
 * for reliable error classification, with fallback string matching for platform-specific
 * exceptions that can't be directly referenced in common code.
 */
interface LoginErrorMapper {
    /**
     * Convert an exception to a semantic login error type.
     *
     * @param exception The exception thrown during login attempt
     * @return A user-friendly [LoginErrorType] for UI display
     */
    fun map(exception: Exception): LoginErrorType
}

/**
 * Default implementation of [LoginErrorMapper].
 *
 * Maps exceptions using type checking and HTTP status codes:
 * - [ClientRequestException] with 401/403 → InvalidCredentials
 * - [ClientRequestException] with other 4xx → ServerError with status
 * - [ServerResponseException] (5xx) → ServerError with status
 * - [ConnectTimeoutException], [SocketTimeoutException] → NetworkError (timeout)
 * - Platform exceptions (ConnectException, UnknownHostException) → Detected via message
 * - Other exceptions → ServerError with original message
 */
class DefaultLoginErrorMapper : LoginErrorMapper {
    override fun map(exception: Exception): LoginErrorType {
        logger.debug { "Mapping login exception: ${exception::class.simpleName} - ${exception.message}" }

        return when (exception) {
            // HTTP 4xx client errors - type-safe via Ktor
            is ClientRequestException -> {
                mapClientError(exception)
            }

            // HTTP 5xx server errors - type-safe via Ktor
            is ServerResponseException -> {
                val status = exception.response.status.value
                LoginErrorType.ServerError("Server error ($status)")
            }

            // Connection timeout - type-safe via Ktor
            is ConnectTimeoutException, is SocketTimeoutException -> {
                LoginErrorType.NetworkError("Connection timed out. Check server address.")
            }

            // Platform-specific exceptions and other cases
            else -> {
                mapByMessageOrCause(exception)
            }
        }
    }

    /**
     * Map HTTP 4xx client errors based on status code.
     */
    private fun mapClientError(exception: ClientRequestException): LoginErrorType =
        when (exception.response.status) {
            HttpStatusCode.Unauthorized -> {
                LoginErrorType.InvalidCredentials
            }

            HttpStatusCode.Forbidden -> {
                LoginErrorType.InvalidCredentials
            }

            else -> {
                val status = exception.response.status.value
                LoginErrorType.ServerError("Request failed ($status)")
            }
        }

    /**
     * Map exceptions by checking message content and cause chain.
     *
     * Platform-specific exceptions (java.net.ConnectException, java.net.UnknownHostException)
     * can't be directly referenced in common code, so we detect them via message content.
     */
    private fun mapByMessageOrCause(exception: Exception): LoginErrorType {
        // Check cause chain for known Ktor exception types
        val cause = exception.cause
        if (cause is Exception && cause !== exception) {
            when (cause) {
                is ClientRequestException -> {
                    return mapClientError(cause)
                }

                is ServerResponseException -> {
                    return LoginErrorType.ServerError("Server error (${cause.response.status.value})")
                }

                is ConnectTimeoutException, is SocketTimeoutException -> {
                    return LoginErrorType.NetworkError("Connection timed out. Check server address.")
                }
            }
            // Recursively check cause chain
            val mappedCause = mapByMessageOrCause(cause)
            if (mappedCause !is LoginErrorType.ServerError ||
                mappedCause.detail?.contains("Unknown") != true
            ) {
                return mappedCause
            }
        }

        // Check message for error indicators (detects platform-specific exceptions and string-based errors)
        val msg = (exception.message ?: "").lowercase()
        val causeMsg = (exception.cause?.message ?: "").lowercase()
        val fullMsg = "$msg $causeMsg"

        return when {
            // Authentication errors (detected via message content)
            fullMsg.contains("invalid credentials") ||
                fullMsg.contains("unauthorized") ||
                fullMsg.contains("401") -> {
                LoginErrorType.InvalidCredentials
            }

            // Connection refused (java.net.ConnectException)
            fullMsg.contains("connection refused") || fullMsg.contains("econnrefused") -> {
                LoginErrorType.NetworkError("Connection refused. Is the server running?")
            }

            // Timeout
            fullMsg.contains("timeout") || fullMsg.contains("timed out") -> {
                LoginErrorType.NetworkError("Connection timed out. Check server address.")
            }

            // DNS resolution failed (java.net.UnknownHostException)
            fullMsg.contains("unable to resolve host") ||
                fullMsg.contains("unknown host") ||
                fullMsg.contains("no address associated") -> {
                LoginErrorType.NetworkError("Server not found. Check the address.")
            }

            // Generic network/socket issues
            fullMsg.contains("network") || fullMsg.contains("socket") -> {
                LoginErrorType.NetworkError(exception.cause?.message ?: exception.message)
            }

            // HTTP 5xx server errors (detected via message content)
            fullMsg.contains("500") || fullMsg.contains("502") ||
                fullMsg.contains("503") || fullMsg.contains("504") -> {
                val statusCode = extractStatusCode(fullMsg)
                LoginErrorType.ServerError("Server error ($statusCode)")
            }

            // Unknown - include the actual error message
            else -> {
                LoginErrorType.ServerError(exception.message ?: "Unknown error")
            }
        }
    }

    /**
     * Extract HTTP status code from error message if present.
     */
    private fun extractStatusCode(msg: String): String {
        val codes = listOf("500", "502", "503", "504", "400", "403", "404")
        return codes.find { msg.contains(it) } ?: "unknown"
    }
}
