package com.calypsan.listenup.client.presentation.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.data.local.db.BookDao
import com.calypsan.listenup.client.data.local.db.DownloadDao
import com.calypsan.listenup.client.data.local.db.DownloadState
import com.calypsan.listenup.client.download.DownloadFileManager
import com.calypsan.listenup.client.download.DownloadService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * Represents a downloaded book for display in the storage screen.
 */
data class DownloadedBook(
    val bookId: String,
    val title: String,
    val authorName: String?,
    val coverBlurHash: String?,
    val sizeBytes: Long,
    val fileCount: Int,
)

/**
 * UI state for the Storage screen.
 */
data class StorageUiState(
    val isLoading: Boolean = true,
    val totalStorageUsed: Long = 0,
    val availableStorage: Long = 0,
    val downloadedBooks: List<DownloadedBook> = emptyList(),
    val deleteConfirmation: DeleteConfirmation? = null,
    val isDeleting: Boolean = false,
)

/**
 * State for delete confirmation dialogs.
 */
sealed interface DeleteConfirmation {
    data class SingleBook(
        val book: DownloadedBook,
    ) : DeleteConfirmation

    data object AllDownloads : DeleteConfirmation
}

/**
 * ViewModel for the Storage management screen.
 *
 * Displays downloaded books and their storage usage.
 * Allows deleting individual downloads or clearing all.
 */
class StorageViewModel(
    private val downloadDao: DownloadDao,
    private val bookDao: BookDao,
    private val downloadService: DownloadService,
    private val downloadFileManager: DownloadFileManager,
) : ViewModel() {
    private val internalState = MutableStateFlow(StorageUiState())

    val state: StateFlow<StorageUiState> =
        combine(
            internalState,
            downloadDao.observeAll(),
        ) { internal, downloads ->
            // Group downloads by book, filter to completed only
            val completedByBook =
                downloads
                    .filter { it.state == DownloadState.COMPLETED }
                    .groupBy { it.bookId }

            // Build list of downloaded books with metadata
            val downloadedBooks =
                completedByBook
                    .mapNotNull { (bookId, files) ->
                        bookDao.getById(BookId(bookId))?.let { book ->
                            // Get primary author from the relation
                            val bookWithContributors = bookDao.getByIdWithContributors(BookId(bookId))
                            val authorName =
                                bookWithContributors?.let { bwc ->
                                    // Find the author role from contributorRoles, then get the contributor
                                    val authorRole = bwc.contributorRoles.firstOrNull { it.role == "author" }
                                    authorRole?.let { role ->
                                        // Use creditedAs if available, otherwise find contributor by ID
                                        role.creditedAs ?: bwc.contributors.find { it.id == role.contributorId }?.name
                                    }
                                }

                            DownloadedBook(
                                bookId = bookId,
                                title = book.title,
                                authorName = authorName,
                                coverBlurHash = book.coverBlurHash,
                                sizeBytes = files.sumOf { it.downloadedBytes },
                                fileCount = files.size,
                            )
                        }
                    }.sortedByDescending { it.sizeBytes }

            val totalUsed = downloadFileManager.calculateStorageUsed()
            val available = downloadFileManager.getAvailableSpace()

            internal.copy(
                isLoading = false,
                totalStorageUsed = totalUsed,
                availableStorage = available,
                downloadedBooks = downloadedBooks,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = StorageUiState(),
        )

    /**
     * Show confirmation dialog for deleting a single book.
     */
    fun confirmDeleteBook(book: DownloadedBook) {
        internalState.update {
            it.copy(deleteConfirmation = DeleteConfirmation.SingleBook(book))
        }
    }

    /**
     * Show confirmation dialog for clearing all downloads.
     */
    fun confirmClearAll() {
        internalState.update {
            it.copy(deleteConfirmation = DeleteConfirmation.AllDownloads)
        }
    }

    /**
     * Cancel the delete confirmation dialog.
     */
    fun cancelDelete() {
        internalState.update {
            it.copy(deleteConfirmation = null)
        }
    }

    /**
     * Execute the confirmed delete action.
     */
    fun executeDelete() {
        val confirmation = internalState.value.deleteConfirmation ?: return

        viewModelScope.launch {
            internalState.update { it.copy(isDeleting = true, deleteConfirmation = null) }

            try {
                when (confirmation) {
                    is DeleteConfirmation.SingleBook -> {
                        logger.info { "Deleting download: ${confirmation.book.title}" }
                        downloadService.deleteDownload(BookId(confirmation.book.bookId))
                    }

                    is DeleteConfirmation.AllDownloads -> {
                        logger.info { "Clearing all downloads" }
                        // Delete each book individually to properly track deletion state
                        state.value.downloadedBooks.forEach { book ->
                            downloadService.deleteDownload(BookId(book.bookId))
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to delete download(s)" }
            } finally {
                internalState.update { it.copy(isDeleting = false) }
            }
        }
    }
}
