package com.calypsan.listenup.client.data.sync.sse

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.data.local.db.BookContributorDao
import com.calypsan.listenup.client.data.local.db.BookDao
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.db.BookSeriesDao
import com.calypsan.listenup.client.data.local.db.Timestamp
import com.calypsan.listenup.client.data.remote.model.BookResponse
import com.calypsan.listenup.client.data.remote.model.toEntity
import com.calypsan.listenup.client.data.sync.ImageDownloaderContract
import com.calypsan.listenup.client.data.sync.SSEEventType
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * Processes real-time Server-Sent Events and applies changes to local database.
 *
 * Handles:
 * - BookCreated/BookUpdated: Upsert book and relationships
 * - BookDeleted: Remove book from database
 * - ScanStarted/ScanCompleted: Log events
 * - Heartbeat: Connection keep-alive
 */
class SSEEventProcessor(
    private val bookDao: BookDao,
    private val bookContributorDao: BookContributorDao,
    private val bookSeriesDao: BookSeriesDao,
    private val imageDownloader: ImageDownloaderContract,
    private val scope: CoroutineScope,
) {
    /**
     * Process an incoming SSE event.
     */
    suspend fun process(event: SSEEventType) {
        try {
            when (event) {
                is SSEEventType.BookCreated -> handleBookCreated(event)
                is SSEEventType.BookUpdated -> handleBookUpdated(event)
                is SSEEventType.BookDeleted -> handleBookDeleted(event)
                is SSEEventType.ScanStarted -> handleScanStarted(event)
                is SSEEventType.ScanCompleted -> handleScanCompleted(event)
                is SSEEventType.Heartbeat -> { /* Keep-alive, no action */ }
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
        bookDao.deleteById(BookId(event.bookId))
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

        val crossRefs = book.contributors.flatMap { contributor ->
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

        val crossRefs = book.seriesInfo.map { seriesInfo ->
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
}
