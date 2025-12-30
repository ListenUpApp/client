package com.calypsan.listenup.client.data.sync.sse

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.data.local.db.BookContributorDao
import com.calypsan.listenup.client.data.local.db.BookDao
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.db.BookSeriesDao
import com.calypsan.listenup.client.data.local.db.CollectionDao
import com.calypsan.listenup.client.data.local.db.CollectionEntity
import com.calypsan.listenup.client.data.local.db.Timestamp
import com.calypsan.listenup.client.data.remote.model.BookResponse
import com.calypsan.listenup.client.data.remote.model.toEntity
import com.calypsan.listenup.client.data.sync.ImageDownloaderContract
import com.calypsan.listenup.client.data.sync.SSEEventType
import com.calypsan.listenup.client.download.DownloadService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * Processes real-time Server-Sent Events and applies changes to local database.
 *
 * Handles:
 * - BookCreated/BookUpdated: Upsert book and relationships
 * - BookDeleted: Remove book from database, stop playback if affected, cancel downloads
 * - ScanStarted/ScanCompleted: Log events
 * - Heartbeat: Connection keep-alive
 * - Collection events: Update local collection cache (admin-only)
 */
class SSEEventProcessor(
    private val bookDao: BookDao,
    private val bookContributorDao: BookContributorDao,
    private val bookSeriesDao: BookSeriesDao,
    private val collectionDao: CollectionDao,
    private val imageDownloader: ImageDownloaderContract,
    private val playbackStateProvider: PlaybackStateProvider,
    private val downloadService: DownloadService,
    private val scope: CoroutineScope,
) {
    private val _accessRevokedEvents = MutableSharedFlow<AccessRevokedEvent>(extraBufferCapacity = 16)

    /**
     * Flow of access revocation events for UI to display notifications.
     * Emitted when a book is deleted while being played or downloaded.
     */
    val accessRevokedEvents: SharedFlow<AccessRevokedEvent> = _accessRevokedEvents.asSharedFlow()
    /**
     * Process an incoming SSE event.
     */
    suspend fun process(event: SSEEventType) {
        try {
            when (event) {
                is SSEEventType.BookCreated -> {
                    handleBookCreated(event)
                }

                is SSEEventType.BookUpdated -> {
                    handleBookUpdated(event)
                }

                is SSEEventType.BookDeleted -> {
                    handleBookDeleted(event)
                }

                is SSEEventType.ScanStarted -> {
                    handleScanStarted(event)
                }

                is SSEEventType.ScanCompleted -> {
                    handleScanCompleted(event)
                }

                is SSEEventType.Heartbeat -> { /* Keep-alive, no action */ }

                is SSEEventType.UserPending,
                is SSEEventType.UserApproved,
                -> {
                    // Admin-only events, handled by AdminViewModel
                }

                is SSEEventType.CollectionCreated -> {
                    handleCollectionCreated(event)
                }

                is SSEEventType.CollectionUpdated -> {
                    handleCollectionUpdated(event)
                }

                is SSEEventType.CollectionDeleted -> {
                    handleCollectionDeleted(event)
                }

                is SSEEventType.CollectionBookAdded -> {
                    handleCollectionBookAdded(event)
                }

                is SSEEventType.CollectionBookRemoved -> {
                    handleCollectionBookRemoved(event)
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to process SSE event: $event" }
        }
    }

    private suspend fun handleBookCreated(event: SSEEventType.BookCreated) {
        logger.debug { "SSE: Book created - ${event.book.title}" }
        val entity = event.book.toEntity()
        bookDao.upsert(entity)

        saveBookContributors(event.book)
        saveBookSeries(event.book)

        scope.launch {
            downloadCoverForBook(event.book.id)
        }
    }

    private suspend fun handleBookUpdated(event: SSEEventType.BookUpdated) {
        logger.debug { "SSE: Book updated - ${event.book.title}" }
        val entity = event.book.toEntity()
        bookDao.upsert(entity)

        saveBookContributors(event.book)
        saveBookSeries(event.book)

        scope.launch {
            downloadCoverForBook(event.book.id)
        }
    }

    private suspend fun handleBookDeleted(event: SSEEventType.BookDeleted) {
        logger.debug { "SSE: Book deleted - ${event.bookId}" }
        val bookId = BookId(event.bookId)

        // Check if this book is currently playing and stop playback
        val isCurrentlyPlaying = playbackStateProvider.currentBookId.value == bookId
        if (isCurrentlyPlaying) {
            logger.info { "Book ${event.bookId} is currently playing, clearing playback" }
            playbackStateProvider.clearPlayback()
            _accessRevokedEvents.tryEmit(AccessRevokedEvent(bookId))
        }

        // Cancel any in-progress download for this book
        try {
            downloadService.cancelDownload(bookId)
            // Also delete any downloaded files
            downloadService.deleteDownload(bookId)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to cancel/delete download for book ${event.bookId}" }
        }

        // Remove from database
        bookDao.deleteById(bookId)
    }

    private fun handleScanStarted(event: SSEEventType.ScanStarted) {
        logger.debug { "SSE: Library scan started - ${event.libraryId}" }
    }

    private fun handleScanCompleted(event: SSEEventType.ScanCompleted) {
        logger.info {
            "SSE: Library scan completed - " +
                "Added: ${event.booksAdded}, " +
                "Updated: ${event.booksUpdated}, " +
                "Removed: ${event.booksRemoved}"
        }
    }

    /**
     * Save book-contributor relationships from an SSE event.
     * Replaces existing relationships for the book.
     */
    private suspend fun saveBookContributors(book: BookResponse) {
        val bookId = BookId(book.id)

        bookContributorDao.deleteContributorsForBook(bookId)

        val crossRefs =
            book.contributors.flatMap { contributor ->
                contributor.roles.map { role ->
                    contributor.toEntity(bookId, role)
                }
            }

        if (crossRefs.isNotEmpty()) {
            bookContributorDao.insertAll(crossRefs)
            logger.debug { "SSE: Saved ${crossRefs.size} contributor relationships for book ${book.id}" }
        }
    }

    /**
     * Save book-series relationships from an SSE event.
     * Replaces existing relationships for the book.
     */
    private suspend fun saveBookSeries(book: BookResponse) {
        val bookId = BookId(book.id)

        bookSeriesDao.deleteSeriesForBook(bookId)

        val crossRefs =
            book.seriesInfo.map { seriesInfo ->
                seriesInfo.toEntity(bookId)
            }

        if (crossRefs.isNotEmpty()) {
            bookSeriesDao.insertAll(crossRefs)
            logger.debug { "SSE: Saved ${crossRefs.size} series relationships for book ${book.id}" }
        }
    }

    /**
     * Download cover for a book and trigger UI refresh if successful.
     */
    private suspend fun downloadCoverForBook(bookId: String) {
        try {
            val result = imageDownloader.downloadCover(BookId(bookId))
            if (result is Result.Success && result.data) {
                try {
                    bookDao.touchUpdatedAt(BookId(bookId), Timestamp.now())
                    logger.debug { "Touched book $bookId to trigger UI refresh" }
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to touch updatedAt for book $bookId" }
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to download cover for book $bookId" }
        }
    }

    // ========== Collection Event Handlers ==========

    private suspend fun handleCollectionCreated(event: SSEEventType.CollectionCreated) {
        logger.debug { "SSE: Collection created - ${event.name} (${event.id})" }
        val now = Timestamp.now()
        collectionDao.upsert(
            CollectionEntity(
                id = event.id,
                name = event.name,
                bookCount = event.bookCount,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private suspend fun handleCollectionUpdated(event: SSEEventType.CollectionUpdated) {
        logger.debug { "SSE: Collection updated - ${event.name} (${event.id})" }
        // Get existing to preserve createdAt, or use now if not found
        val existing = collectionDao.getById(event.id)
        val createdAt = existing?.createdAt ?: Timestamp.now()
        collectionDao.upsert(
            CollectionEntity(
                id = event.id,
                name = event.name,
                bookCount = event.bookCount,
                createdAt = createdAt,
                updatedAt = Timestamp.now(),
            ),
        )
    }

    private suspend fun handleCollectionDeleted(event: SSEEventType.CollectionDeleted) {
        logger.debug { "SSE: Collection deleted - ${event.name} (${event.id})" }
        collectionDao.deleteById(event.id)
    }

    private suspend fun handleCollectionBookAdded(event: SSEEventType.CollectionBookAdded) {
        logger.debug { "SSE: Book ${event.bookId} added to collection ${event.collectionName}" }
        // Update the book count for the collection
        val existing = collectionDao.getById(event.collectionId)
        if (existing != null) {
            collectionDao.upsert(
                existing.copy(
                    bookCount = existing.bookCount + 1,
                    updatedAt = Timestamp.now(),
                ),
            )
        }
    }

    private suspend fun handleCollectionBookRemoved(event: SSEEventType.CollectionBookRemoved) {
        logger.debug { "SSE: Book ${event.bookId} removed from collection ${event.collectionName}" }
        // Update the book count for the collection
        val existing = collectionDao.getById(event.collectionId)
        if (existing != null) {
            collectionDao.upsert(
                existing.copy(
                    bookCount = (existing.bookCount - 1).coerceAtLeast(0),
                    updatedAt = Timestamp.now(),
                ),
            )
        }
    }
}

/**
 * Event emitted when access to a book is revoked while playing.
 * UI should display a notification that "This book is no longer available".
 */
data class AccessRevokedEvent(val bookId: BookId)

/**
 * Interface for playback state access needed by SSEEventProcessor.
 * Implemented by PlaybackManager to avoid direct dependency on final class.
 */
interface PlaybackStateProvider {
    /**
     * The currently playing book ID, or null if nothing is playing.
     */
    val currentBookId: StateFlow<BookId?>

    /**
     * Clear current playback state when access is revoked.
     */
    fun clearPlayback()
}
