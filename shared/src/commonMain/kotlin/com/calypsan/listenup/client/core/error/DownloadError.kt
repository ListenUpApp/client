package com.calypsan.listenup.client.core.error

/**
 * Domain errors for audiobook download operations.
 *
 * Downloads are user-initiated and can fail for various reasons
 * (network, storage, server). These need to surface because the user
 * is waiting for content to be available offline.
 */
sealed interface DownloadError : AppError {
    /**
     * Download of an audiobook failed.
     *
     * @property bookTitle Title of the book for the error message, if available
     */
    data class DownloadFailed(
        val bookTitle: String? = null,
        override val debugInfo: String? = null,
    ) : DownloadError {
        override val message =
            bookTitle?.let { "Failed to download \"$it\"." }
                ?: "Download failed."
        override val code = "DOWNLOAD_FAILED"
        override val isRetryable = true
    }

    /**
     * Not enough storage space to complete download.
     */
    data class InsufficientStorage(
        val bookTitle: String? = null,
        override val debugInfo: String? = null,
    ) : DownloadError {
        override val message =
            bookTitle?.let { "Not enough space to download \"$it\"." }
                ?: "Not enough storage space."
        override val code = "DOWNLOAD_INSUFFICIENT_STORAGE"
        override val isRetryable = false
    }
}
