package com.calypsan.listenup.client.presentation.contributordetail

import com.calypsan.listenup.client.core.AppResult
import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.core.Timestamp
import com.calypsan.listenup.client.domain.model.BookListItem
import com.calypsan.listenup.client.domain.model.Contributor
import com.calypsan.listenup.client.domain.model.ContributorRole
import com.calypsan.listenup.client.domain.model.PlaybackPosition
import com.calypsan.listenup.client.domain.model.RoleWithBookCount
import com.calypsan.listenup.client.domain.repository.BookWithContributorRole
import com.calypsan.listenup.client.domain.repository.ContributorRepository
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Tests for ContributorDetailViewModel.
 *
 * The VM uses `.stateIn(WhileSubscribed)`, so tests must keep a background
 * collector alive via [keepStateHot] before asserting on `state.value`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContributorDetailViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

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

        every { fixture.contributorRepository.observeById(any()) } returns fixture.contributorFlow
        every { fixture.contributorRepository.observeRolesWithCountForContributor(any()) } returns fixture.rolesFlow
        every { fixture.contributorRepository.observeBooksForContributorRole(any(), any()) } returns flowOf(emptyList())
        everySuspend { fixture.playbackPositionRepository.get(any<BookId>()) } returns AppResult.Success(null)

        return fixture
    }

    /** Keep the VM's WhileSubscribed state flow hot for the duration of the test. */
    private fun TestScope.keepStateHot(viewModel: ContributorDetailViewModel) {
        backgroundScope.launch { viewModel.state.collect { } }
    }

    private fun createContributor(
        id: String = "contributor-1",
        name: String = "Test Author",
        description: String? = "A prolific author",
    ): Contributor =
        Contributor(
            id =
                com.calypsan.listenup.client.core
                    .ContributorId(id),
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
    ): BookListItem =
        BookListItem(
            id = BookId(id),
            title = title,
            coverPath = coverPath,
            duration = duration,
            authors = emptyList(),
            narrators = emptyList(),
            addedAt = Timestamp(1704067200000L),
            updatedAt = Timestamp(1704067200000L),
        )

    private fun createBookWithContributorRole(
        book: BookListItem,
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
    fun `initial state is Idle pre-loadContributor`() =
        runTest {
            val fixture = createFixture()
            val viewModel = fixture.build()
            keepStateHot(viewModel)
            advanceUntilIdle()

            assertEquals(ContributorDetailUiState.Idle, viewModel.state.value)
        }

    // ========== Load Contributor ==========

    @Test
    fun `loadContributor success populates contributor data`() =
        runTest {
            val fixture = createFixture()
            val contributor = createContributor(name = "Stephen King", description = "Master of horror")
            val roles = listOf(RoleWithBookCount(role = ContributorRole.AUTHOR.apiValue, bookCount = 5))

            val viewModel = fixture.build()
            keepStateHot(viewModel)

            viewModel.loadContributor("contributor-1")
            fixture.contributorFlow.value = contributor
            fixture.rolesFlow.value = roles
            advanceUntilIdle()

            val state = assertIs<ContributorDetailUiState.Ready>(viewModel.state.value)
            assertEquals("Stephen King", state.contributor.name)
            assertEquals("Master of horror", state.contributor.description)
        }

    @Test
    fun `loadContributor loads books for each role`() =
        runTest {
            val fixture = createFixture()
            val contributor = createContributor()
            val authorRole = RoleWithBookCount(role = ContributorRole.AUTHOR.apiValue, bookCount = 2)
            val book = createBook(id = "book-1", title = "The Shining")

            every {
                fixture.contributorRepository.observeBooksForContributorRole("contributor-1", ContributorRole.AUTHOR.apiValue)
            } returns flowOf(listOf(createBookWithContributorRole(book)))
            val viewModel = fixture.build()
            keepStateHot(viewModel)

            viewModel.loadContributor("contributor-1")
            fixture.contributorFlow.value = contributor
            fixture.rolesFlow.value = listOf(authorRole)
            advanceUntilIdle()

            val state = assertIs<ContributorDetailUiState.Ready>(viewModel.state.value)
            assertEquals(1, state.roleSections.size)
            assertEquals(ContributorRole.AUTHOR.apiValue, state.roleSections[0].role)
            assertEquals(1, state.roleSections[0].previewBooks.size)
            assertEquals("The Shining", state.roleSections[0].previewBooks[0].title)
        }

    @Test
    fun `loadContributor creates multiple role sections`() =
        runTest {
            val fixture = createFixture()
            val contributor = createContributor()
            val authorRole = RoleWithBookCount(role = ContributorRole.AUTHOR.apiValue, bookCount = 3)
            val narratorRole = RoleWithBookCount(role = ContributorRole.NARRATOR.apiValue, bookCount = 2)

            val book1 = createBook(id = "book-1", title = "Author Book")
            val book2 = createBook(id = "book-2", title = "Narrator Book")

            every {
                fixture.contributorRepository.observeBooksForContributorRole("contributor-1", ContributorRole.AUTHOR.apiValue)
            } returns flowOf(listOf(createBookWithContributorRole(book1)))
            every {
                fixture.contributorRepository.observeBooksForContributorRole("contributor-1", ContributorRole.NARRATOR.apiValue)
            } returns flowOf(listOf(createBookWithContributorRole(book2)))
            val viewModel = fixture.build()
            keepStateHot(viewModel)

            viewModel.loadContributor("contributor-1")
            fixture.contributorFlow.value = contributor
            fixture.rolesFlow.value = listOf(authorRole, narratorRole)
            advanceUntilIdle()

            val state = assertIs<ContributorDetailUiState.Ready>(viewModel.state.value)
            assertEquals(2, state.roleSections.size)
            assertEquals(ContributorRole.AUTHOR.apiValue, state.roleSections[0].role)
            assertEquals(ContributorRole.NARRATOR.apiValue, state.roleSections[1].role)
        }

    // ========== Role Display Name ==========

    @Test
    fun `roleToDisplayName converts author to Written By`() {
        assertEquals("Written By", ContributorDetailViewModel.roleToDisplayName(ContributorRole.AUTHOR.apiValue))
    }

    @Test
    fun `roleToDisplayName converts narrator to Narrated By`() {
        assertEquals("Narrated By", ContributorDetailViewModel.roleToDisplayName(ContributorRole.NARRATOR.apiValue))
    }

    @Test
    fun `roleToDisplayName converts translator to Translated By`() {
        assertEquals("Translated By", ContributorDetailViewModel.roleToDisplayName(ContributorRole.TRANSLATOR.apiValue))
    }

    @Test
    fun `roleToDisplayName converts editor to Edited By`() {
        assertEquals("Edited By", ContributorDetailViewModel.roleToDisplayName(ContributorRole.EDITOR.apiValue))
    }

    @Test
    fun `roleToDisplayName capitalizes unknown roles`() {
        assertEquals("Producer", ContributorDetailViewModel.roleToDisplayName("producer"))
    }

    @Test
    fun `roleToDisplayName handles uppercase input`() {
        assertEquals("Written By", ContributorDetailViewModel.roleToDisplayName("AUTHOR"))
    }

    // ========== Book Progress ==========

    @Test
    fun `loadContributor calculates book progress`() =
        runTest {
            val fixture = createFixture()
            val contributor = createContributor()
            val role = RoleWithBookCount(role = ContributorRole.AUTHOR.apiValue, bookCount = 1)
            val book = createBook(id = "book-1", duration = 10_000L)

            every {
                fixture.contributorRepository.observeBooksForContributorRole("contributor-1", ContributorRole.AUTHOR.apiValue)
            } returns flowOf(listOf(createBookWithContributorRole(book)))
            everySuspend { fixture.playbackPositionRepository.get(BookId("book-1")) } returns
                AppResult.Success(createPlaybackPosition("book-1", 5_000L))
            val viewModel = fixture.build()
            keepStateHot(viewModel)

            viewModel.loadContributor("contributor-1")
            fixture.contributorFlow.value = contributor
            fixture.rolesFlow.value = listOf(role)
            advanceUntilIdle()

            val state = assertIs<ContributorDetailUiState.Ready>(viewModel.state.value)
            assertEquals(0.5f, state.bookProgress[BookId("book-1")])
        }

    @Test
    fun `loadContributor excludes completed books from progress`() =
        runTest {
            val fixture = createFixture()
            val contributor = createContributor()
            val role = RoleWithBookCount(role = ContributorRole.AUTHOR.apiValue, bookCount = 1)
            val book = createBook(id = "book-1", duration = 10_000L)

            every {
                fixture.contributorRepository.observeBooksForContributorRole("contributor-1", ContributorRole.AUTHOR.apiValue)
            } returns flowOf(listOf(createBookWithContributorRole(book)))
            everySuspend { fixture.playbackPositionRepository.get(BookId("book-1")) } returns
                AppResult.Success(createPlaybackPosition("book-1", 9_999L))
            val viewModel = fixture.build()
            keepStateHot(viewModel)

            viewModel.loadContributor("contributor-1")
            fixture.contributorFlow.value = contributor
            fixture.rolesFlow.value = listOf(role)
            advanceUntilIdle()

            val state = assertIs<ContributorDetailUiState.Ready>(viewModel.state.value)
            assertFalse(state.bookProgress.containsKey(BookId("book-1")))
        }

    @Test
    fun `loadContributor excludes zero progress from map`() =
        runTest {
            val fixture = createFixture()
            val contributor = createContributor()
            val role = RoleWithBookCount(role = ContributorRole.AUTHOR.apiValue, bookCount = 1)
            val book = createBook(id = "book-1", duration = 10_000L)

            every {
                fixture.contributorRepository.observeBooksForContributorRole("contributor-1", ContributorRole.AUTHOR.apiValue)
            } returns flowOf(listOf(createBookWithContributorRole(book)))
            everySuspend { fixture.playbackPositionRepository.get(BookId("book-1")) } returns AppResult.Success(createPlaybackPosition("book-1", 0L))
            val viewModel = fixture.build()
            keepStateHot(viewModel)

            viewModel.loadContributor("contributor-1")
            fixture.contributorFlow.value = contributor
            fixture.rolesFlow.value = listOf(role)
            advanceUntilIdle()

            val state = assertIs<ContributorDetailUiState.Ready>(viewModel.state.value)
            assertFalse(state.bookProgress.containsKey(BookId("book-1")))
        }

    // ========== View All Threshold ==========

    @Test
    fun `RoleSection showViewAll is true when bookCount exceeds threshold`() {
        val section =
            RoleSection(
                role = ContributorRole.AUTHOR.apiValue,
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
                role = ContributorRole.AUTHOR.apiValue,
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
                role = ContributorRole.AUTHOR.apiValue,
                displayName = "Written By",
                bookCount = 3,
                previewBooks = emptyList(),
            )

        assertFalse(section.showViewAll)
    }

    // ========== Cover Path ==========

    @Test
    fun `loadContributor passes through coverPath from domain model`() =
        runTest {
            val fixture = createFixture()
            val contributor = createContributor()
            val role = RoleWithBookCount(role = ContributorRole.AUTHOR.apiValue, bookCount = 1)
            val book = createBook(id = "book-1", coverPath = "/path/to/cover.jpg")

            every {
                fixture.contributorRepository.observeBooksForContributorRole("contributor-1", ContributorRole.AUTHOR.apiValue)
            } returns flowOf(listOf(createBookWithContributorRole(book)))
            val viewModel = fixture.build()
            keepStateHot(viewModel)

            viewModel.loadContributor("contributor-1")
            fixture.contributorFlow.value = contributor
            fixture.rolesFlow.value = listOf(role)
            advanceUntilIdle()

            val state = assertIs<ContributorDetailUiState.Ready>(viewModel.state.value)
            assertEquals("/path/to/cover.jpg", state.roleSections[0].previewBooks[0].coverPath)
        }

    @Test
    fun `loadContributor handles null coverPath`() =
        runTest {
            val fixture = createFixture()
            val contributor = createContributor()
            val role = RoleWithBookCount(role = ContributorRole.AUTHOR.apiValue, bookCount = 1)
            val book = createBook(id = "book-1", coverPath = null)

            every {
                fixture.contributorRepository.observeBooksForContributorRole("contributor-1", ContributorRole.AUTHOR.apiValue)
            } returns flowOf(listOf(createBookWithContributorRole(book)))
            val viewModel = fixture.build()
            keepStateHot(viewModel)

            viewModel.loadContributor("contributor-1")
            fixture.contributorFlow.value = contributor
            fixture.rolesFlow.value = listOf(role)
            advanceUntilIdle()

            val state = assertIs<ContributorDetailUiState.Ready>(viewModel.state.value)
            assertNull(state.roleSections[0].previewBooks[0].coverPath)
        }

    // ========== Reactive Updates ==========

    @Test
    fun `loadContributor updates when contributor flow emits new value`() =
        runTest {
            val fixture = createFixture()
            val contributor1 = createContributor(name = "Original Name")
            val contributor2 = createContributor(name = "Updated Name")
            val viewModel = fixture.build()
            keepStateHot(viewModel)

            viewModel.loadContributor("contributor-1")
            fixture.contributorFlow.value = contributor1
            fixture.rolesFlow.value = emptyList()
            advanceUntilIdle()
            val first = assertIs<ContributorDetailUiState.Ready>(viewModel.state.value)
            assertEquals("Original Name", first.contributor.name)

            fixture.contributorFlow.value = contributor2
            advanceUntilIdle()

            val second = assertIs<ContributorDetailUiState.Ready>(viewModel.state.value)
            assertEquals("Updated Name", second.contributor.name)
        }
}
