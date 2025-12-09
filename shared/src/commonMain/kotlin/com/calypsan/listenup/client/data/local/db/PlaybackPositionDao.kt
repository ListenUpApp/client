package com.calypsan.listenup.client.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for [PlaybackPositionEntity] operations.
 *
 * Provides local-first position persistence for instant resume.
 * Position is sacred: saves immediately, syncs eventually.
 */
@Dao
interface PlaybackPositionDao {

    /**
     * Get the saved position for a book.
     *
     * @param bookId The book to get position for
     * @return The position entity or null if never played
     */
    @Query("SELECT * FROM playback_positions WHERE bookId = :bookId")
    suspend fun get(bookId: BookId): PlaybackPositionEntity?

    /**
     * Observe the saved position for a book.
     *
     * @param bookId The book to observe
     * @return Flow emitting position updates
     */
    @Query("SELECT * FROM playback_positions WHERE bookId = :bookId")
    fun observe(bookId: BookId): Flow<PlaybackPositionEntity?>

    /**
     * Save or update position. Instant, local operation.
     *
     * @param position The position to save
     */
    @Upsert
    suspend fun save(position: PlaybackPositionEntity)

    /**
     * Get all positions that haven't been synced to server.
     * Used by sync worker for multi-device support.
     *
     * @return List of positions where syncedAt is null or older than updatedAt
     */
    @Query("SELECT * FROM playback_positions WHERE syncedAt IS NULL OR syncedAt < updatedAt")
    suspend fun getUnsyncedPositions(): List<PlaybackPositionEntity>

    /**
     * Mark a position as synced to server.
     *
     * @param bookId The book whose position was synced
     * @param syncedAt When the sync completed (epoch ms)
     */
    @Query("UPDATE playback_positions SET syncedAt = :syncedAt WHERE bookId = :bookId")
    suspend fun markSynced(bookId: BookId, syncedAt: Long)

    /**
     * Delete position for a book.
     * Used when resetting progress.
     *
     * @param bookId The book to clear position for
     */
    @Query("DELETE FROM playback_positions WHERE bookId = :bookId")
    suspend fun delete(bookId: BookId)

    /**
     * Delete all positions.
     * Used for testing and account logout.
     */
    @Query("DELETE FROM playback_positions")
    suspend fun deleteAll()

    /**
     * Get recently played books for "Continue Listening" section.
     * Returns positions ordered by most recently updated, limited to specified count.
     *
     * @param limit Maximum number of positions to return
     * @return List of positions ordered by updatedAt descending
     */
    @Query("SELECT * FROM playback_positions ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun getRecentPositions(limit: Int): List<PlaybackPositionEntity>

    /**
     * Observe all playback positions.
     * Used for displaying progress indicators throughout the app.
     *
     * @return Flow emitting list of all positions whenever any position changes
     */
    @Query("SELECT * FROM playback_positions")
    fun observeAll(): Flow<List<PlaybackPositionEntity>>
}
