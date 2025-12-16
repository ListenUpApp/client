package com.calypsan.listenup.client.presentation.contributordetail

import com.calypsan.listenup.client.data.local.db.BookContributorCrossRef
import com.calypsan.listenup.client.data.local.db.BookDao
import com.calypsan.listenup.client.data.local.db.BookEntity
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.db.BookSeriesCrossRef
import com.calypsan.listenup.client.data.local.db.BookWithContributors
import com.calypsan.listenup.client.data.local.db.ContributorDao
import com.calypsan.listenup.client.data.local.db.ContributorEntity
import com.calypsan.listenup.client.data.local.db.PlaybackPositionDao
import com.calypsan.listenup.client.data.local.db.PlaybackPositionEntity
import com.calypsan.listenup.client.data.local.db.SeriesEntity
import com.calypsan.listenup.client.data.local.db.SyncState
import com.calypsan.listenup.client.data.local.db.Timestamp
import com.calypsan.listenup.client.data.local.images.ImageStorage
import com.calypsan.listenup.client.domain.model.BookSeries
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
        val contributorDao: ContributorDao = mock()
        val bookDao: BookDao = mock()
        val imageStorage: ImageStorage = mock()
        val playbackPositionDao: PlaybackPositionDao = mock()

        val contributorFlow = MutableStateFlow<ContributorEntity?>(null)
        val booksFlow = MutableStateFlow<List<BookWithContributors>>(emptyList())

        fun build(): ContributorBooksViewModel =
            ContributorBooksViewModel(
                contributorDao = contributorDao,
                bookDao = bookDao,
                imageStorage = imageStorage,
                playbackPositionDao = playbackPositionDao,
            )
    }

    private fun createFixture(): TestFixture {
        val fixture = TestFixture()

        // Default stubs
        every { fixture.contributorDao.observeById(any()) } returns fixture.contributorFlow
        every { fixture.bookDao.observeByContributorAndRole(any(), any()) } returns fixture.booksFlow
        every { fixture.imageStorage.exists(any()) } returns false
        everySuspend { fixture.playbackPositionDao.get(any()) } returns null

        return fixture
    }

    // ========== Test Data Factories ==========

    private fun createContributorEntity(
        id: String = "contributor-1",
        name: String = "Stephen King",
    ): ContributorEntity =
        ContributorEntity(
            id = id,
            name = name,
            description = null,
            imagePath = null,
            syncState = SyncState.SYNCED,
            lastModified = Timestamp(1704067200000L),
            serverVersion = Timestamp(1704067200000L),
            createdAt = Timestamp(1704067200000L),
            updatedAt = Timestamp(1704067200000L),
        )

    private fun createBookEntity(
        id: String = "book-1",
        title: String = "Test Book",
        duration: Long = 3_600_000L,
    ): BookEntity =
        BookEntity(
            id = BookId(id),
            title = title,
            subtitle = null,
            coverUrl = null,
            totalDuration = duration,
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

    private fun createSeriesEntity(
        id: String = "series-1",
        name: String = "Test Series",
    ): SeriesEntity =
        SeriesEntity(
            id = id,
            name = name,
            description = null,
            syncState = SyncState.SYNCED,
            lastModified = Timestamp(1704067200000L),
            serverVersion = Timestamp(1704067200000L),
            createdAt = Timestamp(1704067200000L),
            updatedAt = Timestamp(1704067200000L),
        )

    private fun createBookWithContributors(
        bookEntity: BookEntity,
        contributors: List<ContributorEntity> = emptyList(),
        roles: List<BookContributorCrossRef> = emptyList(),
        series: List<SeriesEntity> = emptyList(),
        seriesSequences: List<BookSeriesCrossRef> = emptyList(),
    ): BookWithContributors =
        BookWithContributors(
            book = bookEntity,
            contributors = contributors,
            contributorRoles = roles,
            series = series,
            seriesSequences = seriesSequences,
        )

    private fun createPlaybackPosition(
        bookId: String,
        positionMs: Long,
    ): PlaybackPositionEntity =
        PlaybackPositionEntity(
            bookId = BookId(bookId),
            positionMs = positionMs,
            playbackSpeed = 1.0f,
            updatedAt = System.currentTimeMillis(),
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
            viewModel.loadBooks("contributor-1", "author")
            // Don't advance - check immediate state

            // Then
            assertTrue(viewModel.state.value.isLoading)
        }

    @Test
    fun `loadBooks populates contributor name`() =
        runTest {
            // Given
            val fixture = createFixture()
            val contributor = createContributorEntity(name = "Stephen King")
            val viewModel = fixture.build()

            // When
            viewModel.loadBooks("contributor-1", "author")
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
            viewModel.loadBooks("contributor-1", "author")
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
            val darkTowerSeries = createSeriesEntity(id = "dark-tower-series", name = "Dark Tower")
            val book1 =
                createBookEntity(
                    id = "book-1",
                    title = "The Dark Tower",
                )
            val book2 =
                createBookEntity(
                    id = "book-2",
                    title = "The Drawing of the Three",
                )
            val viewModel = fixture.build()

            // When
            viewModel.loadBooks("contributor-1", "author")
            fixture.booksFlow.value =
                listOf(
                    createBookWithContributors(
                        book1,
                        series = listOf(darkTowerSeries),
                        seriesSequences = listOf(BookSeriesCrossRef(BookId("book-1"), "dark-tower-series", "1")),
                    ),
                    createBookWithContributors(
                        book2,
                        series = listOf(darkTowerSeries),
                        seriesSequences = listOf(BookSeriesCrossRef(BookId("book-2"), "dark-tower-series", "2")),
                    ),
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
            val aSeries = createSeriesEntity(id = "a-series", name = "A Series")
            val seriesBook =
                createBookEntity(
                    id = "book-1",
                    title = "Series Book",
                )
            val standaloneBook =
                createBookEntity(
                    id = "book-2",
                    title = "Standalone Book",
                )
            val viewModel = fixture.build()

            // When
            viewModel.loadBooks("contributor-1", "author")
            fixture.booksFlow.value =
                listOf(
                    createBookWithContributors(
                        seriesBook,
                        series = listOf(aSeries),
                        seriesSequences = listOf(BookSeriesCrossRef(BookId("book-1"), "a-series", null)),
                    ),
                    createBookWithContributors(standaloneBook),
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
            val testSeries = createSeriesEntity(id = "test-series", name = "Series")
            val book1 = createBookEntity(id = "book-1", title = "Book One")
            val book2 = createBookEntity(id = "book-2", title = "Book Two")
            val book3 = createBookEntity(id = "book-3", title = "Book Three")
            val viewModel = fixture.build()

            // When
            viewModel.loadBooks("contributor-1", "author")
            fixture.booksFlow.value =
                listOf(
                    createBookWithContributors(
                        book1,
                        series = listOf(testSeries),
                        seriesSequences = listOf(BookSeriesCrossRef(BookId("book-1"), "test-series", "2")),
                    ),
                    createBookWithContributors(
                        book2,
                        series = listOf(testSeries),
                        seriesSequences = listOf(BookSeriesCrossRef(BookId("book-2"), "test-series", "1")),
                    ),
                    createBookWithContributors(
                        book3,
                        series = listOf(testSeries),
                        seriesSequences = listOf(BookSeriesCrossRef(BookId("book-3"), "test-series", "1.5")),
                    ),
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
            val book1 = createBookEntity(id = "book-1", title = "Zebra")
            val book2 = createBookEntity(id = "book-2", title = "Alpha")
            val book3 = createBookEntity(id = "book-3", title = "Beta")
            val viewModel = fixture.build()

            // When
            viewModel.loadBooks("contributor-1", "author")
            fixture.booksFlow.value =
                listOf(
                    createBookWithContributors(book1),
                    createBookWithContributors(book2),
                    createBookWithContributors(book3),
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
            val zebraSeries = createSeriesEntity(id = "zebra-series", name = "Zebra Series")
            val alphaSeries = createSeriesEntity(id = "alpha-series", name = "Alpha Series")
            val book1 = createBookEntity(id = "book-1", title = "Book")
            val book2 = createBookEntity(id = "book-2", title = "Book")
            val viewModel = fixture.build()

            // When
            viewModel.loadBooks("contributor-1", "author")
            fixture.booksFlow.value =
                listOf(
                    createBookWithContributors(
                        book1,
                        series = listOf(zebraSeries),
                        seriesSequences = listOf(BookSeriesCrossRef(BookId("book-1"), "zebra-series", null)),
                    ),
                    createBookWithContributors(
                        book2,
                        series = listOf(alphaSeries),
                        seriesSequences = listOf(BookSeriesCrossRef(BookId("book-2"), "alpha-series", null)),
                    ),
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
            val book = createBookEntity(id = "book-1", duration = 10_000L)
            everySuspend { fixture.playbackPositionDao.get(BookId("book-1")) } returns
                createPlaybackPosition(
                    "book-1",
                    5_000L,
                )
            val viewModel = fixture.build()

            // When
            viewModel.loadBooks("contributor-1", "author")
            fixture.booksFlow.value = listOf(createBookWithContributors(book))
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
            val book = createBookEntity(id = "book-1", duration = 10_000L)
            everySuspend { fixture.playbackPositionDao.get(BookId("book-1")) } returns
                createPlaybackPosition(
                    "book-1",
                    9_999L,
                )
            val viewModel = fixture.build()

            // When
            viewModel.loadBooks("contributor-1", "author")
            fixture.booksFlow.value = listOf(createBookWithContributors(book))
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
            val sSeries = createSeriesEntity(id = "s-series", name = "S")
            val seriesBook1 = createBookEntity(id = "book-1", title = "Series 1")
            val seriesBook2 = createBookEntity(id = "book-2", title = "Series 2")
            val standalone = createBookEntity(id = "book-3", title = "Standalone")
            val viewModel = fixture.build()

            // When
            viewModel.loadBooks("contributor-1", "author")
            fixture.booksFlow.value =
                listOf(
                    createBookWithContributors(
                        seriesBook1,
                        series = listOf(sSeries),
                        seriesSequences = listOf(BookSeriesCrossRef(BookId("book-1"), "s-series", null)),
                    ),
                    createBookWithContributors(
                        seriesBook2,
                        series = listOf(sSeries),
                        seriesSequences = listOf(BookSeriesCrossRef(BookId("book-2"), "s-series", null)),
                    ),
                    createBookWithContributors(standalone),
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
            val book = createBookEntity(id = "book-1")
            val viewModel = fixture.build()

            // When
            viewModel.loadBooks("contributor-1", "author")
            fixture.booksFlow.value = listOf(createBookWithContributors(book))
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.state.value.hasStandaloneBooks)
        }

    @Test
    fun `hasStandaloneBooks is false when no standalone books`() =
        runTest {
            // Given
            val fixture = createFixture()
            val testSeries = createSeriesEntity(id = "test-series", name = "Series")
            val book = createBookEntity(id = "book-1")
            val viewModel = fixture.build()

            // When
            viewModel.loadBooks("contributor-1", "author")
            fixture.booksFlow.value =
                listOf(
                    createBookWithContributors(
                        book,
                        series = listOf(testSeries),
                        seriesSequences = listOf(BookSeriesCrossRef(BookId("book-1"), "test-series", null)),
                    ),
                )
            advanceUntilIdle()

            // Then
            assertFalse(viewModel.state.value.hasStandaloneBooks)
        }

    // ========== Cover Path Tests ==========

    @Test
    fun `loadBooks sets coverPath when image exists`() =
        runTest {
            // Given
            val fixture = createFixture()
            val book = createBookEntity(id = "book-1")
            every { fixture.imageStorage.exists(BookId("book-1")) } returns true
            every { fixture.imageStorage.getCoverPath(BookId("book-1")) } returns "/path/to/cover.jpg"
            val viewModel = fixture.build()

            // When
            viewModel.loadBooks("contributor-1", "author")
            fixture.booksFlow.value = listOf(createBookWithContributors(book))
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
            val contributor = createContributorEntity()
            val viewModel = fixture.build()

            // When
            viewModel.loadBooks("contributor-1", "author")
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
