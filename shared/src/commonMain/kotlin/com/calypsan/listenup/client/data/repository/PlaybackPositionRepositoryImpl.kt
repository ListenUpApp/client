package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.core.currentEpochMilliseconds
import com.calypsan.listenup.client.data.local.db.PlaybackPositionDao
import com.calypsan.listenup.client.data.local.db.PlaybackPositionEntity
import com.calypsan.listenup.client.domain.model.PlaybackPosition
import com.calypsan.listenup.client.domain.repository.PlaybackPositionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Implementation of PlaybackPositionRepository using Room.
 *
 * Wraps PlaybackPositionDao and converts entities to domain models.
 * Position operations are instant and local-first.
 *
 * @property dao Room DAO for position operations
 */
class PlaybackPositionRepositoryImpl(
    private val dao: PlaybackPositionDao,
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
            )
        dao.save(entity)
    }

    override suspend fun delete(bookId: String) {
        dao.delete(BookId(bookId))
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
    )
