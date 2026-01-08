package com.calypsan.listenup.client.domain.repository

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.domain.model.Book
import com.calypsan.listenup.client.domain.model.Chapter
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for book data operations.
 *
 * Defines the public API for observing and refreshing books.
 * Used by ViewModels and enables testing via fake implementations.
 *
 * Part of the domain layer - implementations live in the data layer.
 */
interface BookRepository {
    /**
     * Observe all books as a reactive Flow of domain models.
     */
    fun observeBooks(): Flow<List<Book>>

    /**
     * Trigger sync to refresh books from server.
     */
    suspend fun refreshBooks(): Result<Unit>

    /**
     * Get a single book by ID.
     */
    suspend fun getBook(id: String): Book?

    /**
     * Get chapters for a book.
     */
    suspend fun getChapters(bookId: String): List<Chapter>

    /**
     * Observe random unstarted books for discovery.
     *
     * Used for "Discover Something New" section.
     * Returns books with no playback position, randomly ordered.
     *
     * @param limit Maximum number of books to return
     * @return Flow emitting list of discovery book summaries
     */
    fun observeRandomUnstartedBooks(limit: Int = 10): Flow<List<DiscoveryBook>>

    /**
     * Observe recently added books for discovery.
     *
     * Used for "Recently Added" section.
     * Returns books ordered by creation date descending.
     *
     * @param limit Maximum number of books to return
     * @return Flow emitting list of discovery book summaries
     */
    fun observeRecentlyAddedBooks(limit: Int = 10): Flow<List<DiscoveryBook>>
}

/**
 * Lightweight book data for discovery sections.
 *
 * Contains only the fields needed for display in discovery carousels,
 * avoiding the overhead of loading full Book models with all contributors.
 */
data class DiscoveryBook(
    val id: String,
    val title: String,
    val authorName: String?,
    val coverPath: String?,
    val coverBlurHash: String?,
    val createdAt: Long,
)
