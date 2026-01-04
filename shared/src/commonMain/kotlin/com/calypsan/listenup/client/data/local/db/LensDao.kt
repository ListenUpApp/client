package com.calypsan.listenup.client.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for [LensEntity] operations.
 *
 * Provides both reactive (Flow-based) and one-shot queries for lenses.
 * Lenses are user-created curated lists of books for personal organization
 * and social discovery.
 */
@Dao
interface LensDao {
    /**
     * Observe lenses owned by the specified user, ordered by most recently updated.
     * Used for the "My Lenses" section on the Home screen.
     *
     * @param userId The owner's user ID
     * @return Flow emitting list of user's lenses
     */
    @Query("SELECT * FROM lenses WHERE ownerId = :userId ORDER BY updatedAt DESC")
    fun observeMyLenses(userId: String): Flow<List<LensEntity>>

    /**
     * Observe lenses from other users for the Discover tab, ordered by owner name and lens name.
     * Groups lenses by user for display purposes.
     *
     * @param userId The current user's ID (to exclude their own lenses)
     * @return Flow emitting list of other users' lenses
     */
    @Query("SELECT * FROM lenses WHERE ownerId != :userId ORDER BY ownerDisplayName ASC, name ASC")
    fun observeDiscoverLenses(userId: String): Flow<List<LensEntity>>

    /**
     * Observe a single lens by ID.
     *
     * @param id The lens ID
     * @return Flow emitting the lens or null if not found
     */
    @Query("SELECT * FROM lenses WHERE id = :id")
    fun observeById(id: String): Flow<LensEntity?>

    /**
     * Get a single lens by ID synchronously.
     *
     * @param id The lens ID
     * @return The lens entity or null if not found
     */
    @Query("SELECT * FROM lenses WHERE id = :id")
    suspend fun getById(id: String): LensEntity?

    /**
     * Insert or update a lens entity.
     * If a lens with the same ID exists, it will be updated.
     *
     * @param lens The lens entity to upsert
     */
    @Upsert
    suspend fun upsert(lens: LensEntity)

    /**
     * Insert or update multiple lens entities in a single transaction.
     *
     * @param lenses List of lens entities to upsert
     */
    @Upsert
    suspend fun upsertAll(lenses: List<LensEntity>)

    /**
     * Delete a lens by ID.
     *
     * @param id The lens ID to delete
     */
    @Query("DELETE FROM lenses WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Delete all lenses.
     * Used for testing and full re-sync scenarios.
     */
    @Query("DELETE FROM lenses")
    suspend fun deleteAll()

    /**
     * Count lenses from other users.
     * Used to check if initial fetch is needed.
     *
     * @param userId The current user's ID (to exclude their own lenses)
     * @return Count of other users' lenses
     */
    @Query("SELECT COUNT(*) FROM lenses WHERE ownerId != :userId")
    suspend fun countDiscoverLenses(userId: String): Int
}
