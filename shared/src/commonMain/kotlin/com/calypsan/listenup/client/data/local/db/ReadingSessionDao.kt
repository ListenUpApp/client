package com.calypsan.listenup.client.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [ReadingSessionEntity] operations.
 *
 * Provides reactive (Flow-based) and one-shot queries for the "Readers" section
 * on book detail pages. Sessions are stored locally for offline-first display.
 *
 * Sessions are keyed by bookId + userId, allowing updates from both:
 * - API response (initial load / refresh)
 * - SSE events (real-time updates)
 */
@Dao
interface ReadingSessionDao {
    /**
     * Observe all reading sessions for a specific book.
     * Used for the "Readers" section on book detail page.
     * Orders by whether currently reading (active first), then by most recent activity.
     *
     * @param bookId The book ID
     * @return Flow emitting list of reading sessions
     */
    @Query(
        """
        SELECT * FROM reading_sessions
        WHERE bookId = :bookId
        ORDER BY isCurrentlyReading DESC, updatedAt DESC
        """,
    )
    fun observeByBookId(bookId: String): Flow<List<ReadingSessionEntity>>

    /**
     * Get all reading sessions for a book synchronously.
     * Used for one-shot queries when Flow isn't needed.
     *
     * @param bookId The book ID
     * @return List of reading sessions
     */
    @Query(
        """
        SELECT * FROM reading_sessions
        WHERE bookId = :bookId
        ORDER BY isCurrentlyReading DESC, updatedAt DESC
        """,
    )
    suspend fun getByBookId(bookId: String): List<ReadingSessionEntity>

    /**
     * Get a specific reading session by book and user.
     *
     * @param bookId The book ID
     * @param userId The user ID
     * @return The reading session or null if not found
     */
    @Query("SELECT * FROM reading_sessions WHERE bookId = :bookId AND oduserId = :userId")
    suspend fun getByBookAndUser(bookId: String, userId: String): ReadingSessionEntity?

    /**
     * Insert or update a reading session entity.
     * If a session with the same ID exists, it will be updated.
     *
     * @param session The reading session entity to upsert
     */
    @Upsert
    suspend fun upsert(session: ReadingSessionEntity)

    /**
     * Insert or update multiple reading session entities in a single transaction.
     * Used when refreshing from API response.
     *
     * @param sessions List of reading session entities to upsert
     */
    @Upsert
    suspend fun upsertAll(sessions: List<ReadingSessionEntity>)

    /**
     * Delete all reading sessions for a specific book.
     * Used when refreshing from API to replace stale data.
     *
     * @param bookId The book ID
     */
    @Query("DELETE FROM reading_sessions WHERE bookId = :bookId")
    suspend fun deleteByBookId(bookId: String)

    /**
     * Delete a specific reading session by ID.
     *
     * @param id The session ID
     */
    @Query("DELETE FROM reading_sessions WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Delete all reading sessions.
     * Used for testing and full re-sync scenarios.
     */
    @Query("DELETE FROM reading_sessions")
    suspend fun deleteAll()

    /**
     * Count total reading sessions.
     * Used for debugging and monitoring.
     */
    @Query("SELECT COUNT(*) FROM reading_sessions")
    suspend fun count(): Int

    /**
     * Count reading sessions for a specific book.
     *
     * @param bookId The book ID
     * @return Number of readers for this book
     */
    @Query("SELECT COUNT(*) FROM reading_sessions WHERE bookId = :bookId")
    suspend fun countByBookId(bookId: String): Int

    /**
     * Count completed readings for a specific book.
     *
     * @param bookId The book ID
     * @return Total number of completions (sum of completionCount for all users)
     */
    @Query("SELECT COALESCE(SUM(completionCount), 0) FROM reading_sessions WHERE bookId = :bookId")
    suspend fun countCompletionsByBookId(bookId: String): Int
}
