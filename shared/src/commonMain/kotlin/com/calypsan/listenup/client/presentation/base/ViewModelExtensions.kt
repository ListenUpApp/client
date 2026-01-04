package com.calypsan.listenup.client.presentation.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Launch a coroutine in viewModelScope with standardized error handling.
 *
 * This extension consolidates the common pattern:
 * ```
 * viewModelScope.launch {
 *     try {
 *         doSomething()
 *     } catch (e: Exception) {
 *         logger.error(e) { "Failed to do something" }
 *     }
 * }
 * ```
 *
 * Into:
 * ```
 * launchSafely(
 *     onError = { logger.error(it) { "Failed to do something" } }
 * ) {
 *     doSomething()
 * }
 * ```
 *
 * @param onError Handler called when an exception is caught
 * @param block The suspend function to execute
 * @return The launched Job
 */
inline fun ViewModel.launchSafely(
    crossinline onError: (Exception) -> Unit = {},
    crossinline block: suspend CoroutineScope.() -> Unit,
): Job =
    viewModelScope.launch {
        try {
            block()
        } catch (e: Exception) {
            onError(e)
        }
    }

/**
 * Launch a coroutine with logging on error.
 *
 * Convenience variant that logs to the provided logger:
 * ```
 * launchWithLogging(logger, "save book") {
 *     repository.save(book)
 * }
 * ```
 *
 * @param logger The logger to use for error logging
 * @param operation Description of the operation for the error message
 * @param block The suspend function to execute
 * @return The launched Job
 */
inline fun ViewModel.launchWithLogging(
    logger: KLogger,
    operation: String,
    crossinline block: suspend CoroutineScope.() -> Unit,
): Job =
    launchSafely(
        onError = { logger.error(it) { "Failed to $operation" } },
        block = block,
    )
