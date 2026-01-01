@file:OptIn(ExperimentalTime::class)
@file:Suppress("MagicNumber")

package com.calypsan.listenup.client.playback

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.db.DownloadDao
import com.calypsan.listenup.client.data.local.db.OperationType
import com.calypsan.listenup.client.data.local.db.PlaybackPositionDao
import com.calypsan.listenup.client.data.local.db.PlaybackPositionEntity
import com.calypsan.listenup.client.data.remote.SyncApiContract
import com.calypsan.listenup.client.data.remote.model.PlaybackProgressResponse
import com.calypsan.listenup.client.data.sync.push.ListeningEventPayload
import com.calypsan.listenup.client.data.sync.push.OperationHandler
import com.calypsan.listenup.client.data.sync.push.PendingOperationRepositoryContract
import com.calypsan.listenup.client.data.sync.push.PushSyncOrchestratorContract
import com.calypsan.listenup.client.util.NanoId
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val logger = KotlinLogging.logger {}

/**
 * Coordinates position persistence and event recording.
 *
 * Two separate concerns:
 * 1. Position persistence (local-first, instant) - never lose user's place
 * 2. Event recording (append-only, eventually consistent) - listening history
 *
 * Position is sacred: saves immediately on every pause/seek.
 * Events are queued locally via the unified push sync system.
 */
class ProgressTracker(
    private val positionDao: PlaybackPositionDao,
    private val downloadDao: DownloadDao,
    private val syncApi: SyncApiContract,
    private val pendingOperationRepository: PendingOperationRepositoryContract,
    private val listeningEventHandler: OperationHandler<ListeningEventPayload>,
    private val pushSyncOrchestrator: PushSyncOrchestratorContract,
    private val deviceId: String,
    private val scope: CoroutineScope,
) {
    private var currentSession: ListeningSession? = null

    /**
     * Represents an active listening session.
     * Created when playback starts, closed when playback pauses.
     */
    data class ListeningSession(
        val bookId: BookId,
        val startPositionMs: Long,
        val startedAt: Long,
        val playbackSpeed: Float,
    )

    /**
     * Called when playback starts/resumes.
     */
    fun onPlaybackStarted(
        bookId: BookId,
        positionMs: Long,
        speed: Float,
    ) {
        currentSession =
            ListeningSession(
                bookId = bookId,
                startPositionMs = positionMs,
                startedAt = Clock.System.now().toEpochMilliseconds(),
                playbackSpeed = speed,
            )
        logger.info { "🎧 LISTENING SESSION STARTED: book=${bookId.value}, position=$positionMs, speed=$speed" }
    }

    /**
     * Called when playback pauses/stops.
     * Saves position immediately, queues event for sync.
     */
    fun onPlaybackPaused(
        bookId: BookId,
        positionMs: Long,
        speed: Float,
    ) {
        logger.info { "🎧 PLAYBACK PAUSED: book=${bookId.value}, position=$positionMs, hasSession=${currentSession != null}" }

        scope.launch {
            // CONCERN 1: Save position immediately (local, fast)
            savePosition(bookId, positionMs, speed)

            // CONCERN 2: Queue listening event (if meaningful session)
            val session = currentSession
            if (session == null) {
                logger.warn { "🎧 NO ACTIVE SESSION - cannot record listening event" }
            } else if (session.bookId != bookId) {
                logger.warn { "🎧 SESSION BOOK MISMATCH: session=${session.bookId.value}, paused=${bookId.value}" }
            } else {
                val durationMs = positionMs - session.startPositionMs
                logger.info { "🎧 SESSION DURATION: ${durationMs}ms (need >=10000ms to record)" }

                // Only record if listened for at least 10 seconds
                if (durationMs >= 10_000) {
                    queueListeningEvent(
                        bookId = bookId,
                        startPositionMs = session.startPositionMs,
                        endPositionMs = positionMs,
                        startedAt = session.startedAt,
                        endedAt = Clock.System.now().toEpochMilliseconds(),
                        playbackSpeed = session.playbackSpeed,
                    )
                    logger.info { "🎧 LISTENING EVENT QUEUED: duration=${durationMs}ms, triggering sync..." }

                    // Fire-and-forget sync attempt
                    trySyncEvents()
                } else {
                    logger.info { "🎧 SESSION TOO SHORT: ${durationMs}ms < 10000ms - not recording" }
                }
            }

            currentSession = null
        }
    }

    /**
     * Called periodically during playback (every 30 seconds).
     * Updates local position AND flushes the current session as a listening event.
     * This enables real-time stats updates during playback.
     */
    fun onPositionUpdate(
        bookId: BookId,
        positionMs: Long,
        speed: Float,
    ) {
        scope.launch {
            // Save position locally
            savePosition(bookId, positionMs, speed)

            // Flush current session as a listening event and start a new one
            val session = currentSession
            if (session != null && session.bookId == bookId) {
                val durationMs = positionMs - session.startPositionMs
                logger.debug { "🎧 PERIODIC UPDATE: ${durationMs}ms listened since session start" }

                // Only record if listened for at least 10 seconds
                if (durationMs >= 10_000) {
                    val now = Clock.System.now().toEpochMilliseconds()
                    queueListeningEvent(
                        bookId = bookId,
                        startPositionMs = session.startPositionMs,
                        endPositionMs = positionMs,
                        startedAt = session.startedAt,
                        endedAt = now,
                        playbackSpeed = session.playbackSpeed,
                    )
                    logger.info { "🎧 PERIODIC EVENT QUEUED: duration=${durationMs}ms, triggering sync..." }

                    // Start a new session from the current position
                    currentSession = ListeningSession(
                        bookId = bookId,
                        startPositionMs = positionMs,
                        startedAt = now,
                        playbackSpeed = speed,
                    )

                    // Fire-and-forget sync attempt
                    trySyncEvents()
                }
            }
        }
    }

    /**
     * Save position to local database immediately.
     * Preserves the existing hasCustomSpeed flag.
     */
    private suspend fun savePosition(
        bookId: BookId,
        positionMs: Long,
        speed: Float,
    ) {
        try {
            // Preserve existing hasCustomSpeed value
            val existing = positionDao.get(bookId)
            val now = Clock.System.now().toEpochMilliseconds()
            positionDao.save(
                PlaybackPositionEntity(
                    bookId = bookId,
                    positionMs = positionMs,
                    playbackSpeed = speed,
                    hasCustomSpeed = existing?.hasCustomSpeed ?: false,
                    updatedAt = now,
                    syncedAt = null,
                    lastPlayedAt = now, // Track actual play time
                ),
            )
            logger.debug { "Position saved: book=${bookId.value}, position=$positionMs" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to save position: book=${bookId.value}, position=$positionMs" }
        }
    }

    /**
     * Called when user explicitly changes playback speed.
     * Marks this book as having a custom speed (not using universal default).
     */
    fun onSpeedChanged(
        bookId: BookId,
        positionMs: Long,
        newSpeed: Float,
    ) {
        scope.launch {
            val now = Clock.System.now().toEpochMilliseconds()
            positionDao.save(
                PlaybackPositionEntity(
                    bookId = bookId,
                    positionMs = positionMs,
                    playbackSpeed = newSpeed,
                    hasCustomSpeed = true, // User explicitly set this speed
                    updatedAt = now,
                    syncedAt = null,
                    lastPlayedAt = now, // Track actual play time
                ),
            )
            logger.debug { "Speed changed: book=${bookId.value}, speed=$newSpeed, hasCustomSpeed=true" }
        }
    }

    /**
     * Reset a book's speed to use the universal default.
     * Called when user explicitly resets to default.
     */
    fun onSpeedReset(
        bookId: BookId,
        positionMs: Long,
        defaultSpeed: Float,
    ) {
        scope.launch {
            val now = Clock.System.now().toEpochMilliseconds()
            positionDao.save(
                PlaybackPositionEntity(
                    bookId = bookId,
                    positionMs = positionMs,
                    playbackSpeed = defaultSpeed,
                    hasCustomSpeed = false, // Now using universal default
                    updatedAt = now,
                    syncedAt = null,
                    lastPlayedAt = now, // Track actual play time
                ),
            )
            logger.debug { "Speed reset to default: book=${bookId.value}, speed=$defaultSpeed, hasCustomSpeed=false" }
        }
    }

    /**
     * Save position immediately (blocking for critical saves).
     * Used before error handling to ensure position is never lost.
     */
    suspend fun savePositionNow(
        bookId: BookId,
        positionMs: Long,
    ) {
        val speed = currentSession?.playbackSpeed ?: 1.0f
        savePosition(bookId, positionMs, speed)
    }

    /**
     * Get resume position for a book.
     *
     * Cross-device sync: Checks both local and server progress, returns whichever
     * is more recent. This enables seamless handoff between devices.
     *
     * Flow:
     * 1. Get local position (instant, offline-first)
     * 2. Try to get server position (best-effort, may fail if offline)
     * 3. Compare timestamps - use whichever is newer
     * 4. If server is newer, update local cache
     *
     * @param bookId Book to get resume position for
     * @return Position to resume from, or null if never played
     */
    suspend fun getResumePosition(bookId: BookId): PlaybackPositionEntity? {
        // 1. Get local position (instant, offline-first)
        val local = positionDao.get(bookId)

        // 2. Try to get server position (best-effort)
        val server = fetchServerProgress(bookId)

        // 3. Merge: latest timestamp wins
        return mergePositions(bookId, local, server)
    }

    /**
     * Fetch progress from server. Returns null on any error (offline, auth, etc.)
     */
    private suspend fun fetchServerProgress(bookId: BookId): PlaybackProgressResponse? =
        try {
            when (val result = syncApi.getProgress(bookId.value)) {
                is Result.Success -> {
                    result.data
                }

                is Result.Failure -> {
                    logger.debug { "Server progress unavailable: ${result.exception.message}" }
                    null
                }
            }
        } catch (e: Exception) {
            logger.debug { "Server progress fetch failed: ${e.message}" }
            null
        }

    /**
     * Merge local and server positions, returning the more recent one.
     * If server is newer, updates local cache for offline access.
     */
    @Suppress("UnusedParameter") // bookId reserved for future logging/debugging
    private suspend fun mergePositions(
        bookId: BookId,
        local: PlaybackPositionEntity?,
        server: PlaybackProgressResponse?,
    ): PlaybackPositionEntity? =
        when {
            local == null && server == null -> {
                null
            }

            local == null && server != null -> {
                // Only server has progress - cache it locally
                val entity = server.toEntity()
                positionDao.save(entity)
                logger.info { "Using server position: ${server.currentPositionMs}ms (first sync)" }
                entity
            }

            server == null -> {
                local
            }

            else -> {
                // Both exist - compare timestamps
                val serverTimestamp = server.lastPlayedAtMillis()
                if (serverTimestamp > local!!.updatedAt) {
                    // Server is newer (listened on another device)
                    val entity = server.toEntity()
                    positionDao.save(entity)
                    logger.info {
                        "Using server position: ${server.currentPositionMs}ms " +
                            "(was ${local.positionMs}ms locally, server is ${(serverTimestamp - local.updatedAt) / 1000}s newer)"
                    }
                    entity
                } else {
                    // Local is newer or same
                    local
                }
            }
        }

    /**
     * Mark a book as finished.
     * Called when playback reaches the end.
     */
    fun onBookFinished(bookId: BookId) {
        scope.launch {
            logger.info { "Book finished: ${bookId.value}" }

            // Record completion event
            currentSession?.let { session ->
                if (session.bookId == bookId) {
                    queueListeningEvent(
                        bookId = bookId,
                        startPositionMs = session.startPositionMs,
                        endPositionMs = Long.MAX_VALUE, // Indicates completion
                        startedAt = session.startedAt,
                        endedAt = Clock.System.now().toEpochMilliseconds(),
                        playbackSpeed = session.playbackSpeed,
                    )
                }
            }
            currentSession = null

            // Clear any DELETED download records so future playback will auto-download again
            // This means that the next time a user wants to listen to the same book. We assume
            // They want the default behavior again (stream + download)
            downloadDao.deleteForBook(bookId.value)
            logger.debug { "Cleared download records for finished book: ${bookId.value}" }
        }
    }

    /**
     * Clear progress for a book (reset to beginning).
     */
    suspend fun clearProgress(bookId: BookId) {
        positionDao.delete(bookId)
        logger.info { "Progress cleared for book: ${bookId.value}" }
    }

    /**
     * Get the current session's playback speed.
     * Returns 1.0 if no active session.
     */
    fun getCurrentSpeed(): Float = currentSession?.playbackSpeed ?: 1.0f

    /**
     * Get the most recently played book.
     * Used for playback resumption from system UI (Android Auto, Wear OS, etc).
     *
     * @return The book ID and position of the most recently played book, or null if never played
     */
    suspend fun getLastPlayedBook(): LastPlayedInfo? {
        val positions = positionDao.getRecentPositions(1)
        val position = positions.firstOrNull() ?: return null

        return LastPlayedInfo(
            bookId = position.bookId,
            positionMs = position.positionMs,
            playbackSpeed = position.playbackSpeed,
        )
    }

    /**
     * Information about the last played book for resumption.
     */
    data class LastPlayedInfo(
        val bookId: BookId,
        val positionMs: Long,
        val playbackSpeed: Float,
    )

    /**
     * Queue a listening event via the unified push sync system.
     */
    private suspend fun queueListeningEvent(
        bookId: BookId,
        startPositionMs: Long,
        endPositionMs: Long,
        startedAt: Long,
        endedAt: Long,
        playbackSpeed: Float,
    ) {
        val eventId = NanoId.generate("evt")
        logger.info { "🎧 CREATING EVENT: id=$eventId, book=${bookId.value}, start=$startPositionMs, end=$endPositionMs" }

        val payload =
            ListeningEventPayload(
                id = eventId,
                bookId = bookId.value,
                startPositionMs = startPositionMs,
                endPositionMs = endPositionMs,
                startedAt = startedAt,
                endedAt = endedAt,
                playbackSpeed = playbackSpeed,
                deviceId = deviceId,
            )

        try {
            pendingOperationRepository.queue(
                type = OperationType.LISTENING_EVENT,
                entityType = null, // Events don't target a specific entity type
                entityId = null, // Events batch together, not by entity
                payload = payload,
                handler = listeningEventHandler,
            )
            logger.info { "🎧 EVENT QUEUED SUCCESSFULLY: id=$eventId" }
        } catch (e: Exception) {
            logger.error(e) { "🎧 FAILED TO QUEUE EVENT: id=$eventId" }
        }
    }

    /**
     * Fire-and-forget sync attempt via the push sync orchestrator.
     */
    private fun trySyncEvents() {
        logger.info { "🎧 TRIGGERING SYNC FLUSH..." }
        scope.launch {
            try {
                pushSyncOrchestrator.flush()
                logger.info { "🎧 SYNC FLUSH COMPLETED" }
            } catch (e: Exception) {
                logger.error(e) { "🎧 SYNC FLUSH FAILED" }
            }
        }
    }
}
