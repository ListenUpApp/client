package com.calypsan.listenup.client.presentation.seriesdetail

import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.core.Timestamp
import com.calypsan.listenup.client.domain.model.BookContributor
import com.calypsan.listenup.client.domain.model.BookListItem
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SeriesDetailViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

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
        every { fixture.seriesRepository.observeSeriesWithBooks(any()) } returns fixture.seriesFlow
        every { fixture.imageRepository.seriesCoverExists(any()) } returns false
        return fixture
    }

    private fun TestScope.keepStateHot(viewModel: SeriesDetailViewModel) {
        backgroundScope.launch { viewModel.state.collect { } }
    }

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
    ): BookListItem =
        BookListItem(
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
        books: List<BookListItem>,
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

    // ========== Initial State ==========

    @Test
    fun `initial state is Idle pre-loadSeries`() =
        runTest {
            val fixture = createFixture()
            val viewModel = fixture.build()
            keepStateHot(viewModel)
            advanceUntilIdle()

            assertEquals(SeriesDetailUiState.Idle, viewModel.state.value)
        }

    // ========== Load Series ==========

    @Test
    fun `loadSeries success populates series data`() =
        runTest {
            val fixture = createFixture()
            val series = createSeries(name = "Epic Fantasy Series", description = "An epic adventure")
            val book = createBook(id = "book-1", title = "Book One")
            val viewModel = fixture.build()
            keepStateHot(viewModel)

            viewModel.loadSeries("series-1")
            fixture.seriesFlow.value =
                createSeriesWithBooks(
                    series = series,
                    books = listOf(book),
                    bookSequences = mapOf("book-1" to "1"),
                )
            advanceUntilIdle()

            val state = assertIs<SeriesDetailUiState.Ready>(viewModel.state.value)
            assertEquals("Epic Fantasy Series", state.seriesName)
            assertEquals("An epic adventure", state.seriesDescription)
            assertEquals(1, state.books.size)
            assertEquals("Book One", state.books[0].title)
        }

    @Test
    fun `loadSeries not found sets error state`() =
        runTest {
            val fixture = createFixture()
            val viewModel = fixture.build()
            keepStateHot(viewModel)

            viewModel.loadSeries("nonexistent")
            fixture.seriesFlow.value = null
            advanceUntilIdle()

            val state = assertIs<SeriesDetailUiState.Error>(viewModel.state.value)
            assertEquals("Series not found", state.message)
        }

    @Test
    fun `loadSeries sorts books by sequence number`() =
        runTest {
            val fixture = createFixture()
            val series = createSeries()
            val book1 = createBook(id = "book-1", title = "Book One", seriesSequence = "1")
            val book2 = createBook(id = "book-2", title = "Book Two", seriesSequence = "2")
            val book3 = createBook(id = "book-3", title = "Book Three", seriesSequence = "1.5")
            val viewModel = fixture.build()
            keepStateHot(viewModel)

            viewModel.loadSeries("series-1")
            fixture.seriesFlow.value =
                createSeriesWithBooks(
                    series = series,
                    books = listOf(book2, book3, book1),
                    bookSequences = mapOf("book-1" to "1", "book-2" to "2", "book-3" to "1.5"),
                )
            advanceUntilIdle()

            val state = assertIs<SeriesDetailUiState.Ready>(viewModel.state.value)
            assertEquals(3, state.books.size)
            assertEquals("Book One", state.books[0].title)
            assertEquals("Book Three", state.books[1].title)
            assertEquals("Book Two", state.books[2].title)
        }

    @Test
    fun `loadSeries handles books with null sequence`() =
        runTest {
            val fixture = createFixture()
            val series = createSeries()
            val book1 = createBook(id = "book-1", title = "Numbered Book", seriesSequence = "1")
            val book2 = createBook(id = "book-2", title = "Unnumbered Book", seriesSequence = null)
            val viewModel = fixture.build()
            keepStateHot(viewModel)

            viewModel.loadSeries("series-1")
            fixture.seriesFlow.value =
                createSeriesWithBooks(
                    series = series,
                    books = listOf(book2, book1),
                    bookSequences = mapOf("book-1" to "1", "book-2" to null),
                )
            advanceUntilIdle()

            val state = assertIs<SeriesDetailUiState.Ready>(viewModel.state.value)
            assertEquals(2, state.books.size)
            assertEquals("Numbered Book", state.books[0].title)
            assertEquals("Unnumbered Book", state.books[1].title)
        }

    @Test
    fun `loadSeries handles null series description`() =
        runTest {
            val fixture = createFixture()
            val series = createSeries(description = null)
            val viewModel = fixture.build()
            keepStateHot(viewModel)

            viewModel.loadSeries("series-1")
            fixture.seriesFlow.value = createSeriesWithBooks(series = series, books = emptyList())
            advanceUntilIdle()

            val state = assertIs<SeriesDetailUiState.Ready>(viewModel.state.value)
            assertNull(state.seriesDescription)
        }

    @Test
    fun `loadSeries handles empty books list`() =
        runTest {
            val fixture = createFixture()
            val series = createSeries(name = "Empty Series")
            val viewModel = fixture.build()
            keepStateHot(viewModel)

            viewModel.loadSeries("series-1")
            fixture.seriesFlow.value = createSeriesWithBooks(series = series, books = emptyList())
            advanceUntilIdle()

            val state = assertIs<SeriesDetailUiState.Ready>(viewModel.state.value)
            assertEquals("Empty Series", state.seriesName)
            assertTrue(state.books.isEmpty())
        }

    @Test
    fun `loadSeries updates when flow emits new value`() =
        runTest {
            val fixture = createFixture()
            val series1 = createSeries(name = "Original Name")
            val series2 = createSeries(name = "Updated Name")
            val viewModel = fixture.build()
            keepStateHot(viewModel)

            viewModel.loadSeries("series-1")
            fixture.seriesFlow.value = createSeriesWithBooks(series = series1, books = emptyList())
            advanceUntilIdle()
            val first = assertIs<SeriesDetailUiState.Ready>(viewModel.state.value)
            assertEquals("Original Name", first.seriesName)

            fixture.seriesFlow.value = createSeriesWithBooks(series = series2, books = emptyList())
            advanceUntilIdle()

            val second = assertIs<SeriesDetailUiState.Ready>(viewModel.state.value)
            assertEquals("Updated Name", second.seriesName)
        }
}
