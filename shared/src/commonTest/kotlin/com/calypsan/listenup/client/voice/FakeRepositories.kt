package com.calypsan.listenup.client.voice

import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.SeriesId
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.core.Timestamp
import com.calypsan.listenup.client.domain.model.Book
import com.calypsan.listenup.client.domain.model.BookContributor
import com.calypsan.listenup.client.domain.model.Chapter
import com.calypsan.listenup.client.domain.model.ContinueListeningBook
import com.calypsan.listenup.client.domain.model.SearchFacets
import com.calypsan.listenup.client.domain.model.SearchHit
import com.calypsan.listenup.client.domain.model.SearchHitType
import com.calypsan.listenup.client.domain.model.SearchResult
import com.calypsan.listenup.client.domain.model.Series
import com.calypsan.listenup.client.domain.model.SeriesSearchResponse
import com.calypsan.listenup.client.domain.model.SeriesWithBooks
import com.calypsan.listenup.client.domain.repository.BookRepository
import com.calypsan.listenup.client.domain.repository.DiscoveryBook
import com.calypsan.listenup.client.domain.repository.HomeRepository
import com.calypsan.listenup.client.domain.repository.SearchRepository
import com.calypsan.listenup.client.domain.repository.SeriesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

// ========== Fake Search Repository ==========

/**
 * Fake implementation of SearchRepository for testing.
 *
 * Configurable search results for testing different resolution scenarios.
 */
class FakeSearchRepository : SearchRepository {
    var searchResults: List<SearchHit> = emptyList()
    var lastQuery: String? = null
    var lastTypes: List<SearchHitType>? = null

    /**
     * Convenience method to set search results from vararg.
     */
    fun setResults(vararg hits: SearchHit) {
        searchResults = hits.toList()
    }

    override suspend fun search(
        query: String,
        types: List<SearchHitType>?,
        genres: List<String>?,
        genrePath: String?,
        limit: Int,
    ): SearchResult {
        lastQuery = query
        lastTypes = types
        return SearchResult(
            query = query,
            total = searchResults.size,
            tookMs = 10,
            hits = searchResults.take(limit),
            facets = SearchFacets(),
            isOfflineResult = false,
        )
    }
}

// ========== Fake Home Repository ==========

/**
 * Fake implementation of HomeRepository for testing.
 *
 * Provides configurable continue listening data for testing resume scenarios.
 */
class FakeHomeRepository : HomeRepository {
    var continueListeningBooks: List<ContinueListeningBook> = emptyList()

    /**
     * Set the continue listening book (most recently played).
     */
    fun setContinueListening(vararg books: ContinueListeningBook) {
        continueListeningBooks = books.toList()
    }

    override suspend fun getContinueListening(limit: Int): Result<List<ContinueListeningBook>> {
        return Success(continueListeningBooks.take(limit))
    }

    override fun observeContinueListening(limit: Int): Flow<List<ContinueListeningBook>> {
        return flowOf(continueListeningBooks.take(limit))
    }
}

// ========== Fake Book Repository ==========

/**
 * Fake implementation of BookRepository for testing.
 *
 * Stores books in memory for testing book lookup scenarios.
 */
class FakeBookRepository : BookRepository {
    private val books = mutableMapOf<String, Book>()
    private val chapters = mutableMapOf<String, List<Chapter>>()

    fun addBook(book: Book) {
        books[book.id.value] = book
    }

    fun addChapters(bookId: String, bookChapters: List<Chapter>) {
        chapters[bookId] = bookChapters
    }

    override fun observeBooks(): Flow<List<Book>> {
        return flowOf(books.values.toList())
    }

    override suspend fun refreshBooks(): Result<Unit> {
        return Success(Unit)
    }

    override suspend fun getBook(id: String): Book? {
        return books[id]
    }

    override suspend fun getChapters(bookId: String): List<Chapter> {
        return chapters[bookId] ?: emptyList()
    }

    override fun observeRandomUnstartedBooks(limit: Int): Flow<List<DiscoveryBook>> {
        return flowOf(emptyList())
    }

    override fun observeRecentlyAddedBooks(limit: Int): Flow<List<DiscoveryBook>> {
        return flowOf(emptyList())
    }
}

// ========== Fake Series Repository ==========

/**
 * Fake implementation of SeriesRepository for testing.
 *
 * Stores series data in memory for testing series navigation scenarios.
 */
class FakeSeriesRepository : SeriesRepository {
    private val seriesMap = mutableMapOf<String, Series>()
    private val seriesBooks = mutableMapOf<String, List<String>>()
    private val seriesWithBooksMap = mutableMapOf<String, SeriesWithBooks>()
    private val bookSeriesMap = mutableMapOf<String, String>()

    fun addSeries(series: Series) {
        seriesMap[series.id.value] = series
    }

    /**
     * Add a series with its book IDs in one call.
     */
    fun addSeries(series: Series, bookIds: List<String>) {
        seriesMap[series.id.value] = series
        seriesBooks[series.id.value] = bookIds
    }

    fun setBookIdsForSeries(seriesId: String, bookIds: List<String>) {
        seriesBooks[seriesId] = bookIds
    }

    fun setSeriesWithBooks(seriesWithBooks: SeriesWithBooks) {
        seriesWithBooksMap[seriesWithBooks.series.id.value] = seriesWithBooks
    }

    fun setSeriesForBook(bookId: String, seriesId: String) {
        bookSeriesMap[bookId] = seriesId
    }

    override fun observeAll(): Flow<List<Series>> {
        return flowOf(seriesMap.values.toList())
    }

    override fun observeById(id: String): Flow<Series?> {
        return flowOf(seriesMap[id])
    }

    override suspend fun getById(id: String): Series? {
        return seriesMap[id]
    }

    override fun observeByBookId(bookId: String): Flow<Series?> {
        val seriesId = bookSeriesMap[bookId]
        return flowOf(seriesId?.let { seriesMap[it] })
    }

    override suspend fun getBookIdsForSeries(seriesId: String): List<String> {
        return seriesBooks[seriesId] ?: emptyList()
    }

    override fun observeBookIdsForSeries(seriesId: String): Flow<List<String>> {
        return flowOf(seriesBooks[seriesId] ?: emptyList())
    }

    override fun observeAllWithBooks(): Flow<List<SeriesWithBooks>> {
        return flowOf(seriesWithBooksMap.values.toList())
    }

    override fun observeSeriesWithBooks(seriesId: String): Flow<SeriesWithBooks?> {
        return flowOf(seriesWithBooksMap[seriesId])
    }

    override suspend fun searchSeries(query: String, limit: Int): SeriesSearchResponse {
        return SeriesSearchResponse(
            series = emptyList(),
            isOfflineResult = false,
            tookMs = 5,
        )
    }
}

// ========== Test Data Builders ==========

/**
 * Create a test book with sensible defaults.
 */
fun testBook(
    id: String = "book-1",
    title: String = "Test Book",
    authorName: String = "Test Author",
    narratorName: String = "Test Narrator",
    seriesId: String? = null,
    seriesName: String? = null,
    seriesSequence: String? = null,
    series: List<com.calypsan.listenup.client.domain.model.BookSeries> = emptyList(),
    duration: Long = 3_600_000, // 1 hour
): Book {
    // Use explicit series list if provided, otherwise build from individual params
    val bookSeries = series.ifEmpty {
        if (seriesId != null && seriesName != null) {
            listOf(
                com.calypsan.listenup.client.domain.model.BookSeries(
                    seriesId = seriesId,
                    seriesName = seriesName,
                    sequence = seriesSequence,
                )
            )
        } else {
            emptyList()
        }
    }

    return Book(
        id = BookId(id),
        title = title,
        authors = listOf(BookContributor(id = "author-1", name = authorName, roles = listOf("author"))),
        narrators = listOf(BookContributor(id = "narrator-1", name = narratorName, roles = listOf("narrator"))),
        duration = duration,
        coverPath = null,
        addedAt = Timestamp(1000),
        updatedAt = Timestamp(1000),
        series = bookSeries,
    )
}

/**
 * Create a test BookSeries with sensible defaults.
 */
fun testBookSeries(
    seriesId: String,
    seriesName: String,
    sequence: String? = null,
) = com.calypsan.listenup.client.domain.model.BookSeries(
    seriesId = seriesId,
    seriesName = seriesName,
    sequence = sequence,
)

/**
 * Create a test series with sensible defaults.
 */
fun testSeries(
    id: String = "series-1",
    name: String = "Test Series",
): Series = Series(
    id = SeriesId(id),
    name = name,
    createdAt = Timestamp(1000),
)

/**
 * Create a test continue listening book with sensible defaults.
 */
fun testContinueListeningBook(
    bookId: String = "book-1",
    title: String = "Test Book",
    authorNames: String = "Test Author",
    progress: Float = 0.5f,
    currentPositionMs: Long = 1_800_000, // 30 minutes
    totalDurationMs: Long = 3_600_000, // 1 hour
    lastPlayedAt: String = "2024-01-15T10:30:00Z",
): ContinueListeningBook = ContinueListeningBook(
    bookId = bookId,
    title = title,
    authorNames = authorNames,
    coverPath = null,
    progress = progress,
    currentPositionMs = currentPositionMs,
    totalDurationMs = totalDurationMs,
    lastPlayedAt = lastPlayedAt,
)

/**
 * Create a test search hit with sensible defaults.
 */
fun testSearchHit(
    id: String = "book-1",
    type: SearchHitType = SearchHitType.BOOK,
    name: String = "Test Book",
    author: String? = "Test Author",
    score: Float = 1.0f,
): SearchHit = SearchHit(
    id = id,
    type = type,
    name = name,
    author = author,
    score = score,
)
