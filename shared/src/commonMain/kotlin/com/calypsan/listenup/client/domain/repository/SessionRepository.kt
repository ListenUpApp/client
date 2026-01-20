package com.calypsan.listenup.client.domain.repository

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.domain.model.BookReadersResult
import com.calypsan.listenup.client.domain.model.ReaderInfo
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for active session/reader operations.
 *
 * Provides access to information about users currently listening to books.
 * Used to display "who's listening" on book detail screens.
 *
 * Follows offline-first pattern: data is cached locally and synced via SSE.
 *
 * Part of the domain layer - implementations live in the data layer.
 */
interface SessionRepository {
    /**
     * Get all users currently reading a specific book.
     *
     * @param bookId The book ID to check
     * @return List of readers currently listening to this book
     */
    suspend fun getBookReaders(bookId: String): List<ReaderInfo>

    /**
     * Get comprehensive book readers information including the current user's
     * reading history and other readers.
     *
     * @param bookId The book ID to get readers for
     * @return Result containing readers information or error
     */
    suspend fun getBookReadersResult(bookId: String): Result<BookReadersResult>

    /**
     * Observe book readers reactively from local cache.
     *
     * Returns a Flow that emits reader data from the local Room cache.
     * Triggers a background refresh from the API to keep data fresh.
     * SSE events also update the cache in real-time.
     *
     * Follows offline-first pattern: UI observes local cache, which is
     * updated asynchronously from network.
     *
     * @param bookId The book ID to observe readers for
     * @return Flow emitting reader data as it changes
     */
    fun observeBookReaders(bookId: String): Flow<BookReadersResult>

    /**
     * Refresh book readers from the server.
     *
     * Fetches the latest reader data from the API and updates the local cache.
     * Called automatically by observeBookReaders but can be invoked manually
     * for pull-to-refresh scenarios.
     *
     * @param bookId The book ID to refresh readers for
     */
    suspend fun refreshBookReaders(bookId: String)
}
