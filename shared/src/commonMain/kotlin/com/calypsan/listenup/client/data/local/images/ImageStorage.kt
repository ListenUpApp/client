package com.calypsan.listenup.client.data.local.images

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.data.local.db.BookId

/**
 * Platform-agnostic interface for storing and retrieving images locally.
 * Implementations handle platform-specific file system operations.
 *
 * Supports:
 * - Book cover images (stored in covers/)
 * - Contributor profile images (stored in contributors/)
 */
interface ImageStorage {
    // ========== Book Cover Methods ==========

    /**
     * Save cover image data to local storage.
     *
     * @param bookId Unique identifier for the book
     * @param imageData Raw image bytes (JPEG format from server)
     * @return Result indicating success or failure
     */
    suspend fun saveCover(
        bookId: BookId,
        imageData: ByteArray,
    ): Result<Unit>

    /**
     * Get the local file path for a book's cover image.
     * Does not verify if the file exists.
     *
     * @param bookId Unique identifier for the book
     * @return Absolute file path where the cover is/would be stored
     */
    fun getCoverPath(bookId: BookId): String

    /**
     * Check if a cover image exists locally.
     *
     * @param bookId Unique identifier for the book
     * @return true if cover exists on disk, false otherwise
     */
    fun exists(bookId: BookId): Boolean

    /**
     * Delete a cover image from local storage.
     * No-op if the cover doesn't exist.
     *
     * @param bookId Unique identifier for the book
     * @return Result indicating success or failure
     */
    suspend fun deleteCover(bookId: BookId): Result<Unit>

    // ========== Book Cover Staging Methods ==========
    // Used for edit screens to preview changes before committing

    /**
     * Save cover image to a staging location for preview.
     * Does not affect the main cover file until [commitCoverStaging] is called.
     *
     * @param bookId Unique identifier for the book
     * @param imageData Raw image bytes
     * @return Result indicating success or failure
     */
    suspend fun saveCoverStaging(
        bookId: BookId,
        imageData: ByteArray,
    ): Result<Unit>

    /**
     * Get the local file path for a book's staging cover image.
     *
     * @param bookId Unique identifier for the book
     * @return Absolute file path where the staging cover is/would be stored
     */
    fun getCoverStagingPath(bookId: BookId): String

    /**
     * Move staging cover to the main cover location.
     * Call this when the user saves their changes.
     *
     * @param bookId Unique identifier for the book
     * @return Result indicating success or failure
     */
    suspend fun commitCoverStaging(bookId: BookId): Result<Unit>

    /**
     * Delete staging cover file.
     * Call this when the user cancels their changes.
     *
     * @param bookId Unique identifier for the book
     * @return Result indicating success or failure
     */
    suspend fun deleteCoverStaging(bookId: BookId): Result<Unit>

    /**
     * Clear all cover images from local storage.
     * Used for cleanup operations (e.g., logout, cache clear).
     *
     * @return Result with count of deleted files
     */
    suspend fun clearAll(): Result<Int>

    // ========== Contributor Image Methods ==========

    /**
     * Save contributor profile image to local storage.
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
     * Get the local file path for a contributor's profile image.
     * Does not verify if the file exists.
     *
     * @param contributorId Unique identifier for the contributor
     * @return Absolute file path where the image is/would be stored
     */
    fun getContributorImagePath(contributorId: String): String

    /**
     * Check if a contributor image exists locally.
     *
     * @param contributorId Unique identifier for the contributor
     * @return true if image exists on disk, false otherwise
     */
    fun contributorImageExists(contributorId: String): Boolean

    /**
     * Delete a contributor image from local storage.
     * No-op if the image doesn't exist.
     *
     * @param contributorId Unique identifier for the contributor
     * @return Result indicating success or failure
     */
    suspend fun deleteContributorImage(contributorId: String): Result<Unit>
}
