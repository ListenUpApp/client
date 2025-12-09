package com.calypsan.listenup.client.data.sync

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.data.local.db.BookDao
import com.calypsan.listenup.client.data.local.db.BookEntity
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.db.ContributorDao
import com.calypsan.listenup.client.data.local.db.SeriesDao
import com.calypsan.listenup.client.data.local.db.SyncDao
import com.calypsan.listenup.client.data.local.db.SyncState
import com.calypsan.listenup.client.data.local.db.Syncable
import com.calypsan.listenup.client.data.local.db.Timestamp
import com.calypsan.listenup.client.data.local.db.clearLastSyncTime
import com.calypsan.listenup.client.data.local.db.getLastSyncTime
import com.calypsan.listenup.client.data.local.db.setLastSyncTime
import com.calypsan.listenup.client.data.remote.SyncApi
import com.calypsan.listenup.client.data.remote.model.toEntity
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

/**
 * Orchestrates synchronization between local Room database and server.
 *
 * Responsibilities:
 * - Pull changes from server (delta sync)
 * - Merge into local database (detect conflicts)
 * - Download cover images for books
 * - Rebuild FTS tables for offline search
 * - Push local changes to server (future)
 * - Handle real-time SSE events for live updates
 * - Expose sync state to UI with progress reporting
 *
 * Sync strategy (v1 - pull only):
 * 1. Fetch all books from server via paginated API
 * 2. Upsert into Room database (conflict detection on serverVersion)
 * 3. Download cover images for new/updated books
 * 4. Update last sync timestamp
 * 5. Rebuild FTS tables for offline search
 * 6. Listen for SSE events and apply updates in real-time
 *
 * Reliability features:
 * - Retry with exponential backoff on transient failures
 * - Progress reporting during sync
 * - Proper cancellation handling
 * - Graceful error recovery
 *
 * @property syncApi HTTP client for sync endpoints
 * @property bookDao Room DAO for book operations
 * @property syncDao Room DAO for sync metadata
 * @property imageDownloader Downloads and caches book cover images
 * @property sseManager Handles real-time Server-Sent Events
 * @property ftsPopulator Populates FTS5 tables for offline search
 */
class SyncManager(
    private val syncApi: SyncApi,
    private val bookDao: BookDao,
    private val seriesDao: SeriesDao,
    private val contributorDao: ContributorDao,
    private val chapterDao: com.calypsan.listenup.client.data.local.db.ChapterDao,
    private val bookContributorDao: com.calypsan.listenup.client.data.local.db.BookContributorDao,
    private val syncDao: SyncDao,
    private val imageDownloader: ImageDownloader,
    private val sseManager: SSEManager,
    private val ftsPopulator: FtsPopulator,
    /**
     * Application-scoped CoroutineScope for background tasks.
     *
     * This scope is intentionally long-lived (app lifetime) because SyncManager is a singleton
     * that handles SSE events and background image downloads throughout the app's lifecycle.
     * The scope should use SupervisorJob to prevent child failures from cancelling siblings.
     *
     * Injected from Koin for testability and consistency with SSEManager.
     */
    private val scope: CoroutineScope,
) {
    private val _syncState = MutableStateFlow<SyncStatus>(SyncStatus.Idle)

    init {
        // Start collecting SSE events (connection will be established after first sync)
        scope.launch {
            sseManager.eventFlow.collect { event ->
                handleSSEEvent(event)
            }
        }
    }

    /**
     * Current synchronization status.
     *
     * Exposed as StateFlow for reactive UI updates. Transitions:
     * - Idle -> Syncing (when sync() called)
     * - Syncing -> Progress (during sync with progress info)
     * - Syncing/Progress -> Success (on successful completion)
     * - Syncing/Progress -> Error (on failure)
     * - Success/Error -> Idle (ready for next sync)
     */
    val syncState: StateFlow<SyncStatus> = _syncState.asStateFlow()

    /**
     * Perform full synchronization with server.
     *
     * Features:
     * - Delta sync (only fetch changes since last sync)
     * - Retry with exponential backoff on transient failures
     * - Progress reporting during sync
     * - Proper cancellation handling
     *
     * @return Result indicating success or failure
     */
    suspend fun sync(): Result<Unit> {
        logger.debug { "Starting sync operation" }
        _syncState.value = SyncStatus.Syncing

        return try {
            // Step 1: Pull changes from server with retry
            pullChangesWithRetry()

            // Step 2: Push local changes (TODO: implement in future version)
            // pushChanges()

            // Step 3: Update last sync time
            val now = Timestamp.now()
            syncDao.setLastSyncTime(now)

            // Step 4: Rebuild FTS tables for offline search
            try {
                ftsPopulator.rebuildAll()
            } catch (e: Exception) {
                // FTS rebuild failure is non-fatal - offline search may be stale but app works
                logger.warn(e) { "FTS rebuild failed, offline search may be incomplete" }
            }

            // Step 5: Connect to SSE stream for real-time updates (if not already connected)
            sseManager.connect()

            logger.info { "Sync completed successfully" }
            _syncState.value = SyncStatus.Success(timestamp = now)
            Result.Success(Unit)
        } catch (e: CancellationException) {
            logger.debug { "Sync was cancelled" }
            _syncState.value = SyncStatus.Idle
            throw e // Re-throw cancellation
        } catch (e: Exception) {
            logger.error(e) { "Sync failed after retries" }
            _syncState.value = SyncStatus.Error(exception = e)

            // Offline-first: Don't redirect to server setup on network errors.
            // The user can continue using cached data. Sync will retry later.
            if (isServerUnreachableError(e)) {
                logger.warn { "Server unreachable - continuing with local data" }
            }

            Result.Failure(exception = e, message = "Sync failed: ${e.message}")
        }
    }

    /**
     * Force a full sync by clearing the sync checkpoint and re-syncing.
     *
     * This is useful when:
     * - Books are missing audioFilesJson (playback shows 0:00 duration)
     * - Database corruption is suspected
     * - User wants to refresh all data from server
     *
     * Unlike regular sync(), this always fetches ALL data from server,
     * not just changes since the last sync.
     *
     * @return Result indicating sync success or failure
     */
    suspend fun forceFullSync(): Result<Unit> {
        logger.info { "Forcing full sync - clearing sync checkpoint" }
        syncDao.clearLastSyncTime()
        return sync()
    }

    /**
     * Pull changes with retry logic and exponential backoff.
     */
    private suspend fun pullChangesWithRetry() {
        var lastException: Exception? = null
        var retryDelay = INITIAL_RETRY_DELAY

        repeat(MAX_RETRIES) { attempt ->
            try {
                if (attempt > 0) {
                    logger.info {
                        "Retry attempt ${attempt + 1}/$MAX_RETRIES after ${retryDelay.inWholeMilliseconds}ms"
                    }
                    _syncState.value = SyncStatus.Retrying(attempt = attempt + 1, maxAttempts = MAX_RETRIES)
                    delay(retryDelay)
                    retryDelay = (retryDelay * RETRY_BACKOFF_MULTIPLIER).coerceAtMost(MAX_RETRY_DELAY)
                }

                pullChanges()
                return // Success, exit retry loop
            } catch (e: CancellationException) {
                throw e // Don't retry on cancellation
            } catch (e: Exception) {
                lastException = e
                logger.warn(e) { "Sync attempt ${attempt + 1} failed" }
            }
        }

        // All retries exhausted
        throw lastException ?: error("Sync failed with unknown error")
    }

    /**
     * Pull changes from server and merge into local database.
     *
     * Performs smart sync:
     * - If last sync time exists, performs delta sync (fetching only changes)
     * - If no last sync time, performs full sync
     * - Detects conflicts by comparing timestamps
     * - Handles deletions propagated from server
     *
     * Runs syncs for Books, Series, and Contributors in parallel for performance.
     * Uses supervisorScope to ensure proper cancellation if any job fails.
     *
     * @throws Exception if network request fails
     */
    private suspend fun pullChanges() =
        coroutineScope {
            logger.debug { "Pulling changes from server" }

            // Get last sync time for delta sync
            val lastSyncTime = syncDao.getLastSyncTime()
            val updatedAfter = lastSyncTime?.toIsoString()

            val syncType = if (updatedAfter != null) "Delta (since $updatedAfter)" else "Full"
            logger.info { "Sync strategy: $syncType" }

            _syncState.value =
                SyncStatus.Progress(
                    phase = SyncPhase.FETCHING_METADATA,
                    current = 0,
                    total = 3,
                    message = "Preparing sync...",
                )

            // Run independent sync operations in parallel with proper error handling
            val booksJob = async { pullBooks(updatedAfter) }
            val seriesJob = async { pullSeries(updatedAfter) }
            val contributorsJob = async { pullContributors(updatedAfter) }

            // Wait for all to complete - if any fails, others will be cancelled
            try {
                awaitAll(booksJob, seriesJob, contributorsJob)
            } catch (e: Exception) {
                // Cancel any remaining jobs
                booksJob.cancel()
                seriesJob.cancel()
                contributorsJob.cancel()
                throw e
            }

            _syncState.value =
                SyncStatus.Progress(
                    phase = SyncPhase.FINALIZING,
                    current = 3,
                    total = 3,
                    message = "Finalizing sync...",
                )
        }

    private suspend fun pullBooks(updatedAfter: String?) {
        var cursor: String? = null
        var hasMore = true
        val limit = 100
        var pageCount = 0

        while (hasMore) {
            _syncState.value =
                SyncStatus.Progress(
                    phase = SyncPhase.SYNCING_BOOKS,
                    current = pageCount,
                    total = -1, // Unknown total
                    message = "Syncing books (page ${pageCount + 1})...",
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

                    // Handle deletions (batch operation for efficiency)
                    if (deletedBookIds.isNotEmpty()) {
                        bookDao.deleteByIds(deletedBookIds.map { BookId(it) })
                        logger.info { "Removed ${deletedBookIds.size} books deleted on server" }
                    }

                    if (serverBooks.isNotEmpty()) {
                        processServerBooks(serverBooks, response)
                    }
                }

                is Result.Failure -> {
                    throw result.exception
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
        response: com.calypsan.listenup.client.data.remote.model.SyncBooksResponse,
    ) {
        logger.info { "processServerBooks: received ${serverBooks.size} books from server" }

        // Detect and mark conflicts (server newer than local changes)
        val conflicts = detectConflicts(serverBooks)
        conflicts.forEach { (bookId, serverVersion) ->
            bookDao.markConflict(bookId, serverVersion)
            logger.warn { "Conflict detected for book $bookId - server version is newer" }
        }

        // Filter out books with local changes that are newer than server
        val booksToUpsert = serverBooks.filterNot { shouldPreserveLocalChanges(it) }
        logger.info { "processServerBooks: ${booksToUpsert.size} books to upsert (after filtering)" }

        if (booksToUpsert.isEmpty()) {
            logger.info { "processServerBooks: no books to upsert, skipping cover downloads" }
            return
        }

        // Upsert books
        bookDao.upsertAll(booksToUpsert)

        // Sync chapters for upserted books
        val chaptersToUpsert =
            response.books
                .filter { bookResponse -> booksToUpsert.any { it.id.value == bookResponse.id } }
                .flatMap { bookResponse ->
                    bookResponse.chapters.mapIndexed { index, chapter ->
                        chapter.toEntity(BookId(bookResponse.id), index)
                    }
                }

        logger.debug { "Upserting ${chaptersToUpsert.size} chapters total" }
        if (chaptersToUpsert.isNotEmpty()) {
            chapterDao.upsertAll(chaptersToUpsert)
        }

        // Sync book-contributor relationships for upserted books
        val bookContributorsToUpsert =
            response.books
                .filter { bookResponse -> booksToUpsert.any { it.id.value == bookResponse.id } }
                .flatMap { bookResponse ->
                    // First, delete existing relationships for this book
                    bookContributorDao.deleteContributorsForBook(BookId(bookResponse.id))

                    // Then create new relationships
                    bookResponse.contributors.flatMap { contributorResponse ->
                        contributorResponse.roles.map { role ->
                            contributorResponse.toEntity(BookId(bookResponse.id), role)
                        }
                    }
                }

        if (bookContributorsToUpsert.isNotEmpty()) {
            bookContributorDao.insertAll(bookContributorsToUpsert)
        }

        // Download cover images for updated books (background/parallel)
        val updatedBookIds = booksToUpsert.map { it.id }
        logger.info { "processServerBooks: scheduling cover downloads for ${updatedBookIds.size} books" }
        if (updatedBookIds.isNotEmpty()) {
            scope.launch {
                logger.info { "Starting cover downloads for ${updatedBookIds.size} books..." }
                val downloadedBookIds = imageDownloader.downloadCovers(updatedBookIds)

                // Touch books that got new covers to trigger UI refresh
                if (downloadedBookIds is com.calypsan.listenup.client.core.Result.Success) {
                    val now = Timestamp.now()
                    downloadedBookIds.data.forEach { bookId ->
                        try {
                            bookDao.touchUpdatedAt(bookId, now)
                        } catch (e: Exception) {
                            logger.warn(e) { "Failed to touch book $bookId after cover download" }
                        }
                    }
                    if (downloadedBookIds.data.isNotEmpty()) {
                        logger.debug {
                            "Touched ${downloadedBookIds.data.size} books to refresh UI after cover downloads"
                        }
                    }
                }
            }
        }
    }

    private suspend fun pullSeries(updatedAfter: String?) {
        var cursor: String? = null
        var hasMore = true
        val limit = 100
        var pageCount = 0
        var totalDeleted = 0

        while (hasMore) {
            _syncState.value =
                SyncStatus.Progress(
                    phase = SyncPhase.SYNCING_SERIES,
                    current = pageCount,
                    total = -1,
                    message = "Syncing series (page ${pageCount + 1})...",
                )

            when (val result = syncApi.getSeries(limit = limit, cursor = cursor, updatedAfter = updatedAfter)) {
                is Result.Success -> {
                    val response = result.data
                    cursor = response.nextCursor
                    hasMore = response.hasMore
                    pageCount++

                    val serverSeries = response.series.map { it.toEntity() }
                    val deletedSeriesIds = response.deletedSeriesIds

                    logger.debug {
                        "Fetched page $pageCount: ${serverSeries.size} series, ${deletedSeriesIds.size} deletions"
                    }

                    // Handle deletions (batch operation for efficiency)
                    if (deletedSeriesIds.isNotEmpty()) {
                        seriesDao.deleteByIds(deletedSeriesIds)
                        totalDeleted += deletedSeriesIds.size
                        logger.info { "Removed ${deletedSeriesIds.size} series deleted on server" }
                    }

                    if (serverSeries.isNotEmpty()) {
                        seriesDao.upsertAll(serverSeries)
                    }
                }

                is Result.Failure -> {
                    throw result.exception
                }
            }
        }

        logger.info { "Series sync complete: $pageCount pages processed, $totalDeleted deleted" }
    }

    private suspend fun pullContributors(updatedAfter: String?) {
        var cursor: String? = null
        var hasMore = true
        val limit = 100
        var pageCount = 0
        var totalDeleted = 0

        while (hasMore) {
            _syncState.value =
                SyncStatus.Progress(
                    phase = SyncPhase.SYNCING_CONTRIBUTORS,
                    current = pageCount,
                    total = -1,
                    message = "Syncing contributors (page ${pageCount + 1})...",
                )

            when (val result = syncApi.getContributors(limit = limit, cursor = cursor, updatedAfter = updatedAfter)) {
                is Result.Success -> {
                    val response = result.data
                    cursor = response.nextCursor
                    hasMore = response.hasMore
                    pageCount++

                    val serverContributors = response.contributors.map { it.toEntity() }
                    val deletedContributorIds = response.deletedContributorIds

                    logger.debug {
                        "Fetched page $pageCount: ${serverContributors.size} contributors, ${deletedContributorIds.size} deletions"
                    }

                    // Handle deletions (batch operation for efficiency)
                    if (deletedContributorIds.isNotEmpty()) {
                        contributorDao.deleteByIds(deletedContributorIds)
                        totalDeleted += deletedContributorIds.size
                        logger.info { "Removed ${deletedContributorIds.size} contributors deleted on server" }
                    }

                    if (serverContributors.isNotEmpty()) {
                        contributorDao.upsertAll(serverContributors)
                    }
                }

                is Result.Failure -> {
                    throw result.exception
                }
            }
        }

        logger.info { "Contributors sync complete: $pageCount pages processed, $totalDeleted deleted" }
    }

    /**
     * Detect conflicts where server has newer version than local unsynced changes.
     *
     * @param serverBooks Books fetched from server
     * @return List of (BookId, Timestamp) pairs for books with conflicts
     */
    private suspend fun detectConflicts(serverBooks: List<BookEntity>): List<Pair<BookId, Timestamp>> =
        serverBooks.mapNotNull { serverBook ->
            bookDao
                .getById(serverBook.id)
                ?.takeIf { it.syncState == SyncState.NOT_SYNCED }
                ?.takeIf { serverBook.updatedAt > it.lastModified }
                ?.let { serverBook.id to serverBook.updatedAt }
        }

    /**
     * Check if local changes should be preserved (local is newer than server).
     *
     * @param serverBook Book from server
     * @return true if local version should be kept, false if server should overwrite
     */
    private suspend fun shouldPreserveLocalChanges(serverBook: BookEntity): Boolean =
        bookDao
            .getById(serverBook.id)
            ?.takeIf { it.syncState == SyncState.NOT_SYNCED }
            ?.let { it.lastModified >= serverBook.updatedAt }
            ?: false

    /**
     * Queue an entity for synchronization with server.
     *
     * Marks entity as NOT_SYNCED so it will be included in next
     * push operation.
     *
     * @param entity The entity to queue (currently only BookEntity supported)
     */
    suspend fun queueUpdate(entity: Syncable) {
        // TODO: Implement push sync in future version
        logger.debug { "Queued update for entity: $entity" }
    }

    /**
     * Handle incoming SSE events for real-time updates.
     *
     * Processes server-sent events and applies changes to local database:
     * - BookCreated/BookUpdated: Upsert book into database
     * - BookDeleted: Remove book from database
     * - ScanStarted/ScanCompleted: Log events (future: trigger UI notifications)
     */
    private suspend fun handleSSEEvent(event: SSEEventType) {
        try {
            when (event) {
                is SSEEventType.BookCreated -> {
                    logger.debug { "SSE: Book created - ${event.book.title}" }
                    val entity = event.book.toEntity()
                    bookDao.upsert(entity)

                    // Download cover image in background
                    scope.launch {
                        downloadCoverForBook(event.book.id)
                    }
                }

                is SSEEventType.BookUpdated -> {
                    logger.debug { "SSE: Book updated - ${event.book.title}" }
                    val entity = event.book.toEntity()
                    bookDao.upsert(entity)

                    // Download updated cover image if changed
                    scope.launch {
                        downloadCoverForBook(event.book.id)
                    }
                }

                is SSEEventType.BookDeleted -> {
                    logger.debug { "SSE: Book deleted - ${event.bookId}" }
                    bookDao.deleteById(BookId(event.bookId))
                }

                is SSEEventType.ScanStarted -> {
                    logger.debug { "SSE: Library scan started - ${event.libraryId}" }
                }

                is SSEEventType.ScanCompleted -> {
                    logger.info {
                        "SSE: Library scan completed - " +
                            "Added: ${event.booksAdded}, " +
                            "Updated: ${event.booksUpdated}, " +
                            "Removed: ${event.booksRemoved}"
                    }
                }

                is SSEEventType.Heartbeat -> {
                    // Heartbeat keeps connection alive, no action needed
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to handle SSE event: $event" }
        }
    }

    /**
     * Download cover for a book and trigger UI refresh if successful.
     */
    private suspend fun downloadCoverForBook(bookId: String) {
        try {
            val result = imageDownloader.downloadCover(BookId(bookId))
            if (result is Result.Success && result.data) {
                // Touch database to trigger Flow re-emission so UI picks up new cover
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

    /**
     * Check if an exception indicates the server is unreachable.
     *
     * This detects connection errors that suggest the server is not running
     * or the URL is incorrect, such as:
     * - Connection refused (ECONNREFUSED)
     * - Connection timeout
     * - Host unreachable
     *
     * @param e The exception to check
     * @return true if the error indicates server is unreachable
     */
    private fun isServerUnreachableError(e: Exception): Boolean {
        // Check the exception and its cause chain for connection errors
        var current: Throwable? = e
        while (current != null) {
            when {
                // Ktor connect timeout
                current is ConnectTimeoutException -> {
                    return true
                }

                // General IO error - check message for connection refused
                current is IOException -> {
                    val message = current.message?.lowercase() ?: ""
                    if (message.contains("econnrefused") ||
                        message.contains("connection refused") ||
                        message.contains("failed to connect") ||
                        message.contains("no route to host") ||
                        message.contains("host unreachable") ||
                        message.contains("network is unreachable")
                    ) {
                        return true
                    }
                }

                // Java ConnectException (Android/JVM) - check class name since we're in common code
                current::class.simpleName == "ConnectException" -> {
                    val message = current.message?.lowercase() ?: ""
                    if (message.contains("econnrefused") ||
                        message.contains("connection refused") ||
                        message.contains("failed to connect")
                    ) {
                        return true
                    }
                }
            }
            current = current.cause
        }
        return false
    }

    // Retry configuration
    private companion object {
        const val MAX_RETRIES = 3
        val INITIAL_RETRY_DELAY = 1.seconds
        val MAX_RETRY_DELAY = 30.seconds
        const val RETRY_BACKOFF_MULTIPLIER = 2.0
    }
}

/**
 * Phases of the sync operation for progress reporting.
 */
enum class SyncPhase {
    FETCHING_METADATA,
    SYNCING_BOOKS,
    SYNCING_SERIES,
    SYNCING_CONTRIBUTORS,
    FINALIZING,
}

/**
 * Synchronization status with progress information.
 *
 * Sealed interface for type-safe state management. UI can observe
 * this via StateFlow to show sync progress, errors, etc.
 */
sealed interface SyncStatus {
    /**
     * Idle state - no sync in progress.
     */
    data object Idle : SyncStatus

    /**
     * Sync operation starting.
     */
    data object Syncing : SyncStatus

    /**
     * Sync in progress with detailed progress information.
     *
     * @property phase Current sync phase
     * @property current Current progress within phase
     * @property total Total items in phase (-1 if unknown)
     * @property message Human-readable progress message
     */
    data class Progress(
        val phase: SyncPhase,
        val current: Int,
        val total: Int,
        val message: String,
    ) : SyncStatus

    /**
     * Retrying after a transient failure.
     *
     * @property attempt Current retry attempt number (1-based)
     * @property maxAttempts Maximum retry attempts
     */
    data class Retrying(
        val attempt: Int,
        val maxAttempts: Int,
    ) : SyncStatus

    /**
     * Sync completed successfully.
     *
     * @property timestamp Type-safe timestamp of completion
     */
    data class Success(
        val timestamp: Timestamp,
    ) : SyncStatus

    /**
     * Sync failed with error.
     *
     * @property exception The exception that caused failure
     */
    data class Error(
        val exception: Exception,
    ) : SyncStatus
}
