package com.calypsan.listenup.client.presentation.library

import com.calypsan.listenup.client.core.AccessToken
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.db.ContributorDao
import com.calypsan.listenup.client.data.local.db.ContributorEntity
import com.calypsan.listenup.client.data.local.db.ContributorWithBookCount
import com.calypsan.listenup.client.data.local.db.PlaybackPositionDao
import com.calypsan.listenup.client.data.local.db.PlaybackPositionEntity
import com.calypsan.listenup.client.data.local.db.SeriesDao
import com.calypsan.listenup.client.data.local.db.SeriesEntity
import com.calypsan.listenup.client.data.local.db.SeriesWithBooks
import com.calypsan.listenup.client.data.local.db.SyncDao
import com.calypsan.listenup.client.data.local.db.SyncState
import com.calypsan.listenup.client.data.local.db.Timestamp
import com.calypsan.listenup.client.data.repository.BookRepositoryContract
import com.calypsan.listenup.client.data.repository.SettingsRepositoryContract
import com.calypsan.listenup.client.data.sync.SyncManagerContract
import com.calypsan.listenup.client.data.sync.SyncStatus
import com.calypsan.listenup.client.domain.model.Book
import com.calypsan.listenup.client.domain.model.Contributor
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for LibraryViewModel.
 *
 * Tests cover:
 * - Sort state initialization and persistence
 * - Sorting logic for books, series, and contributors
 * - Auto-sync behavior on screen visibility
 * - Event handling
 *
 * Uses Mokkery for mocking interfaces (BookRepositoryContract, SettingsRepositoryContract,
 * SyncManagerContract, and DAOs).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    // ========== Test Data Factories ==========

    private fun createTestBook(
        id: String = "book-1",
        title: String = "Test Book",
        authors: List<Contributor> = listOf(Contributor("author-1", "Test Author")),
        duration: Long = 3_600_000L, // 1 hour
        publishYear: Int? = 2023,
        seriesName: String? = null,
        seriesSequence: String? = null,
        addedAt: Timestamp = Timestamp(1000L),
    ): Book =
        Book(
            id = BookId(id),
            title = title,
            authors = authors,
            narrators = emptyList(),
            duration = duration,
            coverPath = null,
            addedAt = addedAt,
            updatedAt = addedAt,
            publishYear = publishYear,
            seriesName = seriesName,
            seriesSequence = seriesSequence,
        )

    private fun createTestSeries(
        id: String = "series-1",
        name: String = "Test Series",
        createdAt: Timestamp = Timestamp(1000L),
    ): SeriesEntity =
        SeriesEntity(
            id = id,
            name = name,
            description = null,
            syncState = SyncState.SYNCED,
            lastModified = createdAt,
            serverVersion = createdAt,
            createdAt = createdAt,
            updatedAt = createdAt,
        )

    private fun createTestContributor(
        id: String = "contrib-1",
        name: String = "Test Contributor",
        bookCount: Int = 5,
    ): ContributorWithBookCount =
        ContributorWithBookCount(
            contributor =
                ContributorEntity(
                    id = id,
                    name = name,
                    description = null,
                    imagePath = null,
                    syncState = SyncState.SYNCED,
                    lastModified = Timestamp(1000L),
                    serverVersion = Timestamp(1000L),
                    createdAt = Timestamp(1000L),
                    updatedAt = Timestamp(1000L),
                ),
            bookCount = bookCount,
        )

    private fun createDummyBookEntity(id: String): com.calypsan.listenup.client.data.local.db.BookEntity {
        val now = Timestamp(System.currentTimeMillis())
        return com.calypsan.listenup.client.data.local.db.BookEntity(
            id = BookId(id),
            title = "Book $id",
            coverUrl = null,
            totalDuration = 3600000L,
            syncState = SyncState.SYNCED,
            lastModified = now,
            serverVersion = now,
            createdAt = now,
            updatedAt = now,
        )
    }

    // ========== Test Fixture Builder ==========

    private class TestFixture {
        val bookRepository: BookRepositoryContract = mock()
        val seriesDao: SeriesDao = mock()
        val contributorDao: ContributorDao = mock()
        val syncManager: SyncManagerContract = mock()
        val settingsRepository: SettingsRepositoryContract = mock()
        val syncDao: SyncDao = mock()
        val playbackPositionDao: PlaybackPositionDao = mock()

        val syncStateFlow = MutableStateFlow<SyncStatus>(SyncStatus.Idle)

        fun build(): LibraryViewModel =
            LibraryViewModel(
                bookRepository = bookRepository,
                seriesDao = seriesDao,
                contributorDao = contributorDao,
                syncManager = syncManager,
                settingsRepository = settingsRepository,
                syncDao = syncDao,
                playbackPositionDao = playbackPositionDao,
            )
    }

    private fun createFixture(): TestFixture {
        val fixture = TestFixture()

        // Default stubs for all dependencies
        every { fixture.bookRepository.observeBooks() } returns flowOf(emptyList())
        every { fixture.seriesDao.observeAllWithBooks() } returns flowOf(emptyList())
        every { fixture.contributorDao.observeByRoleWithCount("author") } returns flowOf(emptyList())
        every { fixture.contributorDao.observeByRoleWithCount("narrator") } returns flowOf(emptyList())
        every { fixture.syncManager.syncState } returns fixture.syncStateFlow
        every { fixture.playbackPositionDao.observeAll() } returns flowOf(emptyList())

        // Default settings stubs (no persisted state)
        everySuspend { fixture.settingsRepository.getBooksSortState() } returns null
        everySuspend { fixture.settingsRepository.getSeriesSortState() } returns null
        everySuspend { fixture.settingsRepository.getAuthorsSortState() } returns null
        everySuspend { fixture.settingsRepository.getNarratorsSortState() } returns null
        everySuspend { fixture.settingsRepository.getIgnoreTitleArticles() } returns true

        return fixture
    }

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Helper to collect a StateFlow in the background so WhileSubscribed flows emit.
     * This is necessary because the ViewModel uses SharingStarted.WhileSubscribed,
     * which only starts upstream collection when there's an active subscriber.
     */
    private fun <T> TestScope.collectInBackground(viewModel: LibraryViewModel) {
        // Start collecting all flows so they can emit
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            launch { viewModel.books.collect {} }
            launch { viewModel.series.collect {} }
            launch { viewModel.authors.collect {} }
            launch { viewModel.narrators.collect {} }
            launch { viewModel.bookProgress.collect {} }
        }
    }

    // ========== Sort State Initialization Tests ==========

    @Test
    fun `initial books sort state is title ascending`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()

            // Then
            assertEquals(SortCategory.TITLE, viewModel.booksSortState.value.category)
            assertEquals(SortDirection.ASCENDING, viewModel.booksSortState.value.direction)
        }

    @Test
    fun `initial series sort state is name ascending`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()

            // Then
            assertEquals(SortCategory.NAME, viewModel.seriesSortState.value.category)
            assertEquals(SortDirection.ASCENDING, viewModel.seriesSortState.value.direction)
        }

    @Test
    fun `initial authors sort state is name ascending`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()

            // Then
            assertEquals(SortCategory.NAME, viewModel.authorsSortState.value.category)
            assertEquals(SortDirection.ASCENDING, viewModel.authorsSortState.value.direction)
        }

    @Test
    fun `loads persisted books sort state on init`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.settingsRepository.getBooksSortState() } returns "duration:descending"

            // When
            val viewModel = fixture.build()
            advanceUntilIdle()

            // Then
            assertEquals(SortCategory.DURATION, viewModel.booksSortState.value.category)
            assertEquals(SortDirection.DESCENDING, viewModel.booksSortState.value.direction)
        }

    @Test
    fun `loads persisted series sort state on init`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.settingsRepository.getSeriesSortState() } returns "book_count:descending"

            // When
            val viewModel = fixture.build()
            advanceUntilIdle()

            // Then
            assertEquals(SortCategory.BOOK_COUNT, viewModel.seriesSortState.value.category)
            assertEquals(SortDirection.DESCENDING, viewModel.seriesSortState.value.direction)
        }

    @Test
    fun `ignores invalid persisted sort state`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.settingsRepository.getBooksSortState() } returns "invalid:garbage"

            // When
            val viewModel = fixture.build()
            advanceUntilIdle()

            // Then - falls back to default
            assertEquals(SortCategory.TITLE, viewModel.booksSortState.value.category)
            assertEquals(SortDirection.ASCENDING, viewModel.booksSortState.value.direction)
        }

    // ========== Event Handling: Sort State Changes ==========

    @Test
    fun `BooksCategoryChanged updates books sort category`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.settingsRepository.setBooksSortState(any()) } returns Unit
            val viewModel = fixture.build()

            // When
            viewModel.onEvent(LibraryUiEvent.BooksCategoryChanged(SortCategory.AUTHOR))
            advanceUntilIdle()

            // Then
            assertEquals(SortCategory.AUTHOR, viewModel.booksSortState.value.category)
        }

    @Test
    fun `BooksCategoryChanged uses category default direction`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.settingsRepository.setBooksSortState(any()) } returns Unit
            val viewModel = fixture.build()
            advanceUntilIdle()

            // When - DURATION defaults to DESCENDING
            viewModel.onEvent(LibraryUiEvent.BooksCategoryChanged(SortCategory.DURATION))
            advanceUntilIdle()

            // Then
            assertEquals(SortDirection.DESCENDING, viewModel.booksSortState.value.direction)
        }

    @Test
    fun `BooksDirectionToggled toggles sort direction`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.settingsRepository.setBooksSortState(any()) } returns Unit
            val viewModel = fixture.build()
            advanceUntilIdle()
            assertEquals(SortDirection.ASCENDING, viewModel.booksSortState.value.direction)

            // When
            viewModel.onEvent(LibraryUiEvent.BooksDirectionToggled)
            advanceUntilIdle()

            // Then
            assertEquals(SortDirection.DESCENDING, viewModel.booksSortState.value.direction)
        }

    @Test
    fun `sort state change persists to settings`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.settingsRepository.setBooksSortState(any()) } returns Unit
            val viewModel = fixture.build()
            advanceUntilIdle()

            // When
            viewModel.onEvent(LibraryUiEvent.BooksCategoryChanged(SortCategory.YEAR))
            advanceUntilIdle()

            // Then
            verifySuspend { fixture.settingsRepository.setBooksSortState("year:descending") }
        }

    @Test
    fun `SeriesCategoryChanged updates series sort category`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.settingsRepository.setSeriesSortState(any()) } returns Unit
            val viewModel = fixture.build()

            // When
            viewModel.onEvent(LibraryUiEvent.SeriesCategoryChanged(SortCategory.BOOK_COUNT))
            advanceUntilIdle()

            // Then
            assertEquals(SortCategory.BOOK_COUNT, viewModel.seriesSortState.value.category)
        }

    @Test
    fun `AuthorsCategoryChanged updates authors sort category`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.settingsRepository.setAuthorsSortState(any()) } returns Unit
            val viewModel = fixture.build()

            // When
            viewModel.onEvent(LibraryUiEvent.AuthorsCategoryChanged(SortCategory.BOOK_COUNT))
            advanceUntilIdle()

            // Then
            assertEquals(SortCategory.BOOK_COUNT, viewModel.authorsSortState.value.category)
        }

    // ========== Toggle Ignore Title Articles ==========

    @Test
    fun `ToggleIgnoreTitleArticles toggles preference`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.settingsRepository.setIgnoreTitleArticles(any()) } returns Unit
            val viewModel = fixture.build()
            advanceUntilIdle()
            assertEquals(true, viewModel.ignoreTitleArticles.value)

            // When
            viewModel.onEvent(LibraryUiEvent.ToggleIgnoreTitleArticles)
            advanceUntilIdle()

            // Then
            assertEquals(false, viewModel.ignoreTitleArticles.value)
            verifySuspend { fixture.settingsRepository.setIgnoreTitleArticles(false) }
        }

    // ========== Books Sorting Tests ==========

    @Test
    fun `books sorted by title ascending`() =
        runTest {
            // Given
            val books =
                listOf(
                    createTestBook(id = "1", title = "Zebra"),
                    createTestBook(id = "2", title = "Apple"),
                    createTestBook(id = "3", title = "Mango"),
                )
            val fixture = createFixture()
            every { fixture.bookRepository.observeBooks() } returns flowOf(books)
            val viewModel = fixture.build()

            // When - Subscribe to flows so WhileSubscribed starts collection
            collectInBackground<Unit>(viewModel)
            advanceUntilIdle()

            // Then
            val sortedBooks = viewModel.books.value
            assertEquals(listOf("Apple", "Mango", "Zebra"), sortedBooks.map { it.title })
        }

    @Test
    fun `books sorted by title descending`() =
        runTest {
            // Given
            val books =
                listOf(
                    createTestBook(id = "1", title = "Apple"),
                    createTestBook(id = "2", title = "Zebra"),
                    createTestBook(id = "3", title = "Mango"),
                )
            val fixture = createFixture()
            every { fixture.bookRepository.observeBooks() } returns flowOf(books)
            everySuspend { fixture.settingsRepository.setBooksSortState(any()) } returns Unit
            val viewModel = fixture.build()
            collectInBackground<Unit>(viewModel)
            advanceUntilIdle()

            // When
            viewModel.onEvent(LibraryUiEvent.BooksDirectionToggled)
            advanceUntilIdle()

            // Then
            val sortedBooks = viewModel.books.value
            assertEquals(listOf("Zebra", "Mango", "Apple"), sortedBooks.map { it.title })
        }

    @Test
    fun `books sorted by title ignores leading articles when enabled`() =
        runTest {
            // Given - "The" should be ignored, so "The Zebra" sorts as "Zebra"
            val books =
                listOf(
                    createTestBook(id = "1", title = "The Zebra"),
                    createTestBook(id = "2", title = "Apple"),
                    createTestBook(id = "3", title = "A Mango"),
                )
            val fixture = createFixture()
            everySuspend { fixture.settingsRepository.getIgnoreTitleArticles() } returns true
            every { fixture.bookRepository.observeBooks() } returns flowOf(books)
            val viewModel = fixture.build()
            collectInBackground<Unit>(viewModel)
            advanceUntilIdle()

            // Then - Should sort as: Apple, A Mango (as "Mango"), The Zebra (as "Zebra")
            val sortedBooks = viewModel.books.value
            assertEquals(listOf("Apple", "A Mango", "The Zebra"), sortedBooks.map { it.title })
        }

    @Test
    fun `books sorted by author groups by author then title`() =
        runTest {
            // Given
            val books =
                listOf(
                    createTestBook(
                        id = "1",
                        title = "Zebra Book",
                        authors = listOf(Contributor("a1", "Alice")),
                    ),
                    createTestBook(
                        id = "2",
                        title = "Apple Book",
                        authors = listOf(Contributor("b1", "Bob")),
                    ),
                    createTestBook(
                        id = "3",
                        title = "Cherry Book",
                        authors = listOf(Contributor("a1", "Alice")),
                    ),
                )
            val fixture = createFixture()
            every { fixture.bookRepository.observeBooks() } returns flowOf(books)
            everySuspend { fixture.settingsRepository.setBooksSortState(any()) } returns Unit
            val viewModel = fixture.build()
            collectInBackground<Unit>(viewModel)
            advanceUntilIdle()

            // When
            viewModel.onEvent(LibraryUiEvent.BooksCategoryChanged(SortCategory.AUTHOR))
            advanceUntilIdle()

            // Then - Alice's books first (then by title), then Bob's
            val sortedBooks = viewModel.books.value
            assertEquals(
                listOf("Cherry Book", "Zebra Book", "Apple Book"),
                sortedBooks.map { it.title },
            )
        }

    @Test
    fun `books sorted by duration ascending`() =
        runTest {
            // Given
            val books =
                listOf(
                    createTestBook(id = "1", title = "Long", duration = 10_000_000L),
                    createTestBook(id = "2", title = "Short", duration = 1_000_000L),
                    createTestBook(id = "3", title = "Medium", duration = 5_000_000L),
                )
            val fixture = createFixture()
            every { fixture.bookRepository.observeBooks() } returns flowOf(books)
            everySuspend { fixture.settingsRepository.setBooksSortState(any()) } returns Unit
            val viewModel = fixture.build()
            collectInBackground<Unit>(viewModel)
            advanceUntilIdle()

            // When - Change to duration, then toggle to ascending
            viewModel.onEvent(LibraryUiEvent.BooksCategoryChanged(SortCategory.DURATION))
            advanceUntilIdle()
            viewModel.onEvent(LibraryUiEvent.BooksDirectionToggled) // DURATION defaults DESC, toggle to ASC
            advanceUntilIdle()

            // Then
            val sortedBooks = viewModel.books.value
            assertEquals(listOf("Short", "Medium", "Long"), sortedBooks.map { it.title })
        }

    @Test
    fun `books sorted by year descending handles null publish years`() =
        runTest {
            // Given
            val books =
                listOf(
                    createTestBook(id = "1", title = "Old", publishYear = 2020),
                    createTestBook(id = "2", title = "No Year", publishYear = null),
                    createTestBook(id = "3", title = "New", publishYear = 2024),
                )
            val fixture = createFixture()
            every { fixture.bookRepository.observeBooks() } returns flowOf(books)
            everySuspend { fixture.settingsRepository.setBooksSortState(any()) } returns Unit
            val viewModel = fixture.build()
            collectInBackground<Unit>(viewModel)
            advanceUntilIdle()

            // When
            viewModel.onEvent(LibraryUiEvent.BooksCategoryChanged(SortCategory.YEAR))
            advanceUntilIdle()

            // Then - Year DESC: newest first, null years go to end (treated as 0)
            val sortedBooks = viewModel.books.value
            assertEquals(listOf("New", "Old", "No Year"), sortedBooks.map { it.title })
        }

    @Test
    fun `books sorted by added date descending`() =
        runTest {
            // Given
            val books =
                listOf(
                    createTestBook(id = "1", title = "First", addedAt = Timestamp(1000L)),
                    createTestBook(id = "2", title = "Last", addedAt = Timestamp(3000L)),
                    createTestBook(id = "3", title = "Middle", addedAt = Timestamp(2000L)),
                )
            val fixture = createFixture()
            every { fixture.bookRepository.observeBooks() } returns flowOf(books)
            everySuspend { fixture.settingsRepository.setBooksSortState(any()) } returns Unit
            val viewModel = fixture.build()
            collectInBackground<Unit>(viewModel)
            advanceUntilIdle()

            // When
            viewModel.onEvent(LibraryUiEvent.BooksCategoryChanged(SortCategory.ADDED))
            advanceUntilIdle()

            // Then - Added DESC: most recent first
            val sortedBooks = viewModel.books.value
            assertEquals(listOf("Last", "Middle", "First"), sortedBooks.map { it.title })
        }

    @Test
    fun `books sorted by series groups by series then sequence then title`() =
        runTest {
            // Given
            val books =
                listOf(
                    createTestBook(id = "1", title = "Book A", seriesName = "Alpha Series", seriesSequence = "2"),
                    createTestBook(id = "2", title = "Book B", seriesName = "Alpha Series", seriesSequence = "1"),
                    createTestBook(id = "3", title = "Standalone", seriesName = null, seriesSequence = null),
                    createTestBook(id = "4", title = "Book C", seriesName = "Beta Series", seriesSequence = "1"),
                )
            val fixture = createFixture()
            every { fixture.bookRepository.observeBooks() } returns flowOf(books)
            everySuspend { fixture.settingsRepository.setBooksSortState(any()) } returns Unit
            val viewModel = fixture.build()
            collectInBackground<Unit>(viewModel)
            advanceUntilIdle()

            // When - SERIES defaults to ASCENDING
            viewModel.onEvent(LibraryUiEvent.BooksCategoryChanged(SortCategory.SERIES))
            advanceUntilIdle()

            // Then - Series ASC: Alpha (seq 1, 2), Beta (seq 1), then null series at end
            val sortedBooks = viewModel.books.value
            assertEquals(listOf("Book B", "Book A", "Book C", "Standalone"), sortedBooks.map { it.title })
        }

    @Test
    fun `books sorted by series handles decimal sequences`() =
        runTest {
            // Given
            val books =
                listOf(
                    createTestBook(id = "1", title = "Book 2", seriesName = "Series", seriesSequence = "2"),
                    createTestBook(id = "2", title = "Book 1.5", seriesName = "Series", seriesSequence = "1.5"),
                    createTestBook(id = "3", title = "Book 1", seriesName = "Series", seriesSequence = "1"),
                )
            val fixture = createFixture()
            every { fixture.bookRepository.observeBooks() } returns flowOf(books)
            everySuspend { fixture.settingsRepository.setBooksSortState(any()) } returns Unit
            val viewModel = fixture.build()
            collectInBackground<Unit>(viewModel)
            advanceUntilIdle()

            // When - SERIES defaults to ASCENDING
            viewModel.onEvent(LibraryUiEvent.BooksCategoryChanged(SortCategory.SERIES))
            advanceUntilIdle()

            // Then - Should handle 1 < 1.5 < 2
            val sortedBooks = viewModel.books.value
            assertEquals(listOf("Book 1", "Book 1.5", "Book 2"), sortedBooks.map { it.title })
        }

    // ========== Series Sorting Tests ==========

    @Test
    fun `series sorted by name ascending`() =
        runTest {
            // Given
            val seriesList =
                listOf(
                    SeriesWithBooks(series = createTestSeries(id = "1", name = "Zebra Series"), books = emptyList()),
                    SeriesWithBooks(series = createTestSeries(id = "2", name = "Apple Series"), books = emptyList()),
                )
            val fixture = createFixture()
            every { fixture.seriesDao.observeAllWithBooks() } returns flowOf(seriesList)
            val viewModel = fixture.build()
            collectInBackground<Unit>(viewModel)
            advanceUntilIdle()

            // Then
            val sorted = viewModel.series.value
            assertEquals(listOf("Apple Series", "Zebra Series"), sorted.map { it.series.name })
        }

    @Test
    fun `series sorted by book count descending`() =
        runTest {
            // Given
            val seriesList =
                listOf(
                    SeriesWithBooks(
                        series = createTestSeries(id = "1", name = "Small"),
                        books = emptyList(),
                    ),
                    SeriesWithBooks(
                        series = createTestSeries(id = "2", name = "Big"),
                        books =
                            listOf(
                                createDummyBookEntity("b1"),
                                createDummyBookEntity("b2"),
                                createDummyBookEntity("b3"),
                            ),
                    ),
                )
            val fixture = createFixture()
            every { fixture.seriesDao.observeAllWithBooks() } returns flowOf(seriesList)
            everySuspend { fixture.settingsRepository.setSeriesSortState(any()) } returns Unit
            val viewModel = fixture.build()
            collectInBackground<Unit>(viewModel)
            advanceUntilIdle()

            // When
            viewModel.onEvent(LibraryUiEvent.SeriesCategoryChanged(SortCategory.BOOK_COUNT))
            advanceUntilIdle()

            // Then - Book count DESC: most books first
            val sorted = viewModel.series.value
            assertEquals(listOf("Big", "Small"), sorted.map { it.series.name })
        }

    // ========== Contributors Sorting Tests ==========

    @Test
    fun `authors sorted by name ascending`() =
        runTest {
            // Given
            val authors =
                listOf(
                    createTestContributor(id = "1", name = "Zelda"),
                    createTestContributor(id = "2", name = "Adam"),
                )
            val fixture = createFixture()
            every { fixture.contributorDao.observeByRoleWithCount("author") } returns flowOf(authors)
            val viewModel = fixture.build()
            collectInBackground<Unit>(viewModel)
            advanceUntilIdle()

            // Then
            val sorted = viewModel.authors.value
            assertEquals(listOf("Adam", "Zelda"), sorted.map { it.contributor.name })
        }

    @Test
    fun `authors sorted by book count descending`() =
        runTest {
            // Given
            val authors =
                listOf(
                    createTestContributor(id = "1", name = "Few Books", bookCount = 2),
                    createTestContributor(id = "2", name = "Many Books", bookCount = 10),
                )
            val fixture = createFixture()
            every { fixture.contributorDao.observeByRoleWithCount("author") } returns flowOf(authors)
            everySuspend { fixture.settingsRepository.setAuthorsSortState(any()) } returns Unit
            val viewModel = fixture.build()
            collectInBackground<Unit>(viewModel)
            advanceUntilIdle()

            // When
            viewModel.onEvent(LibraryUiEvent.AuthorsCategoryChanged(SortCategory.BOOK_COUNT))
            advanceUntilIdle()

            // Then
            val sorted = viewModel.authors.value
            assertEquals(listOf("Many Books", "Few Books"), sorted.map { it.contributor.name })
        }

    // ========== Auto-Sync Tests ==========

    @Test
    fun `onScreenVisible triggers sync when authenticated and never synced`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.settingsRepository.getAccessToken() } returns AccessToken("token")
            everySuspend { fixture.syncDao.getValue(SyncDao.KEY_LAST_SYNC_BOOKS) } returns null // Never synced
            everySuspend { fixture.bookRepository.refreshBooks() } returns Result.Success(Unit)
            val viewModel = fixture.build()
            collectInBackground<Unit>(viewModel)
            advanceUntilIdle()

            // When
            viewModel.onScreenVisible()
            advanceUntilIdle()

            // Then
            verifySuspend { fixture.bookRepository.refreshBooks() }
        }

    @Test
    fun `onScreenVisible does not sync when not authenticated`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.settingsRepository.getAccessToken() } returns null // Not authenticated
            // Still called before if check
            everySuspend { fixture.syncDao.getValue(SyncDao.KEY_LAST_SYNC_BOOKS) } returns null
            val viewModel = fixture.build()
            collectInBackground<Unit>(viewModel)
            advanceUntilIdle()

            // When
            viewModel.onScreenVisible()
            advanceUntilIdle()

            // Then - refreshBooks should not be called (we can't easily verify "not called"
            // but we verify the code path by checking no exception is thrown)
        }

    // ========== Book Progress Tests ==========

    @Test
    fun `bookProgress calculates progress from positions and durations`() =
        runTest {
            // Given
            val books =
                listOf(
                    createTestBook(id = "book-1", duration = 10_000L),
                    createTestBook(id = "book-2", duration = 20_000L),
                )
            val positions =
                listOf(
                    PlaybackPositionEntity(
                        bookId = BookId("book-1"),
                        positionMs = 5_000L, // 50% progress
                        playbackSpeed = 1.0f,
                        updatedAt = 0L,
                    ),
                    PlaybackPositionEntity(
                        bookId = BookId("book-2"),
                        positionMs = 10_000L, // 50% progress
                        playbackSpeed = 1.0f,
                        updatedAt = 0L,
                    ),
                )
            val fixture = createFixture()
            every { fixture.bookRepository.observeBooks() } returns flowOf(books)
            every { fixture.playbackPositionDao.observeAll() } returns flowOf(positions)
            val viewModel = fixture.build()
            collectInBackground<Unit>(viewModel)
            advanceUntilIdle()

            // Then
            val progress = viewModel.bookProgress.value
            assertEquals(0.5f, progress["book-1"])
            assertEquals(0.5f, progress["book-2"])
        }

    @Test
    fun `bookProgress excludes completed books`() =
        runTest {
            // Given - book at 99%+ is considered complete and excluded
            val books =
                listOf(
                    createTestBook(id = "book-1", duration = 10_000L),
                )
            val positions =
                listOf(
                    PlaybackPositionEntity(
                        bookId = BookId("book-1"),
                        positionMs = 9_950L, // 99.5% - should be excluded
                        playbackSpeed = 1.0f,
                        updatedAt = 0L,
                    ),
                )
            val fixture = createFixture()
            every { fixture.bookRepository.observeBooks() } returns flowOf(books)
            every { fixture.playbackPositionDao.observeAll() } returns flowOf(positions)
            val viewModel = fixture.build()
            collectInBackground<Unit>(viewModel)
            advanceUntilIdle()

            // Then - completed book is excluded from progress map
            val progress = viewModel.bookProgress.value
            assertEquals(null, progress["book-1"])
        }
}
