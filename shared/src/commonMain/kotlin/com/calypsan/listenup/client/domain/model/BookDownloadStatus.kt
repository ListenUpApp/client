package com.calypsan.listenup.client.domain.model

/**
 * Aggregated download status for a book.
 *
 * Domain model representing the current download state of a book's audio files.
 * Used by ViewModels to display download progress and state in the UI.
 */
data class BookDownloadStatus(
    val bookId: String,
    val state: BookDownloadState,
    val totalFiles: Int,
    val completedFiles: Int,
    val totalBytes: Long,
    val downloadedBytes: Long,
) {
    val progress: Float
        get() = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f

    val isFullyDownloaded: Boolean
        get() = state == BookDownloadState.COMPLETED

    val isDownloading: Boolean
        get() = state == BookDownloadState.DOWNLOADING || state == BookDownloadState.QUEUED

    companion object {
        fun notDownloaded(bookId: String) =
            BookDownloadStatus(
                bookId = bookId,
                state = BookDownloadState.NOT_DOWNLOADED,
                totalFiles = 0,
                completedFiles = 0,
                totalBytes = 0,
                downloadedBytes = 0,
            )
    }
}

/**
 * Download state enumeration for a book.
 */
enum class BookDownloadState {
    NOT_DOWNLOADED,
    QUEUED,
    DOWNLOADING,
    PARTIAL,
    COMPLETED,
    FAILED,
}
