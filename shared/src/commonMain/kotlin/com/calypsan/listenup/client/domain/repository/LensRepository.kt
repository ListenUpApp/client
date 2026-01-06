package com.calypsan.listenup.client.domain.repository

import com.calypsan.listenup.client.domain.model.Lens
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for lens operations.
 *
 * Provides access to user-created curated book lists.
 * Lenses are personal organization tools that enable social discovery.
 *
 * Part of the domain layer - implementations live in the data layer.
 */
interface LensRepository {
    /**
     * Observe lenses owned by a specific user.
     *
     * Used for "My Lenses" section, ordered by most recently updated.
     *
     * @param userId The owner's user ID
     * @return Flow emitting list of user's lenses
     */
    fun observeMyLenses(userId: String): Flow<List<Lens>>

    /**
     * Observe lenses from other users for discovery.
     *
     * Used for social discovery, excludes current user's lenses.
     *
     * @param currentUserId The current user's ID (to exclude)
     * @return Flow emitting list of other users' lenses
     */
    fun observeDiscoverLenses(currentUserId: String): Flow<List<Lens>>

    /**
     * Observe a single lens by ID.
     *
     * @param id The lens ID
     * @return Flow emitting the lens or null
     */
    fun observeById(id: String): Flow<Lens?>

    /**
     * Get a lens by ID synchronously.
     *
     * @param id The lens ID
     * @return Lens if found, null otherwise
     */
    suspend fun getById(id: String): Lens?

    /**
     * Count lenses from other users.
     *
     * Used to check if initial fetch is needed.
     *
     * @param currentUserId The current user's ID (to exclude)
     * @return Count of discover lenses
     */
    suspend fun countDiscoverLenses(currentUserId: String): Int
}
