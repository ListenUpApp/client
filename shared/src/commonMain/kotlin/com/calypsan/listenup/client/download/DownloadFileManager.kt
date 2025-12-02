package com.calypsan.listenup.client.download

import kotlinx.io.files.Path

/**
 * Cross-platform file manager for downloaded audiobooks.
 *
 * Storage structure:
 * {appFilesDir}/audiobooks/{bookId}/{audioFileId}_{filename}
 *
 * Platform implementations:
 * - Android: Uses Context.filesDir
 * - iOS: Uses NSFileManager documentDirectory
 */
expect class DownloadFileManager {

    /**
     * Get the final destination path for a downloaded file.
     */
    fun getDownloadPath(bookId: String, audioFileId: String, filename: String): Path

    /**
     * Get temp path for in-progress download (supports resume).
     */
    fun getTempPath(bookId: String, audioFileId: String, filename: String): Path

    /**
     * Delete all downloaded files for a book.
     */
    fun deleteBookFiles(bookId: String)

    /**
     * Delete all downloaded files.
     */
    fun deleteAllFiles()

    /**
     * Calculate total storage used by downloads in bytes.
     */
    fun calculateStorageUsed(): Long

    /**
     * Check if a file exists at path.
     */
    fun fileExists(path: String): Boolean

    /**
     * Get file size at path, or 0 if doesn't exist.
     */
    fun getFileSize(path: String): Long

    /**
     * Move a file from source to destination (atomic if possible).
     * Used to finalize downloads from temp to permanent location.
     */
    fun moveFile(source: Path, destination: Path): Boolean

    /**
     * Get available storage space in bytes.
     * Used to check before starting downloads.
     */
    fun getAvailableSpace(): Long
}
