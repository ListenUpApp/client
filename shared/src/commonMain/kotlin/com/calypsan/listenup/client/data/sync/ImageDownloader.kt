package com.calypsan.listenup.client.data.sync

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.images.ImageStorage
import com.calypsan.listenup.client.data.remote.ImageApi
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Orchestrates downloading and storing book cover images during sync.
 *
 * Responsibilities:
 * - Download covers from backend via ImageApi
 * - Save to local storage via ImageStorage
 * - Handle errors gracefully (missing covers are non-fatal)
 * - Support batch operations for efficient syncing
 *
 * @property imageApi API client for downloading cover images
 * @property imageStorage Local storage for cover images
 */
class ImageDownloader(
    private val imageApi: ImageApi,
    private val imageStorage: ImageStorage
) {

    /**
     * Download and save a single book cover.
     *
     * Non-fatal errors (404, network issues) are logged but don't throw.
     * Returns success=true if cover was downloaded and saved successfully.
     *
     * @param bookId Unique identifier for the book
     * @return Result indicating if cover was successfully downloaded and saved
     */
    suspend fun downloadCover(bookId: BookId): Result<Boolean> {
        // Skip if already exists locally
        if (imageStorage.exists(bookId)) {
            logger.debug { "Cover already exists locally for book ${bookId.value}" }
            return Result.Success(false)
        }

        // Download from server
        val downloadResult = imageApi.downloadCover(bookId)
        if (downloadResult is Result.Failure) {
            // 404 is expected for books without covers - don't log as error
            logger.debug { "Cover not available for book ${bookId.value}: ${downloadResult.exception.message}" }
            return Result.Success(false)
        }

        // Save to local storage
        val imageBytes = (downloadResult as Result.Success).data
        val saveResult = imageStorage.saveCover(bookId, imageBytes)

        if (saveResult is Result.Failure) {
            logger.warn(saveResult.exception) {
                "Failed to save cover for book ${bookId.value}"
            }
            return Result.Failure(saveResult.exception)
        }

        logger.debug { "Successfully downloaded and saved cover for book ${bookId.value}" }
        return Result.Success(true)
    }

    /**
     * Download covers for multiple books in batch.
     *
     * Continues on individual failures - one failed download doesn't stop the batch.
     * Returns count of successfully downloaded covers.
     *
     * @param bookIds List of book identifiers to download covers for
     * @return Result containing count of successfully downloaded covers
     */
    suspend fun downloadCovers(bookIds: List<BookId>): Result<Int> {
        var successCount = 0

        bookIds.forEach { bookId ->
            when (val result = downloadCover(bookId)) {
                is Result.Success -> {
                    if (result.data) {
                        successCount++
                    }
                }
                is Result.Failure -> {
                    // Log and continue - non-fatal
                    logger.warn(result.exception) {
                        "Failed to download cover for book ${bookId.value}"
                    }
                }
            }
        }

        logger.info { "Downloaded $successCount covers out of ${bookIds.size} books" }
        return Result.Success(successCount)
    }
}
