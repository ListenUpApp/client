package com.calypsan.listenup.client.presentation.contributordetail

import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.core.Timestamp
import com.calypsan.listenup.client.domain.model.Book
import com.calypsan.listenup.client.domain.model.Contributor
import com.calypsan.listenup.client.domain.model.RoleWithBookCount
import com.calypsan.listenup.client.domain.repository.BookWithContributorRole
import com.calypsan.listenup.client.domain.repository.ContributorRepository
import com.calypsan.listenup.client.domain.model.PlaybackPosition
import com.calypsan.listenup.client.domain.repository.PlaybackPositionRepository
import com.calypsan.listenup.client.domain.usecase.contributor.DeleteContributorUseCase
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
 * Tests for ContributorDetailViewModel.
 *
 * Tests cover:
 * - Initial state
 * - Load contributor with roles and books
 * - Role display name conversion
 * - Book progress calculation
 * - View all threshold logic
 *
 * Uses Mokkery for mocking DAOs and ImageStorage.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContributorDetailViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    // ========== Test Fixtures ==========

    private class TestFixture {
        val contributorRepository: ContributorRepository = mock()
        val playbackPositionRepository: PlaybackPositionRepository = mock()
        val deleteContributorUseCase: DeleteContributorUseCase = mock()

        val contributorFlow = MutableStateFlow<Contributor?>(null)
        val rolesFlow = MutableStateFlow<List<RoleWithBookCount>>(emptyList())

        fun build(): ContributorDetailViewModel =
            ContributorDetailViewModel(
                contributorRepository = contributorRepository,
                playbackPositionRepository = playbackPositionRepository,
                deleteContributorUseCase = deleteContributorUseCase,
            )
    }

    private fun createFixture(): TestFixture {
        val fixture = TestFixture()

        // Default stubs
        every { fixture.contributorRepository.observeById(any()) } returns fixture.contributorFlow
        every { fixture.contributorRepository.observeRolesWithCountForContributor(any()) } returns fixture.rolesFlow
        every { fixture.contributorRepository.observeBooksForContributorRole(any(), any()) } returns flowOf(emptyList())
        everySuspend { fixture.playbackPositionRepository.get(any()) } returns null

        return fixture
    }

    // ========== Test Data Factories ==========

    private fun createContributor(
        id: String = "contributor-1",
        name: String = "Test Author",
        description: String? = "A prolific author",
    ): Contributor =
        Contributor(
            id = id,
            name = name,
            description = description,
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
            assertNull(state.contributor)
            assertTrue(state.roleSections.isEmpty())
            assertNull(state.error)
        }

    // ========== Load Contributor Tests ==========

    @Test
    fun `loadContributor sets isLoading to true initially`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()

            // When
            viewModel.loadContributor("contributor-1")
            // Don't advance - check immediate state

            // Then
            assertTrue(viewModel.state.value.isLoading)
        }

    @Test
    fun `loadContributor success populates contributor data`() =
        runTest {
            // Given
            val fixture = createFixture()
            val contributor = createContributor(name = "Stephen King", description = "Master of horror")
            val roles = listOf(RoleWithBookCount(role = "author", bookCount = 5))

            val viewModel = fixture.build()

            // When
            viewModel.loadContributor("contributor-1")
            fixture.contributorFlow.value = contributor
            fixture.rolesFlow.value = roles
            advanceUntilIdle()

            // Then
            val state = viewModel.state.value
            assertFalse(state.isLoading)
            assertEquals("Stephen King", state.contributor?.name)
            assertEquals("Master of horror", state.contributor?.description)
            assertNull(state.error)
        }

    @Test
    fun `loadContributor loads books for each role`() =
        runTest {
            // Given
            val fixture = createFixture()
            val contributor = createContributor()
            val authorRole = RoleWithBookCount(role = "author", bookCount = 2)
            val book = createBook(id = "book-1", title = "The Shining")
            val bookWithContributors = createBookWithContributorRole(book)

            every { fixture.contributorRepository.observeBooksForContributorRole("contributor-1", "author") } returns
                flowOf(
                    listOf(bookWithContributors),
                )
            val viewModel = fixture.build()

            // When
            viewModel.loadContributor("contributor-1")
            fixture.contributorFlow.value = contributor
            fixture.rolesFlow.value = listOf(authorRole)
            advanceUntilIdle()

            // Then
            val state = viewModel.state.value
            assertEquals(1, state.roleSections.size)
            assertEquals("author", state.roleSections[0].role)
            assertEquals(1, state.roleSections[0].previewBooks.size)
            assertEquals("The Shining", state.roleSections[0].previewBooks[0].title)
        }

    @Test
    fun `loadContributor creates multiple role sections`() =
        runTest {
            // Given
            val fixture = createFixture()
            val contributor = createContributor()
            val authorRole = RoleWithBookCount(role = "author", bookCount = 3)
            val narratorRole = RoleWithBookCount(role = "narrator", bookCount = 2)

            val book1 = createBook(id = "book-1", title = "Author Book")
            val book2 = createBook(id = "book-2", title = "Narrator Book")

            every { fixture.contributorRepository.observeBooksForContributorRole("contributor-1", "author") } returns
                flowOf(
                    listOf(createBookWithContributorRole(book1)),
                )
            every { fixture.contributorRepository.observeBooksForContributorRole("contributor-1", "narrator") } returns
                flowOf(
                    listOf(createBookWithContributorRole(book2)),
                )
            val viewModel = fixture.build()

            // When
            viewModel.loadContributor("contributor-1")
            fixture.contributorFlow.value = contributor
            fixture.rolesFlow.value = listOf(authorRole, narratorRole)
            advanceUntilIdle()

            // Then
            val state = viewModel.state.value
            assertEquals(2, state.roleSections.size)
            assertEquals("author", state.roleSections[0].role)
            assertEquals("narrator", state.roleSections[1].role)
        }

    // ========== Role Display Name Tests ==========

    @Test
    fun `roleToDisplayName converts author to Written By`() {
        assertEquals("Written By", ContributorDetailViewModel.roleToDisplayName("author"))
    }

    @Test
    fun `roleToDisplayName converts narrator to Narrated By`() {
        assertEquals("Narrated By", ContributorDetailViewModel.roleToDisplayName("narrator"))
    }

    @Test
    fun `roleToDisplayName converts translator to Translated By`() {
        assertEquals("Translated By", ContributorDetailViewModel.roleToDisplayName("translator"))
    }

    @Test
    fun `roleToDisplayName converts editor to Edited By`() {
        assertEquals("Edited By", ContributorDetailViewModel.roleToDisplayName("editor"))
    }

    @Test
    fun `roleToDisplayName capitalizes unknown roles`() {
        assertEquals("Producer", ContributorDetailViewModel.roleToDisplayName("producer"))
    }

    @Test
    fun `roleToDisplayName handles uppercase input`() {
        assertEquals("Written By", ContributorDetailViewModel.roleToDisplayName("AUTHOR"))
    }

    // ========== Book Progress Tests ==========

    @Test
    fun `loadContributor calculates book progress`() =
        runTest {
            // Given
            val fixture = createFixture()
            val contributor = createContributor()
            val role = RoleWithBookCount(role = "author", bookCount = 1)
            val book = createBook(id = "book-1", duration = 10_000L)

            every { fixture.contributorRepository.observeBooksForContributorRole("contributor-1", "author") } returns
                flowOf(
                    listOf(createBookWithContributorRole(book)),
                )
            everySuspend { fixture.playbackPositionRepository.get("book-1") } returns
                createPlaybackPosition(
                    "book-1",
                    5_000L,
                )
            val viewModel = fixture.build()

            // When
            viewModel.loadContributor("contributor-1")
            fixture.contributorFlow.value = contributor
            fixture.rolesFlow.value = listOf(role)
            advanceUntilIdle()

            // Then
            val state = viewModel.state.value
            assertEquals(0.5f, state.bookProgress["book-1"])
        }

    @Test
    fun `loadContributor excludes completed books from progress`() =
        runTest {
            // Given
            val fixture = createFixture()
            val contributor = createContributor()
            val role = RoleWithBookCount(role = "author", bookCount = 1)
            val book = createBook(id = "book-1", duration = 10_000L)

            every { fixture.contributorRepository.observeBooksForContributorRole("contributor-1", "author") } returns
                flowOf(
                    listOf(createBookWithContributorRole(book)),
                )
            // 99% or more is considered complete
            everySuspend { fixture.playbackPositionRepository.get("book-1") } returns
                createPlaybackPosition(
                    "book-1",
                    9_999L,
                )
            val viewModel = fixture.build()

            // When
            viewModel.loadContributor("contributor-1")
            fixture.contributorFlow.value = contributor
            fixture.rolesFlow.value = listOf(role)
            advanceUntilIdle()

            // Then
            val state = viewModel.state.value
            assertFalse(state.bookProgress.containsKey("book-1"))
        }

    @Test
    fun `loadContributor excludes zero progress from map`() =
        runTest {
            // Given
            val fixture = createFixture()
            val contributor = createContributor()
            val role = RoleWithBookCount(role = "author", bookCount = 1)
            val book = createBook(id = "book-1", duration = 10_000L)

            every { fixture.contributorRepository.observeBooksForContributorRole("contributor-1", "author") } returns
                flowOf(
                    listOf(createBookWithContributorRole(book)),
                )
            everySuspend { fixture.playbackPositionRepository.get("book-1") } returns createPlaybackPosition("book-1", 0L)
            val viewModel = fixture.build()

            // When
            viewModel.loadContributor("contributor-1")
            fixture.contributorFlow.value = contributor
            fixture.rolesFlow.value = listOf(role)
            advanceUntilIdle()

            // Then
            val state = viewModel.state.value
            assertFalse(state.bookProgress.containsKey("book-1"))
        }

    // ========== View All Threshold Tests ==========

    @Test
    fun `RoleSection showViewAll is true when bookCount exceeds threshold`() {
        val section =
            RoleSection(
                role = "author",
                displayName = "Written By",
                bookCount = ContributorDetailViewModel.VIEW_ALL_THRESHOLD + 1,
                previewBooks = emptyList(),
            )

        assertTrue(section.showViewAll)
    }

    @Test
    fun `RoleSection showViewAll is false when bookCount at threshold`() {
        val section =
            RoleSection(
                role = "author",
                displayName = "Written By",
                bookCount = ContributorDetailViewModel.VIEW_ALL_THRESHOLD,
                previewBooks = emptyList(),
            )

        assertFalse(section.showViewAll)
    }

    @Test
    fun `RoleSection showViewAll is false when bookCount below threshold`() {
        val section =
            RoleSection(
                role = "author",
                displayName = "Written By",
                bookCount = 3,
                previewBooks = emptyList(),
            )

        assertFalse(section.showViewAll)
    }

    // ========== Cover Path Tests ==========

    @Test
    fun `loadContributor passes through coverPath from domain model`() =
        runTest {
            // Given - coverPath is now resolved in repository, ViewModel just passes it through
            val fixture = createFixture()
            val contributor = createContributor()
            val role = RoleWithBookCount(role = "author", bookCount = 1)
            val book = createBook(id = "book-1", coverPath = "/path/to/cover.jpg")

            every { fixture.contributorRepository.observeBooksForContributorRole("contributor-1", "author") } returns
                flowOf(
                    listOf(createBookWithContributorRole(book)),
                )
            val viewModel = fixture.build()

            // When
            viewModel.loadContributor("contributor-1")
            fixture.contributorFlow.value = contributor
            fixture.rolesFlow.value = listOf(role)
            advanceUntilIdle()

            // Then
            val state = viewModel.state.value
            assertEquals("/path/to/cover.jpg", state.roleSections[0].previewBooks[0].coverPath)
        }

    @Test
    fun `loadContributor handles null coverPath`() =
        runTest {
            // Given
            val fixture = createFixture()
            val contributor = createContributor()
            val role = RoleWithBookCount(role = "author", bookCount = 1)
            val book = createBook(id = "book-1", coverPath = null)

            every { fixture.contributorRepository.observeBooksForContributorRole("contributor-1", "author") } returns
                flowOf(
                    listOf(createBookWithContributorRole(book)),
                )
            val viewModel = fixture.build()

            // When
            viewModel.loadContributor("contributor-1")
            fixture.contributorFlow.value = contributor
            fixture.rolesFlow.value = listOf(role)
            advanceUntilIdle()

            // Then
            val state = viewModel.state.value
            assertNull(state.roleSections[0].previewBooks[0].coverPath)
        }

    // ========== Reactive Update Tests ==========

    @Test
    fun `loadContributor updates when contributor flow emits new value`() =
        runTest {
            // Given
            val fixture = createFixture()
            val contributor1 = createContributor(name = "Original Name")
            val contributor2 = createContributor(name = "Updated Name")
            val viewModel = fixture.build()

            // When - first emission
            viewModel.loadContributor("contributor-1")
            fixture.contributorFlow.value = contributor1
            fixture.rolesFlow.value = emptyList()
            advanceUntilIdle()
            assertEquals(
                "Original Name",
                viewModel.state.value.contributor
                    ?.name,
            )

            // When - second emission
            fixture.contributorFlow.value = contributor2
            advanceUntilIdle()

            // Then
            assertEquals(
                "Updated Name",
                viewModel.state.value.contributor
                    ?.name,
            )
        }
}
