package com.calypsan.listenup.client.data.sync

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.ServerResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

/**
 * Shared infrastructure for sync operations.
 *
 * Provides:
 * - Retry logic with exponential backoff
 * - Network error classification
 * - Progress reporting callbacks
 */
class SyncCoordinator {
    /**
     * Execute a block with retry logic and exponential backoff.
     *
     * @param maxRetries Maximum number of retry attempts
     * @param initialDelay Initial delay between retries
     * @param onRetry Callback invoked before each retry attempt
     * @param block The suspend block to execute
     * @return The result of the block
     * @throws Exception if all retries are exhausted
     */
    suspend fun <T> withRetry(
        maxRetries: Int = MAX_RETRIES,
        initialDelay: Duration = INITIAL_RETRY_DELAY,
        onRetry: (attempt: Int, maxAttempts: Int) -> Unit = { _, _ -> },
        block: suspend () -> T,
    ): T {
        var lastException: Exception? = null
        var retryDelay = initialDelay

        repeat(maxRetries) { attempt ->
            try {
                if (attempt > 0) {
                    logger.info {
                        "Retry attempt ${attempt + 1}/$maxRetries after ${retryDelay.inWholeMilliseconds}ms"
                    }
                    onRetry(attempt + 1, maxRetries)
                    delay(retryDelay)
                    retryDelay = (retryDelay * RETRY_BACKOFF_MULTIPLIER).coerceAtMost(MAX_RETRY_DELAY)
                }
                return block()
            } catch (e: CancellationException) {
                throw e // Don't retry on cancellation
            } catch (e: Exception) {
                lastException = e

                // Don't retry non-retryable errors (4xx client errors, etc.)
                if (!isRetryable(e)) {
                    logger.warn(e) { "Non-retryable error, failing immediately" }
                    throw e
                }

                logger.warn(e) { "Attempt ${attempt + 1} failed (retryable)" }
            }
        }

        throw lastException ?: error("Retry failed with unknown error")
    }

    /**
     * Determine if an exception is worth retrying.
     *
     * Retryable errors:
     * - Network errors (timeout, connection refused, IO errors)
     * - Server errors (5xx)
     *
     * Non-retryable errors:
     * - Client errors (4xx) - bad request, unauthorized, forbidden, not found
     * - These indicate a problem with the request itself, not transient issues
     */
    fun isRetryable(e: Exception): Boolean {
        var current: Throwable? = e
        while (current != null) {
            val throwable = current // Capture to immutable for smart cast
            when (throwable) {
                // Client errors (4xx) are not retryable - the request is wrong
                is ClientRequestException -> {
                    val status = throwable.response.status
                    logger.debug { "Client error ${status.value}: not retrying" }
                    return false
                }

                // Server errors (5xx) are retryable - server might recover
                is ServerResponseException -> {
                    logger.debug { "Server error ${throwable.response.status.value}: will retry" }
                    return true
                }

                // Generic response exception - check status code
                is ResponseException -> {
                    val status = throwable.response.status
                    return when {
                        status.value in 400..499 -> {
                            logger.debug { "Client error ${status.value}: not retrying" }
                            false
                        }

                        status.value in 500..599 -> {
                            logger.debug { "Server error ${status.value}: will retry" }
                            true
                        }

                        else -> {
                            true
                        } // Unexpected status, try again
                    }
                }

                // Network errors are retryable
                is ConnectTimeoutException, is IOException -> {
                    return true
                }
            }
            current = current.cause
        }
        // Unknown errors - default to retryable (conservative)
        return true
    }

    /**
     * Check if an exception indicates the server is unreachable.
     *
     * This detects connection errors that suggest the server is not running
     * or the URL is incorrect, such as:
     * - Connection refused (ECONNREFUSED)
     * - Connection timeout
     * - Host unreachable
     *
     * @param e The exception to check
     * @return true if the error indicates server is unreachable
     */
    fun isServerUnreachableError(e: Exception): Boolean {
        var current: Throwable? = e
        while (current != null) {
            when {
                current is ConnectTimeoutException -> {
                    return true
                }

                current is IOException -> {
                    val message = current.message?.lowercase() ?: ""
                    @Suppress("ComplexCondition")
                    if (message.contains("econnrefused") ||
                        message.contains("connection refused") ||
                        message.contains("failed to connect") ||
                        message.contains("no route to host") ||
                        message.contains("host unreachable") ||
                        message.contains("network is unreachable")
                    ) {
                        return true
                    }
                }

                current::class.simpleName == "ConnectException" -> {
                    val message = current.message?.lowercase() ?: ""
                    if (message.contains("econnrefused") ||
                        message.contains("connection refused") ||
                        message.contains("failed to connect")
                    ) {
                        return true
                    }
                }
            }
            current = current.cause
        }
        return false
    }

    companion object {
        const val MAX_RETRIES = 3
        val INITIAL_RETRY_DELAY = 1.seconds
        val MAX_RETRY_DELAY = 30.seconds
        const val RETRY_BACKOFF_MULTIPLIER = 2.0
    }
}
