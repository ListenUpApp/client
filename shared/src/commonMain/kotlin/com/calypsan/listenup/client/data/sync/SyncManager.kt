package com.calypsan.listenup.client.data.sync

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.data.local.db.BookDao
import com.calypsan.listenup.client.data.local.db.BookEntity
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.db.SyncDao
import com.calypsan.listenup.client.data.local.db.SyncState
import com.calypsan.listenup.client.data.local.db.Syncable
import com.calypsan.listenup.client.data.local.db.Timestamp
import com.calypsan.listenup.client.data.local.db.setLastSyncTime
import com.calypsan.listenup.client.data.remote.SyncApi
import com.calypsan.listenup.client.data.remote.model.toEntity
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Orchestrates synchronization between local Room database and server.
 *
 * Responsibilities:
 * - Pull changes from server (delta sync)
 * - Merge into local database (detect conflicts)
 * - Download cover images for books
 * - Push local changes to server (future)
 * - Handle real-time SSE events for live updates
 * - Expose sync state to UI
 *
 * Sync strategy (v1 - pull only):
 * 1. Fetch all books from server via paginated API
 * 2. Upsert into Room database (conflict detection on serverVersion)
 * 3. Download cover images for new/updated books
 * 4. Update last sync timestamp
 * 5. Listen for SSE events and apply updates in real-time
 *
 * Conflict resolution:
 * - Simple last-write-wins by timestamp
 * - Mark conflicts for user review (future)
 * - Don't block sync on conflicts
 *
 * @property syncApi HTTP client for sync endpoints
 * @property bookDao Room DAO for book operations
 * @property syncDao Room DAO for sync metadata
 * @property imageDownloader Downloads and caches book cover images
 * @property sseManager Handles real-time Server-Sent Events
 */
class SyncManager(
    private val syncApi: SyncApi,
    private val bookDao: BookDao,
    private val syncDao: SyncDao,
    private val imageDownloader: ImageDownloader,
    private val sseManager: SSEManager
) {
    private val logger = KotlinLogging.logger {}

    // Scope for SSE event handling
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
     * - Syncing -> Success (on successful completion)
     * - Syncing -> Error (on failure)
     * - Success/Error -> Idle (ready for next sync)
     */
    val syncState: StateFlow<SyncStatus> = _syncState.asStateFlow()

    /**
     * Perform full synchronization with server.
     *
     * For v1, this is a simple pull-only sync:
     * 1. Fetch all books from server
     * 2. Upsert into local database
     * 3. Update last sync timestamp
     *
     * Future versions will add:
     * - Delta sync (only fetch changes since last sync)
     * - Push local changes to server
     * - Conflict resolution UI
     *
     * @return Result indicating success or failure
     */
    @OptIn(ExperimentalTime::class)
    suspend fun sync(): Result<Unit> {
        logger.debug { "Starting sync operation" }
        _syncState.value = SyncStatus.Syncing

        return try {
            // Step 1: Pull changes from server
            pullChanges()

            // Step 2: Push local changes (TODO: implement in future version)
            // pushChanges()

            // Step 3: Update last sync time
            val now = Timestamp.now()
            syncDao.setLastSyncTime(now)

            // Step 4: Connect to SSE stream for real-time updates (if not already connected)
            sseManager.connect()

            logger.debug { "Sync completed successfully" }
            _syncState.value = SyncStatus.Success(timestamp = now)
            Result.Success(Unit)
        } catch (e: Exception) {
            logger.error(e) { "Sync failed" }
            _syncState.value = SyncStatus.Error(exception = e)
            Result.Failure(exception = e, message = "Sync failed: ${e.message}")
        }
    }

    /**
     * Pull changes from server and merge into local database.
     *
     * Fetches all books via paginated API and upserts into Room.
     * Detects conflicts by comparing timestamps - uses functional approach
     * with clear separation of concerns.
     *
     * After syncing book metadata, downloads cover images for all books.
     * Cover download failures are non-fatal and don't block the sync.
     *
     * @throws Exception if network request fails
     */
    private suspend fun pullChanges() {
        logger.debug { "Pulling changes from server" }

        when (val result = syncApi.getAllBooks(limit = 100)) {
            is Result.Success -> {
                val serverBooks = result.data.map { it.toEntity() }
                logger.debug { "Fetched ${serverBooks.size} books from server" }

                // Detect and mark conflicts (server newer than local changes)
                val conflicts = detectConflicts(serverBooks)
                conflicts.forEach { (bookId, serverVersion) ->
                    bookDao.markConflict(bookId, serverVersion)
                    logger.warn { "Conflict detected for book $bookId - server version is newer" }
                }

                // Filter out books with local changes that are newer than server
                val booksToUpsert = serverBooks.filterNot { shouldPreserveLocalChanges(it) }

                // Upsert filtered books
                bookDao.upsertAll(booksToUpsert)

                val preserved = serverBooks.size - booksToUpsert.size - conflicts.size
                logger.debug {
                    "Book sync complete: ${booksToUpsert.size} upserted, " +
                    "${conflicts.size} conflicts marked, $preserved local changes preserved"
                }

                // Download cover images for all books (non-fatal)
                val allBookIds = serverBooks.map { it.id }
                logger.debug { "Starting cover download for ${allBookIds.size} books" }

                when (val coverResult = imageDownloader.downloadCovers(allBookIds)) {
                    is Result.Success -> {
                        val downloadedBookIds = coverResult.data
                        logger.info { "Downloaded ${downloadedBookIds.size} covers successfully" }

                        // Touch database to trigger Flow re-emission so UI picks up new covers
                        val now = Timestamp.now()
                        downloadedBookIds.forEach { bookId ->
                            try {
                                bookDao.touchUpdatedAt(bookId, now)
                            } catch (e: Exception) {
                                logger.warn(e) { "Failed to touch updatedAt for book ${bookId.value}" }
                            }
                        }
                        logger.debug { "Touched ${downloadedBookIds.size} books to trigger UI refresh" }
                    }
                    is Result.Failure -> {
                        logger.warn(coverResult.exception) {
                            "Cover download failed, continuing sync: ${coverResult.exception.message}"
                        }
                    }
                }
            }
            is Result.Failure -> {
                throw result.exception
            }
        }
    }

    /**
     * Detect conflicts where server has newer version than local unsynced changes.
     *
     * Functional approach: map + filter + takeIf creates declarative pipeline.
     *
     * @param serverBooks Books fetched from server
     * @return List of (BookId, Timestamp) pairs for books with conflicts
     */
    private suspend fun detectConflicts(serverBooks: List<BookEntity>): List<Pair<BookId, Timestamp>> {
        return serverBooks.mapNotNull { serverBook ->
            bookDao.getById(serverBook.id)
                ?.takeIf { it.syncState == SyncState.NOT_SYNCED }
                ?.takeIf { serverBook.updatedAt > it.lastModified }
                ?.let { serverBook.id to serverBook.updatedAt }
        }
    }

    /**
     * Check if local changes should be preserved (local is newer than server).
     *
     * Functional approach: chained scope functions make intent clear.
     *
     * @param serverBook Book from server
     * @return true if local version should be kept, false if server should overwrite
     */
    private suspend fun shouldPreserveLocalChanges(serverBook: BookEntity): Boolean {
        return bookDao.getById(serverBook.id)
            ?.takeIf { it.syncState == SyncState.NOT_SYNCED }
            ?.let { it.lastModified >= serverBook.updatedAt }
            ?: false
    }

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
        // For now, just log
        logger.debug { "Queued update for entity: ${entity}" }
    }

    /**
     * Handle incoming SSE events for real-time updates.
     *
     * Processes server-sent events and applies changes to local database:
     * - BookCreated/BookUpdated: Upsert book into database
     * - BookDeleted: Remove book from database
     * - ScanStarted/ScanCompleted: Log events (future: trigger UI notifications)
     *
     * All database operations use the same upsert logic as sync() to maintain
     * consistency and handle conflicts uniformly.
     */
    private suspend fun handleSSEEvent(event: SSEEventType) {
        try {
            when (event) {
                is SSEEventType.BookCreated -> {
                    logger.debug { "SSE: Book created - ${event.book.title}" }
                    val entity = event.book.toEntity()
                    bookDao.upsert(entity)

                    // Download cover image in background (non-blocking)
                    scope.launch {
                        try {
                            logger.info { "SSE: Attempting to download cover for book ${event.book.id}" }
                            val result = imageDownloader.downloadCover(BookId(event.book.id))
                            when (result) {
                                is Result.Success -> {
                                    val wasDownloaded = result.data
                                    logger.info { "SSE: Cover download completed for ${event.book.id}, downloaded=$wasDownloaded" }

                                    // Touch database to trigger Flow re-emission so UI picks up new cover
                                    if (wasDownloaded) {
                                        try {
                                            bookDao.touchUpdatedAt(BookId(event.book.id), Timestamp.now())
                                            logger.debug { "SSE: Touched book ${event.book.id} to trigger UI refresh" }
                                        } catch (e: Exception) {
                                            logger.warn(e) { "SSE: Failed to touch updatedAt for book ${event.book.id}" }
                                        }
                                    }
                                }
                                is Result.Failure -> {
                                    logger.warn(result.exception) { "SSE: Cover download failed for ${event.book.id}" }
                                }
                            }
                        } catch (e: Exception) {
                            logger.warn(e) { "SSE: Exception downloading cover for book ${event.book.id}" }
                        }
                    }
                }

                is SSEEventType.BookUpdated -> {
                    logger.debug { "SSE: Book updated - ${event.book.title}" }
                    val entity = event.book.toEntity()
                    bookDao.upsert(entity)

                    // Download updated cover image if changed
                    scope.launch {
                        try {
                            val result = imageDownloader.downloadCover(BookId(event.book.id))
                            if (result is Result.Success && result.data) {
                                // Touch database to trigger UI refresh if cover was downloaded
                                try {
                                    bookDao.touchUpdatedAt(BookId(event.book.id), Timestamp.now())
                                    logger.debug { "SSE: Touched book ${event.book.id} after cover update" }
                                } catch (e: Exception) {
                                    logger.warn(e) { "SSE: Failed to touch updatedAt for book ${event.book.id}" }
                                }
                            }
                        } catch (e: Exception) {
                            logger.warn(e) { "Failed to download cover for book ${event.book.id}" }
                        }
                    }
                }

                is SSEEventType.BookDeleted -> {
                    logger.debug { "SSE: Book deleted - ${event.bookId}" }
                    bookDao.deleteById(BookId(event.bookId))
                }

                is SSEEventType.ScanStarted -> {
                    logger.debug { "SSE: Library scan started - ${event.libraryId}" }
                    // Future: Show notification to user that scan is in progress
                }

                is SSEEventType.ScanCompleted -> {
                    logger.info {
                        "SSE: Library scan completed - " +
                            "Added: ${event.booksAdded}, " +
                            "Updated: ${event.booksUpdated}, " +
                            "Removed: ${event.booksRemoved}"
                    }
                    // Future: Show notification with scan results
                }

                is SSEEventType.Heartbeat -> {
                    // Heartbeat keeps connection alive, no action needed
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to handle SSE event: $event" }
        }
    }
}

/**
 * Synchronization status.
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
     * Sync operation in progress.
     */
    data object Syncing : SyncStatus

    /**
     * Sync completed successfully.
     *
     * @property timestamp Type-safe timestamp of completion
     */
    data class Success(val timestamp: Timestamp) : SyncStatus

    /**
     * Sync failed with error.
     *
     * @property exception The exception that caused failure
     */
    data class Error(val exception: Exception) : SyncStatus
}
