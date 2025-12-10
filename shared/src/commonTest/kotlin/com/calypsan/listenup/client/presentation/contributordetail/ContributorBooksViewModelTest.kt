package com.calypsan.listenup.client.presentation.contributordetail

import com.calypsan.listenup.client.data.local.db.BookContributorCrossRef
import com.calypsan.listenup.client.data.local.db.BookDao
import com.calypsan.listenup.client.data.local.db.BookEntity
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.db.BookWithContributors
import com.calypsan.listenup.client.data.local.db.ContributorDao
import com.calypsan.listenup.client.data.local.db.ContributorEntity
import com.calypsan.listenup.client.data.local.db.PlaybackPositionDao
import com.calypsan.listenup.client.data.local.db.PlaybackPositionEntity
import com.calypsan.listenup.client.data.local.db.SyncState
import com.calypsan.listenup.client.data.local.db.Timestamp
import com.calypsan.listenup.client.data.local.images.ImageStorage
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

        fun build(): ContributorBooksViewModel = ContributorBooksViewModel(
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
    ): ContributorEntity = ContributorEntity(
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
        seriesName: String? = null,
        seriesId: String? = null,
        sequence: String? = null,
    ): BookEntity = BookEntity(
        id = BookId(id),
        title = title,
        subtitle = null,
        coverUrl = null,
        totalDuration = duration,
        description = null,
        genres = null,
        seriesId = seriesId,
        seriesName = seriesName,
        sequence = sequence,
        publishYear = 2024,
        audioFilesJson = null,
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
    ): BookWithContributors = BookWithContributors(
        book = bookEntity,
        contributors = contributors,
        contributorRoles = roles,
    )

    private fun createPlaybackPosition(
        bookId: String,
        positionMs: Long,
    ): PlaybackPositionEntity = PlaybackPositionEntity(
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
    fun `initial state has isLoading false and empty data`() = runTest {
        // Given
        val fixture = createFixture()
        val viewModel = fixture.build()

        // Then
        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals("", state.contributorName)
        assertEquals("", state.roleDisplayName)
        assertTrue(state.seriesGroups.isEmpty())
        assertTrue(state.standaloneBooks.isEmpty())
        assertNull(state.error)
    }

    // ========== Load Books Tests ==========

    @Test
    fun `loadBooks sets isLoading to true initially`() = runTest {
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
    fun `loadBooks populates contributor name`() = runTest {
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
    fun `loadBooks sets role display name`() = runTest {
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
    fun `loadBooks groups books by series`() = runTest {
        // Given
        val fixture = createFixture()
        val book1 = createBookEntity(
            id = "book-1",
            title = "The Dark Tower",
            seriesName = "Dark Tower",
            sequence = "1",
        )
        val book2 = createBookEntity(
            id = "book-2",
            title = "The Drawing of the Three",
            seriesName = "Dark Tower",
            sequence = "2",
        )
        val viewModel = fixture.build()

        // When
        viewModel.loadBooks("contributor-1", "author")
        fixture.booksFlow.value = listOf(
            createBookWithContributors(book1),
            createBookWithContributors(book2),
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
    fun `loadBooks separates standalone books from series books`() = runTest {
        // Given
        val fixture = createFixture()
        val seriesBook = createBookEntity(
            id = "book-1",
            title = "Series Book",
            seriesName = "A Series",
        )
        val standaloneBook = createBookEntity(
            id = "book-2",
            title = "Standalone Book",
            seriesName = null,
        )
        val viewModel = fixture.build()

        // When
        viewModel.loadBooks("contributor-1", "author")
        fixture.booksFlow.value = listOf(
            createBookWithContributors(seriesBook),
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
    fun `loadBooks sorts series books by sequence`() = runTest {
        // Given
        val fixture = createFixture()
        val book1 = createBookEntity(id = "book-1", title = "Book One", seriesName = "Series", sequence = "2")
        val book2 = createBookEntity(id = "book-2", title = "Book Two", seriesName = "Series", sequence = "1")
        val book3 = createBookEntity(id = "book-3", title = "Book Three", seriesName = "Series", sequence = "1.5")
        val viewModel = fixture.build()

        // When
        viewModel.loadBooks("contributor-1", "author")
        fixture.booksFlow.value = listOf(
            createBookWithContributors(book1),
            createBookWithContributors(book2),
            createBookWithContributors(book3),
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
    fun `loadBooks sorts standalone books alphabetically`() = runTest {
        // Given
        val fixture = createFixture()
        val book1 = createBookEntity(id = "book-1", title = "Zebra", seriesName = null)
        val book2 = createBookEntity(id = "book-2", title = "Alpha", seriesName = null)
        val book3 = createBookEntity(id = "book-3", title = "Beta", seriesName = null)
        val viewModel = fixture.build()

        // When
        viewModel.loadBooks("contributor-1", "author")
        fixture.booksFlow.value = listOf(
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
    fun `loadBooks sorts series groups alphabetically by series name`() = runTest {
        // Given
        val fixture = createFixture()
        val book1 = createBookEntity(id = "book-1", title = "Book", seriesName = "Zebra Series")
        val book2 = createBookEntity(id = "book-2", title = "Book", seriesName = "Alpha Series")
        val viewModel = fixture.build()

        // When
        viewModel.loadBooks("contributor-1", "author")
        fixture.booksFlow.value = listOf(
            createBookWithContributors(book1),
            createBookWithContributors(book2),
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
    fun `loadBooks calculates book progress`() = runTest {
        // Given
        val fixture = createFixture()
        val book = createBookEntity(id = "book-1", duration = 10_000L, seriesName = null)
        everySuspend { fixture.playbackPositionDao.get(BookId("book-1")) } returns createPlaybackPosition(
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
    fun `loadBooks excludes completed books from progress`() = runTest {
        // Given
        val fixture = createFixture()
        val book = createBookEntity(id = "book-1", duration = 10_000L, seriesName = null)
        everySuspend { fixture.playbackPositionDao.get(BookId("book-1")) } returns createPlaybackPosition(
            "book-1",
            9_999L,
        )
        val viewModel = fixture.build()

        // When
        viewModel.loadBooks("contributor-1", "author")
        fixture.booksFlow.value = listOf(createBookWithContributors(book))
        advanceUntilIdle()

        // Then
        assertFalse(viewModel.state.value.bookProgress.containsKey("book-1"))
    }

    // ========== UI State Derived Properties Tests ==========

    @Test
    fun `totalBooks returns sum of series and standalone books`() = runTest {
        // Given
        val fixture = createFixture()
        val seriesBook1 = createBookEntity(id = "book-1", title = "Series 1", seriesName = "S")
        val seriesBook2 = createBookEntity(id = "book-2", title = "Series 2", seriesName = "S")
        val standalone = createBookEntity(id = "book-3", title = "Standalone", seriesName = null)
        val viewModel = fixture.build()

        // When
        viewModel.loadBooks("contributor-1", "author")
        fixture.booksFlow.value = listOf(
            createBookWithContributors(seriesBook1),
            createBookWithContributors(seriesBook2),
            createBookWithContributors(standalone),
        )
        advanceUntilIdle()

        // Then
        assertEquals(3, viewModel.state.value.totalBooks)
    }

    @Test
    fun `hasStandaloneBooks is true when standalone books exist`() = runTest {
        // Given
        val fixture = createFixture()
        val book = createBookEntity(id = "book-1", seriesName = null)
        val viewModel = fixture.build()

        // When
        viewModel.loadBooks("contributor-1", "author")
        fixture.booksFlow.value = listOf(createBookWithContributors(book))
        advanceUntilIdle()

        // Then
        assertTrue(viewModel.state.value.hasStandaloneBooks)
    }

    @Test
    fun `hasStandaloneBooks is false when no standalone books`() = runTest {
        // Given
        val fixture = createFixture()
        val book = createBookEntity(id = "book-1", seriesName = "Series")
        val viewModel = fixture.build()

        // When
        viewModel.loadBooks("contributor-1", "author")
        fixture.booksFlow.value = listOf(createBookWithContributors(book))
        advanceUntilIdle()

        // Then
        assertFalse(viewModel.state.value.hasStandaloneBooks)
    }

    // ========== Cover Path Tests ==========

    @Test
    fun `loadBooks sets coverPath when image exists`() = runTest {
        // Given
        val fixture = createFixture()
        val book = createBookEntity(id = "book-1", seriesName = null)
        every { fixture.imageStorage.exists(BookId("book-1")) } returns true
        every { fixture.imageStorage.getCoverPath(BookId("book-1")) } returns "/path/to/cover.jpg"
        val viewModel = fixture.build()

        // When
        viewModel.loadBooks("contributor-1", "author")
        fixture.booksFlow.value = listOf(createBookWithContributors(book))
        advanceUntilIdle()

        // Then
        assertEquals("/path/to/cover.jpg", viewModel.state.value.standaloneBooks[0].coverPath)
    }

    // ========== Empty State Tests ==========

    @Test
    fun `loadBooks handles empty book list`() = runTest {
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
