package com.calypsan.listenup.client.domain.usecase.lens

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.suspendRunCatching
import com.calypsan.listenup.client.domain.repository.LensRepository
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Removes a book from a lens.
 *
 * Only the lens owner can remove books from their lens.
 *
 * Usage:
 * ```kotlin
 * val result = removeBookFromLensUseCase(
 *     lensId = "lens-123",
 *     bookId = "book-456"
 * )
 * ```
 */
open class RemoveBookFromLensUseCase(
    private val lensRepository: LensRepository,
) {
    /**
     * Remove a book from a lens.
     *
     * @param lensId The lens to remove from
     * @param bookId The book to remove
     * @return Result indicating success or failure
     */
    open suspend operator fun invoke(
        lensId: String,
        bookId: String,
    ): Result<Unit> {
        logger.info { "Removing book $bookId from lens $lensId" }

        return suspendRunCatching {
            lensRepository.removeBookFromLens(lensId, bookId)
        }
    }
}
