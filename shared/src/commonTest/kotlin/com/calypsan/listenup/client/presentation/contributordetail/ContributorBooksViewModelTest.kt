package com.calypsan.listenup.client.presentation.contributordetail

import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.core.Timestamp
import com.calypsan.listenup.client.domain.model.Book
import com.calypsan.listenup.client.domain.model.BookSeries
import com.calypsan.listenup.client.domain.model.Contributor
import com.calypsan.listenup.client.domain.model.ContributorRole
import com.calypsan.listenup.client.domain.model.PlaybackPosition
import com.calypsan.listenup.client.domain.repository.BookWithContributorRole
import com.calypsan.listenup.client.domain.repository.ContributorRepository
import com.calypsan.listenup.client.domain.repository.PlaybackPositionRepository
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
import kotlin.time.Clock

/**
 * Tests for ContributorBooksViewModel.
 *
 * Tests cover:
 * - Initial state
 * - Load books for contributor+role
 * - Books grouped by series vs standalone
 * - Series books sorted by sequence
 * - Standalone books sorted alphabetically
 * - Book progress calculation
 * - Total books count
 *
 * Uses Mokkery for mocking DAOs and ImageStorage.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContributorBooksViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    // ========== Test Fixtures ==========

    private class TestFixture {
        val contributorRepository: ContributorRepository = mock()
        val playbackPositionRepository: PlaybackPositionRepository = mock()

        val contributorFlow = MutableStateFlow<Contributor?>(null)
        val booksFlow = MutableStateFlow<List<BookWithContributorRole>>(emptyList())

        fun build(): ContributorBooksViewModel =
            ContributorBooksViewModel(
                contributorRepository = contributorRepository,
                playbackPositionRepository = playbackPositionRepository,
            )
    }

    private fun createFixture(): TestFixture {
        val fixture = TestFixture()

        // Default stubs
        every { fixture.contributorRepository.observeById(any()) } returns fixture.contributorFlow
        every { fixture.contributorRepository.observeBooksForContributorRole(any(), any()) } returns fixture.booksFlow
        everySuspend { fixture.playbackPositionRepository.get(any()) } returns null

        return fixture
    }

    // ========== Test Data Factories ==========

    private fun createContributor(
        id: String = "contributor-1",
        name: String = "Stephen King",
    ): Contributor =
        Contributor(
            id =
                com.calypsan.listenup.client.core
                    .ContributorId(id),
            name = name,
            description = null,
            imagePath = null,
            imageBlurHash = null,
            website = null,
            birthDate = null,
            deathDate = null,
            aliases = emptyList(),
        )

    private fun createBook(
        id: String = "book-1",
        title: String = "Test Book",
        duration: Long = 3_600_000L,
        coverPath: String? = null,
        series: List<BookSeries> = emptyList(),
    ): Book =
        Book(
            id = BookId(id),
            title = title,
            subtitle = null,
            coverPath = coverPath,
            duration = duration,
            authors = emptyList(),
            narrators = emptyList(),
            publishYear = 2024,
            addedAt = Timestamp(1704067200000L),
            updatedAt = Timestamp(1704067200000L),
            series = series,
        )

    private fun createBookWithContributorRole(
        book: Book,
        creditedAs: String? = null,
    ): BookWithContributorRole =
        BookWithContributorRole(
            book = book,
            creditedAs = creditedAs,
        )

    private fun createPlaybackPosition(
        bookId: String,
        positionMs: Long,
    ): PlaybackPosition =
        PlaybackPosition(
            bookId = bookId,
            positionMs = positionMs,
            playbackSpeed = 1.0f,
            hasCustomSpeed = false,
            updatedAtMs = Clock.System.now().toEpochMilliseconds(),
            syncedAtMs = null,
            lastPlayedAtMs = null,
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
    fun `initial state has isLoading true and empty data`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()

            // Then - isLoading starts true to avoid showing empty content before data loads
            val state = viewModel.state.value
            assertTrue(state.isLoading)
            assertEquals("", state.contributorName)
            assertEquals("", state.roleDisplayName)
            assertTrue(state.seriesGroups.isEmpty())
            assertTrue(state.standaloneBooks.isEmpty())
            assertNull(state.error)
        }

    // ========== Load Books Tests ==========

    @Test
    fun `loadBooks sets isLoading to true initially`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()

            // When
            viewModel.loadBooks("contributor-1", ContributorRole.AUTHOR.apiValue)
            // Don't advance - check immediate state

            // Then
            assertTrue(viewModel.state.value.isLoading)
        }

    @Test
    fun `loadBooks populates contributor name`() =
        runTest {
            // Given
            val fixture = createFixture()
            val contributor = createContributor(name = "Stephen King")
            val viewModel = fixture.build()

            // When
            viewModel.loadBooks("contributor-1", ContributorRole.AUTHOR.apiValue)
            fixture.contributorFlow.value = contributor
            advanceUntilIdle()

            // Then
            assertEquals("Stephen King", viewModel.state.value.contributorName)
        }

    @Test
    fun `loadBooks sets role display name`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()

            // When
            viewModel.loadBooks("contributor-1", ContributorRole.AUTHOR.apiValue)
            fixture.booksFlow.value = emptyList()
            advanceUntilIdle()

            // Then
            assertEquals("Written By", viewModel.state.value.roleDisplayName)
        }

    @Test
    fun `loadBooks groups books by series`() =
        runTest {
            // Given
            val fixture = createFixture()
            val book1 =
                createBook(
                    id = "book-1",
                    title = "The Dark Tower",
                    series = listOf(BookSeries(seriesId = "dark-tower-series", seriesName = "Dark Tower", sequence = "1")),
                )
            val book2 =
                createBook(
                    id = "book-2",
                    title = "The Drawing of the Three",
                    series = listOf(BookSeries(seriesId = "dark-tower-series", seriesName = "Dark Tower", sequence = "2")),
                )
            val viewModel = fixture.build()

            // When
            viewModel.loadBooks("contributor-1", ContributorRole.AUTHOR.apiValue)
            fixture.booksFlow.value =
                listOf(
                    createBookWithContributorRole(book1),
                    createBookWithContributorRole(book2),
                )
            advanceUntilIdle()

            // Then
            val state = viewModel.state.value
            assertEquals(1, state.seriesGroups.size)
            assertEquals("Dark Tower", state.seriesGroups[0].seriesName)
            assertEquals(2, state.seriesGroups[0].books.size)
            assertTrue(state.standaloneBooks.isEmpty())
        }

    @Test
    fun `loadBooks separates standalone books from series books`() =
        runTest {
            // Given
            val fixture = createFixture()
            val seriesBook =
                createBook(
                    id = "book-1",
                    title = "Series Book",
                    series = listOf(BookSeries(seriesId = "a-series", seriesName = "A Series", sequence = null)),
                )
            val standaloneBook =
                createBook(
                    id = "book-2",
                    title = "Standalone Book",
                )
            val viewModel = fixture.build()

            // When
            viewModel.loadBooks("contributor-1", ContributorRole.AUTHOR.apiValue)
            fixture.booksFlow.value =
                listOf(
                    createBookWithContributorRole(seriesBook),
                    createBookWithContributorRole(standaloneBook),
                )
            advanceUntilIdle()

            // Then
            val state = viewModel.state.value
            assertEquals(1, state.seriesGroups.size)
            assertEquals(1, state.standaloneBooks.size)
            assertEquals("Standalone Book", state.standaloneBooks[0].title)
        }

    @Test
    fun `loadBooks sorts series books by sequence`() =
        runTest {
            // Given
            val fixture = createFixture()
            val book1 =
                createBook(
                    id = "book-1",
                    title = "Book One",
                    series = listOf(BookSeries(seriesId = "test-series", seriesName = "Series", sequence = "2")),
                )
            val book2 =
                createBook(
                    id = "book-2",
                    title = "Book Two",
                    series = listOf(BookSeries(seriesId = "test-series", seriesName = "Series", sequence = "1")),
                )
            val book3 =
                createBook(
                    id = "book-3",
                    title = "Book Three",
                    series = listOf(BookSeries(seriesId = "test-series", seriesName = "Series", sequence = "1.5")),
                )
            val viewModel = fixture.build()

            // When
            viewModel.loadBooks("contributor-1", ContributorRole.AUTHOR.apiValue)
            fixture.booksFlow.value =
                listOf(
                    createBookWithContributorRole(book1),
                    createBookWithContributorRole(book2),
                    createBookWithContributorRole(book3),
                )
            advanceUntilIdle()

            // Then - sorted by sequence: 1, 1.5, 2
            val state = viewModel.state.value
            val seriesBooks = state.seriesGroups[0].books
            assertEquals("Book Two", seriesBooks[0].title)
            assertEquals("Book Three", seriesBooks[1].title)
            assertEquals("Book One", seriesBooks[2].title)
        }

    @Test
    fun `loadBooks sorts standalone books alphabetically`() =
        runTest {
            // Given
            val fixture = createFixture()
            val book1 = createBook(id = "book-1", title = "Zebra")
            val book2 = createBook(id = "book-2", title = "Alpha")
            val book3 = createBook(id = "book-3", title = "Beta")
            val viewModel = fixture.build()

            // When
            viewModel.loadBooks("contributor-1", ContributorRole.AUTHOR.apiValue)
            fixture.booksFlow.value =
                listOf(
                    createBookWithContributorRole(book1),
                    createBookWithContributorRole(book2),
                    createBookWithContributorRole(book3),
                )
            advanceUntilIdle()

            // Then
            val state = viewModel.state.value
            assertEquals("Alpha", state.standaloneBooks[0].title)
            assertEquals("Beta", state.standaloneBooks[1].title)
            assertEquals("Zebra", state.standaloneBooks[2].title)
        }

    @Test
    fun `loadBooks sorts series groups alphabetically by series name`() =
        runTest {
            // Given
            val fixture = createFixture()
            val book1 =
                createBook(
                    id = "book-1",
                    title = "Book",
                    series = listOf(BookSeries(seriesId = "zebra-series", seriesName = "Zebra Series", sequence = null)),
                )
            val book2 =
                createBook(
                    id = "book-2",
                    title = "Book",
                    series = listOf(BookSeries(seriesId = "alpha-series", seriesName = "Alpha Series", sequence = null)),
                )
            val viewModel = fixture.build()

            // When
            viewModel.loadBooks("contributor-1", ContributorRole.AUTHOR.apiValue)
            fixture.booksFlow.value =
                listOf(
                    createBookWithContributorRole(book1),
                    createBookWithContributorRole(book2),
                )
            advanceUntilIdle()

            // Then
            val state = viewModel.state.value
            assertEquals(2, state.seriesGroups.size)
            assertEquals("Alpha Series", state.seriesGroups[0].seriesName)
            assertEquals("Zebra Series", state.seriesGroups[1].seriesName)
        }

    // ========== Book Progress Tests ==========

    @Test
    fun `loadBooks calculates book progress`() =
        runTest {
            // Given
            val fixture = createFixture()
            val book = createBook(id = "book-1", duration = 10_000L)
            everySuspend { fixture.playbackPositionRepository.get("book-1") } returns
                createPlaybackPosition(
                    "book-1",
                    5_000L,
                )
            val viewModel = fixture.build()

            // When
            viewModel.loadBooks("contributor-1", ContributorRole.AUTHOR.apiValue)
            fixture.booksFlow.value = listOf(createBookWithContributorRole(book))
            advanceUntilIdle()

            // Then
            val state = viewModel.state.value
            assertEquals(0.5f, state.bookProgress["book-1"])
        }

    @Test
    fun `loadBooks excludes completed books from progress`() =
        runTest {
            // Given
            val fixture = createFixture()
            val book = createBook(id = "book-1", duration = 10_000L)
            everySuspend { fixture.playbackPositionRepository.get("book-1") } returns
                createPlaybackPosition(
                    "book-1",
                    9_999L,
                )
            val viewModel = fixture.build()

            // When
            viewModel.loadBooks("contributor-1", ContributorRole.AUTHOR.apiValue)
            fixture.booksFlow.value = listOf(createBookWithContributorRole(book))
            advanceUntilIdle()

            // Then
            assertFalse(
                viewModel.state.value.bookProgress
                    .containsKey("book-1"),
            )
        }

    // ========== UI State Derived Properties Tests ==========

    @Test
    fun `totalBooks returns sum of series and standalone books`() =
        runTest {
            // Given
            val fixture = createFixture()
            val seriesBook1 =
                createBook(
                    id = "book-1",
                    title = "Series 1",
                    series = listOf(BookSeries(seriesId = "s-series", seriesName = "S", sequence = null)),
                )
            val seriesBook2 =
                createBook(
                    id = "book-2",
                    title = "Series 2",
                    series = listOf(BookSeries(seriesId = "s-series", seriesName = "S", sequence = null)),
                )
            val standalone = createBook(id = "book-3", title = "Standalone")
            val viewModel = fixture.build()

            // When
            viewModel.loadBooks("contributor-1", ContributorRole.AUTHOR.apiValue)
            fixture.booksFlow.value =
                listOf(
                    createBookWithContributorRole(seriesBook1),
                    createBookWithContributorRole(seriesBook2),
                    createBookWithContributorRole(standalone),
                )
            advanceUntilIdle()

            // Then
            assertEquals(3, viewModel.state.value.totalBooks)
        }

    @Test
    fun `hasStandaloneBooks is true when standalone books exist`() =
        runTest {
            // Given
            val fixture = createFixture()
            val book = createBook(id = "book-1")
            val viewModel = fixture.build()

            // When
            viewModel.loadBooks("contributor-1", ContributorRole.AUTHOR.apiValue)
            fixture.booksFlow.value = listOf(createBookWithContributorRole(book))
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.state.value.hasStandaloneBooks)
        }

    @Test
    fun `hasStandaloneBooks is false when no standalone books`() =
        runTest {
            // Given
            val fixture = createFixture()
            val book =
                createBook(
                    id = "book-1",
                    series = listOf(BookSeries(seriesId = "test-series", seriesName = "Series", sequence = null)),
                )
            val viewModel = fixture.build()

            // When
            viewModel.loadBooks("contributor-1", ContributorRole.AUTHOR.apiValue)
            fixture.booksFlow.value =
                listOf(
                    createBookWithContributorRole(book),
                )
            advanceUntilIdle()

            // Then
            assertFalse(viewModel.state.value.hasStandaloneBooks)
        }

    // ========== Cover Path Tests ==========

    @Test
    fun `loadBooks passes through coverPath from domain model`() =
        runTest {
            // Given - coverPath is now resolved in repository, ViewModel just passes it through
            val fixture = createFixture()
            val book = createBook(id = "book-1", coverPath = "/path/to/cover.jpg")
            val viewModel = fixture.build()

            // When
            viewModel.loadBooks("contributor-1", ContributorRole.AUTHOR.apiValue)
            fixture.booksFlow.value = listOf(createBookWithContributorRole(book))
            advanceUntilIdle()

            // Then
            assertEquals(
                "/path/to/cover.jpg",
                viewModel.state.value.standaloneBooks[0]
                    .coverPath,
            )
        }

    // ========== Empty State Tests ==========

    @Test
    fun `loadBooks handles empty book list`() =
        runTest {
            // Given
            val fixture = createFixture()
            val contributor = createContributor()
            val viewModel = fixture.build()

            // When
            viewModel.loadBooks("contributor-1", ContributorRole.AUTHOR.apiValue)
            fixture.contributorFlow.value = contributor
            fixture.booksFlow.value = emptyList()
            advanceUntilIdle()

            // Then
            val state = viewModel.state.value
            assertFalse(state.isLoading)
            assertTrue(state.seriesGroups.isEmpty())
            assertTrue(state.standaloneBooks.isEmpty())
            assertEquals(0, state.totalBooks)
        }
}
