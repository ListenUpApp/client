@file:OptIn(ExperimentalTime::class)
@file:Suppress("MagicNumber")

package com.calypsan.listenup.client.playback

import com.calypsan.listenup.client.core.AppResult
import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.data.local.db.PlaybackPositionDao
import com.calypsan.listenup.client.data.local.db.PlaybackPositionEntity
import com.calypsan.listenup.client.data.remote.SyncApiContract
import com.calypsan.listenup.client.data.remote.model.PlaybackProgressResponse
import com.calypsan.listenup.client.data.sync.push.PushSyncOrchestratorContract
import com.calypsan.listenup.client.domain.repository.DownloadRepository
import com.calypsan.listenup.client.domain.repository.ListeningEventRepository
import com.calypsan.listenup.client.domain.repository.PlaybackPositionRepository
import com.calypsan.listenup.client.domain.repository.PlaybackUpdate
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.core.Failure

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
    private val downloadRepository: DownloadRepository,
    private val listeningEventRepository: ListeningEventRepository,
    private val syncApi: SyncApiContract,
    private val pushSyncOrchestrator: PushSyncOrchestratorContract,
    private val positionRepository: PlaybackPositionRepository,
    private val scope: CoroutineScope,
) {
    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Idle)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    /**
     * Called when playback starts/resumes.
     */
    fun onPlaybackStarted(
        bookId: BookId,
        positionMs: Long,
        speed: Float,
    ) {
        val now = Clock.System.now().toEpochMilliseconds()
        _sessionState.value =
            SessionState.Active(
                bookId = bookId,
                chunkStartPositionMs = positionMs,
                chunkStartedAt = now,
                playbackStartPositionMs = positionMs,
                playbackStartedAt = now,
                speed = speed,
            )
        logger.info { "🎧 LISTENING SESSION STARTED: book=${bookId.value}, position=$positionMs, speed=$speed" }

        // Save position immediately so the book appears in Continue Listening right away
        // This ensures even brief playback sessions are tracked.
        // Uses PlaybackStarted (not PeriodicUpdate) so the handler can insert a new row
        // when none exists (never-played book first-play).
        scope.launch {
            when (
                val r =
                    positionRepository.savePlaybackState(
                        bookId = bookId,
                        update = PlaybackUpdate.PlaybackStarted(positionMs = positionMs, speed = speed),
                    )
            ) {
                is AppResult.Success -> {
                    logger.debug { "Initial position recorded: book=${bookId.value}" }
                }

                is AppResult.Failure -> {
                    logger.warn {
                        "Failed to record initial position for ${bookId.value}: ${r.error.message}"
                    }
                }
            }
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
        val now = Clock.System.now().toEpochMilliseconds()

        // Atomic transition: Active(matching bookId) -> Paused; else no-op
        val priorState = _sessionState.value // capture for side effects below
        _sessionState.update { current ->
            if (current is SessionState.Active && current.bookId == bookId) {
                SessionState.Paused(
                    bookId = current.bookId,
                    chunkStartPositionMs = current.chunkStartPositionMs,
                    chunkStartedAt = current.chunkStartedAt,
                    playbackStartPositionMs = current.playbackStartPositionMs,
                    playbackStartedAt = current.playbackStartedAt,
                    pausedAt = now,
                    speed = current.speed,
                )
            } else {
                current
            }
        }

        logPausedTransition(bookId, positionMs, priorState)

        scope.launch {
            // CONCERN 1: Save position immediately
            savePosition(bookId, positionMs, speed)

            // CONCERN 2: Queue listening event (if meaningful session)
            if (priorState is SessionState.Active && priorState.bookId == bookId) {
                val chunkDurationMs = positionMs - priorState.chunkStartPositionMs
                if (chunkDurationMs >= 10_000) {
                    queueListeningEvent(
                        bookId = bookId,
                        startPositionMs = priorState.chunkStartPositionMs,
                        endPositionMs = positionMs,
                        startedAt = priorState.chunkStartedAt,
                        endedAt = now,
                        playbackSpeed = priorState.speed,
                    )
                    trySyncEvents()
                }
            }

            // CONCERN 3: Activity feed — full playback session
            if (priorState is SessionState.Active && priorState.bookId == bookId) {
                val totalDurationMs = positionMs - priorState.playbackStartPositionMs
                if (totalDurationMs >= 30_000) {
                    try {
                        syncApi.endPlaybackSession(bookId.value, totalDurationMs)
                        logger.info {
                            "🎧 ACTIVITY RECORDED: listened to ${totalDurationMs / 1000}s of ${bookId.value}"
                        }
                    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logger.warn { "🎧 Failed to record activity: ${e.message}" }
                    }
                }
            }
        }
    }

    private fun logPausedTransition(
        bookId: BookId,
        positionMs: Long,
        priorState: SessionState,
    ) {
        when (priorState) {
            is SessionState.Active -> {
                if (priorState.bookId != bookId) {
                    logger.warn { "🎧 PAUSE BOOK MISMATCH: state=${priorState.bookId.value}, paused=${bookId.value}" }
                }
            }

            is SessionState.Paused -> {
                logger.warn { "🎧 PAUSE FROM PAUSED — no-op" }
            }

            SessionState.Idle -> {
                logger.warn { "🎧 PAUSE FROM IDLE — no-op" }
            }
        }
        logger.info {
            "🎧 PLAYBACK PAUSED: book=${bookId.value}, position=$positionMs, prior=${priorState::class.simpleName}"
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

            val state = _sessionState.value
            if (state is SessionState.Active && state.bookId == bookId) {
                val chunkDurationMs = positionMs - state.chunkStartPositionMs
                if (chunkDurationMs >= 10_000) {
                    val now = Clock.System.now().toEpochMilliseconds()
                    queueListeningEvent(
                        bookId = bookId,
                        startPositionMs = state.chunkStartPositionMs,
                        endPositionMs = positionMs,
                        startedAt = state.chunkStartedAt,
                        endedAt = now,
                        playbackSpeed = state.speed,
                    )
                    // Atomic chunk-window advance: only update if state is still Active for the same book.
                    // If a concurrent onPlaybackPaused / onBookFinished / onPlaybackStarted has changed
                    // state, leave the new state in place — the chunk has already been recorded.
                    _sessionState.update { current ->
                        if (current is SessionState.Active && current.bookId == bookId) {
                            current.copy(
                                chunkStartPositionMs = positionMs,
                                chunkStartedAt = now,
                                speed = speed,
                            )
                        } else {
                            current
                        }
                    }
                    trySyncEvents()
                }
            }
        }
    }

    /**
     * Save position via the repository seam.
     * Routes through [PlaybackPositionRepository.savePlaybackState] with [PlaybackUpdate.PeriodicUpdate].
     *
     * @param bookId The book to save position for
     * @param positionMs Current position in milliseconds
     * @param speed Current playback speed
     */
    private suspend fun savePosition(
        bookId: BookId,
        positionMs: Long,
        speed: Float,
    ) {
        when (
            val r =
                positionRepository.savePlaybackState(
                    bookId = bookId,
                    update = PlaybackUpdate.PeriodicUpdate(positionMs = positionMs, speed = speed),
                )
        ) {
            is AppResult.Success -> logger.info { "Position saved: book=${bookId.value}, position=$positionMs" }
            is AppResult.Failure -> logger.warn { "Failed to save position for ${bookId.value}: ${r.error.message}" }
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
        // Update session speed atomically if active/paused for this book
        _sessionState.update { current ->
            when {
                current is SessionState.Active && current.bookId == bookId -> current.copy(speed = newSpeed)
                current is SessionState.Paused && current.bookId == bookId -> current.copy(speed = newSpeed)
                else -> current
            }
        }

        scope.launch {
            when (
                val r =
                    positionRepository.savePlaybackState(
                        bookId = bookId,
                        update = PlaybackUpdate.Speed(positionMs = positionMs, speed = newSpeed, custom = true),
                    )
            ) {
                is AppResult.Success -> logger.debug { "Speed changed: book=${bookId.value}, speed=$newSpeed" }
                is AppResult.Failure -> logger.warn { "Failed to change speed for ${bookId.value}: ${r.error.message}" }
            }
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
        // Update session speed atomically if active/paused for this book
        _sessionState.update { current ->
            when {
                current is SessionState.Active && current.bookId == bookId -> current.copy(speed = defaultSpeed)
                current is SessionState.Paused && current.bookId == bookId -> current.copy(speed = defaultSpeed)
                else -> current
            }
        }

        scope.launch {
            when (
                val r =
                    positionRepository.savePlaybackState(
                        bookId = bookId,
                        update = PlaybackUpdate.SpeedReset(positionMs = positionMs, defaultSpeed = defaultSpeed),
                    )
            ) {
                is AppResult.Success -> logger.debug { "Speed reset: book=${bookId.value}, speed=$defaultSpeed" }
                is AppResult.Failure -> logger.warn { "Failed to reset speed for ${bookId.value}: ${r.error.message}" }
            }
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
        val speed =
            when (val s = _sessionState.value) {
                is SessionState.Active -> s.speed
                is SessionState.Paused -> s.speed
                SessionState.Idle -> 1.0f
            }
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

        // 2. Try to get server position within a short deadline.
        //    A slow or unreachable server used to block this call indefinitely,
        //    delaying ExoPlayer startup by 30–60 seconds. We cap at 3 seconds —
        //    if the server doesn't respond in time, local position is good enough.
        val server = withTimeoutOrNull(3_000L) { fetchServerProgress(bookId) }
        if (server == null) {
            logger.debug { "Server progress fetch timed out or was null — using local position" }
        }

        // 3. Merge: latest timestamp wins
        return mergePositions(bookId, local, server)
    }

    /**
     * Fetch progress from server. Returns null on any error (offline, auth, etc.)
     */
    private suspend fun fetchServerProgress(bookId: BookId): PlaybackProgressResponse? =
        try {
            when (val result = syncApi.getProgress(bookId.value)) {
                is Success -> {
                    result.data
                }

                is Failure -> {
                    logger.debug { "Server progress unavailable: ${result.message}" }
                    null
                }
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
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
        val priorState = _sessionState.value
        _sessionState.value = SessionState.Idle

        scope.launch {
            logger.info { "Book finished: ${bookId.value}, finalPosition=$finalPositionMs" }

            // Record listening-event chunk if Active for this book
            if (priorState is SessionState.Active && priorState.bookId == bookId) {
                queueListeningEvent(
                    bookId = bookId,
                    startPositionMs = priorState.chunkStartPositionMs,
                    endPositionMs = finalPositionMs,
                    startedAt = priorState.chunkStartedAt,
                    endedAt = Clock.System.now().toEpochMilliseconds(),
                    playbackSpeed = priorState.speed,
                )
            }

            // Activity-feed (still direct syncApi until Task 8)
            if (priorState is SessionState.Active && priorState.bookId == bookId) {
                val totalDurationMs = finalPositionMs - priorState.playbackStartPositionMs
                if (totalDurationMs >= 30_000) {
                    try {
                        syncApi.endPlaybackSession(bookId.value, totalDurationMs)
                        logger.info { "🎧 ACTIVITY RECORDED (book finished): listened to ${totalDurationMs / 1000}s" }
                    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logger.warn { "🎧 Failed to record activity: ${e.message}" }
                    }
                }
            }

            // Clear any DELETED download records so future playback will auto-download again
            // This means that the next time a user wants to listen to the same book. We assume
            // They want the default behavior again (stream + download)
            try {
                downloadRepository.deleteForBook(bookId.value)
                logger.debug { "Cleared download records for finished book: ${bookId.value}" }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn { "Failed to delete download records for ${bookId.value} (non-fatal): ${e.message}" }
            }

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
        positionRepository.delete(bookId.value)
        logger.info { "Progress cleared for book: ${bookId.value}" }
    }

    /**
     * Get the current session's playback speed.
     * Returns 1.0 if no active session.
     */
    fun getCurrentSpeed(): Float =
        when (val s = _sessionState.value) {
            is SessionState.Active -> s.speed
            is SessionState.Paused -> s.speed
            SessionState.Idle -> 1.0f
        }

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
     * Validate and delegate a listening event to [ListeningEventRepository].
     *
     * Position validation is applied here (not in the repository) because ProgressTracker
     * owns the "what counts as a valid event" decision at the playback layer. The repository
     * owns atomicity of the write, not event semantics.
     */
    private suspend fun queueListeningEvent(
        bookId: BookId,
        startPositionMs: Long,
        endPositionMs: Long,
        startedAt: Long,
        endedAt: Long,
        playbackSpeed: Float,
    ) {
        // Validate positions to prevent corrupted events.
        // Max reasonable audiobook position: 7 days worth of audio (168 hours).
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

        when (
            val result =
                listeningEventRepository.queueListeningEvent(
                    bookId = bookId,
                    startPositionMs = startPositionMs,
                    endPositionMs = endPositionMs,
                    startedAt = startedAt,
                    endedAt = endedAt,
                    playbackSpeed = playbackSpeed,
                )
        ) {
            is AppResult.Success -> {
                logger.info {
                    "🎧 EVENT QUEUED: book=${bookId.value}, start=$startPositionMs, end=$endPositionMs"
                }
            }

            is AppResult.Failure -> {
                logger.warn {
                    "🎧 FAILED TO QUEUE EVENT: book=${bookId.value}: ${result.error.message}"
                }
            }
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
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(e) { "🎧 SYNC FLUSH FAILED" }
            }
        }
    }
}
