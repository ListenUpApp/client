package com.calypsan.listenup.client.test.fake

import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.domain.model.Book
import com.calypsan.listenup.client.domain.model.Chapter
import com.calypsan.listenup.client.domain.repository.BookRepository
import com.calypsan.listenup.client.domain.repository.DiscoveryBook
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory fake of [BookRepository]. Backed by a [MutableStateFlow] of the book list
 * so `observeBooks()` emits on every `setBooks` call; chapters live in a parallel map.
 *
 * [refreshCount] is tracked for tests that care about the refresh side effect.
 * Discovery flows are derived on every emission and take the first [limit] items
 * after the appropriate ordering — deterministic for tests and close enough to
 * production for seam verification.
 */
class FakeBookRepository(
    initialBooks: List<Book> = emptyList(),
    initialChapters: Map<String, List<Chapter>> = emptyMap(),
) : BookRepository {
    private val books = MutableStateFlow(initialBooks)
    private val chaptersByBookId = initialChapters.toMutableMap()
    private var _refreshCount = 0

    /** Number of times [refreshBooks] was called. */
    val refreshCount: Int get() = _refreshCount

    override fun observeBooks(): Flow<List<Book>> = books.asStateFlow()

    override suspend fun refreshBooks(): Result<Unit> {
        _refreshCount++
        return Result.Success(Unit)
    }

    override suspend fun getBook(id: String): Book? = books.value.firstOrNull { it.id == BookId(id) }

    override suspend fun getBooks(ids: List<String>): List<Book> {
        val wanted = ids.map(::BookId).toSet()
        return books.value.filter { it.id in wanted }
    }

    override suspend fun getChapters(bookId: String): List<Chapter> = chaptersByBookId[bookId].orEmpty()

    override fun observeRandomUnstartedBooks(limit: Int): Flow<List<DiscoveryBook>> =
        books.asStateFlow().map { list -> list.take(limit).map(::toDiscoveryBook) }

    override fun observeRecentlyAddedBooks(limit: Int): Flow<List<DiscoveryBook>> =
        books.asStateFlow().map { list ->
            list.sortedByDescending { it.addedAt.epochMillis }.take(limit).map(::toDiscoveryBook)
        }

    /** Test helper: replace the book list, emitting to all observers. */
    fun setBooks(list: List<Book>) {
        books.value = list
    }

    /** Test helper: set chapters for [bookId]. */
    fun setChapters(
        bookId: String,
        chapters: List<Chapter>,
    ) {
        chaptersByBookId[bookId] = chapters
    }

    private fun toDiscoveryBook(book: Book): DiscoveryBook =
        DiscoveryBook(
            id = book.id.value,
            title = book.title,
            authorName = book.authors.firstOrNull()?.name,
            coverPath = book.coverPath,
            coverBlurHash = book.coverBlurHash,
            createdAt = book.addedAt.epochMillis,
        )
}
