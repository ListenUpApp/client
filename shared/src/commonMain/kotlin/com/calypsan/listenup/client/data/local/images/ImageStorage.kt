package com.calypsan.listenup.client.data.local.images

import com.calypsan.listenup.client.data.local.db.BookId

/**
 * Platform-agnostic interface for storing and retrieving book cover images locally.
 * Implementations handle platform-specific file system operations.
 */
interface ImageStorage {
    /**
     * Save cover image data to local storage.
     *
     * @param bookId Unique identifier for the book
     * @param imageData Raw image bytes (JPEG format from server)
     * @return Result indicating success or failure
     */
    suspend fun saveCover(bookId: BookId, imageData: ByteArray): Result<Unit>

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

    /**
     * Clear all cover images from local storage.
     * Used for cleanup operations (e.g., logout, cache clear).
     *
     * @return Result with count of deleted files
     */
    suspend fun clearAll(): Result<Int>
}
