package com.calypsan.listenup.client.data.sync.pull

import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.core.Timestamp
import com.calypsan.listenup.client.data.local.db.BookContributorDao
import com.calypsan.listenup.client.data.local.db.BookDao
import com.calypsan.listenup.client.data.local.db.BookEntity
import com.calypsan.listenup.client.data.local.db.BookGenreCrossRef
import com.calypsan.listenup.client.data.local.db.BookSeriesDao
import com.calypsan.listenup.client.data.local.db.BookTagCrossRef
import com.calypsan.listenup.client.data.local.db.ChapterDao
import com.calypsan.listenup.client.data.local.db.GenreDao
import com.calypsan.listenup.client.data.local.db.TagDao
import com.calypsan.listenup.client.data.local.db.TagEntity
import com.calypsan.listenup.client.data.local.db.TransactionRunner
import com.calypsan.listenup.client.data.remote.SyncApiContract
import com.calypsan.listenup.client.data.remote.model.SyncBooksResponse
import com.calypsan.listenup.client.data.remote.model.toEntity
import com.calypsan.listenup.client.data.sync.ImageDownloaderContract
import com.calypsan.listenup.client.data.sync.conflict.ConflictDetectorContract
import com.calypsan.listenup.client.data.sync.model.SyncPhase
import com.calypsan.listenup.client.data.sync.model.SyncStatus
import com.calypsan.listenup.client.core.error.AppException
import io.github.oshai.kotlinlogging.KotlinLogging
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.core.Failure

private val logger = KotlinLogging.logger {}

/**
 * Handles paginated book fetching, processing, and relationship syncing.
 */
class BookPuller(
    private val transactionRunner: TransactionRunner,
    private val syncApi: SyncApiContract,
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao,
    private val bookContributorDao: BookContributorDao,
    private val bookSeriesDao: BookSeriesDao,
    private val tagDao: TagDao,
    private val genreDao: GenreDao,
    private val conflictDetector: ConflictDetectorContract,
    private val imageDownloader: ImageDownloaderContract,
    private val coverDownloadDao: com.calypsan.listenup.client.data.local.db.CoverDownloadDao,
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
        var itemsSynced = 0

        while (hasMore) {
            when (val result = syncApi.getBooks(limit = limit, cursor = cursor, updatedAfter = updatedAfter)) {
                is Success -> {
                    val response = result.data
                    val serverBooks = response.books.map { it.toEntity() }
                    val deletedBookIds = response.deletedBookIds
                    itemsSynced += serverBooks.size + deletedBookIds.size

                    onProgress(
                        SyncStatus.Progress(
                            phase = SyncPhase.SYNCING_BOOKS,
                            phaseItemsSynced = itemsSynced,
                            phaseTotalItems = -1,
                            message = "Syncing books: $itemsSynced synced...",
                        ),
                    )

                    logger.debug {
                        "Fetched batch: ${serverBooks.size} books, ${deletedBookIds.size} deletions"
                    }

                    // Handle deletions
                    if (deletedBookIds.isNotEmpty()) {
                        bookDao.deleteByIds(deletedBookIds.map { BookId(it) })
                        logger.info { "Removed ${deletedBookIds.size} books deleted on server" }
                    }

                    if (serverBooks.isNotEmpty()) {
                        processServerBooks(serverBooks, response)
                    }

                    // Advance cursor only after all books in this page are persisted.
                    // Moving this after persistence ensures an interrupted sync does not
                    // skip books: the next delta sync will re-fetch the missing page.
                    cursor = response.nextCursor
                    hasMore = response.hasMore
                }

                is Failure -> {
                    throw AppException(result.error)
                }
            }
        }

        logger.info { "Books sync complete: $itemsSynced items processed" }
    }

    /**
     * Process server books: detect conflicts, upsert, sync related entities.
     *
     * All DB writes for a page — conflict marks, book upsert, chapters, contributor
     * and series cross-refs, tags, cover-download tasks — commit atomically. If any
     * step throws, Room rolls the transaction back so the DB never holds a half-synced
     * book (Finding 05 D2).
     *
     * Reads and disk I/O run outside the transaction so the writer connection is held
     * only long enough to apply the actual mutations.
     */
    private suspend fun processServerBooks(
        serverBooks: List<BookEntity>,
        response: SyncBooksResponse,
    ) {
        logger.info { "processServerBooks: received ${serverBooks.size} books from server" }

        val conflicts = conflictDetector.detectBookConflicts(serverBooks)
        conflicts.forEach { (bookId, _) ->
            logger.warn { "Conflict detected for book $bookId - server version is newer" }
        }

        val booksToUpsert = serverBooks.filterNot { conflictDetector.shouldPreserveLocalChanges(it) }
        logger.info { "processServerBooks: ${booksToUpsert.size} books to upsert (after filtering)" }

        if (booksToUpsert.isEmpty()) {
            logger.info { "processServerBooks: no books to upsert, skipping cover downloads" }
            return
        }

        invalidateChangedCovers(booksToUpsert)

        val booksWithColors = preserveLocalColors(booksToUpsert)

        transactionRunner.atomically {
            conflicts.forEach { (bookId, serverVersion) ->
                bookDao.markConflict(bookId, serverVersion)
            }

            bookDao.upsertAll(booksWithColors)

            syncChapters(response, booksToUpsert)

            syncBookContributors(response, booksToUpsert)
            syncBookSeries(response, booksToUpsert)
            syncBookTags(response, booksToUpsert)
            syncBookGenres(response, booksToUpsert)

            enqueueCoverDownloads(booksToUpsert)
        }
    }

    /**
     * Preserve local-only palette colors (dominantColor, darkMutedColor, vibrantColor)
     * that would be wiped by upsert since the server doesn't send them.
     */
    private suspend fun preserveLocalColors(books: List<BookEntity>): List<BookEntity> {
        val existingColors =
            books
                .mapNotNull { book ->
                    bookDao.getById(book.id)?.let { existing ->
                        book.id to Triple(existing.dominantColor, existing.darkMutedColor, existing.vibrantColor)
                    }
                }.toMap()

        return books.map { book ->
            val colors = existingColors[book.id]
            if (colors != null && book.dominantColor == null && book.darkMutedColor == null &&
                book.vibrantColor == null
            ) {
                book.copy(
                    dominantColor = colors.first,
                    darkMutedColor = colors.second,
                    vibrantColor = colors.third,
                )
            } else {
                book
            }
        }
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

    private suspend fun syncBookGenres(
        response: SyncBooksResponse,
        upsertedBooks: List<BookEntity>,
    ) {
        val upsertedIds = upsertedBooks.map { it.id.value }.toSet()
        val perBook: List<Pair<String, List<String>>> =
            response.books
                .filter { it.id in upsertedIds }
                .map { it.id to it.genres.orEmpty() }

        val allNames = perBook.flatMap { it.second }.distinctBy { it.lowercase() }

        if (allNames.isEmpty()) {
            // Server sent no genres for these books — clear any existing junctions
            // so the server remains the authoritative source of record.
            upsertedIds.forEach { genreDao.deleteGenresForBook(BookId(it)) }
            return
        }

        val nameToId: Map<String, String> =
            genreDao.getIdsByNames(allNames).associate { it.name.lowercase() to it.id }

        val crossRefs =
            perBook.flatMap { (bookIdStr, names) ->
                val bookId = BookId(bookIdStr)
                genreDao.deleteGenresForBook(bookId)
                names.mapNotNull { name ->
                    val id = nameToId[name.lowercase()]
                    if (id == null) {
                        logger.warn { "Genre '$name' not in catalog; skipping for book $bookIdStr" }
                        null
                    } else {
                        BookGenreCrossRef(bookId = bookId, genreId = id)
                    }
                }
            }

        if (crossRefs.isNotEmpty()) {
            genreDao.insertAllBookGenres(crossRefs)
            logger.debug { "Created ${crossRefs.size} book-genre relationships" }
        }
    }

    /**
     * Enqueue cover downloads for the given books into the persistent queue.
     *
     * Replaces the old fire-and-forget scope.launch approach. Tasks are persisted
     * in Room and processed by CoverDownloadWorker, surviving app backgrounding
     * and crashes.
     */
    private suspend fun enqueueCoverDownloads(books: List<BookEntity>) {
        val tasks =
            books.map { book ->
                com.calypsan.listenup.client.data.local.db
                    .CoverDownloadTaskEntity(bookId = book.id)
            }

        if (tasks.isEmpty()) return

        coverDownloadDao.enqueueAll(tasks)
        logger.info { "Enqueued ${tasks.size} cover downloads to persistent queue" }
    }
}
