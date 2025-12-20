package com.calypsan.listenup.client.data.sync

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.images.CoverColorExtractor
import com.calypsan.listenup.client.data.local.images.ImageStorage
import com.calypsan.listenup.client.data.remote.ImageApiContract
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

private const val BATCH_SIZE = 10

/**
 * Orchestrates downloading and storing book cover images during sync.
 *
 * Responsibilities:
 * - Download covers from backend via ImageApi
 * - Save to local storage via ImageStorage
 * - Extract color palette from covers for instant UI rendering
 * - Handle errors gracefully (missing covers are non-fatal)
 * - Support batch operations for efficient syncing
 *
 * @property imageApi API client for downloading cover images
 * @property imageStorage Local storage for cover images
 * @property colorExtractor Platform-specific color palette extractor
 */
class ImageDownloader(
    private val imageApi: ImageApiContract,
    private val imageStorage: ImageStorage,
    private val colorExtractor: CoverColorExtractor,
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
     * Download covers for multiple books using batch requests.
     *
     * Filters to only books missing covers locally, then downloads
     * in batches of BATCH_SIZE for efficiency. Extracts color palette
     * from each cover for caching in the database.
     *
     * Continues on individual failures - one failed download doesn't stop the batch.
     * Returns list of download results with extracted colors.
     *
     * @param bookIds List of book identifiers to download covers for
     * @return Result containing list of download results with extracted colors
     */
    override suspend fun downloadCovers(bookIds: List<BookId>): Result<List<CoverDownloadResult>> {
        // Filter to only books missing covers locally
        val needed = bookIds.filter { !imageStorage.exists(it) }

        if (needed.isEmpty()) {
            logger.debug { "All covers already cached" }
            return Result.Success(emptyList())
        }

        logger.debug { "Downloading ${needed.size} covers in batches of $BATCH_SIZE" }

        val downloadResults = buildList {
            // Download in batches
            needed.chunked(BATCH_SIZE).forEach { batch ->
                val bookIdStrings = batch.map { it.value }
                when (val result = imageApi.downloadCoverBatch(bookIdStrings)) {
                    is Result.Success -> {
                        result.data.forEach { (bookId, bytes) ->
                            val bookIdObj = BookId(bookId)
                            val saveResult = imageStorage.saveCover(bookIdObj, bytes)
                            if (saveResult is Result.Success) {
                                // Extract colors from the downloaded image
                                val colors = colorExtractor.extractColors(bytes)
                                add(CoverDownloadResult(bookIdObj, colors))
                                if (colors != null) {
                                    logger.debug { "Extracted colors for book $bookId" }
                                }
                            } else if (saveResult is Result.Failure) {
                                logger.warn(saveResult.exception) {
                                    "Failed to save cover for book $bookId"
                                }
                            }
                        }
                        logger.debug { "Downloaded batch of ${result.data.size} covers" }
                    }

                    is Result.Failure -> {
                        logger.warn { "Batch download failed: ${result.exception.message}" }
                    }
                }
            }
        }

        logger.info { "Downloaded ${downloadResults.size} covers out of ${bookIds.size} books" }
        return Result.Success(downloadResults)
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
     * Download images for multiple contributors using batch requests.
     *
     * Filters to only contributors missing images locally, then downloads
     * in batches of BATCH_SIZE for efficiency.
     *
     * Continues on individual failures - one failed download doesn't stop the batch.
     * Returns list of contributor IDs that had images successfully downloaded.
     *
     * @param contributorIds List of contributor identifiers to download images for
     * @return Result containing list of contributor IDs that were successfully downloaded
     */
    override suspend fun downloadContributorImages(contributorIds: List<String>): Result<List<String>> {
        // Filter to only contributors missing images locally
        val needed = contributorIds.filter { !imageStorage.contributorImageExists(it) }

        if (needed.isEmpty()) {
            logger.debug { "All contributor images already cached" }
            return Result.Success(emptyList())
        }

        logger.debug { "Downloading ${needed.size} contributor images in batches of $BATCH_SIZE" }

        val successfulDownloads = buildList {
            // Download in batches
            needed.chunked(BATCH_SIZE).forEach { batch ->
                when (val result = imageApi.downloadContributorImageBatch(batch)) {
                    is Result.Success -> {
                        result.data.forEach { (contributorId, bytes) ->
                            val saveResult = imageStorage.saveContributorImage(contributorId, bytes)
                            if (saveResult is Result.Success) {
                                add(contributorId)
                            } else if (saveResult is Result.Failure) {
                                logger.warn(saveResult.exception) {
                                    "Failed to save image for contributor $contributorId"
                                }
                            }
                        }
                        logger.debug { "Downloaded batch of ${result.data.size} contributor images" }
                    }

                    is Result.Failure -> {
                        logger.warn { "Batch download failed: ${result.exception.message}" }
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
        val successfulDownloads = buildList {
            seriesIds.forEach { seriesId ->
                when (val result = downloadSeriesCover(seriesId)) {
                    is Result.Success -> {
                        if (result.data) {
                            add(seriesId)
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
        }

        logger.info { "Downloaded ${successfulDownloads.size} covers out of ${seriesIds.size} series" }
        return Result.Success(successfulDownloads)
    }
}
