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

    /**
     * Fetch discover lenses from API and cache locally.
     *
     * Used for initial population of discover lenses when Room is empty,
     * and for manual refresh. Fetches lenses from other users via API
     * and stores them in the local database.
     *
     * @return Number of lenses fetched and cached
     */
    suspend fun fetchAndCacheDiscoverLenses(): Int

    /**
     * Get full lens detail including books from the server.
     *
     * Fetches lens details via API and updates local cache.
     *
     * @param lensId The lens ID to fetch
     * @return The lens detail with books
     */
    suspend fun getLensDetail(lensId: String): com.calypsan.listenup.client.domain.model.LensDetail

    /**
     * Remove a book from a lens.
     *
     * @param lensId The lens to remove from
     * @param bookId The book to remove
     */
    suspend fun removeBookFromLens(lensId: String, bookId: String)

    /**
     * Add books to a lens.
     *
     * @param lensId The lens to add to
     * @param bookIds The books to add
     */
    suspend fun addBooksToLens(lensId: String, bookIds: List<String>)

    /**
     * Create a new lens.
     *
     * @param name The lens name
     * @param description Optional description
     * @return The created lens
     */
    suspend fun createLens(name: String, description: String?): Lens

    /**
     * Update an existing lens.
     *
     * @param lensId The lens ID to update
     * @param name The new name
     * @param description The new description (null to clear)
     * @return The updated lens
     */
    suspend fun updateLens(lensId: String, name: String, description: String?): Lens

    /**
     * Delete a lens.
     *
     * @param lensId The lens ID to delete
     */
    suspend fun deleteLens(lensId: String)
}
