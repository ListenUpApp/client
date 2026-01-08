package com.calypsan.listenup.client.domain.repository

import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.core.Result

/**
 * Domain repository for image operations.
 *
 * Provides a unified interface for image download, upload, and local storage
 * operations. Use cases depend on this interface; implementations live in
 * the data layer.
 *
 * This abstracts:
 * - ImageDownloaderContract (download operations)
 * - ImageStorage (local file operations)
 * - ImageApiContract (upload operations)
 *
 * Into a single domain-level contract.
 */
interface ImageRepository {
    // ========== Book Cover Operations ==========

    /**
     * Delete a book's cover from local storage.
     *
     * Used when the server's cover has changed and the local cached
     * version needs to be invalidated before downloading the new one.
     *
     * @param bookId Unique identifier for the book
     * @return Result indicating success or failure
     */
    suspend fun deleteBookCover(bookId: BookId): Result<Unit>

    /**
     * Download and save a single book cover from the server.
     *
     * @param bookId Unique identifier for the book
     * @return Result indicating if cover was downloaded (true) or already existed/unavailable (false)
     */
    suspend fun downloadBookCover(bookId: BookId): Result<Boolean>

    /**
     * Save book cover to staging location for preview.
     *
     * Used during book editing to preview cover changes before committing.
     *
     * @param bookId Unique identifier for the book
     * @param imageData Raw image bytes
     * @return Result indicating success or failure
     */
    suspend fun saveBookCoverStaging(
        bookId: BookId,
        imageData: ByteArray,
    ): Result<Unit>

    /**
     * Get the local file path for a book's staging cover.
     *
     * @param bookId Unique identifier for the book
     * @return Absolute file path where the staging cover is stored
     */
    fun getBookCoverStagingPath(bookId: BookId): String

    /**
     * Delete staging cover file.
     *
     * Used when canceling edits or cleaning up.
     *
     * @param bookId Unique identifier for the book
     * @return Result indicating success or failure
     */
    suspend fun deleteBookCoverStaging(bookId: BookId): Result<Unit>

    /**
     * Commit staged book cover to the main location.
     *
     * Used when saving book edits - moves the staged cover to the main cover path.
     *
     * @param bookId Unique identifier for the book
     * @return Result indicating success or failure
     */
    suspend fun commitBookCoverStaging(bookId: BookId): Result<Unit>

    /**
     * Upload book cover to the server.
     *
     * @param bookId Unique identifier for the book
     * @param imageData Raw image bytes
     * @param filename Original filename
     * @return Result containing the server image URL or error
     */
    suspend fun uploadBookCover(
        bookId: String,
        imageData: ByteArray,
        filename: String,
    ): Result<String>

    // ========== Series Cover Operations ==========

    /**
     * Save series cover to staging location for preview.
     *
     * Used during series editing to preview cover changes before committing.
     *
     * @param seriesId Unique identifier for the series
     * @param imageData Raw image bytes
     * @return Result indicating success or failure
     */
    suspend fun saveSeriesCoverStaging(
        seriesId: String,
        imageData: ByteArray,
    ): Result<Unit>

    /**
     * Get the local file path for a series's staging cover.
     *
     * @param seriesId Unique identifier for the series
     * @return Absolute file path where the staging cover is stored
     */
    fun getSeriesCoverStagingPath(seriesId: String): String

    /**
     * Delete series staging cover file.
     *
     * Used when canceling edits or cleaning up.
     *
     * @param seriesId Unique identifier for the series
     * @return Result indicating success or failure
     */
    suspend fun deleteSeriesCoverStaging(seriesId: String): Result<Unit>

    /**
     * Commit staged series cover to the main location.
     *
     * @param seriesId Unique identifier for the series
     * @return Result indicating success or failure
     */
    suspend fun commitSeriesCoverStaging(seriesId: String): Result<Unit>

    /**
     * Upload series cover to the server.
     *
     * @param seriesId Unique identifier for the series
     * @param imageData Raw image bytes
     * @param filename Original filename
     * @return Result containing the server image URL or error
     */
    suspend fun uploadSeriesCover(
        seriesId: String,
        imageData: ByteArray,
        filename: String,
    ): Result<String>

    // ========== Contributor Image Operations ==========

    /**
     * Download contributor image from the server and save locally.
     *
     * @param contributorId Unique identifier for the contributor
     * @return Result containing the image bytes or error
     */
    suspend fun downloadContributorImage(contributorId: String): Result<ByteArray>

    /**
     * Save contributor image to local storage.
     *
     * @param contributorId Unique identifier for the contributor
     * @param imageData Raw image bytes
     * @return Result indicating success or failure
     */
    suspend fun saveContributorImage(
        contributorId: String,
        imageData: ByteArray,
    ): Result<Unit>

    /**
     * Get the local file path for a contributor's image.
     *
     * @param contributorId Unique identifier for the contributor
     * @return Absolute file path where the image is stored
     */
    fun getContributorImagePath(contributorId: String): String

    /**
     * Upload contributor image to the server.
     *
     * @param contributorId Unique identifier for the contributor
     * @param imageData Raw image bytes
     * @param filename Original filename
     * @return Result containing the server image URL or error
     */
    suspend fun uploadContributorImage(
        contributorId: String,
        imageData: ByteArray,
        filename: String,
    ): Result<String>

    // ========== Book Cover Path Operations ==========

    /**
     * Check if a book's cover exists locally.
     *
     * @param bookId Unique identifier for the book
     * @return true if cover exists on disk, false otherwise
     */
    fun bookCoverExists(bookId: BookId): Boolean

    /**
     * Get the local file path for a book's cover.
     *
     * @param bookId Unique identifier for the book
     * @return Absolute file path where the cover is stored
     */
    fun getBookCoverPath(bookId: BookId): String

    // ========== Series Cover Path Operations ==========

    /**
     * Check if a series cover exists locally.
     *
     * @param seriesId Unique identifier for the series
     * @return true if cover exists on disk, false otherwise
     */
    fun seriesCoverExists(seriesId: String): Boolean

    /**
     * Get the local file path for a series cover.
     *
     * @param seriesId Unique identifier for the series
     * @return Absolute file path where the cover is stored
     */
    fun getSeriesCoverPath(seriesId: String): String

    // ========== User Avatar Operations ==========

    /**
     * Check if a user's avatar exists locally.
     *
     * @param userId Unique identifier for the user
     * @return true if avatar exists on disk, false otherwise
     */
    fun userAvatarExists(userId: String): Boolean

    /**
     * Get the local file path for a user's avatar.
     *
     * @param userId Unique identifier for the user
     * @return Absolute file path where the avatar is stored
     */
    fun getUserAvatarPath(userId: String): String

    /**
     * Download and save a user's avatar from the server.
     *
     * @param userId Unique identifier for the user
     * @param forceRefresh If true, re-downloads even if avatar exists locally
     * @return Result indicating if avatar was downloaded (true) or already existed/unavailable (false)
     */
    suspend fun downloadUserAvatar(
        userId: String,
        forceRefresh: Boolean = false,
    ): Result<Boolean>
}
