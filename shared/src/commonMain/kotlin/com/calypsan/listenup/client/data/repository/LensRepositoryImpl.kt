package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.data.local.db.LensDao
import com.calypsan.listenup.client.data.local.db.LensEntity
import com.calypsan.listenup.client.domain.model.Lens
import com.calypsan.listenup.client.domain.repository.LensRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Implementation of LensRepository using Room.
 *
 * Wraps LensDao and converts entities to domain models.
 *
 * @property dao Room DAO for lens operations
 */
class LensRepositoryImpl(
    private val dao: LensDao,
) : LensRepository {
    override fun observeMyLenses(userId: String): Flow<List<Lens>> =
        dao.observeMyLenses(userId).map { entities ->
            entities.map { it.toDomain() }
        }

    override fun observeDiscoverLenses(currentUserId: String): Flow<List<Lens>> =
        dao.observeDiscoverLenses(currentUserId).map { entities ->
            entities.map { it.toDomain() }
        }

    override fun observeById(id: String): Flow<Lens?> =
        dao.observeById(id).map { it?.toDomain() }

    override suspend fun getById(id: String): Lens? =
        dao.getById(id)?.toDomain()

    override suspend fun countDiscoverLenses(currentUserId: String): Int =
        dao.countDiscoverLenses(currentUserId)
}

/**
 * Convert LensEntity to Lens domain model.
 */
private fun LensEntity.toDomain(): Lens =
    Lens(
        id = id,
        name = name,
        description = description,
        ownerId = ownerId,
        ownerDisplayName = ownerDisplayName,
        ownerAvatarColor = ownerAvatarColor,
        bookCount = bookCount,
        totalDurationSeconds = totalDurationSeconds,
        createdAtMs = createdAt.epochMillis,
        updatedAtMs = updatedAt.epochMillis,
    )
