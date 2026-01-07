package com.calypsan.listenup.client.domain.usecase.lens

import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.suspendRunCatching
import com.calypsan.listenup.client.domain.model.LensBook
import com.calypsan.listenup.client.domain.model.LensDetail
import com.calypsan.listenup.client.domain.repository.ImageRepository
import com.calypsan.listenup.client.domain.repository.LensRepository
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Loads full lens detail including books from the server.
 *
 * Fetches lens details via the repository and resolves local cover paths
 * for books that have cached images.
 *
 * Usage:
 * ```kotlin
 * val result = loadLensDetailUseCase(lensId = "lens-123")
 * ```
 */
open class LoadLensDetailUseCase(
    private val lensRepository: LensRepository,
    private val imageRepository: ImageRepository,
) {
    /**
     * Load lens detail with resolved cover paths.
     *
     * @param lensId The lens ID to load
     * @return Result containing the lens detail or a failure
     */
    open suspend operator fun invoke(lensId: String): Result<LensDetail> {
        logger.debug { "Loading lens detail: $lensId" }

        return suspendRunCatching {
            val lensDetail = lensRepository.getLensDetail(lensId)

            // Resolve local cover paths for books
            val booksWithLocalCovers = lensDetail.books.map { book ->
                val bookId = BookId(book.id)
                val localCoverPath = if (imageRepository.bookCoverExists(bookId)) {
                    imageRepository.getBookCoverPath(bookId)
                } else {
                    null
                }
                book.copy(coverPath = localCoverPath)
            }

            lensDetail.copy(books = booksWithLocalCovers)
        }
    }
}
