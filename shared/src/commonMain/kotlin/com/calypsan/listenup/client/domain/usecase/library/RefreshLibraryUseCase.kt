package com.calypsan.listenup.client.domain.usecase.library

import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.core.suspendRunCatching
import com.calypsan.listenup.client.domain.model.SyncState
import com.calypsan.listenup.client.domain.repository.SyncRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.StateFlow

private val logger = KotlinLogging.logger {}

/**
 * Result of a library refresh operation.
 *
 * Contains the sync state after the operation completes.
 */
data class RefreshLibraryResult(
    val state: SyncState,
    val message: String,
)

/**
 * Use case for refreshing the library from the server.
 *
 * Encapsulates all business logic for library refresh:
 * - Triggering sync via SyncManager
 * - Handling sync errors gracefully
 * - Providing user-friendly error messages
 *
 * The ViewModel becomes a thin coordinator that:
 * - Manages UI state (Loading indicator)
 * - Observes sync state changes
 * - Delegates to this use case for business logic
 *
 * Follows the operator invoke pattern for clean call-site syntax:
 * ```kotlin
 * when (val result = refreshLibraryUseCase()) {
 *     is Success -> showSuccess(result.data.message)
 *     is Failure -> showError(result.message)
 * }
 * ```
 */
open class RefreshLibraryUseCase(
    private val syncRepository: SyncRepository,
) {
    /**
     * Observable sync state for progress monitoring.
     *
     * ViewModels can observe this to show sync progress in the UI.
     */
    val syncState: StateFlow<SyncState>
        get() = syncRepository.syncState

    /**
     * Execute library refresh.
     *
     * Triggers a full sync with the server. Progress can be observed
     * via [syncState].
     *
     * @return Result containing RefreshLibraryResult on success, or an error on failure
     */
    open suspend operator fun invoke(): Result<RefreshLibraryResult> {
        logger.info { "Starting library refresh" }

        return suspendRunCatching {
            when (val syncResult = syncRepository.sync()) {
                is Success -> {
                    logger.info { "Library refresh completed successfully" }
                    RefreshLibraryResult(
                        state = syncRepository.syncState.value,
                        message = "Library refreshed successfully",
                    )
                }

                is Failure -> {
                    logger.warn { "Library refresh failed: ${syncResult.message}" }
                    throw RefreshException(
                        message = mapErrorMessage(syncResult),
                        cause = syncResult.exception,
                    )
                }
            }
        }
    }

    /**
     * Handle library mismatch by resetting local data for new library.
     *
     * Called when the server's library has changed (e.g., server reinstalled).
     * User should confirm before calling this as it will clear all local data.
     *
     * @param newLibraryId The new library ID to sync with
     * @return Result containing RefreshLibraryResult on success, or an error on failure
     */
    open suspend fun resetForNewLibrary(newLibraryId: String): Result<RefreshLibraryResult> {
        logger.info { "Resetting for new library: $newLibraryId" }

        return suspendRunCatching {
            when (val syncResult = syncRepository.resetForNewLibrary(newLibraryId)) {
                is Success -> {
                    logger.info { "Library reset completed successfully" }
                    RefreshLibraryResult(
                        state = syncRepository.syncState.value,
                        message = "Library synced with new server",
                    )
                }

                is Failure -> {
                    logger.warn { "Library reset failed: ${syncResult.message}" }
                    throw RefreshException(
                        message = mapErrorMessage(syncResult),
                        cause = syncResult.exception,
                    )
                }
            }
        }
    }

    /**
     * Map technical errors to user-friendly messages.
     */
    private fun mapErrorMessage(failure: Failure): String {
        val exceptionMessage = failure.exception?.message
        return when {
            exceptionMessage?.contains("network", ignoreCase = true) == true -> {
                "Unable to connect to server. Check your network connection."
            }

            exceptionMessage?.contains("unauthorized", ignoreCase = true) == true -> {
                "Session expired. Please log in again."
            }

            exceptionMessage?.contains("timeout", ignoreCase = true) == true -> {
                "Server is not responding. Please try again later."
            }

            exceptionMessage?.contains("mismatch", ignoreCase = true) == true -> {
                "Server library has changed. Local data needs to be reset."
            }

            else -> {
                failure.message
            }
        }
    }
}

/**
 * Exception thrown when library refresh fails.
 */
class RefreshException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
