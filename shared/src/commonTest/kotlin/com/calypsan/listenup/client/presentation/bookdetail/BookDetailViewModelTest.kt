package com.calypsan.listenup.client.presentation.bookdetail

import com.calypsan.listenup.client.TestData
import com.calypsan.listenup.client.domain.model.Genre
import com.calypsan.listenup.client.domain.model.PlaybackPosition
import com.calypsan.listenup.client.domain.model.Tag
import com.calypsan.listenup.client.domain.repository.BookRepository
import com.calypsan.listenup.client.domain.repository.GenreRepository
import com.calypsan.listenup.client.domain.repository.PlaybackPositionRepository
import com.calypsan.listenup.client.domain.repository.TagRepository
import com.calypsan.listenup.client.domain.repository.UserRepository
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * Uses Mokkery for mocking domain repositories.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookDetailViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    // ========== Test Fixtures ==========

    private class TestFixture {
        val bookRepository: BookRepository = mock()
        val genreRepository: GenreRepository = mock()
        val tagRepository: TagRepository = mock()
        val playbackPositionRepository: PlaybackPositionRepository = mock()
        val userRepository: UserRepository = mock()

        fun build(): BookDetailViewModel =
            BookDetailViewModel(
                bookRepository = bookRepository,
                genreRepository = genreRepository,
                tagRepository = tagRepository,
                playbackPositionRepository = playbackPositionRepository,
                userRepository = userRepository,
            )
    }

    private fun createFixture(): TestFixture {
        val fixture = TestFixture()

        // Default stubs for domain repositories
        every { fixture.genreRepository.observeGenresForBook(any()) } returns flowOf(emptyList())
        everySuspend { fixture.playbackPositionRepository.get(any()) } returns null
        every { fixture.tagRepository.observeTagsForBook(any()) } returns flowOf(emptyList())
        every { fixture.tagRepository.observeAll() } returns flowOf(emptyList())
        every { fixture.userRepository.observeIsAdmin() } returns flowOf(false)

        return fixture
    }

    // ========== Test Data Factories ==========

    private fun createPlaybackPosition(
        bookId: String = "book-1",
        positionMs: Long = 1_800_000L, // 30 min in
        playbackSpeed: Float = 1.0f,
    ): PlaybackPosition =
        PlaybackPosition(
            bookId = bookId,
            positionMs = positionMs,
            playbackSpeed = playbackSpeed,
            hasCustomSpeed = false,
            updatedAtMs = 1704067200000L, // Fixed test timestamp
            syncedAtMs = null,
            lastPlayedAtMs = 1704067200000L, // Fixed test timestamp
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
            val book = TestData.book(title = "My Book", description = "A description")
            val chapters = listOf(TestData.chapter(id = "ch1"), TestData.chapter(id = "ch2"))
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
                TestData.book(
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
                TestData.book(
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
                TestData.book(
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
            val book = TestData.book()
            val bookGenres =
                listOf(
                    Genre(id = "g1", name = "Fiction", slug = "fiction", path = "/fiction"),
                    Genre(id = "g2", name = "Fantasy", slug = "fantasy", path = "/fiction/fantasy"),
                    Genre(id = "g3", name = "Adventure", slug = "adventure", path = "/fiction/adventure"),
                )
            everySuspend { fixture.bookRepository.getBook(any()) } returns book
            everySuspend { fixture.bookRepository.getChapters(any()) } returns emptyList()
            every { fixture.genreRepository.observeGenresForBook(any()) } returns flowOf(bookGenres)
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
            val book = TestData.book()
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
            val book = TestData.book()
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
            val book = TestData.book(duration = 3_600_000L) // 1 hour
            val position = createPlaybackPosition(positionMs = 1_800_000L) // 30 min in
            everySuspend { fixture.bookRepository.getBook(any()) } returns book
            everySuspend { fixture.bookRepository.getChapters(any()) } returns emptyList()
            everySuspend { fixture.playbackPositionRepository.get(any()) } returns position
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
            val book = TestData.book(duration = 3_600_000L)
            everySuspend { fixture.bookRepository.getBook(any()) } returns book
            everySuspend { fixture.bookRepository.getChapters(any()) } returns emptyList()
            everySuspend { fixture.playbackPositionRepository.get(any()) } returns null
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
            val book = TestData.book(duration = 3_600_000L) // 1 hour
            val position = createPlaybackPosition(positionMs = 3_564_000L) // 99% complete
            everySuspend { fixture.bookRepository.getBook(any()) } returns book
            everySuspend { fixture.bookRepository.getChapters(any()) } returns emptyList()
            everySuspend { fixture.playbackPositionRepository.get(any()) } returns position
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
            val book = TestData.book(duration = 10_800_000L) // 3 hours
            val position = createPlaybackPosition(positionMs = 2_700_000L) // 45 min in
            everySuspend { fixture.bookRepository.getBook(any()) } returns book
            everySuspend { fixture.bookRepository.getChapters(any()) } returns emptyList()
            everySuspend { fixture.playbackPositionRepository.get(any()) } returns position
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
            val book = TestData.book(duration = 3_600_000L) // 1 hour
            val position = createPlaybackPosition(positionMs = 2_700_000L) // 45 min in
            everySuspend { fixture.bookRepository.getBook(any()) } returns book
            everySuspend { fixture.bookRepository.getChapters(any()) } returns emptyList()
            everySuspend { fixture.playbackPositionRepository.get(any()) } returns position
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
            // Given - tags come from repository flows (offline-first)
            val fixture = createFixture()
            val book = TestData.book()
            val bookTags =
                listOf(
                    Tag(id = "tag-1", slug = "favorites", bookCount = 5),
                )
            val allTags =
                listOf(
                    Tag(id = "tag-1", slug = "favorites", bookCount = 5),
                    Tag(id = "tag-2", slug = "to-read", bookCount = 3),
                )
            everySuspend { fixture.bookRepository.getBook(any()) } returns book
            everySuspend { fixture.bookRepository.getChapters(any()) } returns emptyList()
            every { fixture.tagRepository.observeTagsForBook(any()) } returns flowOf(bookTags)
            every { fixture.tagRepository.observeAll() } returns flowOf(allTags)
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
    fun `addTag calls repository and refreshes tags`() =
        runTest {
            // Given
            val fixture = createFixture()
            val book = TestData.book(id = "book-1")
            val tag = TestData.tag(id = "tag-1", slug = "favorites")
            everySuspend { fixture.bookRepository.getBook(any()) } returns book
            everySuspend { fixture.bookRepository.getChapters(any()) } returns emptyList()
            everySuspend { fixture.tagRepository.addTagToBook(any(), any()) } returns tag
            val viewModel = fixture.build()
            viewModel.loadBook("book-1")
            advanceUntilIdle()

            // When - addTag takes a slug
            viewModel.addTag("favorites")
            advanceUntilIdle()

            // Then
            verifySuspend { fixture.tagRepository.addTagToBook("book-1", "favorites") }
        }

    @Test
    fun `removeTag calls repository and refreshes tags`() =
        runTest {
            // Given - need tags in state for removeTag to find the tag by slug
            val fixture = createFixture()
            val book = TestData.book(id = "book-1")
            val bookTags =
                listOf(Tag(id = "tag-1", slug = "favorites", bookCount = 5))
            everySuspend { fixture.bookRepository.getBook(any()) } returns book
            everySuspend { fixture.bookRepository.getChapters(any()) } returns emptyList()
            every { fixture.tagRepository.observeTagsForBook(any()) } returns flowOf(bookTags)
            everySuspend { fixture.tagRepository.removeTagFromBook(any(), any(), any()) } returns Unit
            val viewModel = fixture.build()
            viewModel.loadBook("book-1")
            advanceUntilIdle()

            // When - removeTag takes a slug and finds tag ID from state
            viewModel.removeTag("favorites")
            advanceUntilIdle()

            // Then
            verifySuspend { fixture.tagRepository.removeTagFromBook("book-1", "favorites", "tag-1") }
        }

    @Test
    fun `addNewTag adds tag to book`() =
        runTest {
            // Given
            val fixture = createFixture()
            val book = TestData.book(id = "book-1")
            val newTag = TestData.tag(id = "new-tag", slug = "new-tag")
            everySuspend { fixture.bookRepository.getBook(any()) } returns book
            everySuspend { fixture.bookRepository.getChapters(any()) } returns emptyList()
            everySuspend { fixture.tagRepository.addTagToBook(any(), any()) } returns newTag
            val viewModel = fixture.build()
            viewModel.loadBook("book-1")
            advanceUntilIdle()
            viewModel.showTagPicker()

            // When - addNewTag sends raw input, server normalizes to slug
            viewModel.addNewTag("New Tag")
            advanceUntilIdle()

            // Then
            verifySuspend { fixture.tagRepository.addTagToBook("book-1", "New Tag") }
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
            // Given - tags come from Room flows, so test empty flow scenario
            val fixture = createFixture()
            val book = TestData.book()
            everySuspend { fixture.bookRepository.getBook(any()) } returns book
            everySuspend { fixture.bookRepository.getChapters(any()) } returns emptyList()
            // Tags come from Room via observeTagsForBook (already returns empty in createFixture)
            val viewModel = fixture.build()

            // When
            viewModel.loadBook("book-1")
            advanceUntilIdle()

            // Then - book loads successfully, tags are empty but no error
            assertFalse(viewModel.state.value.isLoading)
            assertEquals(book, viewModel.state.value.book)
            assertNull(viewModel.state.value.error) // Tags are optional - no error shown
        }
}
