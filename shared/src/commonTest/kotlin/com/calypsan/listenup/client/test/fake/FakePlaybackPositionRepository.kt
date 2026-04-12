package com.calypsan.listenup.client.test.fake

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.domain.model.PlaybackPosition
import com.calypsan.listenup.client.domain.repository.PlaybackPositionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory fake of [PlaybackPositionRepository] backed by a [MutableStateFlow] of
 * book-id → position. Emits on every write so reactive consumers (`observe`, `observeAll`)
 * behave like the real Room-backed implementation.
 *
 * Why a fake instead of a Mokkery mock: the bugs this interface participates in (Bug 2 —
 * completion persistence, Bug 3 — speed stickiness, Bug 6 — position sync) are all
 * read-after-write bugs. A mock can only verify calls happened; this fake reproduces
 * the read-back semantics so seam tests can catch write-that-doesn't-stick regressions.
 *
 * @param initialPositions seed state for the fake (useful for test fixtures).
 * @param nowMs timestamp provider — defaults to `0L` so tests can use
 *     `TestScope.currentTime` for deterministic time, or override for specific moments.
 */
class FakePlaybackPositionRepository(
    initialPositions: Map<String, PlaybackPosition> = emptyMap(),
    private val nowMs: () -> Long = { 0L },
) : PlaybackPositionRepository {
    private val state = MutableStateFlow(initialPositions)

    override suspend fun get(bookId: String): PlaybackPosition? = state.value[bookId]

    override fun observe(bookId: String): Flow<PlaybackPosition?> = state.asStateFlow().map { it[bookId] }

    override fun observeAll(): Flow<Map<String, PlaybackPosition>> = state.asStateFlow()

    override suspend fun getRecentPositions(limit: Int): List<PlaybackPosition> =
        state.value.values
            .sortedByDescending { it.effectiveLastPlayedAtMs }
            .take(limit)

    override suspend fun save(
        bookId: String,
        positionMs: Long,
        playbackSpeed: Float,
        hasCustomSpeed: Boolean,
    ) {
        val now = nowMs()
        state.value = state.value + (
            bookId to
                PlaybackPosition(
                    bookId = bookId,
                    positionMs = positionMs,
                    playbackSpeed = playbackSpeed,
                    hasCustomSpeed = hasCustomSpeed,
                    updatedAtMs = now,
                    syncedAtMs = null,
                    lastPlayedAtMs = now,
                    isFinished = state.value[bookId]?.isFinished ?: false,
                    finishedAtMs = state.value[bookId]?.finishedAtMs,
                    startedAtMs = state.value[bookId]?.startedAtMs,
                )
        )
    }

    override suspend fun delete(bookId: String) {
        state.value = state.value - bookId
    }

    override suspend fun markComplete(
        bookId: String,
        startedAt: Long?,
        finishedAt: Long?,
    ): Result<Unit> {
        val now = nowMs()
        val existing =
            state.value[bookId] ?: PlaybackPosition(
                bookId = bookId,
                positionMs = 0L,
                playbackSpeed = 1.0f,
                hasCustomSpeed = false,
                updatedAtMs = now,
                syncedAtMs = null,
                lastPlayedAtMs = now,
            )
        state.value = state.value + (
            bookId to
                existing.copy(
                    isFinished = true,
                    finishedAtMs = finishedAt ?: now,
                    startedAtMs = startedAt ?: existing.startedAtMs,
                    updatedAtMs = now,
                )
        )
        return Result.Success(Unit)
    }

    override suspend fun discardProgress(bookId: String): Result<Unit> {
        state.value = state.value - bookId
        return Result.Success(Unit)
    }

    override suspend fun restartBook(bookId: String): Result<Unit> {
        val existing = state.value[bookId] ?: return Result.Success(Unit)
        state.value = state.value + (
            bookId to
                existing.copy(
                    positionMs = 0L,
                    isFinished = false,
                    finishedAtMs = null,
                    updatedAtMs = nowMs(),
                )
        )
        return Result.Success(Unit)
    }
}
