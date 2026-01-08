package com.calypsan.listenup.client.presentation.bookedit

import com.calypsan.listenup.client.TestData
import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.domain.model.BookEditData
import com.calypsan.listenup.client.domain.model.BookMetadata
import com.calypsan.listenup.client.domain.model.ContributorSearchResponse
import com.calypsan.listenup.client.domain.model.ContributorSearchResult
import com.calypsan.listenup.client.domain.model.SeriesSearchResponse
import com.calypsan.listenup.client.domain.model.SeriesSearchResult
import com.calypsan.listenup.client.domain.repository.ContributorRepository
import com.calypsan.listenup.client.domain.repository.ImageRepository
import com.calypsan.listenup.client.domain.repository.SeriesRepository
import com.calypsan.listenup.client.domain.usecase.book.LoadBookForEditUseCase
import com.calypsan.listenup.client.domain.usecase.book.UpdateBookUseCase
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * Tests for BookEditViewModel.
 *
 * Tests cover:
 * - Initial state (loading)
 * - Load book success/not found (via use case)
 * - Metadata change tracking (hasChanges)
 * - Contributor management (add, select, remove)
 * - Series management
 * - Genre/Tag management
 * - Save functionality (via use case)
 *
 * Uses Mokkery for mocking use cases and repository contracts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookEditViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    // ========== Test Fixtures ==========

    private class TestFixture {
        val loadBookForEditUseCase: LoadBookForEditUseCase = mock()
        val updateBookUseCase: UpdateBookUseCase = mock()
        val contributorRepository: ContributorRepository = mock()
        val seriesRepository: SeriesRepository = mock()
        val imageRepository: ImageRepository = mock()

        fun build(): BookEditViewModel =
            BookEditViewModel(
                loadBookForEditUseCase = loadBookForEditUseCase,
                updateBookUseCase = updateBookUseCase,
                contributorRepository = contributorRepository,
                seriesRepository = seriesRepository,
                imageRepository = imageRepository,
            )
    }

    private fun createFixture(): TestFixture {
        val fixture = TestFixture()

        // Default stubs for delegate operations
        everySuspend { fixture.contributorRepository.searchContributors(any(), any()) } returns
            ContributorSearchResponse(emptyList(), false, 0L)
        everySuspend { fixture.seriesRepository.searchSeries(any(), any()) } returns
            SeriesSearchResponse(emptyList(), false, 0L)

        return fixture
    }

    private fun createBookEditData(
        bookId: String = "book-1",
        title: String = "Test Book",
        subtitle: String = "",
        description: String = "",
        publishYear: String = "",
        publisher: String = "",
        language: String? = null,
        isbn: String = "",
        asin: String = "",
        abridged: Boolean = false,
        addedAt: Long? = null,
        contributors: List<EditableContributor> = emptyList(),
        series: List<EditableSeries> = emptyList(),
        genres: List<EditableGenre> = emptyList(),
        tags: List<EditableTag> = emptyList(),
        allGenres: List<EditableGenre> = emptyList(),
        allTags: List<EditableTag> = emptyList(),
        coverPath: String? = null,
    ): BookEditData =
        BookEditData(
            bookId = bookId,
            metadata =
                BookMetadata(
                    title = title,
                    subtitle = subtitle,
                    description = description,
                    publishYear = publishYear,
                    publisher = publisher,
                    language = language,
                    isbn = isbn,
                    asin = asin,
                    abridged = abridged,
                    addedAt = addedAt,
                ),
            contributors = contributors,
            series = series,
            genres = genres,
            tags = tags,
            allGenres = allGenres,
            allTags = allTags,
            coverPath = coverPath,
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
            assertEquals("", viewModel.state.value.title)
            assertFalse(viewModel.state.value.hasChanges)
        }

    // ========== Load Book Tests ==========

    @Test
    fun `loadBook success populates state with book data`() =
        runTest {
            // Given
            val fixture = createFixture()
            val author = EditableContributor(id = "author-1", name = "Brandon Sanderson", roles = setOf(ContributorRole.AUTHOR))
            val narrator = EditableContributor(id = "narrator-1", name = "Michael Kramer", roles = setOf(ContributorRole.NARRATOR))
            val editData =
                createBookEditData(
                    bookId = "book-1",
                    title = "The Way of Kings",
                    subtitle = "Book One of the Stormlight Archive",
                    description = "An epic fantasy adventure",
                    publishYear = "2010",
                    publisher = "Tor Books",
                    language = "en",
                    contributors = listOf(author, narrator),
                )
            everySuspend { fixture.loadBookForEditUseCase("book-1") } returns Success(editData)
            val viewModel = fixture.build()

            // When
            viewModel.loadBook("book-1")
            advanceUntilIdle()

            // Then
            val state = viewModel.state.value
            assertFalse(state.isLoading)
            assertEquals("The Way of Kings", state.title)
            assertEquals("Book One of the Stormlight Archive", state.subtitle)
            assertEquals("An epic fantasy adventure", state.description)
            assertEquals("2010", state.publishYear)
            assertEquals("Tor Books", state.publisher)
            assertEquals("en", state.language)
            assertFalse(state.hasChanges)
            assertNull(state.error)
        }

    @Test
    fun `loadBook not found sets error state`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.loadBookForEditUseCase("nonexistent") } returns Failure(message = "Book not found")
            val viewModel = fixture.build()

            // When
            viewModel.loadBook("nonexistent")
            advanceUntilIdle()

            // Then
            val state = viewModel.state.value
            assertFalse(state.isLoading)
            assertEquals("Book not found", state.error)
        }

    @Test
    fun `loadBook converts contributors to editable format with roles`() =
        runTest {
            // Given
            val fixture = createFixture()
            val contributor =
                EditableContributor(
                    id = "person-1",
                    name = "Patrick Rothfuss",
                    roles = setOf(ContributorRole.AUTHOR, ContributorRole.NARRATOR),
                )
            val editData =
                createBookEditData(
                    bookId = "book-1",
                    title = "The Name of the Wind",
                    contributors = listOf(contributor),
                )
            everySuspend { fixture.loadBookForEditUseCase("book-1") } returns Success(editData)
            val viewModel = fixture.build()

            // When
            viewModel.loadBook("book-1")
            advanceUntilIdle()

            // Then
            val state = viewModel.state.value
            assertEquals(1, state.contributors.size)
            val editable = state.contributors.first()
            assertEquals("Patrick Rothfuss", editable.name)
            assertTrue(ContributorRole.AUTHOR in editable.roles)
            assertTrue(ContributorRole.NARRATOR in editable.roles)
        }

    // ========== Metadata Change Tracking Tests ==========

    @Test
    fun `changing title sets hasChanges to true`() =
        runTest {
            // Given
            val fixture = createFixture()
            val editData = createBookEditData(bookId = "book-1", title = "Original Title")
            everySuspend { fixture.loadBookForEditUseCase("book-1") } returns Success(editData)
            val viewModel = fixture.build()
            viewModel.loadBook("book-1")
            advanceUntilIdle()
            assertFalse(viewModel.state.value.hasChanges)

            // When
            viewModel.onEvent(BookEditUiEvent.TitleChanged("New Title"))

            // Then
            assertTrue(viewModel.state.value.hasChanges)
            assertEquals("New Title", viewModel.state.value.title)
        }

    @Test
    fun `reverting title to original clears hasChanges`() =
        runTest {
            // Given
            val fixture = createFixture()
            val editData = createBookEditData(bookId = "book-1", title = "Original Title")
            everySuspend { fixture.loadBookForEditUseCase("book-1") } returns Success(editData)
            val viewModel = fixture.build()
            viewModel.loadBook("book-1")
            advanceUntilIdle()

            // When - change then revert
            viewModel.onEvent(BookEditUiEvent.TitleChanged("New Title"))
            assertTrue(viewModel.state.value.hasChanges)
            viewModel.onEvent(BookEditUiEvent.TitleChanged("Original Title"))

            // Then
            assertFalse(viewModel.state.value.hasChanges)
        }

    @Test
    fun `publish year only accepts numeric input`() =
        runTest {
            // Given
            val fixture = createFixture()
            val editData = createBookEditData(bookId = "book-1")
            everySuspend { fixture.loadBookForEditUseCase("book-1") } returns Success(editData)
            val viewModel = fixture.build()
            viewModel.loadBook("book-1")
            advanceUntilIdle()

            // When - try to input invalid characters
            viewModel.onEvent(BookEditUiEvent.PublishYearChanged("20ab24cd"))

            // Then - only digits kept, max 4 chars
            assertEquals("2024", viewModel.state.value.publishYear)
        }

    // ========== Contributor Management Tests ==========

    @Test
    fun `adding role section makes role visible`() =
        runTest {
            // Given
            val fixture = createFixture()
            val editData = createBookEditData(bookId = "book-1")
            everySuspend { fixture.loadBookForEditUseCase("book-1") } returns Success(editData)
            val viewModel = fixture.build()
            viewModel.loadBook("book-1")
            advanceUntilIdle()

            // When
            viewModel.onEvent(BookEditUiEvent.AddRoleSection(ContributorRole.EDITOR))

            // Then
            assertTrue(ContributorRole.EDITOR in viewModel.state.value.visibleRoles)
        }

    @Test
    fun `entering contributor name adds new contributor with role`() =
        runTest {
            // Given
            val fixture = createFixture()
            val editData = createBookEditData(bookId = "book-1", contributors = emptyList())
            everySuspend { fixture.loadBookForEditUseCase("book-1") } returns Success(editData)
            val viewModel = fixture.build()
            viewModel.loadBook("book-1")
            advanceUntilIdle()

            // When
            viewModel.onEvent(BookEditUiEvent.RoleContributorEntered(ContributorRole.AUTHOR, "New Author"))
            advanceUntilIdle()

            // Then
            val authors = viewModel.state.value.authors
            assertEquals(1, authors.size)
            assertEquals("New Author", authors.first().name)
            assertNull(authors.first().id) // New contributor has no ID
            assertTrue(viewModel.state.value.hasChanges)
        }

    @Test
    fun `selecting contributor from search adds with existing ID`() =
        runTest {
            // Given
            val fixture = createFixture()
            val editData = createBookEditData(bookId = "book-1", contributors = emptyList())
            val searchResult = ContributorSearchResult(id = "existing-1", name = "Existing Author", bookCount = 10)
            everySuspend { fixture.loadBookForEditUseCase("book-1") } returns Success(editData)
            val viewModel = fixture.build()
            viewModel.loadBook("book-1")
            advanceUntilIdle()

            // When
            viewModel.onEvent(BookEditUiEvent.RoleContributorSelected(ContributorRole.AUTHOR, searchResult))
            advanceUntilIdle()

            // Then
            val authors = viewModel.state.value.authors
            assertEquals(1, authors.size)
            assertEquals("existing-1", authors.first().id)
            assertEquals("Existing Author", authors.first().name)
        }

    @Test
    fun `removing contributor from role updates state`() =
        runTest {
            // Given
            val fixture = createFixture()
            val author = EditableContributor(id = "author-1", name = "Author Name", roles = setOf(ContributorRole.AUTHOR))
            val editData = createBookEditData(bookId = "book-1", contributors = listOf(author))
            everySuspend { fixture.loadBookForEditUseCase("book-1") } returns Success(editData)
            val viewModel = fixture.build()
            viewModel.loadBook("book-1")
            advanceUntilIdle()
            assertEquals(1, viewModel.state.value.authors.size)

            // When
            val editable =
                viewModel.state.value.contributors
                    .first()
            viewModel.onEvent(BookEditUiEvent.RemoveContributor(editable, ContributorRole.AUTHOR))

            // Then
            assertEquals(0, viewModel.state.value.authors.size)
            assertTrue(viewModel.state.value.hasChanges)
        }

    // ========== Series Management Tests ==========

    @Test
    fun `selecting series from search adds to book`() =
        runTest {
            // Given
            val fixture = createFixture()
            val editData = createBookEditData(bookId = "book-1")
            val searchResult = SeriesSearchResult(id = "series-1", name = "The Stormlight Archive", bookCount = 4)
            everySuspend { fixture.loadBookForEditUseCase("book-1") } returns Success(editData)
            val viewModel = fixture.build()
            viewModel.loadBook("book-1")
            advanceUntilIdle()

            // When
            viewModel.onEvent(BookEditUiEvent.SeriesSelected(searchResult))

            // Then
            assertEquals(1, viewModel.state.value.series.size)
            assertEquals(
                "The Stormlight Archive",
                viewModel.state.value.series
                    .first()
                    .name,
            )
            assertTrue(viewModel.state.value.hasChanges)
        }

    @Test
    fun `entering series name creates new series`() =
        runTest {
            // Given
            val fixture = createFixture()
            val editData = createBookEditData(bookId = "book-1")
            everySuspend { fixture.loadBookForEditUseCase("book-1") } returns Success(editData)
            val viewModel = fixture.build()
            viewModel.loadBook("book-1")
            advanceUntilIdle()

            // When
            viewModel.onEvent(BookEditUiEvent.SeriesEntered("New Fantasy Series"))

            // Then
            assertEquals(1, viewModel.state.value.series.size)
            assertEquals(
                "New Fantasy Series",
                viewModel.state.value.series
                    .first()
                    .name,
            )
            assertNull(
                viewModel.state.value.series
                    .first()
                    .id,
            ) // New series has no ID
        }

    @Test
    fun `updating series sequence modifies existing series`() =
        runTest {
            // Given
            val fixture = createFixture()
            val series = EditableSeries(id = "series-1", name = "Mistborn", sequence = null)
            val editData = createBookEditData(bookId = "book-1", series = listOf(series))
            everySuspend { fixture.loadBookForEditUseCase("book-1") } returns Success(editData)
            val viewModel = fixture.build()
            viewModel.loadBook("book-1")
            advanceUntilIdle()

            // When
            val loadedSeries =
                viewModel.state.value.series
                    .first()
            viewModel.onEvent(BookEditUiEvent.SeriesSequenceChanged(loadedSeries, "1"))

            // Then
            assertEquals(
                "1",
                viewModel.state.value.series
                    .first()
                    .sequence,
            )
            assertTrue(viewModel.state.value.hasChanges)
        }

    // ========== Genre Management Tests ==========

    @Test
    fun `selecting genre adds to book`() =
        runTest {
            // Given
            val fixture = createFixture()
            val editData = createBookEditData(bookId = "book-1")
            val genre = EditableGenre(id = "genre-1", name = "Fantasy", path = "/fiction/fantasy")
            everySuspend { fixture.loadBookForEditUseCase("book-1") } returns Success(editData)
            val viewModel = fixture.build()
            viewModel.loadBook("book-1")
            advanceUntilIdle()

            // When
            viewModel.onEvent(BookEditUiEvent.GenreSelected(genre))

            // Then
            assertEquals(1, viewModel.state.value.genres.size)
            assertEquals(
                "Fantasy",
                viewModel.state.value.genres
                    .first()
                    .name,
            )
            assertTrue(viewModel.state.value.hasChanges)
        }

    @Test
    fun `removing genre updates state`() =
        runTest {
            // Given
            val fixture = createFixture()
            val genre = EditableGenre(id = "genre-1", name = "Fantasy", path = "/fiction/fantasy")
            val editData = createBookEditData(bookId = "book-1", genres = listOf(genre))
            everySuspend { fixture.loadBookForEditUseCase("book-1") } returns Success(editData)
            val viewModel = fixture.build()
            viewModel.loadBook("book-1")
            advanceUntilIdle()
            assertEquals(1, viewModel.state.value.genres.size)

            // When
            val editableGenre =
                viewModel.state.value.genres
                    .first()
            viewModel.onEvent(BookEditUiEvent.RemoveGenre(editableGenre))

            // Then
            assertEquals(0, viewModel.state.value.genres.size)
            assertTrue(viewModel.state.value.hasChanges)
        }

    // ========== Tag Management Tests ==========

    @Test
    fun `selecting tag adds to book`() =
        runTest {
            // Given
            val fixture = createFixture()
            val editData = createBookEditData(bookId = "book-1")
            val tag = EditableTag(id = "tag-1", slug = "favorites")
            everySuspend { fixture.loadBookForEditUseCase("book-1") } returns Success(editData)
            val viewModel = fixture.build()
            viewModel.loadBook("book-1")
            advanceUntilIdle()

            // When
            viewModel.onEvent(BookEditUiEvent.TagSelected(tag))

            // Then
            assertEquals(1, viewModel.state.value.tags.size)
            assertEquals(
                "Favorites",
                viewModel.state.value.tags
                    .first()
                    .displayName(),
            )
            assertTrue(viewModel.state.value.hasChanges)
        }

    @Test
    fun `removing tag updates state`() =
        runTest {
            // Given
            val fixture = createFixture()
            val tag = EditableTag(id = "tag-1", slug = "favorites")
            val editData = createBookEditData(bookId = "book-1", tags = listOf(tag))
            everySuspend { fixture.loadBookForEditUseCase("book-1") } returns Success(editData)
            val viewModel = fixture.build()
            viewModel.loadBook("book-1")
            advanceUntilIdle()
            assertEquals(1, viewModel.state.value.tags.size)

            // When
            val editableTag =
                viewModel.state.value.tags
                    .first()
            viewModel.onEvent(BookEditUiEvent.RemoveTag(editableTag))

            // Then
            assertEquals(0, viewModel.state.value.tags.size)
            assertTrue(viewModel.state.value.hasChanges)
        }

    // ========== Save Tests ==========

    @Test
    fun `save with no changes navigates back without calling use case`() =
        runTest {
            // Given
            val fixture = createFixture()
            val editData = createBookEditData(bookId = "book-1", title = "Unchanged")
            everySuspend { fixture.loadBookForEditUseCase("book-1") } returns Success(editData)
            val viewModel = fixture.build()
            viewModel.loadBook("book-1")
            advanceUntilIdle()
            assertFalse(viewModel.state.value.hasChanges)

            // When
            viewModel.onEvent(BookEditUiEvent.Save)
            advanceUntilIdle()

            // Then
            assertEquals(BookEditNavAction.NavigateBack, viewModel.navActions.value)
        }

    @Test
    fun `save with changes calls use case and navigates back`() =
        runTest {
            // Given
            val fixture = createFixture()
            val editData = createBookEditData(bookId = "book-1", title = "Original")
            everySuspend { fixture.loadBookForEditUseCase("book-1") } returns Success(editData)
            everySuspend { fixture.updateBookUseCase(any(), any()) } returns Success(Unit)
            val viewModel = fixture.build()
            viewModel.loadBook("book-1")
            advanceUntilIdle()

            // When
            viewModel.onEvent(BookEditUiEvent.TitleChanged("Updated"))
            viewModel.onEvent(BookEditUiEvent.Save)
            advanceUntilIdle()

            // Then
            verifySuspend { fixture.updateBookUseCase(any(), any()) }
            assertEquals(BookEditNavAction.NavigateBack, viewModel.navActions.value)
        }

    @Test
    fun `save failure shows error`() =
        runTest {
            // Given
            val fixture = createFixture()
            val editData = createBookEditData(bookId = "book-1", title = "Original")
            everySuspend { fixture.loadBookForEditUseCase("book-1") } returns Success(editData)
            everySuspend { fixture.updateBookUseCase(any(), any()) } returns Failure(message = "Save failed")
            val viewModel = fixture.build()
            viewModel.loadBook("book-1")
            advanceUntilIdle()

            // When
            viewModel.onEvent(BookEditUiEvent.TitleChanged("Updated"))
            viewModel.onEvent(BookEditUiEvent.Save)
            advanceUntilIdle()

            // Then
            assertEquals("Save failed", viewModel.state.value.error)
            assertNull(viewModel.navActions.value) // Should not navigate
        }

    @Test
    fun `cancel navigates back`() =
        runTest {
            // Given
            val fixture = createFixture()
            val editData = createBookEditData(bookId = "book-1")
            everySuspend { fixture.loadBookForEditUseCase("book-1") } returns Success(editData)
            val viewModel = fixture.build()
            viewModel.loadBook("book-1")
            advanceUntilIdle()

            // When
            viewModel.onEvent(BookEditUiEvent.Cancel)

            // Then
            assertEquals(BookEditNavAction.NavigateBack, viewModel.navActions.value)
        }

    @Test
    fun `dismiss error clears error state`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.loadBookForEditUseCase("nonexistent") } returns Failure(message = "Book not found")
            val viewModel = fixture.build()
            viewModel.loadBook("nonexistent")
            advanceUntilIdle()
            assertEquals("Book not found", viewModel.state.value.error)

            // When
            viewModel.onEvent(BookEditUiEvent.DismissError)

            // Then
            assertNull(viewModel.state.value.error)
        }
}
