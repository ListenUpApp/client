package com.calypsan.listenup.client.domain.repository

import com.calypsan.listenup.client.domain.model.PlaybackPosition
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for playback position operations.
 *
 * Provides access to saved playback positions for books.
 * Position is sacred data - never lose where the user left off.
 *
 * Part of the domain layer - implementations live in the data layer.
 */
interface PlaybackPositionRepository {
    /**
     * Get the saved position for a specific book.
     *
     * @param bookId The book to get position for
     * @return PlaybackPosition if exists, null if never played
     */
    suspend fun get(bookId: String): PlaybackPosition?

    /**
     * Observe position changes for a specific book reactively.
     *
     * Emits whenever the position changes, enabling real-time
     * progress indicators and resume UI updates.
     *
     * @param bookId The book to observe
     * @return Flow emitting PlaybackPosition or null
     */
    fun observe(bookId: String): Flow<PlaybackPosition?>

    /**
     * Observe all playback positions reactively.
     *
     * Used for displaying progress indicators across the library,
     * showing completion percentages on book cards.
     *
     * @return Flow emitting map of bookId to PlaybackPosition
     */
    fun observeAll(): Flow<Map<String, PlaybackPosition>>

    /**
     * Get recently played books for "Continue Listening" section.
     *
     * Returns positions ordered by most recently played time.
     *
     * @param limit Maximum number of positions to return
     * @return List of positions, most recent first
     */
    suspend fun getRecentPositions(limit: Int): List<PlaybackPosition>

    /**
     * Save a playback position.
     *
     * Instant local operation - syncs eventually.
     * Position updates should never block on network.
     *
     * @param bookId The book to save position for
     * @param positionMs Current position in milliseconds
     * @param playbackSpeed Current playback speed
     * @param hasCustomSpeed Whether user set a custom speed
     */
    suspend fun save(
        bookId: String,
        positionMs: Long,
        playbackSpeed: Float,
        hasCustomSpeed: Boolean,
    )

    /**
     * Delete position for a book.
     *
     * Used when resetting progress or removing a book.
     *
     * @param bookId The book to clear position for
     */
    suspend fun delete(bookId: String)

    /**
     * Mark a book as complete.
     *
     * Optimistic local update followed by server sync.
     * Sets isFinished=true, finishedAt to the provided value (or now).
     * On server failure, rolls back to previous state.
     *
     * @param bookId The book to mark as complete
     * @param startedAt Optional start date in epoch milliseconds (overrides existing)
     * @param finishedAt Optional finish date in epoch milliseconds (defaults to now)
     * @return Result with Unit on success, or Failure on error
     */
    suspend fun markComplete(
        bookId: String,
        startedAt: Long? = null,
        finishedAt: Long? = null,
    ): com.calypsan.listenup.client.core.Result<Unit>

    /**
     * Discard all progress for a book.
     *
     * Optimistic local delete followed by server sync.
     * Removes the position from local storage immediately.
     * On server failure, restores previous position.
     *
     * @param bookId The book to discard progress for
     * @return Result with Unit on success, or Failure on error
     */
    suspend fun discardProgress(bookId: String): com.calypsan.listenup.client.core.Result<Unit>

    /**
     * Restart a book from the beginning.
     *
     * Optimistic local update followed by server sync.
     * Sets position to 0, clears isFinished.
     * On server failure, rolls back to previous state.
     *
     * @param bookId The book to restart
     * @return Result with Unit on success, or Failure on error
     */
    suspend fun restartBook(bookId: String): com.calypsan.listenup.client.core.Result<Unit>
}
