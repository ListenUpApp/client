@file:OptIn(ExperimentalTime::class)

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
     */
    private suspend fun savePosition(
        bookId: BookId,
        positionMs: Long,
        speed: Float,
    ) {
        positionDao.save(
            PlaybackPositionEntity(
                bookId = bookId,
                positionMs = positionMs,
                playbackSpeed = speed,
                updatedAt = Clock.System.now().toEpochMilliseconds(),
                syncedAt = null,
            ),
        )
        logger.debug { "Position saved: book=${bookId.value}, position=$positionMs" }
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
     * Get resume position for a book. Local-first.
     */
    suspend fun getResumePosition(bookId: BookId): PlaybackPositionEntity? = positionDao.get(bookId)

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
