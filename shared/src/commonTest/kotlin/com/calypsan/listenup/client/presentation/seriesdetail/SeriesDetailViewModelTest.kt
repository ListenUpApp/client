package com.calypsan.listenup.client.presentation.seriesdetail

import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.core.Timestamp
import com.calypsan.listenup.client.domain.model.Book
import com.calypsan.listenup.client.domain.model.BookContributor
import com.calypsan.listenup.client.domain.model.BookSeries
import com.calypsan.listenup.client.domain.model.Series
import com.calypsan.listenup.client.domain.model.SeriesWithBooks
import com.calypsan.listenup.client.domain.repository.ImageRepository
import com.calypsan.listenup.client.domain.repository.SeriesRepository
import dev.mokkery.answering.returns
import dev.mokkery.every
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
 * Uses Mokkery for mocking SeriesRepository.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SeriesDetailViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    // ========== Test Fixtures ==========

    private class TestFixture {
        val seriesRepository: SeriesRepository = mock()
        val imageRepository: ImageRepository = mock()
        val seriesFlow = MutableStateFlow<SeriesWithBooks?>(null)

        fun build(): SeriesDetailViewModel =
            SeriesDetailViewModel(
                seriesRepository = seriesRepository,
                imageRepository = imageRepository,
            )
    }

    private fun createFixture(): TestFixture {
        val fixture = TestFixture()

        // Default stub for observeSeriesWithBooks
        every { fixture.seriesRepository.observeSeriesWithBooks(any()) } returns fixture.seriesFlow

        // Default stub for imageRepository - no series cover exists
        every { fixture.imageRepository.seriesCoverExists(any()) } returns false

        return fixture
    }

    // ========== Test Data Factories ==========

    private fun createSeries(
        id: String = "series-1",
        name: String = "Test Series",
        description: String? = "A great series",
    ): Series =
        Series(
            id =
                com.calypsan.listenup.client.core
                    .SeriesId(id),
            name = name,
            description = description,
            createdAt = Timestamp(1704067200000L),
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
            authors = listOf(BookContributor(id = "author-1", name = "Author", roles = listOf("Author"))),
            narrators = emptyList(),
            duration = 3_600_000L,
            coverPath = null,
            addedAt = Timestamp(1704067200000L),
            updatedAt = Timestamp(1704067200000L),
            series = listOf(BookSeries(seriesId = seriesId, seriesName = seriesName, sequence = seriesSequence)),
        )

    private fun createSeriesWithBooks(
        series: Series,
        books: List<Book>,
        bookSequences: Map<String, String?> = emptyMap(),
    ): SeriesWithBooks =
        SeriesWithBooks(
            series = series,
            books = books,
            bookSequences = bookSequences,
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
            val series = createSeries(name = "Epic Fantasy Series", description = "An epic adventure")
            val book = createBook(id = "book-1", title = "Book One")

            val viewModel = fixture.build()

            // When
            viewModel.loadSeries("series-1")
            fixture.seriesFlow.value =
                createSeriesWithBooks(
                    series = series,
                    books = listOf(book),
                    bookSequences = mapOf("book-1" to "1"),
                )
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
            val series = createSeries()

            val book1 = createBook(id = "book-1", title = "Book One", seriesSequence = "1")
            val book2 = createBook(id = "book-2", title = "Book Two", seriesSequence = "2")
            val book3 = createBook(id = "book-3", title = "Book Three", seriesSequence = "1.5")

            val viewModel = fixture.build()

            // When - books come in unsorted order, sequence info in bookSequences
            viewModel.loadSeries("series-1")
            fixture.seriesFlow.value =
                createSeriesWithBooks(
                    series = series,
                    books = listOf(book2, book3, book1), // Out of order
                    bookSequences =
                        mapOf(
                            "book-1" to "1",
                            "book-2" to "2",
                            "book-3" to "1.5",
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
            val series = createSeries()

            val book1 = createBook(id = "book-1", title = "Numbered Book", seriesSequence = "1")
            val book2 = createBook(id = "book-2", title = "Unnumbered Book", seriesSequence = null)

            val viewModel = fixture.build()

            // When - sequence info in bookSequences (null for unnumbered)
            viewModel.loadSeries("series-1")
            fixture.seriesFlow.value =
                createSeriesWithBooks(
                    series = series,
                    books = listOf(book2, book1),
                    bookSequences =
                        mapOf(
                            "book-1" to "1",
                            "book-2" to null,
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
            val series = createSeries(description = null)
            val viewModel = fixture.build()

            // When
            viewModel.loadSeries("series-1")
            fixture.seriesFlow.value = createSeriesWithBooks(series = series, books = emptyList())
            advanceUntilIdle()

            // Then
            assertNull(viewModel.state.value.seriesDescription)
        }

    @Test
    fun `loadSeries handles empty books list`() =
        runTest {
            // Given
            val fixture = createFixture()
            val series = createSeries(name = "Empty Series")
            val viewModel = fixture.build()

            // When
            viewModel.loadSeries("series-1")
            fixture.seriesFlow.value = createSeriesWithBooks(series = series, books = emptyList())
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
            val series1 = createSeries(name = "Original Name")
            val series2 = createSeries(name = "Updated Name")
            val viewModel = fixture.build()

            // When - first emission
            viewModel.loadSeries("series-1")
            fixture.seriesFlow.value = createSeriesWithBooks(series = series1, books = emptyList())
            advanceUntilIdle()
            assertEquals("Original Name", viewModel.state.value.seriesName)

            // When - second emission (simulating sync update)
            fixture.seriesFlow.value = createSeriesWithBooks(series = series2, books = emptyList())
            advanceUntilIdle()

            // Then
            assertEquals("Updated Name", viewModel.state.value.seriesName)
        }
}
