package com.calypsan.listenup.client.presentation.contributordetail

import com.calypsan.listenup.client.data.local.db.BookContributorCrossRef
import com.calypsan.listenup.client.data.local.db.BookDao
import com.calypsan.listenup.client.data.local.db.BookEntity
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.db.BookWithContributors
import com.calypsan.listenup.client.data.local.db.ContributorDao
import com.calypsan.listenup.client.data.local.db.ContributorEntity
import com.calypsan.listenup.client.data.local.db.RoleWithBookCount
import com.calypsan.listenup.client.data.local.db.SyncState
import com.calypsan.listenup.client.data.local.db.Timestamp
import com.calypsan.listenup.client.data.local.images.ImageStorage
import com.calypsan.listenup.client.data.repository.ContributorRepositoryContract
import com.calypsan.listenup.client.domain.model.PlaybackPosition
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
        val contributorDao: ContributorDao = mock()
        val bookDao: BookDao = mock()
        val imageStorage: ImageStorage = mock()
        val playbackPositionRepository: PlaybackPositionRepository = mock()
        val contributorRepository: ContributorRepositoryContract = mock()

        val contributorFlow = MutableStateFlow<ContributorEntity?>(null)
        val rolesFlow = MutableStateFlow<List<RoleWithBookCount>>(emptyList())

        fun build(): ContributorDetailViewModel =
            ContributorDetailViewModel(
                contributorDao = contributorDao,
                bookDao = bookDao,
                imageStorage = imageStorage,
                playbackPositionRepository = playbackPositionRepository,
                contributorRepository = contributorRepository,
            )
    }

    private fun createFixture(): TestFixture {
        val fixture = TestFixture()

        // Default stubs
        every { fixture.contributorDao.observeById(any()) } returns fixture.contributorFlow
        every { fixture.contributorDao.observeRolesWithCountForContributor(any()) } returns fixture.rolesFlow
        every { fixture.bookDao.observeByContributorAndRole(any(), any()) } returns flowOf(emptyList())
        every { fixture.imageStorage.exists(any()) } returns false
        everySuspend { fixture.playbackPositionRepository.get(any()) } returns null

        return fixture
    }

    // ========== Test Data Factories ==========

    private fun createContributorEntity(
        id: String = "contributor-1",
        name: String = "Test Author",
        description: String? = "A prolific author",
    ): ContributorEntity =
        ContributorEntity(
            id = id,
            name = name,
            description = description,
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

    private fun createBookWithContributors(
        bookEntity: BookEntity,
        contributors: List<ContributorEntity> = emptyList(),
        roles: List<BookContributorCrossRef> = emptyList(),
    ): BookWithContributors =
        BookWithContributors(
            book = bookEntity,
            contributors = contributors,
            contributorRoles = roles,
            series = emptyList(),
            seriesSequences = emptyList(),
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
            val contributor = createContributorEntity(name = "Stephen King", description = "Master of horror")
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
            val contributor = createContributorEntity()
            val authorRole = RoleWithBookCount(role = "author", bookCount = 2)
            val bookEntity = createBookEntity(id = "book-1", title = "The Shining")
            val bookWithContributors = createBookWithContributors(bookEntity)

            every { fixture.bookDao.observeByContributorAndRole("contributor-1", "author") } returns
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
            val contributor = createContributorEntity()
            val authorRole = RoleWithBookCount(role = "author", bookCount = 3)
            val narratorRole = RoleWithBookCount(role = "narrator", bookCount = 2)

            val book1 = createBookEntity(id = "book-1", title = "Author Book")
            val book2 = createBookEntity(id = "book-2", title = "Narrator Book")

            every { fixture.bookDao.observeByContributorAndRole("contributor-1", "author") } returns
                flowOf(
                    listOf(createBookWithContributors(book1)),
                )
            every { fixture.bookDao.observeByContributorAndRole("contributor-1", "narrator") } returns
                flowOf(
                    listOf(createBookWithContributors(book2)),
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
            val contributor = createContributorEntity()
            val role = RoleWithBookCount(role = "author", bookCount = 1)
            val bookEntity = createBookEntity(id = "book-1", duration = 10_000L)

            every { fixture.bookDao.observeByContributorAndRole("contributor-1", "author") } returns
                flowOf(
                    listOf(createBookWithContributors(bookEntity)),
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
            val contributor = createContributorEntity()
            val role = RoleWithBookCount(role = "author", bookCount = 1)
            val bookEntity = createBookEntity(id = "book-1", duration = 10_000L)

            every { fixture.bookDao.observeByContributorAndRole("contributor-1", "author") } returns
                flowOf(
                    listOf(createBookWithContributors(bookEntity)),
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
            val contributor = createContributorEntity()
            val role = RoleWithBookCount(role = "author", bookCount = 1)
            val bookEntity = createBookEntity(id = "book-1", duration = 10_000L)

            every { fixture.bookDao.observeByContributorAndRole("contributor-1", "author") } returns
                flowOf(
                    listOf(createBookWithContributors(bookEntity)),
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
    fun `loadContributor sets coverPath when image exists`() =
        runTest {
            // Given
            val fixture = createFixture()
            val contributor = createContributorEntity()
            val role = RoleWithBookCount(role = "author", bookCount = 1)
            val bookEntity = createBookEntity(id = "book-1")

            every { fixture.bookDao.observeByContributorAndRole("contributor-1", "author") } returns
                flowOf(
                    listOf(createBookWithContributors(bookEntity)),
                )
            every { fixture.imageStorage.exists(BookId("book-1")) } returns true
            every { fixture.imageStorage.getCoverPath(BookId("book-1")) } returns "/path/to/cover.jpg"
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
    fun `loadContributor sets null coverPath when image does not exist`() =
        runTest {
            // Given
            val fixture = createFixture()
            val contributor = createContributorEntity()
            val role = RoleWithBookCount(role = "author", bookCount = 1)
            val bookEntity = createBookEntity(id = "book-1")

            every { fixture.bookDao.observeByContributorAndRole("contributor-1", "author") } returns
                flowOf(
                    listOf(createBookWithContributors(bookEntity)),
                )
            every { fixture.imageStorage.exists(BookId("book-1")) } returns false
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
            val contributor1 = createContributorEntity(name = "Original Name")
            val contributor2 = createContributorEntity(name = "Updated Name")
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
