package com.calypsan.listenup.client.domain.repository

import com.calypsan.listenup.client.core.AppResult
import com.calypsan.listenup.client.domain.model.Book
import com.calypsan.listenup.client.domain.model.BookDetail
import com.calypsan.listenup.client.domain.model.BookListItem
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
    suspend fun refreshBooks(): AppResult<Unit>

    /**
     * Get a single book by ID.
     */
    suspend fun getBook(id: String): Book?

    /**
     * Get multiple books by IDs in a single batched query.
     *
     * More efficient than calling getBook in a loop when loading
     * multiple books (e.g., for series navigation).
     *
     * @param ids List of book IDs to fetch
     * @return List of books found (may be fewer than requested if some not found)
     */
    suspend fun getBooks(ids: List<String>): List<Book>

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

    /**
     * Observe all books as a reactive Flow of [BookListItem] projections.
     *
     * The list surface does not carry [BookDetail.genres]/[BookDetail.tags]/
     * [BookDetail.allContributors] — those are detail-only. Use this for home,
     * shelf, library, and any other list/grid consumer.
     *
     * Unlike [observeBookDetail], genre-only or tag-only edits to a book do
     * NOT cause this flow to re-emit — the list projection does not depend on
     * those edges.
     *
     * @return Flow emitting the current list-shaped book projection
     */
    fun observeBookListItems(): Flow<List<BookListItem>>

    /**
     * Get a single [BookListItem] by ID.
     *
     * @param id The book ID
     * @return List-shaped projection, or null if the book doesn't exist.
     */
    suspend fun getBookListItem(id: String): BookListItem?

    /**
     * Get multiple [BookListItem]s by IDs in a single batched query.
     *
     * Uses a SQL IN clause to batch-load. Results are unordered — callers that
     * need a specific order should re-sort on the returned list.
     *
     * @param ids Book IDs to fetch. Empty input returns an empty list.
     * @return List-shaped projections for the books that exist. May be smaller
     *   than the requested input if some IDs aren't in the local DB.
     */
    suspend fun getBookListItems(ids: List<String>): List<BookListItem>

    /**
     * Observe a single book's [BookDetail] reactively.
     *
     * Emits null if the book is absent; emits a [BookDetail] when the row
     * exists. Composes the book Flow with the book's genres Flow and tags
     * Flow — so genre and tag edits flow through to detail-screen consumers
     * without any additional subscription bookkeeping.
     *
     * @param id The book ID
     * @return Flow emitting the current detail shape, or null if absent.
     */
    fun observeBookDetail(id: String): Flow<BookDetail?>

    /**
     * Get a single book's [BookDetail] as a one-shot read.
     *
     * Snapshots the current book row + its genres + its tags. For reactive
     * detail-screen consumption prefer [observeBookDetail].
     *
     * @param id The book ID
     * @return Detail shape, or null if the book doesn't exist.
     */
    suspend fun getBookDetail(id: String): BookDetail?
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
