package com.calypsan.listenup.client.data.sync

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.images.ImageStorage
import com.calypsan.listenup.client.data.remote.ImageApiContract
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
    private val imageApi: ImageApiContract,
    private val imageStorage: ImageStorage,
) : ImageDownloaderContract {
    /**
     * Download and save a single book cover.
     *
     * Non-fatal errors (404, network issues) are logged but don't throw.
     * Returns success=true if cover was downloaded and saved successfully.
     *
     * @param bookId Unique identifier for the book
     * @return Result indicating if cover was successfully downloaded and saved
     */
    override suspend fun downloadCover(bookId: BookId): Result<Boolean> {
        // Skip if already exists locally
        if (imageStorage.exists(bookId)) {
            logger.info { "Cover already exists locally for book ${bookId.value}" }
            return Result.Success(false)
        }

        logger.info { "Downloading cover for book ${bookId.value}..." }

        // Download from server
        val downloadResult = imageApi.downloadCover(bookId)
        if (downloadResult is Result.Failure) {
            // 404 is expected for books without covers - don't log as error
            logger.info { "Cover not available for book ${bookId.value}: ${downloadResult.exception.message}" }
            return Result.Success(false)
        }

        // Save to local storage
        val imageBytes = (downloadResult as Result.Success).data
        logger.info { "Downloaded ${imageBytes.size} bytes for book ${bookId.value}, saving..." }

        val saveResult = imageStorage.saveCover(bookId, imageBytes)

        if (saveResult is Result.Failure) {
            logger.error(saveResult.exception) {
                "Failed to save cover for book ${bookId.value}"
            }
            return Result.Failure(saveResult.exception)
        }

        logger.info { "Successfully downloaded and saved cover for book ${bookId.value}" }
        return Result.Success(true)
    }

    /**
     * Download covers for multiple books in batch.
     *
     * Continues on individual failures - one failed download doesn't stop the batch.
     * Returns list of book IDs that had covers successfully downloaded.
     *
     * @param bookIds List of book identifiers to download covers for
     * @return Result containing list of BookIds that were successfully downloaded
     */
    override suspend fun downloadCovers(bookIds: List<BookId>): Result<List<BookId>> {
        val successfulDownloads = mutableListOf<BookId>()

        bookIds.forEach { bookId ->
            when (val result = downloadCover(bookId)) {
                is Result.Success -> {
                    if (result.data) {
                        successfulDownloads.add(bookId)
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

        logger.info { "Downloaded ${successfulDownloads.size} covers out of ${bookIds.size} books" }
        return Result.Success(successfulDownloads)
    }

    /**
     * Download and save a single contributor image.
     *
     * Non-fatal errors (404, network issues) are logged but don't throw.
     * Returns success=true if image was downloaded and saved successfully.
     *
     * @param contributorId Unique identifier for the contributor
     * @return Result indicating if image was successfully downloaded and saved
     */
    override suspend fun downloadContributorImage(contributorId: String): Result<Boolean> {
        // Skip if already exists locally
        if (imageStorage.contributorImageExists(contributorId)) {
            logger.info { "Image already exists locally for contributor $contributorId" }
            return Result.Success(false)
        }

        logger.info { "Downloading image for contributor $contributorId..." }

        // Download from server
        val downloadResult = imageApi.downloadContributorImage(contributorId)
        if (downloadResult is Result.Failure) {
            // 404 is expected for contributors without images - don't log as error
            logger.info { "Image not available for contributor $contributorId: ${downloadResult.exception.message}" }
            return Result.Success(false)
        }

        // Save to local storage
        val imageBytes = (downloadResult as Result.Success).data
        logger.info { "Downloaded ${imageBytes.size} bytes for contributor $contributorId, saving..." }

        val saveResult = imageStorage.saveContributorImage(contributorId, imageBytes)

        if (saveResult is Result.Failure) {
            logger.error(saveResult.exception) {
                "Failed to save image for contributor $contributorId"
            }
            return Result.Failure(saveResult.exception)
        }

        logger.info { "Successfully downloaded and saved image for contributor $contributorId" }
        return Result.Success(true)
    }

    /**
     * Download images for multiple contributors in batch.
     *
     * Continues on individual failures - one failed download doesn't stop the batch.
     * Returns list of contributor IDs that had images successfully downloaded.
     *
     * @param contributorIds List of contributor identifiers to download images for
     * @return Result containing list of contributor IDs that were successfully downloaded
     */
    override suspend fun downloadContributorImages(contributorIds: List<String>): Result<List<String>> {
        val successfulDownloads = mutableListOf<String>()

        contributorIds.forEach { contributorId ->
            when (val result = downloadContributorImage(contributorId)) {
                is Result.Success -> {
                    if (result.data) {
                        successfulDownloads.add(contributorId)
                    }
                }

                is Result.Failure -> {
                    // Log and continue - non-fatal
                    logger.warn(result.exception) {
                        "Failed to download image for contributor $contributorId"
                    }
                }
            }
        }

        logger.info { "Downloaded ${successfulDownloads.size} images out of ${contributorIds.size} contributors" }
        return Result.Success(successfulDownloads)
    }

    /**
     * Get the local file path for a contributor's image.
     *
     * @param contributorId Unique identifier for the contributor
     * @return Absolute file path where the image is stored, or null if image doesn't exist
     */
    override fun getContributorImagePath(contributorId: String): String? =
        if (imageStorage.contributorImageExists(contributorId)) {
            imageStorage.getContributorImagePath(contributorId)
        } else {
            null
        }

    /**
     * Download and save a single series cover.
     *
     * Non-fatal errors (404, network issues) are logged but don't throw.
     * Returns success=true if cover was downloaded and saved successfully.
     *
     * @param seriesId Unique identifier for the series
     * @return Result indicating if cover was successfully downloaded and saved
     */
    override suspend fun downloadSeriesCover(seriesId: String): Result<Boolean> {
        // Skip if already exists locally
        if (imageStorage.seriesCoverExists(seriesId)) {
            logger.info { "Cover already exists locally for series $seriesId" }
            return Result.Success(false)
        }

        logger.info { "Downloading cover for series $seriesId..." }

        // Download from server
        val downloadResult = imageApi.downloadSeriesCover(seriesId)
        if (downloadResult is Result.Failure) {
            // 404 is expected for series without covers - don't log as error
            logger.info { "Cover not available for series $seriesId: ${downloadResult.exception.message}" }
            return Result.Success(false)
        }

        // Save to local storage
        val imageBytes = (downloadResult as Result.Success).data
        logger.info { "Downloaded ${imageBytes.size} bytes for series $seriesId, saving..." }

        val saveResult = imageStorage.saveSeriesCover(seriesId, imageBytes)

        if (saveResult is Result.Failure) {
            logger.error(saveResult.exception) {
                "Failed to save cover for series $seriesId"
            }
            return Result.Failure(saveResult.exception)
        }

        logger.info { "Successfully downloaded and saved cover for series $seriesId" }
        return Result.Success(true)
    }

    /**
     * Download covers for multiple series in batch.
     *
     * Continues on individual failures - one failed download doesn't stop the batch.
     * Returns list of series IDs that had covers successfully downloaded.
     *
     * @param seriesIds List of series identifiers to download covers for
     * @return Result containing list of series IDs that were successfully downloaded
     */
    override suspend fun downloadSeriesCovers(seriesIds: List<String>): Result<List<String>> {
        val successfulDownloads = mutableListOf<String>()

        seriesIds.forEach { seriesId ->
            when (val result = downloadSeriesCover(seriesId)) {
                is Result.Success -> {
                    if (result.data) {
                        successfulDownloads.add(seriesId)
                    }
                }

                is Result.Failure -> {
                    // Log and continue - non-fatal
                    logger.warn(result.exception) {
                        "Failed to download cover for series $seriesId"
                    }
                }
            }
        }

        logger.info { "Downloaded ${successfulDownloads.size} covers out of ${seriesIds.size} series" }
        return Result.Success(successfulDownloads)
    }
}
