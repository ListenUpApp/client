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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Tests for ContributorBooksViewModel.
 *
 * The VM uses `.stateIn(WhileSubscribed)`, so tests must keep a background
 * collector alive via [keepStateHot] before asserting on `state.value`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContributorBooksViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

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

        every { fixture.contributorRepository.observeById(any()) } returns fixture.contributorFlow
        every { fixture.contributorRepository.observeBooksForContributorRole(any(), any()) } returns fixture.booksFlow
        everySuspend { fixture.playbackPositionRepository.get(any()) } returns null

        return fixture
    }

    /** Keep the VM's WhileSubscribed state flow hot for the duration of the test. */
    private fun TestScope.keepStateHot(viewModel: ContributorBooksViewModel) {
        backgroundScope.launch { viewModel.state.collect { } }
    }

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

    // ========== Initial State ==========

    @Test
    fun `initial state is Idle pre-loadBooks`() =
        runTest {
            val fixture = createFixture()
            val viewModel = fixture.build()
            keepStateHot(viewModel)
            advanceUntilIdle()

            assertEquals(ContributorBooksUiState.Idle, viewModel.state.value)
        }

    // ========== Load Books ==========

    @Test
    fun `loadBooks populates contributor name`() =
        runTest {
            val fixture = createFixture()
            val viewModel = fixture.build()
            keepStateHot(viewModel)

            viewModel.loadBooks("contributor-1", ContributorRole.AUTHOR.apiValue)
            fixture.contributorFlow.value = createContributor(name = "Stephen King")
            advanceUntilIdle()

            val state = assertIs<ContributorBooksUiState.Ready>(viewModel.state.value)
            assertEquals("Stephen King", state.contributorName)
        }

    @Test
    fun `loadBooks sets role display name`() =
        runTest {
            val fixture = createFixture()
            val viewModel = fixture.build()
            keepStateHot(viewModel)

            viewModel.loadBooks("contributor-1", ContributorRole.AUTHOR.apiValue)
            fixture.contributorFlow.value = createContributor()
            advanceUntilIdle()

            val state = assertIs<ContributorBooksUiState.Ready>(viewModel.state.value)
            assertEquals("Written By", state.roleDisplayName)
        }

    @Test
    fun `loadBooks groups books by series`() =
        runTest {
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
            keepStateHot(viewModel)

            viewModel.loadBooks("contributor-1", ContributorRole.AUTHOR.apiValue)
            fixture.contributorFlow.value = createContributor()
            fixture.booksFlow.value =
                listOf(
                    createBookWithContributorRole(book1),
                    createBookWithContributorRole(book2),
                )
            advanceUntilIdle()

            val state = assertIs<ContributorBooksUiState.Ready>(viewModel.state.value)
            assertEquals(1, state.seriesGroups.size)
            assertEquals("Dark Tower", state.seriesGroups[0].seriesName)
            assertEquals(2, state.seriesGroups[0].books.size)
            assertTrue(state.standaloneBooks.isEmpty())
        }

    @Test
    fun `loadBooks separates standalone books from series books`() =
        runTest {
            val fixture = createFixture()
            val seriesBook =
                createBook(
                    id = "book-1",
                    title = "Series Book",
                    series = listOf(BookSeries(seriesId = "a-series", seriesName = "A Series", sequence = null)),
                )
            val standaloneBook = createBook(id = "book-2", title = "Standalone Book")
            val viewModel = fixture.build()
            keepStateHot(viewModel)

            viewModel.loadBooks("contributor-1", ContributorRole.AUTHOR.apiValue)
            fixture.contributorFlow.value = createContributor()
            fixture.booksFlow.value =
                listOf(
                    createBookWithContributorRole(seriesBook),
                    createBookWithContributorRole(standaloneBook),
                )
            advanceUntilIdle()

            val state = assertIs<ContributorBooksUiState.Ready>(viewModel.state.value)
            assertEquals(1, state.seriesGroups.size)
            assertEquals(1, state.standaloneBooks.size)
            assertEquals("Standalone Book", state.standaloneBooks[0].title)
        }

    @Test
    fun `loadBooks sorts series books by sequence`() =
        runTest {
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
            keepStateHot(viewModel)

            viewModel.loadBooks("contributor-1", ContributorRole.AUTHOR.apiValue)
            fixture.contributorFlow.value = createContributor()
            fixture.booksFlow.value =
                listOf(
                    createBookWithContributorRole(book1),
                    createBookWithContributorRole(book2),
                    createBookWithContributorRole(book3),
                )
            advanceUntilIdle()

            val state = assertIs<ContributorBooksUiState.Ready>(viewModel.state.value)
            val seriesBooks = state.seriesGroups[0].books
            assertEquals("Book Two", seriesBooks[0].title)
            assertEquals("Book Three", seriesBooks[1].title)
            assertEquals("Book One", seriesBooks[2].title)
        }

    @Test
    fun `loadBooks sorts standalone books alphabetically`() =
        runTest {
            val fixture = createFixture()
            val book1 = createBook(id = "book-1", title = "Zebra")
            val book2 = createBook(id = "book-2", title = "Alpha")
            val book3 = createBook(id = "book-3", title = "Beta")
            val viewModel = fixture.build()
            keepStateHot(viewModel)

            viewModel.loadBooks("contributor-1", ContributorRole.AUTHOR.apiValue)
            fixture.contributorFlow.value = createContributor()
            fixture.booksFlow.value =
                listOf(
                    createBookWithContributorRole(book1),
                    createBookWithContributorRole(book2),
                    createBookWithContributorRole(book3),
                )
            advanceUntilIdle()

            val state = assertIs<ContributorBooksUiState.Ready>(viewModel.state.value)
            assertEquals("Alpha", state.standaloneBooks[0].title)
            assertEquals("Beta", state.standaloneBooks[1].title)
            assertEquals("Zebra", state.standaloneBooks[2].title)
        }

    @Test
    fun `loadBooks sorts series groups alphabetically by series name`() =
        runTest {
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
            keepStateHot(viewModel)

            viewModel.loadBooks("contributor-1", ContributorRole.AUTHOR.apiValue)
            fixture.contributorFlow.value = createContributor()
            fixture.booksFlow.value =
                listOf(
                    createBookWithContributorRole(book1),
                    createBookWithContributorRole(book2),
                )
            advanceUntilIdle()

            val state = assertIs<ContributorBooksUiState.Ready>(viewModel.state.value)
            assertEquals(2, state.seriesGroups.size)
            assertEquals("Alpha Series", state.seriesGroups[0].seriesName)
            assertEquals("Zebra Series", state.seriesGroups[1].seriesName)
        }

    // ========== Book Progress ==========

    @Test
    fun `loadBooks calculates book progress`() =
        runTest {
            val fixture = createFixture()
            val book = createBook(id = "book-1", duration = 10_000L)
            everySuspend { fixture.playbackPositionRepository.get("book-1") } returns
                createPlaybackPosition("book-1", 5_000L)
            val viewModel = fixture.build()
            keepStateHot(viewModel)

            viewModel.loadBooks("contributor-1", ContributorRole.AUTHOR.apiValue)
            fixture.contributorFlow.value = createContributor()
            fixture.booksFlow.value = listOf(createBookWithContributorRole(book))
            advanceUntilIdle()

            val state = assertIs<ContributorBooksUiState.Ready>(viewModel.state.value)
            assertEquals(0.5f, state.bookProgress["book-1"])
        }

    @Test
    fun `loadBooks excludes completed books from progress`() =
        runTest {
            val fixture = createFixture()
            val book = createBook(id = "book-1", duration = 10_000L)
            everySuspend { fixture.playbackPositionRepository.get("book-1") } returns
                createPlaybackPosition("book-1", 9_999L)
            val viewModel = fixture.build()
            keepStateHot(viewModel)

            viewModel.loadBooks("contributor-1", ContributorRole.AUTHOR.apiValue)
            fixture.contributorFlow.value = createContributor()
            fixture.booksFlow.value = listOf(createBookWithContributorRole(book))
            advanceUntilIdle()

            val state = assertIs<ContributorBooksUiState.Ready>(viewModel.state.value)
            assertFalse(state.bookProgress.containsKey("book-1"))
        }

    // ========== Derived Properties ==========

    @Test
    fun `totalBooks returns sum of series and standalone books`() =
        runTest {
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
            keepStateHot(viewModel)

            viewModel.loadBooks("contributor-1", ContributorRole.AUTHOR.apiValue)
            fixture.contributorFlow.value = createContributor()
            fixture.booksFlow.value =
                listOf(
                    createBookWithContributorRole(seriesBook1),
                    createBookWithContributorRole(seriesBook2),
                    createBookWithContributorRole(standalone),
                )
            advanceUntilIdle()

            val state = assertIs<ContributorBooksUiState.Ready>(viewModel.state.value)
            assertEquals(3, state.totalBooks)
        }

    @Test
    fun `hasStandaloneBooks is true when standalone books exist`() =
        runTest {
            val fixture = createFixture()
            val book = createBook(id = "book-1")
            val viewModel = fixture.build()
            keepStateHot(viewModel)

            viewModel.loadBooks("contributor-1", ContributorRole.AUTHOR.apiValue)
            fixture.contributorFlow.value = createContributor()
            fixture.booksFlow.value = listOf(createBookWithContributorRole(book))
            advanceUntilIdle()

            val state = assertIs<ContributorBooksUiState.Ready>(viewModel.state.value)
            assertTrue(state.hasStandaloneBooks)
        }

    @Test
    fun `hasStandaloneBooks is false when no standalone books`() =
        runTest {
            val fixture = createFixture()
            val book =
                createBook(
                    id = "book-1",
                    series = listOf(BookSeries(seriesId = "test-series", seriesName = "Series", sequence = null)),
                )
            val viewModel = fixture.build()
            keepStateHot(viewModel)

            viewModel.loadBooks("contributor-1", ContributorRole.AUTHOR.apiValue)
            fixture.contributorFlow.value = createContributor()
            fixture.booksFlow.value = listOf(createBookWithContributorRole(book))
            advanceUntilIdle()

            val state = assertIs<ContributorBooksUiState.Ready>(viewModel.state.value)
            assertFalse(state.hasStandaloneBooks)
        }

    // ========== Cover Path ==========

    @Test
    fun `loadBooks passes through coverPath from domain model`() =
        runTest {
            val fixture = createFixture()
            val book = createBook(id = "book-1", coverPath = "/path/to/cover.jpg")
            val viewModel = fixture.build()
            keepStateHot(viewModel)

            viewModel.loadBooks("contributor-1", ContributorRole.AUTHOR.apiValue)
            fixture.contributorFlow.value = createContributor()
            fixture.booksFlow.value = listOf(createBookWithContributorRole(book))
            advanceUntilIdle()

            val state = assertIs<ContributorBooksUiState.Ready>(viewModel.state.value)
            assertEquals("/path/to/cover.jpg", state.standaloneBooks[0].coverPath)
        }

    // ========== Empty State ==========

    @Test
    fun `loadBooks handles empty book list`() =
        runTest {
            val fixture = createFixture()
            val viewModel = fixture.build()
            keepStateHot(viewModel)

            viewModel.loadBooks("contributor-1", ContributorRole.AUTHOR.apiValue)
            fixture.contributorFlow.value = createContributor()
            fixture.booksFlow.value = emptyList()
            advanceUntilIdle()

            val state = assertIs<ContributorBooksUiState.Ready>(viewModel.state.value)
            assertTrue(state.seriesGroups.isEmpty())
            assertTrue(state.standaloneBooks.isEmpty())
            assertEquals(0, state.totalBooks)
        }
}
