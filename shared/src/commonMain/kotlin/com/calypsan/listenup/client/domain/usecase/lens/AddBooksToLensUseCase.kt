package com.calypsan.listenup.client.domain.usecase.lens

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.suspendRunCatching
import com.calypsan.listenup.client.core.validationError
import com.calypsan.listenup.client.domain.repository.LensRepository
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Adds books to a lens.
 *
 * Only the lens owner can add books to their lens.
 * The book IDs list must not be empty.
 *
 * Usage:
 * ```kotlin
 * val result = addBooksToLensUseCase(
 *     lensId = "lens-123",
 *     bookIds = listOf("book-1", "book-2")
 * )
 * ```
 */
open class AddBooksToLensUseCase(
    private val lensRepository: LensRepository,
) {
    /**
     * Add books to a lens.
     *
     * @param lensId The lens to add to
     * @param bookIds The books to add (must not be empty)
     * @return Result indicating success or failure
     */
    open suspend operator fun invoke(
        lensId: String,
        bookIds: List<String>,
    ): Result<Unit> {
        if (bookIds.isEmpty()) {
            return validationError("At least one book must be selected")
        }

        logger.info { "Adding ${bookIds.size} books to lens $lensId" }

        return suspendRunCatching {
            lensRepository.addBooksToLens(lensId, bookIds)
        }
    }
}
