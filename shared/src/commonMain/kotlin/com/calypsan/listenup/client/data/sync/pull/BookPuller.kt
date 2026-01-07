package com.calypsan.listenup.client.data.sync.pull

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.exceptionOrFromMessage
import com.calypsan.listenup.client.data.local.db.BookContributorDao
import com.calypsan.listenup.client.data.local.db.BookDao
import com.calypsan.listenup.client.data.local.db.BookEntity
import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.data.local.db.BookSeriesDao
import com.calypsan.listenup.client.data.local.db.BookTagCrossRef
import com.calypsan.listenup.client.data.local.db.ChapterDao
import com.calypsan.listenup.client.data.local.db.TagDao
import com.calypsan.listenup.client.data.local.db.TagEntity
import com.calypsan.listenup.client.core.Timestamp
import com.calypsan.listenup.client.data.remote.SyncApiContract
import com.calypsan.listenup.client.data.remote.model.SyncBooksResponse
import com.calypsan.listenup.client.data.remote.model.toEntity
import com.calypsan.listenup.client.data.sync.ImageDownloaderContract
import com.calypsan.listenup.client.data.sync.conflict.ConflictDetectorContract
import com.calypsan.listenup.client.data.sync.model.SyncPhase
import com.calypsan.listenup.client.data.sync.model.SyncStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * Handles paginated book fetching, processing, and relationship syncing.
 */
class BookPuller(
    private val syncApi: SyncApiContract,
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao,
    private val bookContributorDao: BookContributorDao,
    private val bookSeriesDao: BookSeriesDao,
    private val tagDao: TagDao,
    private val conflictDetector: ConflictDetectorContract,
    private val imageDownloader: ImageDownloaderContract,
    private val scope: CoroutineScope,
) : Puller {
    /**
     * Pull all books from server with pagination.
     *
     * @param updatedAfter ISO timestamp for delta sync, null for full sync
     * @param onProgress Callback for progress updates
     */
    override suspend fun pull(
        updatedAfter: String?,
        onProgress: (SyncStatus) -> Unit,
    ) {
        var cursor: String? = null
        var hasMore = true
        val limit = 100
        var pageCount = 0

        while (hasMore) {
            onProgress(
                SyncStatus.Progress(
                    phase = SyncPhase.SYNCING_BOOKS,
                    current = pageCount,
                    total = -1,
                    message = "Syncing books (page ${pageCount + 1})...",
                ),
            )

            when (val result = syncApi.getBooks(limit = limit, cursor = cursor, updatedAfter = updatedAfter)) {
                is Result.Success -> {
                    val response = result.data
                    cursor = response.nextCursor
                    hasMore = response.hasMore
                    pageCount++

                    val serverBooks = response.books.map { it.toEntity() }
                    val deletedBookIds = response.deletedBookIds

                    logger.debug {
                        "Fetched page $pageCount: ${serverBooks.size} books, ${deletedBookIds.size} deletions"
                    }

                    // Handle deletions
                    if (deletedBookIds.isNotEmpty()) {
                        bookDao.deleteByIds(deletedBookIds.map { BookId(it) })
                        logger.info { "Removed ${deletedBookIds.size} books deleted on server" }
                    }

                    if (serverBooks.isNotEmpty()) {
                        processServerBooks(serverBooks, response)
                    }
                }

                is Result.Failure -> {
                    throw result.exceptionOrFromMessage()
                }
            }
        }

        logger.info { "Books sync complete: $pageCount pages processed" }
    }

    /**
     * Process server books: detect conflicts, upsert, sync related entities.
     */
    private suspend fun processServerBooks(
        serverBooks: List<BookEntity>,
        response: SyncBooksResponse,
    ) {
        logger.info { "processServerBooks: received ${serverBooks.size} books from server" }

        // Detect and mark conflicts (server newer than local changes)
        val conflicts = conflictDetector.detectBookConflicts(serverBooks)
        conflicts.forEach { (bookId, serverVersion) ->
            bookDao.markConflict(bookId, serverVersion)
            logger.warn { "Conflict detected for book $bookId - server version is newer" }
        }

        // Filter out books with local changes that are newer than server
        val booksToUpsert = serverBooks.filterNot { conflictDetector.shouldPreserveLocalChanges(it) }
        logger.info { "processServerBooks: ${booksToUpsert.size} books to upsert (after filtering)" }

        if (booksToUpsert.isEmpty()) {
            logger.info { "processServerBooks: no books to upsert, skipping cover downloads" }
            return
        }

        // Invalidate local covers for books whose cover has changed on server
        invalidateChangedCovers(booksToUpsert)

        // Upsert books
        bookDao.upsertAll(booksToUpsert)

        // Sync chapters
        syncChapters(response, booksToUpsert)

        // Sync relationships
        syncBookContributors(response, booksToUpsert)
        syncBookSeries(response, booksToUpsert)
        syncBookTags(response, booksToUpsert)

        // Download cover images in background
        downloadCovers(booksToUpsert)
    }

    /**
     * Detect books whose cover URL has changed and delete their local cached covers.
     *
     * This ensures that when metadata is updated with a new cover, the new cover
     * will be downloaded instead of using the stale cached version.
     */
    private suspend fun invalidateChangedCovers(serverBooks: List<BookEntity>) {
        serverBooks.forEach { serverBook ->
            val localBook = bookDao.getById(serverBook.id)
            if (localBook != null && localBook.coverUrl != serverBook.coverUrl) {
                logger.info {
                    "Cover changed for book ${serverBook.id.value}: " +
                        "'${localBook.coverUrl}' -> '${serverBook.coverUrl}'"
                }
                imageDownloader.deleteCover(serverBook.id)
            }
        }
    }

    private suspend fun syncChapters(
        response: SyncBooksResponse,
        upsertedBooks: List<BookEntity>,
    ) {
        val chaptersToUpsert =
            response.books
                .filter { bookResponse -> upsertedBooks.any { it.id.value == bookResponse.id } }
                .flatMap { bookResponse ->
                    bookResponse.chapters.mapIndexed { index, chapter ->
                        chapter.toEntity(BookId(bookResponse.id), index)
                    }
                }

        logger.debug { "Upserting ${chaptersToUpsert.size} chapters total" }
        if (chaptersToUpsert.isNotEmpty()) {
            chapterDao.upsertAll(chaptersToUpsert)
        }
    }

    private suspend fun syncBookContributors(
        response: SyncBooksResponse,
        upsertedBooks: List<BookEntity>,
    ) {
        val bookContributorsToUpsert =
            response.books
                .filter { bookResponse -> upsertedBooks.any { it.id.value == bookResponse.id } }
                .flatMap { bookResponse ->
                    bookContributorDao.deleteContributorsForBook(BookId(bookResponse.id))

                    bookResponse.contributors.flatMap { contributorResponse ->
                        contributorResponse.roles.map { role ->
                            contributorResponse.toEntity(BookId(bookResponse.id), role)
                        }
                    }
                }

        if (bookContributorsToUpsert.isNotEmpty()) {
            bookContributorDao.insertAll(bookContributorsToUpsert)
        }
    }

    private suspend fun syncBookSeries(
        response: SyncBooksResponse,
        upsertedBooks: List<BookEntity>,
    ) {
        val bookSeriesToUpsert =
            response.books
                .filter { bookResponse -> upsertedBooks.any { it.id.value == bookResponse.id } }
                .flatMap { bookResponse ->
                    bookSeriesDao.deleteSeriesForBook(BookId(bookResponse.id))

                    bookResponse.seriesInfo.map { seriesInfo ->
                        seriesInfo.toEntity(BookId(bookResponse.id))
                    }
                }

        if (bookSeriesToUpsert.isNotEmpty()) {
            bookSeriesDao.insertAll(bookSeriesToUpsert)
        }
    }

    private suspend fun syncBookTags(
        response: SyncBooksResponse,
        upsertedBooks: List<BookEntity>,
    ) {
        // First, collect all unique tags and upsert them
        val allTags =
            response.books
                .filter { bookResponse -> upsertedBooks.any { it.id.value == bookResponse.id } }
                .flatMap { it.tags }
                .distinctBy { it.id }
                .map { tag ->
                    TagEntity(
                        id = tag.id,
                        slug = tag.slug,
                        bookCount = tag.bookCount,
                        createdAt = Timestamp.now(),
                    )
                }

        if (allTags.isNotEmpty()) {
            tagDao.upsertAll(allTags)
            logger.debug { "Upserted ${allTags.size} unique tags" }
        }

        // Then create book-tag cross references
        val bookTagCrossRefs =
            response.books
                .filter { bookResponse -> upsertedBooks.any { it.id.value == bookResponse.id } }
                .flatMap { bookResponse ->
                    tagDao.deleteTagsForBook(BookId(bookResponse.id))

                    bookResponse.tags.map { tag ->
                        BookTagCrossRef(bookId = BookId(bookResponse.id), tagId = tag.id)
                    }
                }

        if (bookTagCrossRefs.isNotEmpty()) {
            tagDao.insertAllBookTags(bookTagCrossRefs)
            logger.debug { "Created ${bookTagCrossRefs.size} book-tag relationships" }
        }
    }

    private fun downloadCovers(books: List<BookEntity>) {
        val updatedBookIds = books.map { it.id }
        logger.info { "processServerBooks: scheduling cover downloads for ${updatedBookIds.size} books" }

        if (updatedBookIds.isEmpty()) return

        scope.launch {
            logger.info { "Starting cover downloads for ${updatedBookIds.size} books..." }
            val downloadResults = imageDownloader.downloadCovers(updatedBookIds)

            if (downloadResults is Result.Success) {
                val now = Timestamp.now()
                downloadResults.data.forEach { result ->
                    try {
                        if (result.colors != null) {
                            bookDao.updateCoverColors(
                                id = result.bookId,
                                dominantColor = result.colors.dominant,
                                darkMutedColor = result.colors.darkMuted,
                                vibrantColor = result.colors.vibrant,
                                timestamp = now,
                            )
                        } else {
                            bookDao.touchUpdatedAt(result.bookId, now)
                        }
                    } catch (e: Exception) {
                        logger.warn(e) { "Failed to update book ${result.bookId} after cover download" }
                    }
                }
                if (downloadResults.data.isNotEmpty()) {
                    val withColors = downloadResults.data.count { it.colors != null }
                    logger.debug {
                        "Updated ${downloadResults.data.size} books after cover downloads ($withColors with colors)"
                    }
                }
            }
        }
    }
}
