package com.calypsan.listenup.client.presentation.seriesdetail

import com.calypsan.listenup.client.data.local.db.BookEntity
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.db.BookSeriesCrossRef
import com.calypsan.listenup.client.data.local.db.SeriesDao
import com.calypsan.listenup.client.data.local.db.SeriesEntity
import com.calypsan.listenup.client.data.local.db.SeriesWithBooks
import com.calypsan.listenup.client.data.local.db.SyncState
import com.calypsan.listenup.client.data.local.db.Timestamp
import com.calypsan.listenup.client.data.local.images.ImageStorage
import com.calypsan.listenup.client.data.repository.BookRepositoryContract
import com.calypsan.listenup.client.domain.model.Book
import com.calypsan.listenup.client.domain.model.BookSeries
import com.calypsan.listenup.client.domain.model.Contributor
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for SeriesDetailViewModel.
 *
 * Tests cover:
 * - Initial state
 * - Load series success with books
 * - Load series not found
 * - Books sorted by sequence number
 * - Series description handling
 *
 * Uses Mokkery for mocking SeriesDao and BookRepositoryContract.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SeriesDetailViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    // ========== Test Fixtures ==========

    private class TestFixture {
        val seriesDao: SeriesDao = mock()
        val bookRepository: BookRepositoryContract = mock()
        val imageStorage: ImageStorage = mock()
        val seriesFlow = MutableStateFlow<SeriesWithBooks?>(null)

        fun build(): SeriesDetailViewModel =
            SeriesDetailViewModel(
                seriesDao = seriesDao,
                bookRepository = bookRepository,
                imageStorage = imageStorage,
            )
    }

    private fun createFixture(): TestFixture {
        val fixture = TestFixture()

        // Default stub for observeByIdWithBooks
        every { fixture.seriesDao.observeByIdWithBooks(any()) } returns fixture.seriesFlow

        // Default stub for imageStorage - no series cover exists
        every { fixture.imageStorage.seriesCoverExists(any()) } returns false

        return fixture
    }

    // ========== Test Data Factories ==========

    private fun createSeriesEntity(
        id: String = "series-1",
        name: String = "Test Series",
        description: String? = "A great series",
    ): SeriesEntity =
        SeriesEntity(
            id = id,
            name = name,
            description = description,
            syncState = SyncState.SYNCED,
            lastModified = Timestamp(1704067200000L),
            serverVersion = Timestamp(1704067200000L),
            createdAt = Timestamp(1704067200000L),
            updatedAt = Timestamp(1704067200000L),
        )

    private fun createBookEntity(
        id: String = "book-1",
        title: String = "Test Book",
    ): BookEntity =
        BookEntity(
            id = BookId(id),
            title = title,
            subtitle = null,
            coverUrl = null,
            totalDuration = 3_600_000L,
            description = null,
            genres = null,
            publishYear = 2024,
            audioFilesJson = null,
            syncState = SyncState.SYNCED,
            lastModified = Timestamp(1704067200000L),
            serverVersion = Timestamp(1704067200000L),
            createdAt = Timestamp(1704067200000L),
            updatedAt = Timestamp(1704067200000L),
        )

    private fun createBookSeriesCrossRef(
        bookId: String,
        seriesId: String,
        sequence: String? = null,
    ): BookSeriesCrossRef =
        BookSeriesCrossRef(
            bookId = BookId(bookId),
            seriesId = seriesId,
            sequence = sequence,
        )

    private fun createBook(
        id: String = "book-1",
        title: String = "Test Book",
        seriesSequence: String? = "1",
        seriesId: String = "series-1",
        seriesName: String = "Test Series",
    ): Book =
        Book(
            id = BookId(id),
            title = title,
            subtitle = null,
            authors = listOf(Contributor(id = "author-1", name = "Author", roles = listOf("Author"))),
            narrators = emptyList(),
            duration = 3_600_000L,
            coverPath = null,
            addedAt = Timestamp(1704067200000L),
            updatedAt = Timestamp(1704067200000L),
            series = listOf(BookSeries(seriesId = seriesId, seriesName = seriesName, sequence = seriesSequence)),
        )

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ========== Initial State Tests ==========

    @Test
    fun `initial state has isLoading false and empty data`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()

            // Then
            val state = viewModel.state.value
            assertFalse(state.isLoading)
            assertEquals("", state.seriesName)
            assertTrue(state.books.isEmpty())
            assertNull(state.error)
        }

    // ========== Load Series Tests ==========

    @Test
    fun `loadSeries sets isLoading to true initially`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()

            // When
            viewModel.loadSeries("series-1")
            // Don't advance - check immediate state

            // Then
            assertTrue(viewModel.state.value.isLoading)
        }

    @Test
    fun `loadSeries success populates series data`() =
        runTest {
            // Given
            val fixture = createFixture()
            val series = createSeriesEntity(name = "Epic Fantasy Series", description = "An epic adventure")
            val bookEntity = createBookEntity(id = "book-1", title = "Book One")
            val book = createBook(id = "book-1", title = "Book One")

            everySuspend { fixture.bookRepository.getBook("book-1") } returns book
            val viewModel = fixture.build()

            // When
            viewModel.loadSeries("series-1")
            fixture.seriesFlow.value = SeriesWithBooks(series = series, books = listOf(bookEntity), bookSequences = listOf(createBookSeriesCrossRef("book-1", "series-1", "1")))
            advanceUntilIdle()

            // Then
            val state = viewModel.state.value
            assertFalse(state.isLoading)
            assertEquals("Epic Fantasy Series", state.seriesName)
            assertEquals("An epic adventure", state.seriesDescription)
            assertEquals(1, state.books.size)
            assertEquals("Book One", state.books[0].title)
            assertNull(state.error)
        }

    @Test
    fun `loadSeries not found sets error state`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()

            // When
            viewModel.loadSeries("nonexistent")
            fixture.seriesFlow.value = null
            advanceUntilIdle()

            // Then
            val state = viewModel.state.value
            assertFalse(state.isLoading)
            assertEquals("Series not found", state.error)
        }

    @Test
    fun `loadSeries sorts books by sequence number`() =
        runTest {
            // Given
            val fixture = createFixture()
            val series = createSeriesEntity()
            val book1Entity = createBookEntity(id = "book-1", title = "Book One")
            val book2Entity = createBookEntity(id = "book-2", title = "Book Two")
            val book3Entity = createBookEntity(id = "book-3", title = "Book Three")

            val book1 = createBook(id = "book-1", title = "Book One", seriesSequence = "1")
            val book2 = createBook(id = "book-2", title = "Book Two", seriesSequence = "2")
            val book3 = createBook(id = "book-3", title = "Book Three", seriesSequence = "1.5")

            everySuspend { fixture.bookRepository.getBook("book-1") } returns book1
            everySuspend { fixture.bookRepository.getBook("book-2") } returns book2
            everySuspend { fixture.bookRepository.getBook("book-3") } returns book3
            val viewModel = fixture.build()

            // When - books come in unsorted order, sequence info in bookSequences
            viewModel.loadSeries("series-1")
            fixture.seriesFlow.value =
                SeriesWithBooks(
                    series = series,
                    books = listOf(book2Entity, book3Entity, book1Entity), // Out of order
                    bookSequences =
                        listOf(
                            createBookSeriesCrossRef("book-1", "series-1", "1"),
                            createBookSeriesCrossRef("book-2", "series-1", "2"),
                            createBookSeriesCrossRef("book-3", "series-1", "1.5"),
                        ),
                )
            advanceUntilIdle()

            // Then - should be sorted by sequence: 1, 1.5, 2
            val state = viewModel.state.value
            assertEquals(3, state.books.size)
            assertEquals("Book One", state.books[0].title)
            assertEquals("Book Three", state.books[1].title)
            assertEquals("Book Two", state.books[2].title)
        }

    @Test
    fun `loadSeries handles books with null sequence`() =
        runTest {
            // Given
            val fixture = createFixture()
            val series = createSeriesEntity()
            val book1Entity = createBookEntity(id = "book-1", title = "Numbered Book")
            val book2Entity = createBookEntity(id = "book-2", title = "Unnumbered Book")

            val book1 = createBook(id = "book-1", title = "Numbered Book", seriesSequence = "1")
            val book2 = createBook(id = "book-2", title = "Unnumbered Book", seriesSequence = null)

            everySuspend { fixture.bookRepository.getBook("book-1") } returns book1
            everySuspend { fixture.bookRepository.getBook("book-2") } returns book2
            val viewModel = fixture.build()

            // When - sequence info in bookSequences (null for unnumbered)
            viewModel.loadSeries("series-1")
            fixture.seriesFlow.value =
                SeriesWithBooks(
                    series = series,
                    books = listOf(book2Entity, book1Entity),
                    bookSequences =
                        listOf(
                            createBookSeriesCrossRef("book-1", "series-1", "1"),
                            createBookSeriesCrossRef("book-2", "series-1", null),
                        ),
                )
            advanceUntilIdle()

            // Then - numbered first, unnumbered at end
            val state = viewModel.state.value
            assertEquals(2, state.books.size)
            assertEquals("Numbered Book", state.books[0].title)
            assertEquals("Unnumbered Book", state.books[1].title)
        }

    @Test
    fun `loadSeries handles null series description`() =
        runTest {
            // Given
            val fixture = createFixture()
            val series = createSeriesEntity(description = null)
            val viewModel = fixture.build()

            // When
            viewModel.loadSeries("series-1")
            fixture.seriesFlow.value = SeriesWithBooks(series = series, books = emptyList(), bookSequences = emptyList())
            advanceUntilIdle()

            // Then
            assertNull(viewModel.state.value.seriesDescription)
        }

    @Test
    fun `loadSeries filters out books not found in repository`() =
        runTest {
            // Given
            val fixture = createFixture()
            val series = createSeriesEntity()
            val book1Entity = createBookEntity(id = "book-1", title = "Found Book")
            val book2Entity = createBookEntity(id = "book-2", title = "Missing Book")

            val book1 = createBook(id = "book-1", title = "Found Book")

            everySuspend { fixture.bookRepository.getBook("book-1") } returns book1
            everySuspend { fixture.bookRepository.getBook("book-2") } returns null // Not found
            val viewModel = fixture.build()

            // When
            viewModel.loadSeries("series-1")
            fixture.seriesFlow.value =
                SeriesWithBooks(
                    series = series,
                    books = listOf(book1Entity, book2Entity),
                    bookSequences = emptyList(),
                )
            advanceUntilIdle()

            // Then - only found book included
            val state = viewModel.state.value
            assertEquals(1, state.books.size)
            assertEquals("Found Book", state.books[0].title)
        }

    @Test
    fun `loadSeries handles empty books list`() =
        runTest {
            // Given
            val fixture = createFixture()
            val series = createSeriesEntity(name = "Empty Series")
            val viewModel = fixture.build()

            // When
            viewModel.loadSeries("series-1")
            fixture.seriesFlow.value = SeriesWithBooks(series = series, books = emptyList(), bookSequences = emptyList())
            advanceUntilIdle()

            // Then
            val state = viewModel.state.value
            assertEquals("Empty Series", state.seriesName)
            assertTrue(state.books.isEmpty())
            assertNull(state.error)
        }

    @Test
    fun `loadSeries updates when flow emits new value`() =
        runTest {
            // Given
            val fixture = createFixture()
            val series1 = createSeriesEntity(name = "Original Name")
            val series2 = createSeriesEntity(name = "Updated Name")
            val viewModel = fixture.build()

            // When - first emission
            viewModel.loadSeries("series-1")
            fixture.seriesFlow.value = SeriesWithBooks(series = series1, books = emptyList(), bookSequences = emptyList())
            advanceUntilIdle()
            assertEquals("Original Name", viewModel.state.value.seriesName)

            // When - second emission (simulating sync update)
            fixture.seriesFlow.value = SeriesWithBooks(series = series2, books = emptyList(), bookSequences = emptyList())
            advanceUntilIdle()

            // Then
            assertEquals("Updated Name", viewModel.state.value.seriesName)
        }
}
