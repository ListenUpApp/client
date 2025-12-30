package com.calypsan.listenup.client.download

import com.calypsan.listenup.client.data.local.db.BookId

/**
 * Result of a download operation.
 */
sealed interface DownloadResult {
    data object Success : DownloadResult

    data object AlreadyDownloaded : DownloadResult

    data class InsufficientStorage(
        val requiredBytes: Long,
        val availableBytes: Long,
    ) : DownloadResult

    data class Error(
        val message: String,
    ) : DownloadResult
}

/**
 * Interface for download operations needed by PlaybackManager.
 *
 * This abstraction allows PlaybackManager to live in shared code while
 * the full download implementation remains platform-specific.
 *
 * Android: Implemented by DownloadManager (WorkManager-based)
 * iOS: Will use URLSession background downloads
 */
interface DownloadService {
    /**
     * Get local file path for an audio file if downloaded.
     * Returns null if not downloaded or file missing.
     */
    suspend fun getLocalPath(audioFileId: String): String?

    /**
     * Check if user explicitly deleted downloads for this book.
     * Used to determine if we should auto-download on playback.
     */
    suspend fun wasExplicitlyDeleted(bookId: BookId): Boolean

    /**
     * Trigger background download of a book's audio files.
     *
     * @return Result indicating success, failure reason, or if already downloaded
     */
    suspend fun downloadBook(bookId: BookId): DownloadResult

    /**
     * Cancel active download for a book.
     * Called when book access is revoked or user cancels download.
     */
    suspend fun cancelDownload(bookId: BookId)

    /**
     * Delete downloaded files for a book.
     * Called when book is deleted (access revoked) to clean up local storage.
     */
    suspend fun deleteDownload(bookId: BookId)
}
