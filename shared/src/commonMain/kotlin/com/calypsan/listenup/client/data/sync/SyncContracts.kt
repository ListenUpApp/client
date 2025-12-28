package com.calypsan.listenup.client.data.sync

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.images.ExtractedColors
import kotlinx.coroutines.flow.SharedFlow

/**
 * Result of downloading a book cover, including extracted palette colors.
 *
 * @property bookId The book whose cover was downloaded
 * @property colors Extracted color palette, or null if extraction failed/unsupported
 */
data class CoverDownloadResult(
    val bookId: BookId,
    val colors: ExtractedColors?,
)

/**
 * Contract interface for image downloading operations.
 *
 * Extracted to enable mocking in tests. Production implementation
 * is [ImageDownloader], test implementation can be a mock or fake.
 */
interface ImageDownloaderContract {
    /**
     * Delete a book's cover from local storage.
     *
     * Used when the server's cover has changed and the local cached
     * version needs to be invalidated before downloading the new one.
     *
     * @param bookId Unique identifier for the book
     * @return Result indicating success or failure
     */
    suspend fun deleteCover(bookId: BookId): Result<Unit>

    /**
     * Download and save a single book cover.
     *
     * @param bookId Unique identifier for the book
     * @return Result indicating if cover was successfully downloaded (true) or already existed/unavailable (false)
     */
    suspend fun downloadCover(bookId: BookId): Result<Boolean>

    /**
     * Download covers for multiple books in batch.
     *
     * Extracts color palette from each downloaded cover for caching.
     *
     * @param bookIds List of book identifiers to download covers for
     * @return Result containing list of download results with extracted colors
     */
    suspend fun downloadCovers(bookIds: List<BookId>): Result<List<CoverDownloadResult>>

    /**
     * Download and save a single contributor image.
     *
     * @param contributorId Unique identifier for the contributor
     * @return Result indicating if image was successfully downloaded (true) or already existed/unavailable (false)
     */
    suspend fun downloadContributorImage(contributorId: String): Result<Boolean>

    /**
     * Download images for multiple contributors in batch.
     *
     * @param contributorIds List of contributor identifiers to download images for
     * @return Result containing list of contributor IDs that were successfully downloaded
     */
    suspend fun downloadContributorImages(contributorIds: List<String>): Result<List<String>>

    /**
     * Get the local file path for a contributor's image.
     *
     * @param contributorId Unique identifier for the contributor
     * @return Absolute file path where the image is stored, or null if not available
     */
    fun getContributorImagePath(contributorId: String): String?

    /**
     * Download and save a single series cover.
     *
     * @param seriesId Unique identifier for the series
     * @return Result indicating if cover was successfully downloaded (true) or already existed/unavailable (false)
     */
    suspend fun downloadSeriesCover(seriesId: String): Result<Boolean>

    /**
     * Download covers for multiple series in batch.
     *
     * @param seriesIds List of series identifiers to download covers for
     * @return Result containing list of series IDs that were successfully downloaded
     */
    suspend fun downloadSeriesCovers(seriesIds: List<String>): Result<List<String>>
}

/**
 * Contract interface for SSE (Server-Sent Events) management.
 *
 * Extracted to enable mocking in tests. Production implementation
 * is [SSEManager], test implementation can be a mock or fake.
 */
interface SSEManagerContract {
    /**
     * Flow of SSE events for real-time library updates.
     */
    val eventFlow: SharedFlow<SSEEventType>

    /**
     * Connect to SSE stream and begin emitting events.
     * Safe to call multiple times - will not create duplicate connections.
     */
    fun connect()

    /**
     * Disconnect from SSE stream and stop emitting events.
     */
    fun disconnect()
}

/**
 * Contract interface for FTS (Full-Text Search) population.
 *
 * Extracted to enable mocking in tests. Production implementation
 * is [FtsPopulator], test implementation can be a mock or fake.
 */
interface FtsPopulatorContract {
    /**
     * Rebuild all FTS tables from main tables.
     *
     * This is a full rebuild that clears and repopulates all FTS tables.
     * Call after sync operations complete to ensure search is up-to-date.
     */
    suspend fun rebuildAll()
}
