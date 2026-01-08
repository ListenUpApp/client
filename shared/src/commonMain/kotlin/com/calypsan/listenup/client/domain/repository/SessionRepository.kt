package com.calypsan.listenup.client.domain.repository

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.domain.model.BookReadersResult
import com.calypsan.listenup.client.domain.model.ReaderInfo

/**
 * Repository contract for active session/reader operations.
 *
 * Provides access to information about users currently listening to books.
 * Used to display "who's listening" on book detail screens.
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
}
