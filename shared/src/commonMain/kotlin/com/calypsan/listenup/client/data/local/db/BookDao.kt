package com.calypsan.listenup.client.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for [BookEntity] operations.
 *
 * Provides both reactive (Flow-based) and one-shot queries for books,
 * with special support for sync operations (pending changes, state updates).
 *
 * All queries respect soft deletes from the server (deletedAt field will be
 * added in future version when full Book model is implemented).
 *
 * Note: Room @Query annotations require compile-time constants, so we use
 * ordinal values with comments instead of ${SyncState.SYNCED.ordinal} templates.
 */
@Dao
interface BookDao {

    /**
     * Insert or update a book entity.
     * If a book with the same ID exists, it will be updated.
     *
     * @param book The book entity to upsert
     */
    @Upsert
    suspend fun upsert(book: BookEntity)

    /**
     * Insert or update multiple book entities in a single transaction.
     *
     * @param books List of book entities to upsert
     */
    @Upsert
    suspend fun upsertAll(books: List<BookEntity>)

    /**
     * Get a single book by ID.
     * Returns null if book doesn't exist.
     *
     * @param id The type-safe book ID
     * @return The book entity or null
     */
    @Query("SELECT * FROM books WHERE id = :id LIMIT 1")
    suspend fun getById(id: BookId): BookEntity?

    /**
     * Observe all books as a reactive Flow.
     * Emits new list whenever any book changes.
     *
     * Used by UI to display book library with automatic updates.
     *
     * @return Flow emitting list of all books
     */
    @Query("SELECT * FROM books ORDER BY title ASC")
    fun observeAll(): Flow<List<BookEntity>>

    /**
     * Observe all books with their contributors as a reactive Flow.
     *
     * Uses Room Relations to efficiently load books and their contributors
     * in a single batched query, avoiding N+1 query problems.
     *
     * The @Transaction annotation ensures that the book and its related
     * contributors are loaded atomically.
     *
     * @return Flow emitting list of books with their contributors
     */
    @Transaction
    @Query("SELECT * FROM books ORDER BY title ASC")
    fun observeAllWithContributors(): Flow<List<BookWithContributors>>

    /**
     * Get a single book by ID with its contributors.
     *
     * Uses Room Relations to efficiently load the book and its contributors
     * in a single batched query.
     *
     * @param id The type-safe book ID
     * @return The book with contributors or null if not found
     */
    @Transaction
    @Query("SELECT * FROM books WHERE id = :id LIMIT 1")
    suspend fun getByIdWithContributors(id: BookId): BookWithContributors?

    /**
     * Get all books with pending local changes that need to be synced.
     *
     * Returns books in NOT_SYNCED state (local modifications) and
     * CONFLICT state (needs resolution).
     *
     * Used by SyncManager during push phase.
     *
     * @return List of books requiring sync
     */
    @Query("SELECT * FROM books WHERE syncState IN (:states)")
    suspend fun getByStates(states: List<Int>): List<BookEntity>

    /**
     * Get books with pending changes (NOT_SYNCED or CONFLICT states).
     * Convenience method wrapping getByStates.
     */
    suspend fun getPendingChanges(): List<BookEntity> {
        return getByStates(
            listOf(
                SyncState.NOT_SYNCED_ORDINAL,
                SyncState.CONFLICT_ORDINAL
            )
        )
    }

    /**
     * Mark a book as successfully synced with server.
     *
     * Updates syncState to SYNCED and stores server version timestamp.
     * Called after successful upload to server.
     *
     * @param id Type-safe book ID
     * @param serverVersion Server's updated_at timestamp
     */
    @Query("""
        UPDATE books
        SET syncState = ${SyncState.SYNCED_ORDINAL},
            serverVersion = :serverVersion
        WHERE id = :id
    """)
    suspend fun markSynced(id: BookId, serverVersion: Timestamp)

    /**
     * Mark a book as having a sync conflict.
     *
     * Sets syncState to CONFLICT when server has a newer version
     * than our local modifications.
     *
     * @param id Type-safe book ID
     * @param serverVersion Server's newer updated_at timestamp
     */
    @Query("""
        UPDATE books
        SET syncState = ${SyncState.CONFLICT_ORDINAL},
            serverVersion = :serverVersion
        WHERE id = :id
    """)
    suspend fun markConflict(id: BookId, serverVersion: Timestamp)

    /**
     * Delete a book by ID.
     *
     * Hard delete from local database. Soft deletes from server
     * (deletedAt field) will be handled differently in future version.
     *
     * @param id Type-safe book ID to delete
     */
    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: BookId)

    /**
     * Delete all books.
     * Used for testing and full re-sync scenarios.
     */
    @Query("DELETE FROM books")
    suspend fun deleteAll()

    /**
     * Touch a book's updatedAt timestamp to trigger Flow re-emission.
     *
     * Used after cover downloads to force UI updates when cover files
     * appear on disk (even though database content hasn't changed).
     *
     * @param id Type-safe book ID to touch
     */
    @Query("UPDATE books SET updatedAt = :timestamp WHERE id = :id")
    suspend fun touchUpdatedAt(id: BookId, timestamp: Timestamp)
}
