package com.calypsan.listenup.client.domain.repository

import com.calypsan.listenup.client.domain.model.Collection
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for collection operations.
 *
 * Provides access to admin-managed book collections.
 * Collections are organizational groups created by administrators.
 *
 * Part of the domain layer - implementations live in the data layer.
 */
interface CollectionRepository {
    /**
     * Observe all collections reactively, ordered by name.
     *
     * @return Flow emitting list of all collections
     */
    fun observeAll(): Flow<List<Collection>>

    /**
     * Get all collections synchronously.
     *
     * @return List of all collections
     */
    suspend fun getAll(): List<Collection>

    /**
     * Get a collection by ID.
     *
     * @param id The collection ID
     * @return Collection if found, null otherwise
     */
    suspend fun getById(id: String): Collection?
}
