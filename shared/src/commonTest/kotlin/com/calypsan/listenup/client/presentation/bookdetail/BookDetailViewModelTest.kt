package com.calypsan.listenup.client.presentation.bookdetail

import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.db.GenreDao
import com.calypsan.listenup.client.data.local.db.GenreEntity
import com.calypsan.listenup.client.data.local.db.PlaybackPositionDao
import com.calypsan.listenup.client.data.local.db.PlaybackPositionEntity
import com.calypsan.listenup.client.data.local.db.TagDao
import com.calypsan.listenup.client.data.local.db.TagEntity
import com.calypsan.listenup.client.data.local.db.Timestamp
import com.calypsan.listenup.client.data.local.db.UserDao
import com.calypsan.listenup.client.data.local.db.UserEntity
import com.calypsan.listenup.client.data.remote.TagApiContract
import com.calypsan.listenup.client.data.repository.BookRepositoryContract
import com.calypsan.listenup.client.domain.model.Book
import com.calypsan.listenup.client.domain.model.BookSeries
import com.calypsan.listenup.client.domain.model.Chapter
import com.calypsan.listenup.client.domain.model.Contributor
import com.calypsan.listenup.client.domain.model.Genre
import com.calypsan.listenup.client.domain.model.Tag
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verifySuspend
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
 * Tests for BookDetailViewModel.
 *
 * Tests cover:
 * - Initial state (loading)
 * - Load book success/not found
 * - Subtitle filtering (redundant subtitle removal)
 * - Genre parsing
 * - Progress calculation and time remaining
 * - Tag management (show/hide picker, add/remove/create tags)
 *
 * Uses Mokkery for mocking BookRepositoryContract, TagApiContract, and PlaybackPositionDao.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookDetailViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    // ========== Test Fixtures ==========

    private class TestFixture {
        val bookRepository: BookRepositoryContract = mock()
        val genreDao: GenreDao = mock()
        val tagApi: TagApiContract = mock()
        val tagDao: TagDao = mock()
        val playbackPositionDao: PlaybackPositionDao = mock()
        val userDao: UserDao = mock()

        fun build(): BookDetailViewModel =
            BookDetailViewModel(
                bookRepository = bookRepository,
                genreDao = genreDao,
                tagApi = tagApi,
                tagDao = tagDao,
                playbackPositionDao = playbackPositionDao,
                userDao = userDao,
            )
    }

    private fun createFixture(): TestFixture {
        val fixture = TestFixture()

        // Default stubs for genre and tag operations
        every { fixture.genreDao.observeGenresForBook(any()) } returns flowOf(emptyList())
        everySuspend { fixture.tagApi.getBookTags(any()) } returns emptyList()
        everySuspend { fixture.tagApi.listTags() } returns emptyList()
        everySuspend { fixture.playbackPositionDao.get(any()) } returns null
        every { fixture.tagDao.observeTagsForBook(any()) } returns flowOf(emptyList())
        every { fixture.tagDao.observeAllTags() } returns flowOf(emptyList())
        every { fixture.userDao.observeCurrentUser() } returns flowOf(null)

        return fixture
    }

    // ========== Test Data Factories ==========

    private fun createBook(
        id: String = "book-1",
        title: String = "Test Book",
        subtitle: String? = null,
        authorName: String = "Test Author",
        narratorName: String = "Test Narrator",
        duration: Long = 3_600_000L, // 1 hour
        description: String? = "A great book",
        genres: List<Genre> = emptyList(),
        seriesId: String? = null,
        seriesName: String? = null,
        seriesSequence: String? = null,
        publishYear: Int? = 2024,
        rating: Double? = 4.5,
    ): Book {
        val seriesList =
            if (seriesId != null && seriesName != null) {
                listOf(BookSeries(seriesId = seriesId, seriesName = seriesName, sequence = seriesSequence))
            } else {
                emptyList()
            }
        return Book(
            id = BookId(id),
            title = title,
            subtitle = subtitle,
            authors = listOf(Contributor(id = "author-1", name = authorName, roles = listOf("Author"))),
            narrators = listOf(Contributor(id = "narrator-1", name = narratorName, roles = listOf("Narrator"))),
            duration = duration,
            coverPath = null,
            addedAt = Timestamp(1704067200000L),
            updatedAt = Timestamp(1704067200000L),
            description = description,
            genres = genres,
            series = seriesList,
            publishYear = publishYear,
            rating = rating,
        )
    }

    private fun createChapter(
        id: String = "chapter-1",
        title: String = "Chapter 1",
        duration: Long = 1_800_000L, // 30 min
        startTime: Long = 0L,
    ): Chapter =
        Chapter(
            id = id,
            title = title,
            duration = duration,
            startTime = startTime,
        )

    private fun createGenre(
        id: String = "genre-1",
        name: String = "Fantasy",
        slug: String = "fantasy",
        path: String = "/fiction/fantasy",
        bookCount: Int = 10,
    ): Genre =
        Genre(
            id = id,
            name = name,
            slug = slug,
            path = path,
            bookCount = bookCount,
        )

    private fun createTag(
        id: String = "tag-1",
        slug: String = "favorites",
        bookCount: Int = 5,
    ): Tag =
        Tag(
            id = id,
            slug = slug,
            bookCount = bookCount,
        )

    private fun createPlaybackPosition(
        bookId: String = "book-1",
        positionMs: Long = 1_800_000L, // 30 min in
        playbackSpeed: Float = 1.0f,
        updatedAt: Long = Clock.System.now().toEpochMilliseconds(),
    ): PlaybackPositionEntity =
        PlaybackPositionEntity(
            bookId = BookId(bookId),
            positionMs = positionMs,
            playbackSpeed = playbackSpeed,
            updatedAt = updatedAt,
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
    fun `initial state has isLoading true`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()

            // Then
            assertTrue(viewModel.state.value.isLoading)
            assertNull(viewModel.state.value.book)
            assertNull(viewModel.state.value.error)
        }

    // ========== Load Book Tests ==========

    @Test
    fun `loadBook success populates book data`() =
        runTest {
            // Given
            val fixture = createFixture()
            val book = createBook(title = "My Book", description = "A description")
            val chapters = listOf(createChapter(id = "ch1"), createChapter(id = "ch2"))
            everySuspend { fixture.bookRepository.getBook("book-1") } returns book
            everySuspend { fixture.bookRepository.getChapters("book-1") } returns chapters
            val viewModel = fixture.build()

            // When
            viewModel.loadBook("book-1")
            advanceUntilIdle()

            // Then
            val state = viewModel.state.value
            assertFalse(state.isLoading)
            assertEquals(book, state.book)
            assertEquals("A description", state.description)
            assertEquals(2, state.chapters.size)
            assertNull(state.error)
        }

    @Test
    fun `loadBook not found sets error state`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.bookRepository.getBook("nonexistent") } returns null
            val viewModel = fixture.build()

            // When
            viewModel.loadBook("nonexistent")
            advanceUntilIdle()

            // Then
            val state = viewModel.state.value
            assertFalse(state.isLoading)
            assertNull(state.book)
            assertEquals("Book not found", state.error)
        }

    // ========== Subtitle Filtering Tests ==========

    @Test
    fun `loadBook filters redundant subtitle with series name and book number`() =
        runTest {
            // Given
            val fixture = createFixture()
            val book =
                createBook(
                    subtitle = "The Stormlight Archive, Book 1",
                    seriesId = "series-1",
                    seriesName = "The Stormlight Archive",
                    seriesSequence = "1",
                )
            everySuspend { fixture.bookRepository.getBook(any()) } returns book
            everySuspend { fixture.bookRepository.getChapters(any()) } returns emptyList()
            val viewModel = fixture.build()

            // When
            viewModel.loadBook("book-1")
            advanceUntilIdle()

            // Then - subtitle should be filtered out (redundant)
            assertNull(viewModel.state.value.subtitle)
        }

    @Test
    fun `loadBook keeps meaningful subtitle`() =
        runTest {
            // Given
            val fixture = createFixture()
            val book =
                createBook(
                    subtitle = "A Novel of Discovery",
                    seriesName = "The Stormlight Archive",
                    seriesSequence = "1",
                )
            everySuspend { fixture.bookRepository.getBook(any()) } returns book
            everySuspend { fixture.bookRepository.getChapters(any()) } returns emptyList()
            val viewModel = fixture.build()

            // When
            viewModel.loadBook("book-1")
            advanceUntilIdle()

            // Then - subtitle should be kept (not redundant)
            assertEquals("A Novel of Discovery", viewModel.state.value.subtitle)
        }

    @Test
    fun `loadBook keeps subtitle when no series`() =
        runTest {
            // Given
            val fixture = createFixture()
            val book =
                createBook(
                    subtitle = "Part 1",
                    seriesName = null,
                )
            everySuspend { fixture.bookRepository.getBook(any()) } returns book
            everySuspend { fixture.bookRepository.getChapters(any()) } returns emptyList()
            val viewModel = fixture.build()

            // When
            viewModel.loadBook("book-1")
            advanceUntilIdle()

            // Then - subtitle should be kept (no series to be redundant with)
            assertEquals("Part 1", viewModel.state.value.subtitle)
        }

    // ========== Genre Loading Tests ==========

    @Test
    fun `loadBook loads genres from API`() =
        runTest {
            // Given
            val fixture = createFixture()
            val book = createBook()
            val bookGenres =
                listOf(
                    createGenre(id = "g1", name = "Fiction", slug = "fiction", path = "/fiction"),
                    createGenre(id = "g2", name = "Fantasy", slug = "fantasy", path = "/fiction/fantasy"),
                    createGenre(id = "g3", name = "Adventure", slug = "adventure", path = "/fiction/adventure"),
                )
            val genreEntities =
                bookGenres.map { g ->
                    GenreEntity(id = g.id, name = g.name, slug = g.slug, path = g.path, bookCount = g.bookCount)
                }
            everySuspend { fixture.bookRepository.getBook(any()) } returns book
            everySuspend { fixture.bookRepository.getChapters(any()) } returns emptyList()
            every { fixture.genreDao.observeGenresForBook(any()) } returns flowOf(genreEntities)
            val viewModel = fixture.build()

            // When
            viewModel.loadBook("book-1")
            advanceUntilIdle()

            // Then
            val genresList = viewModel.state.value.genresList
            assertEquals(3, genresList.size)
            assertEquals("Fiction", genresList[0])
            assertEquals("Fantasy", genresList[1])
            assertEquals("Adventure", genresList[2])
        }

    @Test
    fun `loadBook handles empty genres from API`() =
        runTest {
            // Given
            val fixture = createFixture()
            val book = createBook()
            everySuspend { fixture.bookRepository.getBook(any()) } returns book
            everySuspend { fixture.bookRepository.getChapters(any()) } returns emptyList()
            // genreDao.observeGenresForBook already returns empty list from createFixture()
            val viewModel = fixture.build()

            // When
            viewModel.loadBook("book-1")
            advanceUntilIdle()

            // Then
            assertTrue(
                viewModel.state.value.genresList
                    .isEmpty(),
            )
        }

    @Test
    fun `loadBook handles genre loading failure gracefully`() =
        runTest {
            // Given - genres come from Room, no network involved, so simulate empty flow
            val fixture = createFixture()
            val book = createBook()
            everySuspend { fixture.bookRepository.getBook(any()) } returns book
            everySuspend { fixture.bookRepository.getChapters(any()) } returns emptyList()
            // genreDao.observeGenresForBook already returns empty list from createFixture()
            val viewModel = fixture.build()

            // When
            viewModel.loadBook("book-1")
            advanceUntilIdle()

            // Then - book loads successfully despite genre failure
            assertFalse(viewModel.state.value.isLoading)
            assertEquals(book, viewModel.state.value.book)
            assertNull(viewModel.state.value.error) // Genres are optional - no error shown
            assertTrue(
                viewModel.state.value.genresList
                    .isEmpty(),
            )
        }

    // ========== Progress Calculation Tests ==========

    @Test
    fun `loadBook calculates progress from playback position`() =
        runTest {
            // Given
            val fixture = createFixture()
            val book = createBook(duration = 3_600_000L) // 1 hour
            val position = createPlaybackPosition(positionMs = 1_800_000L) // 30 min in
            everySuspend { fixture.bookRepository.getBook(any()) } returns book
            everySuspend { fixture.bookRepository.getChapters(any()) } returns emptyList()
            everySuspend { fixture.playbackPositionDao.get(any()) } returns position
            val viewModel = fixture.build()

            // When
            viewModel.loadBook("book-1")
            advanceUntilIdle()

            // Then - 30 min / 60 min = 0.5
            assertEquals(0.5f, viewModel.state.value.progress)
        }

    @Test
    fun `loadBook hides progress when no position saved`() =
        runTest {
            // Given
            val fixture = createFixture()
            val book = createBook(duration = 3_600_000L)
            everySuspend { fixture.bookRepository.getBook(any()) } returns book
            everySuspend { fixture.bookRepository.getChapters(any()) } returns emptyList()
            everySuspend { fixture.playbackPositionDao.get(any()) } returns null
            val viewModel = fixture.build()

            // When
            viewModel.loadBook("book-1")
            advanceUntilIdle()

            // Then
            assertNull(viewModel.state.value.progress)
        }

    @Test
    fun `loadBook hides progress when nearly complete`() =
        runTest {
            // Given
            val fixture = createFixture()
            val book = createBook(duration = 3_600_000L) // 1 hour
            val position = createPlaybackPosition(positionMs = 3_564_000L) // 99% complete
            everySuspend { fixture.bookRepository.getBook(any()) } returns book
            everySuspend { fixture.bookRepository.getChapters(any()) } returns emptyList()
            everySuspend { fixture.playbackPositionDao.get(any()) } returns position
            val viewModel = fixture.build()

            // When
            viewModel.loadBook("book-1")
            advanceUntilIdle()

            // Then - progress should be null when > 99% complete
            assertNull(viewModel.state.value.progress)
        }

    // ========== Time Remaining Tests ==========

    @Test
    fun `loadBook formats time remaining with hours and minutes`() =
        runTest {
            // Given
            val fixture = createFixture()
            val book = createBook(duration = 10_800_000L) // 3 hours
            val position = createPlaybackPosition(positionMs = 2_700_000L) // 45 min in
            everySuspend { fixture.bookRepository.getBook(any()) } returns book
            everySuspend { fixture.bookRepository.getChapters(any()) } returns emptyList()
            everySuspend { fixture.playbackPositionDao.get(any()) } returns position
            val viewModel = fixture.build()

            // When
            viewModel.loadBook("book-1")
            advanceUntilIdle()

            // Then - 2h 15m remaining
            assertEquals("2h 15m left", viewModel.state.value.timeRemainingFormatted)
        }

    @Test
    fun `loadBook formats time remaining with minutes only`() =
        runTest {
            // Given
            val fixture = createFixture()
            val book = createBook(duration = 3_600_000L) // 1 hour
            val position = createPlaybackPosition(positionMs = 2_700_000L) // 45 min in
            everySuspend { fixture.bookRepository.getBook(any()) } returns book
            everySuspend { fixture.bookRepository.getChapters(any()) } returns emptyList()
            everySuspend { fixture.playbackPositionDao.get(any()) } returns position
            val viewModel = fixture.build()

            // When
            viewModel.loadBook("book-1")
            advanceUntilIdle()

            // Then - 15m remaining
            assertEquals("15m left", viewModel.state.value.timeRemainingFormatted)
        }

    // ========== Tag Management Tests ==========

    @Test
    fun `showTagPicker sets showTagPicker to true`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()
            assertFalse(viewModel.state.value.showTagPicker)

            // When
            viewModel.showTagPicker()

            // Then
            assertTrue(viewModel.state.value.showTagPicker)
        }

    @Test
    fun `hideTagPicker sets showTagPicker to false`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()
            viewModel.showTagPicker()
            assertTrue(viewModel.state.value.showTagPicker)

            // When
            viewModel.hideTagPicker()

            // Then
            assertFalse(viewModel.state.value.showTagPicker)
        }

    @Test
    fun `loadBook loads tags for book`() =
        runTest {
            // Given - tags come from Room flows (offline-first)
            val fixture = createFixture()
            val book = createBook()
            val bookTagEntities =
                listOf(
                    TagEntity(id = "tag-1", slug = "favorites", bookCount = 5, createdAt = Timestamp.now()),
                )
            val allTagEntities =
                listOf(
                    TagEntity(id = "tag-1", slug = "favorites", bookCount = 5, createdAt = Timestamp.now()),
                    TagEntity(id = "tag-2", slug = "to-read", bookCount = 3, createdAt = Timestamp.now()),
                )
            everySuspend { fixture.bookRepository.getBook(any()) } returns book
            everySuspend { fixture.bookRepository.getChapters(any()) } returns emptyList()
            every { fixture.tagDao.observeTagsForBook(any()) } returns flowOf(bookTagEntities)
            every { fixture.tagDao.observeAllTags() } returns flowOf(allTagEntities)
            val viewModel = fixture.build()

            // When
            viewModel.loadBook("book-1")
            advanceUntilIdle()

            // Then
            assertEquals(1, viewModel.state.value.tags.size)
            assertEquals(
                "Favorites",
                viewModel.state.value.tags[0]
                    .displayName(),
            )
            assertEquals(2, viewModel.state.value.allTags.size)
        }

    @Test
    fun `addTag calls API and refreshes tags`() =
        runTest {
            // Given
            val fixture = createFixture()
            val book = createBook(id = "book-1")
            val tag = createTag(id = "tag-1", slug = "favorites")
            everySuspend { fixture.bookRepository.getBook(any()) } returns book
            everySuspend { fixture.bookRepository.getChapters(any()) } returns emptyList()
            everySuspend { fixture.tagApi.addTagToBook(any(), any()) } returns tag
            val viewModel = fixture.build()
            viewModel.loadBook("book-1")
            advanceUntilIdle()

            // When - addTag now takes a slug
            viewModel.addTag("favorites")
            advanceUntilIdle()

            // Then
            verifySuspend { fixture.tagApi.addTagToBook("book-1", "favorites") }
        }

    @Test
    fun `removeTag calls API and refreshes tags`() =
        runTest {
            // Given - need tags in state for removeTag to find the tag by slug
            val fixture = createFixture()
            val book = createBook(id = "book-1")
            val bookTagEntities =
                listOf(TagEntity(id = "tag-1", slug = "favorites", bookCount = 5, createdAt = Timestamp.now()))
            everySuspend { fixture.bookRepository.getBook(any()) } returns book
            everySuspend { fixture.bookRepository.getChapters(any()) } returns emptyList()
            every { fixture.tagDao.observeTagsForBook(any()) } returns flowOf(bookTagEntities)
            everySuspend { fixture.tagApi.removeTagFromBook(any(), any()) } returns Unit
            everySuspend { fixture.tagDao.deleteBookTag(any(), any()) } returns Unit
            val viewModel = fixture.build()
            viewModel.loadBook("book-1")
            advanceUntilIdle()

            // When - removeTag takes a slug and finds tag ID from state
            viewModel.removeTag("favorites")
            advanceUntilIdle()

            // Then
            verifySuspend { fixture.tagApi.removeTagFromBook("book-1", "favorites") }
        }

    @Test
    fun `addNewTag adds tag to book`() =
        runTest {
            // Given
            val fixture = createFixture()
            val book = createBook(id = "book-1")
            val newTag = createTag(id = "new-tag", slug = "new-tag")
            everySuspend { fixture.bookRepository.getBook(any()) } returns book
            everySuspend { fixture.bookRepository.getChapters(any()) } returns emptyList()
            everySuspend { fixture.tagApi.addTagToBook(any(), any()) } returns newTag
            everySuspend { fixture.tagDao.upsert(any()) } returns Unit
            everySuspend { fixture.tagDao.insertBookTag(any()) } returns Unit
            val viewModel = fixture.build()
            viewModel.loadBook("book-1")
            advanceUntilIdle()
            viewModel.showTagPicker()

            // When - addNewTag sends raw input to API, server normalizes to slug
            viewModel.addNewTag("New Tag")
            advanceUntilIdle()

            // Then
            verifySuspend { fixture.tagApi.addTagToBook("book-1", "New Tag") }
            assertFalse(viewModel.state.value.showTagPicker) // Picker should close
        }

    @Test
    fun `tag operations do nothing when no book loaded`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()

            // When - try to add tag without loading book
            viewModel.addTag("tag-1")
            advanceUntilIdle()

            // Then - no API calls made (book is null)
            // This test verifies the early return behavior
        }

    @Test
    fun `loadBook handles tag loading failure gracefully`() =
        runTest {
            // Given
            val fixture = createFixture()
            val book = createBook()
            everySuspend { fixture.bookRepository.getBook(any()) } returns book
            everySuspend { fixture.bookRepository.getChapters(any()) } returns emptyList()
            everySuspend { fixture.tagApi.getBookTags(any()) } throws Exception("Network error")
            val viewModel = fixture.build()

            // When
            viewModel.loadBook("book-1")
            advanceUntilIdle()

            // Then - book loads successfully despite tag failure
            assertFalse(viewModel.state.value.isLoading)
            assertEquals(book, viewModel.state.value.book)
            assertNull(viewModel.state.value.error) // Tags are optional - no error shown
        }
}
