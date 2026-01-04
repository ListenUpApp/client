@file:OptIn(ExperimentalTime::class)

package com.calypsan.listenup.client.data.sync.sse

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.data.local.db.ActiveSessionDao
import com.calypsan.listenup.client.data.local.db.ActiveSessionEntity
import com.calypsan.listenup.client.data.local.db.ActivityDao
import com.calypsan.listenup.client.data.local.db.ActivityEntity
import com.calypsan.listenup.client.data.local.db.BookContributorDao
import com.calypsan.listenup.client.data.local.db.BookDao
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.db.BookSeriesDao
import com.calypsan.listenup.client.data.local.db.BookTagCrossRef
import com.calypsan.listenup.client.data.local.db.CollectionDao
import com.calypsan.listenup.client.data.local.db.CollectionEntity
import com.calypsan.listenup.client.data.local.db.LensDao
import com.calypsan.listenup.client.data.local.db.LensEntity
import com.calypsan.listenup.client.data.local.db.ListeningEventDao
import com.calypsan.listenup.client.data.local.db.ListeningEventEntity
import com.calypsan.listenup.client.data.local.db.SyncState
import com.calypsan.listenup.client.data.local.db.TagDao
import com.calypsan.listenup.client.data.local.db.TagEntity
import com.calypsan.listenup.client.data.local.db.Timestamp
import com.calypsan.listenup.client.data.local.db.UserDao
import com.calypsan.listenup.client.data.local.db.UserProfileDao
import com.calypsan.listenup.client.data.local.db.UserProfileEntity
import com.calypsan.listenup.client.data.local.db.UserStatsDao
import com.calypsan.listenup.client.data.local.db.UserStatsEntity
import com.calypsan.listenup.client.data.remote.model.BookResponse
import com.calypsan.listenup.client.data.remote.model.toEntity
import com.calypsan.listenup.client.data.sync.ImageDownloaderContract
import com.calypsan.listenup.client.data.sync.SSEEventType
import com.calypsan.listenup.client.download.DownloadService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Parse ISO 8601 timestamp string to Timestamp, falling back to current time on error.
 */
private fun parseTimestamp(isoString: String): Timestamp =
    try {
        Timestamp.fromEpochMillis(Instant.parse(isoString).toEpochMilliseconds())
    } catch (_: Exception) {
        Timestamp.now()
    }

/**
 * Processes real-time Server-Sent Events and applies changes to local database.
 *
 * Handles:
 * - BookCreated/BookUpdated: Upsert book and relationships
 * - BookDeleted: Remove book from database, stop playback if affected, cancel downloads
 * - ScanStarted/ScanCompleted: Log events
 * - Heartbeat: Connection keep-alive
 * - Collection events: Update local collection cache (admin-only)
 * - Tag events: Update local tag cache and book-tag relationships
 */
class SSEEventProcessor(
    private val bookDao: BookDao,
    private val bookContributorDao: BookContributorDao,
    private val bookSeriesDao: BookSeriesDao,
    private val collectionDao: CollectionDao,
    private val lensDao: LensDao,
    private val tagDao: TagDao,
    private val listeningEventDao: ListeningEventDao,
    private val activityDao: ActivityDao,
    private val userDao: UserDao,
    private val userProfileDao: UserProfileDao,
    private val activeSessionDao: ActiveSessionDao,
    private val userStatsDao: UserStatsDao,
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
                is SSEEventType.InboxBookAdded,
                is SSEEventType.InboxBookReleased,
                -> {
                    // Admin-only events, handled by AdminViewModel/AdminInboxViewModel
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

                is SSEEventType.LensCreated -> {
                    handleLensCreated(event)
                }

                is SSEEventType.LensUpdated -> {
                    handleLensUpdated(event)
                }

                is SSEEventType.LensDeleted -> {
                    handleLensDeleted(event)
                }

                is SSEEventType.LensBookAdded -> {
                    handleLensBookAdded(event)
                }

                is SSEEventType.LensBookRemoved -> {
                    handleLensBookRemoved(event)
                }

                is SSEEventType.TagCreated -> {
                    handleTagCreated(event)
                }

                is SSEEventType.BookTagAdded -> {
                    handleBookTagAdded(event)
                }

                is SSEEventType.BookTagRemoved -> {
                    handleBookTagRemoved(event)
                }

                is SSEEventType.ProgressUpdated -> {
                    handleProgressUpdated(event)
                }

                is SSEEventType.ReadingSessionUpdated -> {
                    handleReadingSessionUpdated(event)
                }

                is SSEEventType.ListeningEventCreated -> {
                    handleListeningEventCreated(event)
                }

                is SSEEventType.ActivityCreated -> {
                    handleActivityCreated(event)
                }

                is SSEEventType.ProfileUpdated -> {
                    handleProfileUpdated(event)
                }

                is SSEEventType.SessionStarted -> {
                    handleSessionStarted(event)
                }

                is SSEEventType.SessionEnded -> {
                    handleSessionEnded(event)
                }

                is SSEEventType.UserStatsUpdated -> {
                    handleUserStatsUpdated(event)
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

    // ========== Lens Event Handlers ==========

    private suspend fun handleLensCreated(event: SSEEventType.LensCreated) {
        logger.debug { "SSE: Lens created - ${event.name} (${event.id})" }
        lensDao.upsert(
            LensEntity(
                id = event.id,
                name = event.name,
                description = event.description,
                ownerId = event.ownerId,
                ownerDisplayName = event.ownerDisplayName,
                ownerAvatarColor = event.ownerAvatarColor,
                bookCount = event.bookCount,
                totalDurationSeconds = 0, // Will be updated on first detail view
                createdAt = parseTimestamp(event.createdAt),
                updatedAt = parseTimestamp(event.updatedAt),
            ),
        )
    }

    private suspend fun handleLensUpdated(event: SSEEventType.LensUpdated) {
        logger.debug { "SSE: Lens updated - ${event.name} (${event.id})" }
        // Get existing to preserve totalDurationSeconds
        val existing = lensDao.getById(event.id)
        lensDao.upsert(
            LensEntity(
                id = event.id,
                name = event.name,
                description = event.description,
                ownerId = event.ownerId,
                ownerDisplayName = event.ownerDisplayName,
                ownerAvatarColor = event.ownerAvatarColor,
                bookCount = event.bookCount,
                totalDurationSeconds = existing?.totalDurationSeconds ?: 0,
                createdAt = parseTimestamp(event.createdAt),
                updatedAt = parseTimestamp(event.updatedAt),
            ),
        )
    }

    private suspend fun handleLensDeleted(event: SSEEventType.LensDeleted) {
        logger.debug { "SSE: Lens deleted - ${event.id}" }
        lensDao.deleteById(event.id)
    }

    private suspend fun handleLensBookAdded(event: SSEEventType.LensBookAdded) {
        logger.debug { "SSE: Book ${event.bookId} added to lens ${event.lensId}" }
        // Update the book count for the lens
        val existing = lensDao.getById(event.lensId)
        if (existing != null) {
            lensDao.upsert(
                existing.copy(
                    bookCount = event.bookCount,
                    updatedAt = Timestamp.now(),
                ),
            )
        }
    }

    private suspend fun handleLensBookRemoved(event: SSEEventType.LensBookRemoved) {
        logger.debug { "SSE: Book ${event.bookId} removed from lens ${event.lensId}" }
        // Update the book count for the lens
        val existing = lensDao.getById(event.lensId)
        if (existing != null) {
            lensDao.upsert(
                existing.copy(
                    bookCount = event.bookCount,
                    updatedAt = Timestamp.now(),
                ),
            )
        }
    }

    // ========== Tag Event Handlers ==========

    private suspend fun handleTagCreated(event: SSEEventType.TagCreated) {
        logger.debug { "SSE: Tag created - ${event.slug} (${event.id})" }
        tagDao.upsert(
            TagEntity(
                id = event.id,
                slug = event.slug,
                bookCount = event.bookCount,
                createdAt = Timestamp.now(),
            ),
        )
    }

    private suspend fun handleBookTagAdded(event: SSEEventType.BookTagAdded) {
        logger.debug { "SSE: Tag ${event.tagSlug} added to book ${event.bookId}" }

        // Ensure the tag exists locally (upsert with updated book count)
        tagDao.upsert(
            TagEntity(
                id = event.tagId,
                slug = event.tagSlug,
                bookCount = event.tagBookCount,
                createdAt = Timestamp.now(),
            ),
        )

        // Add the book-tag relationship
        tagDao.insertBookTag(
            BookTagCrossRef(
                bookId = BookId(event.bookId),
                tagId = event.tagId,
            ),
        )

        // Touch the book's updatedAt to trigger UI refresh
        try {
            bookDao.touchUpdatedAt(BookId(event.bookId), Timestamp.now())
        } catch (e: Exception) {
            logger.warn(e) { "Failed to touch book ${event.bookId} after tag added" }
        }
    }

    private suspend fun handleBookTagRemoved(event: SSEEventType.BookTagRemoved) {
        logger.debug { "SSE: Tag ${event.tagSlug} removed from book ${event.bookId}" }

        // Remove the book-tag relationship
        tagDao.deleteBookTag(BookId(event.bookId), event.tagId)

        // Update the tag's book count
        val existingTag = tagDao.getById(event.tagId)
        if (existingTag != null) {
            tagDao.upsert(
                existingTag.copy(
                    bookCount = event.tagBookCount,
                ),
            )
        }

        // Touch the book's updatedAt to trigger UI refresh
        try {
            bookDao.touchUpdatedAt(BookId(event.bookId), Timestamp.now())
        } catch (e: Exception) {
            logger.warn(e) { "Failed to touch book ${event.bookId} after tag removed" }
        }
    }

    // ========== Listening Event Handlers ==========

    private fun handleProgressUpdated(event: SSEEventType.ProgressUpdated) {
        logger.info {
            "SSE: Progress updated for book ${event.bookId} - ${(event.progress * 100).toInt()}% (from another device)"
        }
        // The event is logged and can be observed by ViewModels via SSEManager.eventFlow
        // Stats/Continue Listening screens can listen for this to refresh
    }

    private fun handleReadingSessionUpdated(event: SSEEventType.ReadingSessionUpdated) {
        logger.info {
            "SSE: Reading session ${event.sessionId} updated for book ${event.bookId} - completed=${event.isCompleted}"
        }
        // The event is logged and can be observed by ViewModels via SSEManager.eventFlow
        // Book detail/readers screens can listen for this to refresh
    }

    private suspend fun handleListeningEventCreated(event: SSEEventType.ListeningEventCreated) {
        logger.info { "SSE: Listening event ${event.id} received for book ${event.bookId} (from another device)" }

        // Convert ISO timestamps to epoch ms
        val startedAtMs = parseTimestamp(event.startedAt).epochMillis
        val endedAtMs = parseTimestamp(event.endedAt).epochMillis
        val createdAtMs = parseTimestamp(event.createdAt).epochMillis

        // Save to Room - this triggers stats to auto-update via Flow
        val entity =
            ListeningEventEntity(
                id = event.id,
                bookId = event.bookId,
                startPositionMs = event.startPositionMs,
                endPositionMs = event.endPositionMs,
                startedAt = startedAtMs,
                endedAt = endedAtMs,
                playbackSpeed = event.playbackSpeed,
                deviceId = event.deviceId,
                syncState = SyncState.SYNCED, // Already synced since it came from server
                createdAt = createdAtMs,
            )

        try {
            listeningEventDao.upsert(entity)
            logger.debug { "SSE: Saved listening event ${event.id} to Room" }
        } catch (e: Exception) {
            logger.error(e) { "SSE: Failed to save listening event ${event.id}" }
        }
    }

    // ========== Activity Event Handlers ==========

    /**
     * Handle activity created SSE event.
     *
     * Stores the activity in Room for offline-first activity feed display.
     * Activities are denormalized with all display data (user name, avatar, book info)
     * to avoid joins and enable instant offline display.
     */
    private suspend fun handleActivityCreated(event: SSEEventType.ActivityCreated) {
        logger.debug { "SSE: Activity created - ${event.type} by ${event.userDisplayName}" }

        val createdAtMs = parseTimestamp(event.createdAt).epochMillis

        val entity =
            ActivityEntity(
                id = event.id,
                userId = event.userId,
                type = event.type,
                createdAt = createdAtMs,
                userDisplayName = event.userDisplayName,
                userAvatarColor = event.userAvatarColor,
                userAvatarType = event.userAvatarType,
                userAvatarValue = event.userAvatarValue,
                bookId = event.bookId,
                bookTitle = event.bookTitle,
                bookAuthorName = event.bookAuthorName,
                bookCoverPath = event.bookCoverPath,
                isReread = event.isReread,
                durationMs = event.durationMs,
                milestoneValue = event.milestoneValue,
                milestoneUnit = event.milestoneUnit,
                lensId = event.lensId,
                lensName = event.lensName,
            )

        try {
            activityDao.upsert(entity)
            logger.debug { "SSE: Saved activity ${event.id} to Room" }
        } catch (e: Exception) {
            logger.error(e) { "SSE: Failed to save activity ${event.id}" }
        }
    }

    // ========== Active Session Event Handlers ==========

    /**
     * Handle session started SSE event.
     *
     * Stores the active session in Room for "What Others Are Listening To" feature.
     * The session is joined with UserProfileEntity and BookEntity for display.
     *
     * Also ensures the user's avatar image is downloaded locally if they have one,
     * so the avatar displays correctly in offline-first UI.
     */
    private suspend fun handleSessionStarted(event: SSEEventType.SessionStarted) {
        logger.debug { "SSE: Session started - ${event.sessionId} for user ${event.userId}" }

        // Download user's avatar if they have an image avatar and it's not cached locally
        // Do this BEFORE storing the session so the avatar file exists when UI renders
        val userProfile = userProfileDao.getById(event.userId)
        if (userProfile != null && userProfile.avatarType == "image") {
            scope.launch {
                try {
                    // downloadUserAvatar checks if file exists locally and skips if so
                    imageDownloader.downloadUserAvatar(event.userId, forceRefresh = false)
                    logger.debug { "SSE: Ensured avatar exists for user ${event.userId}" }
                } catch (e: Exception) {
                    logger.warn(e) { "SSE: Failed to download avatar for user ${event.userId}" }
                }
            }
        }

        val startedAtMs = parseTimestamp(event.startedAt).epochMillis
        val now = Timestamp.now().epochMillis

        activeSessionDao.upsert(
            ActiveSessionEntity(
                sessionId = event.sessionId,
                userId = event.userId,
                bookId = event.bookId,
                startedAt = startedAtMs,
                updatedAt = now,
            ),
        )
    }

    /**
     * Handle session ended SSE event.
     *
     * Removes the session from Room.
     */
    private suspend fun handleSessionEnded(event: SSEEventType.SessionEnded) {
        logger.debug { "SSE: Session ended - ${event.sessionId}" }
        activeSessionDao.deleteBySessionId(event.sessionId)
    }

    // ========== User Stats Event Handlers ==========

    /**
     * Handle user stats updated SSE event.
     *
     * Updates the cached user stats in Room for leaderboard display.
     * This enables offline-first leaderboard with real-time updates.
     */
    private suspend fun handleUserStatsUpdated(event: SSEEventType.UserStatsUpdated) {
        logger.debug { "SSE: User stats updated - ${event.displayName} (${event.userId})" }

        val entity =
            UserStatsEntity(
                oduserId = event.userId,
                displayName = event.displayName,
                avatarColor = event.avatarColor,
                avatarType = event.avatarType,
                avatarValue = event.avatarValue,
                totalTimeMs = event.totalTimeMs,
                totalBooks = event.totalBooks,
                currentStreak = event.currentStreak,
                updatedAt = Timestamp.now().epochMillis,
            )

        try {
            userStatsDao.upsert(entity)
            logger.debug { "SSE: Cached user stats for ${event.userId}" }
        } catch (e: Exception) {
            logger.error(e) { "SSE: Failed to cache user stats for ${event.userId}" }
        }
    }

    // ========== Profile Event Handlers ==========

    /**
     * Handle profile updated SSE event.
     *
     * For current user's profile:
     * - Updates local UserEntity so UI observing it sees the new avatar
     *
     * For any user (including current user):
     * - Caches the user profile in UserProfileEntity for offline access
     * - Downloads the avatar image locally for offline access
     *
     * This enables fully offline profile viewing for any user whose
     * profile update was received while online.
     */
    private suspend fun handleProfileUpdated(event: SSEEventType.ProfileUpdated) {
        logger.info { "SSE: Profile updated - ${event.displayName} (${event.userId})" }

        val currentUser = userDao.getCurrentUser()
        val isCurrentUser = currentUser != null && event.userId == currentUser.id

        // IMPORTANT: Download/delete avatar FIRST, before updating userDao
        // This ensures the local file exists when the UI Flow emits after userDao update.
        // If we update userDao first, the UI checks for the file before it's downloaded.
        if (event.avatarType == "image" && event.avatarValue != null) {
            try {
                imageDownloader.downloadUserAvatar(event.userId, forceRefresh = true)
                logger.info { "SSE: Downloaded avatar image for user ${event.userId}" }
            } catch (e: Exception) {
                logger.warn(e) { "SSE: Failed to download avatar for user ${event.userId}" }
            }
        } else if (event.avatarType == "auto") {
            // User reverted to auto avatar, delete the local image file
            try {
                imageDownloader.deleteUserAvatar(event.userId)
                logger.info { "SSE: Deleted local avatar for user ${event.userId} (reverted to auto)" }
            } catch (e: Exception) {
                logger.warn(e) { "SSS: Failed to delete avatar for user ${event.userId}" }
            }
        }

        // Cache user profile for offline display (for ALL users, not just current)
        try {
            userProfileDao.upsert(
                UserProfileEntity(
                    id = event.userId,
                    displayName = event.displayName,
                    avatarType = event.avatarType,
                    avatarValue = event.avatarValue,
                    avatarColor = event.avatarColor,
                    updatedAt = Timestamp.now().epochMillis,
                ),
            )
            logger.debug { "SSE: Cached user profile for ${event.userId}" }
        } catch (e: Exception) {
            logger.warn(e) { "SSE: Failed to cache user profile for ${event.userId}" }
        }

        // Now update local UserEntity for current user - avatar file is already ready
        if (isCurrentUser && currentUser != null) {
            try {
                userDao.updateAvatar(
                    userId = event.userId,
                    avatarType = event.avatarType,
                    avatarValue = event.avatarValue,
                    avatarColor = event.avatarColor,
                    updatedAt = Timestamp.now().epochMillis,
                )
                if (event.tagline != null || currentUser.tagline != null) {
                    userDao.updateTagline(
                        userId = event.userId,
                        tagline = event.tagline,
                        updatedAt = Timestamp.now().epochMillis,
                    )
                }
                logger.info {
                    "SSE: Updated local UserEntity with profile changes - avatar=${event.avatarType}/${event.avatarValue}"
                }
            } catch (e: Exception) {
                logger.error(e) { "SSE: Failed to update local UserEntity for profile event" }
            }
        }
    }
}

/**
 * Event emitted when access to a book is revoked while playing.
 * UI should display a notification that "This book is no longer available".
 */
data class AccessRevokedEvent(
    val bookId: BookId,
)

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
