package com.calypsan.listenup.client.data.sync

import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.core.AppResult
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
    suspend fun deleteCover(bookId: BookId): AppResult<Unit>

    /**
     * Download and save a single book cover.
     *
     * @param bookId Unique identifier for the book
     * @return Result indicating if cover was successfully downloaded (true) or already existed/unavailable (false)
     */
    suspend fun downloadCover(bookId: BookId): AppResult<Boolean>

    /**
     * Download and save a single contributor image.
     *
     * @param contributorId Unique identifier for the contributor
     * @return Result indicating if image was successfully downloaded (true) or already existed/unavailable (false)
     */
    suspend fun downloadContributorImage(contributorId: String): AppResult<Boolean>

    /**
     * Download images for multiple contributors in batch.
     *
     * @param contributorIds List of contributor identifiers to download images for
     * @return Result containing list of contributor IDs that were successfully downloaded
     */
    suspend fun downloadContributorImages(contributorIds: List<String>): AppResult<List<String>>

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
    suspend fun downloadSeriesCover(seriesId: String): AppResult<Boolean>

    /**
     * Download covers for multiple series in batch.
     *
     * @param seriesIds List of series identifiers to download covers for
     * @return Result containing list of series IDs that were successfully downloaded
     */
    suspend fun downloadSeriesCovers(seriesIds: List<String>): AppResult<List<String>>

    /**
     * Download and save a user's avatar image.
     *
     * @param userId Unique identifier for the user
     * @param forceRefresh If true, re-downloads even if avatar exists locally
     * @return Result indicating if avatar was successfully downloaded (true) or already existed/unavailable (false)
     */
    suspend fun downloadUserAvatar(
        userId: String,
        forceRefresh: Boolean = false,
    ): AppResult<Boolean>

    /**
     * Get the local file path for a user's avatar image.
     *
     * @param userId Unique identifier for the user
     * @return Absolute file path where the avatar is stored, or null if not available
     */
    fun getUserAvatarPath(userId: String): String?

    /**
     * Delete a user's avatar from local storage.
     *
     * @param userId Unique identifier for the user
     * @return Result indicating success or failure
     */
    suspend fun deleteUserAvatar(userId: String): AppResult<Unit>
}

/**
 * Contract interface for SSE (Server-Sent Events) management.
 *
 * Extracted to enable mocking in tests. Production implementation
 * is [SSEManager], test implementation can be a mock or fake.
 */
interface SSEManagerContract {
    /**
     * Flow of SSE messages for real-time library updates.
     *
     * Emits [SSEChannelMessage.Wire] for decoded wire events and
     * [SSEChannelMessage.Reconnected] for synthetic reconnection signals.
     */
    val eventFlow: SharedFlow<SSEChannelMessage>

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
