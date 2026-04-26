package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.AppResult
import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.core.currentEpochMilliseconds
import com.calypsan.listenup.client.core.suspendRunCatching
import com.calypsan.listenup.client.data.local.db.EntityType
import com.calypsan.listenup.client.data.local.db.OperationType
import com.calypsan.listenup.client.data.local.db.PlaybackPositionDao
import com.calypsan.listenup.client.data.local.db.PlaybackPositionEntity
import com.calypsan.listenup.client.data.local.db.TransactionRunner
import com.calypsan.listenup.client.data.remote.SyncApiContract
import com.calypsan.listenup.client.data.sync.push.MarkCompleteHandler
import com.calypsan.listenup.client.data.sync.push.MarkCompletePayload
import com.calypsan.listenup.client.data.sync.push.PendingOperationRepositoryContract
import com.calypsan.listenup.client.domain.model.PlaybackPosition
import com.calypsan.listenup.client.domain.repository.PlaybackPositionRepository
import com.calypsan.listenup.client.domain.repository.PlaybackUpdate
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Implementation of PlaybackPositionRepository using Room.
 *
 * Wraps PlaybackPositionDao and converts entities to domain models.
 * Position operations are instant and local-first.
 *
 * [markComplete] is a thin facade over [savePlaybackState] and uses the outbox
 * pattern: local save + unconditional MARK_COMPLETE pending-op queue (the sync
 * engine processes it asynchronously). [discardProgress] and [restartBook] retain
 * their own implementations with optimistic local update + immediate server sync +
 * rollback on failure — no pending-op infrastructure exists for those operations yet.
 *
 * The [savePlaybackState] entry point owns per-book Mutex serialization
 * plus per-call transactional dispatch over the 11-variant [PlaybackUpdate]
 * sealed hierarchy. Every variant handler runs inside [TransactionRunner.atomically]
 * so each fetch-then-save pair is rollback-safe. Concurrent writes for the same
 * book serialize on a per-book Mutex; different books proceed in parallel.
 *
 * @property dao Room DAO for position operations
 * @property syncApi API for syncing progress changes to server
 * @property pendingOps Queue for pending push operations
 * @property markCompleteHandler Handler for MARK_COMPLETE pending ops
 * @property transactionRunner Runs each variant handler inside a write transaction
 */
class PlaybackPositionRepositoryImpl(
    private val dao: PlaybackPositionDao,
    private val syncApi: SyncApiContract,
    private val pendingOps: PendingOperationRepositoryContract,
    private val markCompleteHandler: MarkCompleteHandler,
    private val transactionRunner: TransactionRunner,
) : PlaybackPositionRepository {
    // ----- Per-book Mutex map ---------------------------------------------------------------

    private val mutexMapLock = Mutex()
    private val mutexes = mutableMapOf<BookId, Mutex>()

    /**
     * Returns the [Mutex] guarding writes for [bookId]. Creates and stores a new
     * Mutex on first access; subsequent accesses for the same book return the same
     * instance. The map grows monotonically; eviction is a future optimization.
     *
     * Holds [mutexMapLock] only for the duration of [getOrPut] so other books'
     * Mutex creations don't block on per-book write durations.
     */
    private suspend fun mutexFor(bookId: BookId): Mutex = mutexMapLock.withLock { mutexes.getOrPut(bookId) { Mutex() } }

    // ----- Read paths -----------------------------------------------------------------------

    override suspend fun get(bookId: String): PlaybackPosition? = dao.get(BookId(bookId))?.toDomain()

    override fun observe(bookId: String): Flow<PlaybackPosition?> = dao.observe(BookId(bookId)).map { it?.toDomain() }

    override fun observeAll(): Flow<Map<String, PlaybackPosition>> =
        dao.observeAll().map { positions ->
            positions.associate { it.bookId.value to it.toDomain() }
        }

    override suspend fun getRecentPositions(limit: Int): List<PlaybackPosition> =
        dao.getRecentPositions(limit).map { it.toDomain() }

    // ----- Write paths -----------------------------------------------------------------------

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
    ): AppResult<Unit> = savePlaybackState(BookId(bookId), PlaybackUpdate.MarkComplete(startedAt, finishedAt))

    override suspend fun discardProgress(bookId: String): AppResult<Unit> {
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

    override suspend fun restartBook(bookId: String): AppResult<Unit> {
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

    // ----- Canonical entry point ------------------------------------------------------------

    override suspend fun savePlaybackState(
        bookId: BookId,
        update: PlaybackUpdate,
    ): AppResult<Unit> =
        suspendRunCatching {
            mutexFor(bookId).withLock {
                transactionRunner.atomically {
                    handle(bookId, update)
                }
            }
        }

    /**
     * Exhaustive dispatcher over the 11-variant [PlaybackUpdate] hierarchy.
     *
     * Adding a new variant produces a `when` exhaustiveness compile error here —
     * the sealed-hierarchy contract every consumer must satisfy.
     */
    private suspend fun handle(
        bookId: BookId,
        update: PlaybackUpdate,
    ) {
        when (update) {
            is PlaybackUpdate.Position -> handlePosition(bookId, update)
            is PlaybackUpdate.Speed -> handleSpeed(bookId, update)
            is PlaybackUpdate.SpeedReset -> handleSpeedReset(bookId, update)
            is PlaybackUpdate.PlaybackStarted -> handlePlaybackStarted(bookId, update)
            is PlaybackUpdate.PlaybackPaused -> handlePlaybackPaused(bookId, update)
            is PlaybackUpdate.PeriodicUpdate -> handlePeriodicUpdate(bookId, update)
            is PlaybackUpdate.BookFinished -> handleBookFinished(bookId, update)
            is PlaybackUpdate.CrossDeviceSync -> handleCrossDeviceSync(bookId, update)
            is PlaybackUpdate.MarkComplete -> handleMarkComplete(bookId, update)
            PlaybackUpdate.DiscardProgress -> handleDiscardProgress(bookId)
            PlaybackUpdate.Restart -> handleRestart(bookId)
        }
    }

    // ----- Per-variant handlers -------------------------------------------------------------

    private suspend fun handlePosition(
        bookId: BookId,
        u: PlaybackUpdate.Position,
    ) {
        // Periodic position save during playback. Use updatePositionOnly to preserve
        // hasCustomSpeed + playbackSpeed against concurrent speed-change writers
        // (PlaybackPositionDao.updatePositionOnly contract).
        val now = currentEpochMilliseconds()
        dao.updatePositionOnly(bookId, u.positionMs, updatedAt = now, lastPlayedAt = now)
    }

    private suspend fun handleSpeed(
        bookId: BookId,
        u: PlaybackUpdate.Speed,
    ) {
        val existing = dao.get(bookId)
        val now = currentEpochMilliseconds()
        val merged =
            existing?.copy(
                positionMs = u.positionMs,
                playbackSpeed = u.speed,
                hasCustomSpeed = u.custom,
                updatedAt = now,
                lastPlayedAt = now,
                syncedAt = null,
            ) ?: blank(bookId, now).copy(
                positionMs = u.positionMs,
                playbackSpeed = u.speed,
                hasCustomSpeed = u.custom,
            )
        dao.save(merged)
    }

    private suspend fun handleSpeedReset(
        bookId: BookId,
        u: PlaybackUpdate.SpeedReset,
    ) {
        val existing = dao.get(bookId)
        val now = currentEpochMilliseconds()
        val merged =
            existing?.copy(
                positionMs = u.positionMs,
                playbackSpeed = u.defaultSpeed,
                hasCustomSpeed = false,
                updatedAt = now,
                lastPlayedAt = now,
                syncedAt = null,
            ) ?: blank(bookId, now).copy(
                positionMs = u.positionMs,
                playbackSpeed = u.defaultSpeed,
                hasCustomSpeed = false,
            )
        dao.save(merged)
    }

    private suspend fun handlePlaybackStarted(
        bookId: BookId,
        u: PlaybackUpdate.PlaybackStarted,
    ) {
        val existing = dao.get(bookId)
        val now = currentEpochMilliseconds()
        val merged =
            existing?.copy(
                positionMs = u.positionMs,
                playbackSpeed = u.speed,
                startedAt = existing.startedAt ?: now, // preserve original startedAt if set
                lastPlayedAt = now,
                updatedAt = now,
                syncedAt = null,
            ) ?: blank(bookId, now).copy(
                positionMs = u.positionMs,
                playbackSpeed = u.speed,
                startedAt = now,
            )
        dao.save(merged)
    }

    private suspend fun handlePlaybackPaused(
        bookId: BookId,
        u: PlaybackUpdate.PlaybackPaused,
    ) {
        // Same shape as Position — periodic position flush; speed preserved via
        // updatePositionOnly (per dao contract).
        val now = currentEpochMilliseconds()
        dao.updatePositionOnly(bookId, u.positionMs, updatedAt = now, lastPlayedAt = now)
    }

    private suspend fun handlePeriodicUpdate(
        bookId: BookId,
        u: PlaybackUpdate.PeriodicUpdate,
    ) {
        val now = currentEpochMilliseconds()
        dao.updatePositionOnly(bookId, u.positionMs, updatedAt = now, lastPlayedAt = now)
    }

    private suspend fun handleBookFinished(
        bookId: BookId,
        u: PlaybackUpdate.BookFinished,
    ) {
        val existing = dao.get(bookId)
        val now = currentEpochMilliseconds()
        val finishedAt = existing?.finishedAt ?: now
        val startedAt = existing?.startedAt ?: now
        val merged =
            existing?.copy(
                positionMs = u.finalPositionMs,
                isFinished = true,
                finishedAt = finishedAt,
                startedAt = startedAt,
                updatedAt = now,
                lastPlayedAt = now,
                syncedAt = null,
            ) ?: blank(bookId, now).copy(
                positionMs = u.finalPositionMs,
                isFinished = true,
                finishedAt = finishedAt,
                startedAt = startedAt,
            )
        dao.save(merged)
        // Queue MARK_COMPLETE for server sync. Mirrors the existing markComplete
        // facade's failure-path queueing semantics so both write paths converge on
        // the same pending-op shape.
        pendingOps.queue(
            type = OperationType.MARK_COMPLETE,
            entityType = EntityType.BOOK,
            entityId = bookId.value,
            payload =
                MarkCompletePayload(
                    bookId = bookId.value,
                    startedAt = epochMillisToIso8601(startedAt),
                    finishedAt = epochMillisToIso8601(finishedAt),
                ),
            handler = markCompleteHandler,
        )
    }

    private suspend fun handleCrossDeviceSync(
        bookId: BookId,
        u: PlaybackUpdate.CrossDeviceSync,
    ) {
        // Reconcile-with-stored: only apply if event is newer than stored.
        // Mirrors `SSEEventProcessor.handleProgressUpdated` lifecycle; ported here
        // so the canonical merge lives in the repository (Phase B's single-writer
        // goal). The handler's caller is responsible for "is this book locally
        // playing? skip" — that's a higher-level policy, not a per-row write rule.
        val payload = u.event.data
        val lastPlayedAtMs = parseIsoOrNull(payload.lastPlayedAt) ?: return
        val finishedAtMs = payload.finishedAt?.let { parseIsoOrNull(it) }
        val startedAtMs = payload.startedAt?.let { parseIsoOrNull(it) }

        val existing = dao.get(bookId)
        if (existing != null && (existing.lastPlayedAt ?: 0L) >= lastPlayedAtMs) {
            // Local is newer — nothing to do.
            return
        }

        val merged =
            existing?.copy(
                positionMs = payload.currentPositionMs,
                isFinished = payload.isFinished,
                lastPlayedAt = lastPlayedAtMs,
                updatedAt = lastPlayedAtMs,
                syncedAt = lastPlayedAtMs,
                // Server omits null timestamps; wire-absence means "no change".
                finishedAt = finishedAtMs ?: existing.finishedAt,
                startedAt = startedAtMs ?: existing.startedAt,
                // playbackSpeed and hasCustomSpeed preserved implicitly via .copy().
            ) ?: PlaybackPositionEntity(
                bookId = bookId,
                positionMs = payload.currentPositionMs,
                playbackSpeed = 1.0f,
                hasCustomSpeed = false,
                isFinished = payload.isFinished,
                lastPlayedAt = lastPlayedAtMs,
                updatedAt = lastPlayedAtMs,
                syncedAt = lastPlayedAtMs,
                finishedAt = finishedAtMs,
                startedAt = startedAtMs,
            )
        dao.save(merged)
    }

    private suspend fun handleMarkComplete(
        bookId: BookId,
        u: PlaybackUpdate.MarkComplete,
    ) {
        val existing = dao.get(bookId)
        val now = currentEpochMilliseconds()
        val effectiveFinishedAt = u.finishedAt ?: now
        val effectiveStartedAt = u.startedAt ?: existing?.startedAt ?: now
        val merged =
            existing?.copy(
                isFinished = true,
                finishedAt = effectiveFinishedAt,
                startedAt = effectiveStartedAt,
                updatedAt = now,
                syncedAt = null,
            ) ?: PlaybackPositionEntity(
                bookId = bookId,
                positionMs = 0L,
                playbackSpeed = 1.0f,
                hasCustomSpeed = false,
                updatedAt = now,
                lastPlayedAt = now,
                isFinished = true,
                finishedAt = effectiveFinishedAt,
                startedAt = effectiveStartedAt,
            )
        dao.save(merged)
        pendingOps.queue(
            type = OperationType.MARK_COMPLETE,
            entityType = EntityType.BOOK,
            entityId = bookId.value,
            payload =
                MarkCompletePayload(
                    bookId = bookId.value,
                    startedAt = epochMillisToIso8601(effectiveStartedAt),
                    finishedAt = epochMillisToIso8601(effectiveFinishedAt),
                ),
            handler = markCompleteHandler,
        )
    }

    private suspend fun handleDiscardProgress(bookId: BookId) {
        // Local-only reset. The existing public `discardProgress(bookId)` facade
        // owns the server-sync side (syncApi.discardProgress + rollback). No
        // pending-op infrastructure exists for DISCARD_PROGRESS; the handler's
        // job is just the local DB write inside the same transaction.
        val existing = dao.get(bookId) ?: return // no-op if no row
        val now = currentEpochMilliseconds()
        dao.save(
            existing.copy(
                positionMs = 0L,
                isFinished = false,
                finishedAt = null,
                updatedAt = now,
                lastPlayedAt = now,
                syncedAt = null,
            ),
        )
    }

    private suspend fun handleRestart(bookId: BookId) {
        // Local-only reset. The existing public `restartBook(bookId)` facade owns
        // the server-sync side (syncApi.restartBook + rollback). No pending-op
        // infrastructure exists for RESTART_BOOK; the handler's job is just the
        // local DB write inside the same transaction.
        val existing = dao.get(bookId) ?: return // no-op if no row
        val now = currentEpochMilliseconds()
        dao.save(
            existing.copy(
                positionMs = 0L,
                isFinished = false,
                finishedAt = null,
                startedAt = now, // new reading session starts now (matches existing facade)
                updatedAt = now,
                lastPlayedAt = now,
                syncedAt = null,
            ),
        )
    }

    /**
     * Construct a fresh blank entity for [bookId] anchored at [now].
     * Used when a variant handler must materialize a row that doesn't yet exist.
     */
    private fun blank(
        bookId: BookId,
        now: Long,
    ): PlaybackPositionEntity =
        PlaybackPositionEntity(
            bookId = bookId,
            positionMs = 0L,
            playbackSpeed = 1.0f,
            hasCustomSpeed = false,
            updatedAt = now,
            syncedAt = null,
            lastPlayedAt = now,
            isFinished = false,
            finishedAt = null,
            startedAt = now,
        )
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

/**
 * Parse ISO 8601 to epoch ms; returns null on malformed input.
 *
 * Mirrors `SSEEventProcessor.parseLastPlayedOrNull` — used by [PlaybackUpdate.CrossDeviceSync]
 * to skip rows whose timestamps the server malformed, leaving the next sync to reconcile.
 */
@Suppress("SwallowedException", "TooGenericExceptionCaught")
private fun parseIsoOrNull(iso: String): Long? =
    try {
        Instant.parse(iso).toEpochMilliseconds()
    } catch (_: Exception) {
        null
    }
