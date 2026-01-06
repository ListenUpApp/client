package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.data.local.db.CollectionDao
import com.calypsan.listenup.client.data.local.db.CollectionEntity
import com.calypsan.listenup.client.domain.model.Collection
import com.calypsan.listenup.client.domain.repository.CollectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Implementation of CollectionRepository using Room.
 *
 * Wraps CollectionDao and converts entities to domain models.
 *
 * @property dao Room DAO for collection operations
 */
class CollectionRepositoryImpl(
    private val dao: CollectionDao,
) : CollectionRepository {
    override fun observeAll(): Flow<List<Collection>> =
        dao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getAll(): List<Collection> =
        dao.getAll().map { it.toDomain() }

    override suspend fun getById(id: String): Collection? =
        dao.getById(id)?.toDomain()
}

/**
 * Convert CollectionEntity to Collection domain model.
 */
private fun CollectionEntity.toDomain(): Collection =
    Collection(
        id = id,
        name = name,
        bookCount = bookCount,
        createdAtMs = createdAt.epochMillis,
        updatedAtMs = updatedAt.epochMillis,
    )
