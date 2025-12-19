@file:OptIn(ExperimentalTime::class)
@file:Suppress("MagicNumber")

package com.calypsan.listenup.client.playback

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.db.DownloadDao
import com.calypsan.listenup.client.data.local.db.PendingListeningEventDao
import com.calypsan.listenup.client.data.local.db.PendingListeningEventEntity
import com.calypsan.listenup.client.data.local.db.PlaybackPositionDao
import com.calypsan.listenup.client.data.local.db.PlaybackPositionEntity
import com.calypsan.listenup.client.data.remote.ListeningEventRequest
import com.calypsan.listenup.client.data.remote.SyncApiContract
import com.calypsan.listenup.client.data.remote.model.PlaybackProgressResponse
import com.calypsan.listenup.client.util.NanoId
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val logger = KotlinLogging.logger {}

private const val MAX_SYNC_BATCH_SIZE = 50

/**
 * Coordinates position persistence and event recording.
 *
 * Two separate concerns:
 * 1. Position persistence (local-first, instant) - never lose user's place
 * 2. Event recording (append-only, eventually consistent) - listening history
 *
 * Position is sacred: saves immediately on every pause/seek.
 * Events are queued locally and synced when network is available.
 */
class ProgressTracker(
    private val positionDao: PlaybackPositionDao,
    private val eventDao: PendingListeningEventDao,
    private val downloadDao: DownloadDao,
    private val syncApi: SyncApiContract,
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
        logger.debug { "Playback started: book=${bookId.value}, position=$positionMs" }
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
        scope.launch {
            // CONCERN 1: Save position immediately (local, fast)
            savePosition(bookId, positionMs, speed)

            // CONCERN 2: Queue listening event (if meaningful session)
            currentSession?.let { session ->
                if (session.bookId == bookId) {
                    val durationMs = positionMs - session.startPositionMs

                    // Only record if listened for at least 10 seconds
                    if (durationMs >= 10_000) {
                        val event =
                            PendingListeningEventEntity(
                                id = NanoId.generate("evt"),
                                bookId = bookId,
                                startPositionMs = session.startPositionMs,
                                endPositionMs = positionMs,
                                startedAt = session.startedAt,
                                endedAt = Clock.System.now().toEpochMilliseconds(),
                                playbackSpeed = session.playbackSpeed,
                                deviceId = deviceId,
                            )
                        eventDao.insert(event)
                        logger.debug { "Listening event queued: ${event.id}, duration=${durationMs}ms" }

                        // Fire-and-forget sync attempt
                        trySyncEvents()
                    }
                }
            }

            currentSession = null
        }
    }

    /**
     * Called periodically during playback (every 30 seconds).
     * Updates local position, but doesn't create events.
     */
    fun onPositionUpdate(
        bookId: BookId,
        positionMs: Long,
        speed: Float,
    ) {
        scope.launch {
            savePosition(bookId, positionMs, speed)
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
        // Preserve existing hasCustomSpeed value
        val existing = positionDao.get(bookId)
        positionDao.save(
            PlaybackPositionEntity(
                bookId = bookId,
                positionMs = positionMs,
                playbackSpeed = speed,
                hasCustomSpeed = existing?.hasCustomSpeed ?: false,
                updatedAt = Clock.System.now().toEpochMilliseconds(),
                syncedAt = null,
            ),
        )
        logger.debug { "Position saved: book=${bookId.value}, position=$positionMs" }
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
            positionDao.save(
                PlaybackPositionEntity(
                    bookId = bookId,
                    positionMs = positionMs,
                    playbackSpeed = newSpeed,
                    hasCustomSpeed = true, // User explicitly set this speed
                    updatedAt = Clock.System.now().toEpochMilliseconds(),
                    syncedAt = null,
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
            positionDao.save(
                PlaybackPositionEntity(
                    bookId = bookId,
                    positionMs = positionMs,
                    playbackSpeed = defaultSpeed,
                    hasCustomSpeed = false, // Now using universal default
                    updatedAt = Clock.System.now().toEpochMilliseconds(),
                    syncedAt = null,
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
                is Result.Success -> result.data
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
    private suspend fun mergePositions(
        bookId: BookId,
        local: PlaybackPositionEntity?,
        server: PlaybackProgressResponse?,
    ): PlaybackPositionEntity? =
        when {
            local == null && server == null -> null

            local == null && server != null -> {
                // Only server has progress - cache it locally
                val entity = server.toEntity()
                positionDao.save(entity)
                logger.info { "Using server position: ${server.currentPositionMs}ms (first sync)" }
                entity
            }

            server == null -> local

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
                    val event =
                        PendingListeningEventEntity(
                            id = NanoId.generate("evt"),
                            bookId = bookId,
                            startPositionMs = session.startPositionMs,
                            endPositionMs = Long.MAX_VALUE, // Indicates completion
                            startedAt = session.startedAt,
                            endedAt = Clock.System.now().toEpochMilliseconds(),
                            playbackSpeed = session.playbackSpeed,
                            deviceId = deviceId,
                        )
                    eventDao.insert(event)
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
     * Fire-and-forget sync of pending listening events.
     * Called after recording new events; failures are silent (events stay queued).
     */
    private fun trySyncEvents() {
        scope.launch {
            try {
                val pending = eventDao.getPending(MAX_SYNC_BATCH_SIZE)
                if (pending.isEmpty()) return@launch

                val requests =
                    pending.map { event ->
                        ListeningEventRequest(
                            id = event.id,
                            book_id = event.bookId.value,
                            start_position_ms = event.startPositionMs,
                            end_position_ms = event.endPositionMs,
                            started_at = event.startedAt,
                            ended_at = event.endedAt,
                            playback_speed = event.playbackSpeed,
                            device_id = event.deviceId,
                        )
                    }

                when (val result = syncApi.submitListeningEvents(requests)) {
                    is Result.Success -> {
                        // Delete acknowledged events
                        result.data.acknowledged.forEach { id ->
                            eventDao.deleteById(id)
                        }
                        logger.debug { "Synced ${result.data.acknowledged.size} events" }
                    }

                    is Result.Failure -> {
                        // Silent failure - events stay queued for next attempt
                        logger.debug { "Event sync failed: ${result.exception.message}" }
                    }
                }
            } catch (e: Exception) {
                // Network errors, auth errors, etc. - events stay queued
                logger.debug { "Event sync error: ${e.message}" }
            }
        }
    }
}
