package com.calypsan.listenup.client.domain.usecase.collection

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.suspendRunCatching
import com.calypsan.listenup.client.core.validationError
import com.calypsan.listenup.client.domain.repository.CollectionRepository
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Adds books to an admin collection.
 *
 * Validates that at least one book is selected before adding.
 * Admin-only operation.
 *
 * Usage:
 * ```kotlin
 * val result = addBooksToCollectionUseCase(
 *     collectionId = "collection-123",
 *     bookIds = listOf("book-1", "book-2")
 * )
 * ```
 */
open class AddBooksToCollectionUseCase(
    private val collectionRepository: CollectionRepository,
) {
    /**
     * Add books to a collection.
     *
     * @param collectionId The collection to add books to
     * @param bookIds The books to add (must not be empty)
     * @return Result indicating success or failure
     */
    open suspend operator fun invoke(
        collectionId: String,
        bookIds: List<String>,
    ): Result<Unit> {
        if (bookIds.isEmpty()) {
            return validationError("At least one book must be selected")
        }

        logger.info { "Adding ${bookIds.size} books to collection $collectionId" }

        return suspendRunCatching {
            collectionRepository.addBooksToCollection(collectionId, bookIds)
        }
    }
}
