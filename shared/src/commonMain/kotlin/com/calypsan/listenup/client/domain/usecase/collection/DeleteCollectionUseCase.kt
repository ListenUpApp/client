package com.calypsan.listenup.client.domain.usecase.collection

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.suspendRunCatching
import com.calypsan.listenup.client.domain.repository.CollectionRepository
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Deletes a collection by ID.
 *
 * Delegates to the repository which deletes on server first, then removes
 * from local storage for immediate UI feedback. If the server call fails,
 * local data is not modified to keep client and server in sync.
 *
 * Usage:
 * ```kotlin
 * val result = deleteCollectionUseCase(collectionId = "collection-123")
 * when (result) {
 *     is Success -> navigateBack()
 *     is Failure -> showError(result.message)
 * }
 * ```
 */
open class DeleteCollectionUseCase(
    private val collectionRepository: CollectionRepository,
) {
    /**
     * Delete a collection.
     *
     * @param collectionId The ID of the collection to delete
     * @return Result indicating success or failure
     */
    open suspend operator fun invoke(collectionId: String): Result<Unit> {
        logger.info { "Deleting collection $collectionId" }

        return suspendRunCatching {
            collectionRepository.delete(collectionId)
        }
    }
}
