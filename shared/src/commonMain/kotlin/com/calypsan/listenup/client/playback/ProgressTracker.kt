@file:OptIn(ExperimentalTime::class)
@file:Suppress("MagicNumber")

package com.calypsan.listenup.client.playback

import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.data.local.db.DownloadDao
import com.calypsan.listenup.client.data.local.db.ListeningEventDao
import com.calypsan.listenup.client.data.local.db.ListeningEventEntity
import com.calypsan.listenup.client.data.local.db.OperationType
import com.calypsan.listenup.client.data.local.db.PlaybackPositionDao
import com.calypsan.listenup.client.data.local.db.PlaybackPositionEntity
import com.calypsan.listenup.client.data.local.db.SyncState
import com.calypsan.listenup.client.data.remote.SyncApiContract
import com.calypsan.listenup.client.data.remote.model.PlaybackProgressResponse
import com.calypsan.listenup.client.data.sync.push.ListeningEventPayload
import com.calypsan.listenup.client.data.sync.push.OperationHandler
import com.calypsan.listenup.client.data.sync.push.PendingOperationRepositoryContract
import com.calypsan.listenup.client.data.sync.push.PushSyncOrchestratorContract
import com.calypsan.listenup.client.domain.repository.PlaybackPositionRepository
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
    private val listeningEventDao: ListeningEventDao,
    private val syncApi: SyncApiContract,
    private val pendingOperationRepository: PendingOperationRepositoryContract,
    private val listeningEventHandler: OperationHandler<ListeningEventPayload>,
    private val pushSyncOrchestrator: PushSyncOrchestratorContract,
    private val positionRepository: PlaybackPositionRepository,
    private val deviceId: String,
    private val scope: CoroutineScope,
) {
    private var currentSession: ListeningSession? = null

    // Track the full playback session for activity feed (not reset every 30 seconds)
    private var playbackSessionStart: PlaybackSessionStart? = null

    /**
     * Represents an active listening session.
     * Created when playback starts, closed when playback pauses.
     * Reset every 30 seconds for listening event chunking.
     */
    data class ListeningSession(
        val bookId: BookId,
        val startPositionMs: Long,
        val startedAt: Long,
        val playbackSpeed: Float,
    )

    /**
     * Tracks when the user pressed play for activity feed purposes.
     * NOT reset every 30 seconds - tracks the full play session.
     */
    data class PlaybackSessionStart(
        val bookId: BookId,
        val startPositionMs: Long,
        val startedAt: Long,
    )

    /**
     * Called when playback starts/resumes.
     */
    fun onPlaybackStarted(
        bookId: BookId,
        positionMs: Long,
        speed: Float,
    ) {
        val now = Clock.System.now().toEpochMilliseconds()
        currentSession =
            ListeningSession(
                bookId = bookId,
                startPositionMs = positionMs,
                startedAt = now,
                playbackSpeed = speed,
            )
        // Also track the full playback session for activity feed
        playbackSessionStart =
            PlaybackSessionStart(
                bookId = bookId,
                startPositionMs = positionMs,
                startedAt = now,
            )
        logger.info { "🎧 LISTENING SESSION STARTED: book=${bookId.value}, position=$positionMs, speed=$speed" }

        // Save position immediately so the book appears in Continue Listening right away
        // This ensures even brief playback sessions are tracked
        scope.launch {
            savePosition(bookId, positionMs, speed)
        }
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
        // Capture playback session start before launching coroutine (to avoid race)
        val playbackStart = playbackSessionStart

        logger.info {
            "🎧 PLAYBACK PAUSED: book=${bookId.value}, position=$positionMs, " +
                "hasSession=${currentSession != null}, hasPlaybackStart=${playbackStart != null}"
        }

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
                val chunkDurationMs = positionMs - session.startPositionMs
                logger.info { "🎧 CHUNK DURATION: ${chunkDurationMs}ms (need >=10000ms to record)" }

                // Only record if listened for at least 10 seconds
                if (chunkDurationMs >= 10_000) {
                    queueListeningEvent(
                        bookId = bookId,
                        startPositionMs = session.startPositionMs,
                        endPositionMs = positionMs,
                        startedAt = session.startedAt,
                        endedAt = Clock.System.now().toEpochMilliseconds(),
                        playbackSpeed = session.playbackSpeed,
                    )
                    logger.info { "🎧 LISTENING EVENT QUEUED: duration=${chunkDurationMs}ms, triggering sync..." }

                    // Fire-and-forget sync attempt
                    trySyncEvents()
                } else {
                    logger.info { "🎧 CHUNK TOO SHORT: ${chunkDurationMs}ms < 10000ms - not recording" }
                }
            }

            // CONCERN 3: Record activity for the activity feed (fire-and-forget)
            // Uses the FULL playback session duration, not just the last chunk
            if (playbackStart != null && playbackStart.bookId == bookId) {
                val totalDurationMs = positionMs - playbackStart.startPositionMs
                logger.info {
                    "🎧 FULL PLAYBACK SESSION: startPos=${playbackStart.startPositionMs}, " +
                        "endPos=$positionMs, totalDuration=${totalDurationMs}ms"
                }

                // Only record if listened for at least 30 seconds (matches server threshold)
                if (totalDurationMs >= 30_000) {
                    try {
                        syncApi.endPlaybackSession(bookId.value, totalDurationMs)
                        logger.info {
                            "🎧 ACTIVITY RECORDED: listened to ${totalDurationMs / 1000}s of ${bookId.value}"
                        }
                    } catch (e: Exception) {
                        logger.warn { "🎧 Failed to record activity: ${e.message}" }
                    }
                } else {
                    logger.info { "🎧 PLAYBACK TOO SHORT FOR ACTIVITY: ${totalDurationMs}ms < 30000ms" }
                }
            } else {
                logger.debug { "🎧 No playback session to record activity for" }
            }
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
                    currentSession =
                        ListeningSession(
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
     *
     * @param bookId The book to save position for
     * @param positionMs Current position in milliseconds
     * @param speed Current playback speed
     * @param totalDurationMs Optional total duration for defensive completion check (Issue #208)
     */
    private suspend fun savePosition(
        bookId: BookId,
        positionMs: Long,
        speed: Float,
        totalDurationMs: Long? = null,
    ) {
        try {
            // Preserve existing hasCustomSpeed and isFinished values
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
                    isFinished = existing?.isFinished ?: false,
                ),
            )
            logger.info { "Position saved: book=${bookId.value}, position=$positionMs, lastPlayedAt=$now" }

            // Defensive check: mark complete if position >= total duration (Issue #208)
            if (totalDurationMs != null && positionMs >= totalDurationMs && existing?.isFinished != true) {
                positionRepository.markComplete(
                    bookId = bookId.value,
                    startedAt = null,
                    finishedAt = now,
                )
                logger.info { "Book defensively marked complete: ${bookId.value} (position $positionMs >= duration $totalDurationMs)" }
            }
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
            val existing = positionDao.get(bookId)
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
                    isFinished = existing?.isFinished ?: false,
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
            val existing = positionDao.get(bookId)
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
                    isFinished = existing?.isFinished ?: false,
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
                    logger.debug { "Server progress unavailable: ${result.message}" }
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
                // Compare using lastPlayedAt consistently. Previously this compared
                // server.lastPlayedAt against local.updatedAt, but caching server
                // positions set updatedAt to Clock.System.now(), inflating it beyond
                // the server timestamp and causing stale local positions to win.
                val serverTimestamp = server.lastPlayedAtMillis()
                val localTimestamp = local!!.lastPlayedAt ?: local.updatedAt
                if (serverTimestamp > localTimestamp) {
                    // Server is newer (listened on another device)
                    // Preserve local speed settings — server doesn't track per-position speed
                    val entity =
                        server.toEntity().copy(
                            playbackSpeed = local.playbackSpeed,
                            hasCustomSpeed = local.hasCustomSpeed,
                        )
                    positionDao.save(entity)
                    logger.info {
                        "Using server position: ${server.currentPositionMs}ms " +
                            "(was ${local.positionMs}ms locally, server is ${(serverTimestamp - localTimestamp) / 1000}s newer)"
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
     *
     * @param bookId The book that finished
     * @param finalPositionMs The final position (typically the book's total duration)
     */
    fun onBookFinished(
        bookId: BookId,
        finalPositionMs: Long,
    ) {
        // Capture and clear atomically before launching coroutine
        val session = currentSession
        val playbackStart = playbackSessionStart
        currentSession = null
        playbackSessionStart = null

        scope.launch {
            logger.info { "Book finished: ${bookId.value}, finalPosition=$finalPositionMs" }

            // Record completion event
            session?.let {
                if (it.bookId == bookId) {
                    queueListeningEvent(
                        bookId = bookId,
                        startPositionMs = it.startPositionMs,
                        endPositionMs = finalPositionMs,
                        startedAt = it.startedAt,
                        endedAt = Clock.System.now().toEpochMilliseconds(),
                        playbackSpeed = it.playbackSpeed,
                    )
                }
            }

            // Record activity for the activity feed
            if (playbackStart != null && playbackStart.bookId == bookId) {
                val totalDurationMs = finalPositionMs - playbackStart.startPositionMs
                if (totalDurationMs >= 30_000) {
                    try {
                        syncApi.endPlaybackSession(bookId.value, totalDurationMs)
                        logger.info { "🎧 ACTIVITY RECORDED (book finished): listened to ${totalDurationMs / 1000}s" }
                    } catch (e: Exception) {
                        logger.warn { "🎧 Failed to record activity: ${e.message}" }
                    }
                }
            }

            // Clear any DELETED download records so future playback will auto-download again
            // This means that the next time a user wants to listen to the same book. We assume
            // They want the default behavior again (stream + download)
            downloadDao.deleteForBook(bookId.value)
            logger.debug { "Cleared download records for finished book: ${bookId.value}" }

            // Mark book as complete (Issue #206)
            val finishedAt = Clock.System.now().toEpochMilliseconds()
            positionRepository.markComplete(
                bookId = bookId.value,
                startedAt = null,
                finishedAt = finishedAt,
            )
            logger.info { "Book marked complete: ${bookId.value}" }
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
     * Save a listening event to Room and queue for server sync.
     *
     * Offline-first: Event is saved locally first, then queued for sync.
     * This ensures stats are immediately available even when offline.
     */
    private suspend fun queueListeningEvent(
        bookId: BookId,
        startPositionMs: Long,
        endPositionMs: Long,
        startedAt: Long,
        endedAt: Long,
        playbackSpeed: Float,
    ) {
        // Validate positions to prevent corrupted events
        // Max reasonable audiobook position: 7 days worth of audio (168 hours)
        val maxReasonablePositionMs = 7L * 24 * 60 * 60 * 1000 // ~604,800,000ms
        if (endPositionMs < 0 || endPositionMs > maxReasonablePositionMs) {
            logger.error {
                "🎧 REJECTING CORRUPTED EVENT: book=${bookId.value}, " +
                    "endPositionMs=$endPositionMs is invalid (expected 0-$maxReasonablePositionMs)"
            }
            return
        }
        if (startPositionMs < 0 || startPositionMs > endPositionMs) {
            logger.error {
                "🎧 REJECTING CORRUPTED EVENT: book=${bookId.value}, " +
                    "startPositionMs=$startPositionMs is invalid (expected 0-$endPositionMs)"
            }
            return
        }

        val eventId = NanoId.generate("evt")
        val now = Clock.System.now().toEpochMilliseconds()
        logger.info {
            "🎧 CREATING EVENT: id=$eventId, book=${bookId.value}, start=$startPositionMs, end=$endPositionMs"
        }

        // 1. Save to Room first (offline-first)
        val entity =
            ListeningEventEntity(
                id = eventId,
                bookId = bookId.value,
                startPositionMs = startPositionMs,
                endPositionMs = endPositionMs,
                startedAt = startedAt,
                endedAt = endedAt,
                playbackSpeed = playbackSpeed,
                deviceId = deviceId,
                syncState = SyncState.NOT_SYNCED,
                createdAt = now,
            )

        try {
            listeningEventDao.upsert(entity)
            logger.info { "🎧 EVENT SAVED TO ROOM: id=$eventId" }
        } catch (e: Exception) {
            logger.error(e) { "🎧 FAILED TO SAVE EVENT TO ROOM: id=$eventId" }
            // Continue to queue for sync anyway - worst case it syncs without local storage
        }

        // 2. Queue for server sync
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
            logger.info { "🎧 EVENT QUEUED FOR SYNC: id=$eventId" }
        } catch (e: Exception) {
            logger.error(e) { "🎧 FAILED TO QUEUE EVENT FOR SYNC: id=$eventId" }
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
