package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.core.currentEpochMilliseconds
import com.calypsan.listenup.client.data.local.db.PlaybackPositionDao
import com.calypsan.listenup.client.data.local.db.PlaybackPositionEntity
import com.calypsan.listenup.client.data.remote.SyncApiContract
import com.calypsan.listenup.client.data.local.db.EntityType
import com.calypsan.listenup.client.data.local.db.OperationType
import com.calypsan.listenup.client.data.sync.push.MarkCompleteHandler
import com.calypsan.listenup.client.data.sync.push.MarkCompletePayload
import com.calypsan.listenup.client.data.sync.push.PendingOperationRepositoryContract
import com.calypsan.listenup.client.domain.model.PlaybackPosition
import com.calypsan.listenup.client.domain.repository.PlaybackPositionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Implementation of PlaybackPositionRepository using Room.
 *
 * Wraps PlaybackPositionDao and converts entities to domain models.
 * Position operations are instant and local-first.
 *
 * The mark/discard/restart operations use optimistic updates:
 * 1. Update local database immediately for instant UI response
 * 2. Sync to server in background
 * 3. Rollback local changes if server sync fails
 *
 * @property dao Room DAO for position operations
 * @property syncApi API for syncing progress changes to server
 */
class PlaybackPositionRepositoryImpl(
    private val dao: PlaybackPositionDao,
    private val syncApi: SyncApiContract,
    private val pendingOps: PendingOperationRepositoryContract,
    private val markCompleteHandler: MarkCompleteHandler,
) : PlaybackPositionRepository {
    override suspend fun get(bookId: String): PlaybackPosition? = dao.get(BookId(bookId))?.toDomain()

    override fun observe(bookId: String): Flow<PlaybackPosition?> = dao.observe(BookId(bookId)).map { it?.toDomain() }

    override fun observeAll(): Flow<Map<String, PlaybackPosition>> =
        dao.observeAll().map { positions ->
            positions.associate { it.bookId.value to it.toDomain() }
        }

    override suspend fun getRecentPositions(limit: Int): List<PlaybackPosition> =
        dao.getRecentPositions(limit).map { it.toDomain() }

    override suspend fun save(
        bookId: String,
        positionMs: Long,
        playbackSpeed: Float,
        hasCustomSpeed: Boolean,
    ) {
        val now = currentEpochMilliseconds()
        val existing = dao.get(BookId(bookId))

        val entity =
            PlaybackPositionEntity(
                bookId = BookId(bookId),
                positionMs = positionMs,
                playbackSpeed = playbackSpeed,
                hasCustomSpeed = hasCustomSpeed,
                updatedAt = now,
                syncedAt = existing?.syncedAt,
                lastPlayedAt = now,
                isFinished = existing?.isFinished ?: false,
                finishedAt = existing?.finishedAt,
                startedAt = existing?.startedAt ?: now, // Set on first save
            )
        dao.save(entity)
    }

    override suspend fun delete(bookId: String) {
        dao.delete(BookId(bookId))
    }

    override suspend fun markComplete(
        bookId: String,
        startedAt: Long?,
        finishedAt: Long?,
    ): Result<Unit> {
        logger.debug { "markComplete: $bookId" }
        val existing = dao.get(BookId(bookId))
        val now = currentEpochMilliseconds()
        val effectiveFinishedAt = finishedAt ?: now
        val effectiveStartedAt = startedAt ?: existing?.startedAt ?: now

        // Optimistic local update
        val updated =
            existing?.copy(
                isFinished = true,
                finishedAt = effectiveFinishedAt,
                startedAt = effectiveStartedAt,
                updatedAt = now,
            ) ?: PlaybackPositionEntity(
                bookId = BookId(bookId),
                positionMs = 0,
                playbackSpeed = 1.0f,
                hasCustomSpeed = false,
                updatedAt = now,
                lastPlayedAt = now,
                isFinished = true,
                finishedAt = effectiveFinishedAt,
                startedAt = effectiveStartedAt,
            )
        dao.save(updated)

        // Convert to ISO 8601 for API
        val startedAtIso = epochMillisToIso8601(effectiveStartedAt)
        val finishedAtIso = epochMillisToIso8601(effectiveFinishedAt)

        // Sync to server
        return when (val result = syncApi.markComplete(bookId, startedAt = startedAtIso, finishedAt = finishedAtIso)) {
            is Success -> {
                logger.info { "markComplete: synced $bookId to server" }
                Success(Unit)
            }

            is Failure -> {
                logger.warn {
                    "markComplete: server sync failed for $bookId, enqueueing for retry"
                }
                // Do NOT rollback. Optimistic local update is correct.
                // Enqueue for retry so PushSyncOrchestrator will keep trying,
                // preventing ProgressPuller from overwriting local isFinished=true.
                pendingOps.queue(
                    type = OperationType.MARK_COMPLETE,
                    entityType = EntityType.BOOK,
                    entityId = bookId,
                    payload =
                        MarkCompletePayload(
                            bookId = bookId,
                            startedAt = startedAtIso,
                            finishedAt = finishedAtIso,
                        ),
                    handler = markCompleteHandler,
                )
                // Return success since local state is correct and retry is enqueued
                Success(Unit)
            }
        }
    }

    override suspend fun discardProgress(bookId: String): Result<Unit> {
        logger.debug { "discardProgress: $bookId" }
        val existing = dao.get(BookId(bookId))

        // Optimistic local delete
        dao.delete(BookId(bookId))

        // Sync to server
        return when (val result = syncApi.discardProgress(bookId, keepHistory = true)) {
            is Success -> {
                logger.info { "discardProgress: synced $bookId to server" }
                Success(Unit)
            }

            is Failure -> {
                logger.warn { "discardProgress: server sync failed for $bookId, rolling back" }
                // Rollback on failure - restore previous state
                if (existing != null) {
                    dao.save(existing)
                }
                result
            }
        }
    }

    override suspend fun restartBook(bookId: String): Result<Unit> {
        logger.debug { "restartBook: $bookId" }
        val existing = dao.get(BookId(bookId))
        val now = currentEpochMilliseconds()

        // Optimistic local update
        if (existing != null) {
            val restarted =
                existing.copy(
                    positionMs = 0,
                    isFinished = false,
                    finishedAt = null,
                    updatedAt = now,
                    lastPlayedAt = now,
                    startedAt = now, // New reading session starts now
                )
            dao.save(restarted)
        } else {
            // Create new position if none exists
            val newPosition =
                PlaybackPositionEntity(
                    bookId = BookId(bookId),
                    positionMs = 0,
                    playbackSpeed = 1.0f,
                    hasCustomSpeed = false,
                    updatedAt = now,
                    lastPlayedAt = now,
                    isFinished = false,
                    finishedAt = null,
                    startedAt = now,
                )
            dao.save(newPosition)
        }

        // Sync to server
        return when (val result = syncApi.restartBook(bookId)) {
            is Success -> {
                logger.info { "restartBook: synced $bookId to server" }
                Success(Unit)
            }

            is Failure -> {
                logger.warn { "restartBook: server sync failed for $bookId, rolling back" }
                // Rollback on failure
                if (existing != null) {
                    dao.save(existing)
                } else {
                    dao.delete(BookId(bookId))
                }
                result
            }
        }
    }
}

/**
 * Convert PlaybackPositionEntity to PlaybackPosition domain model.
 */
private fun PlaybackPositionEntity.toDomain(): PlaybackPosition =
    PlaybackPosition(
        bookId = bookId.value,
        positionMs = positionMs,
        playbackSpeed = playbackSpeed,
        hasCustomSpeed = hasCustomSpeed,
        updatedAtMs = updatedAt,
        syncedAtMs = syncedAt,
        lastPlayedAtMs = lastPlayedAt,
        isFinished = isFinished,
        finishedAtMs = finishedAt,
        startedAtMs = startedAt,
    )

/**
 * Convert epoch milliseconds to ISO 8601 string for API communication.
 */
private fun epochMillisToIso8601(millis: Long): String = Instant.fromEpochMilliseconds(millis).toString()
