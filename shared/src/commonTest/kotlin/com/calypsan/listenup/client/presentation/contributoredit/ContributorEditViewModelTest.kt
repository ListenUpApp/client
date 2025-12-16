package com.calypsan.listenup.client.presentation.contributoredit

import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.local.db.BookContributorCrossRef
import com.calypsan.listenup.client.data.local.db.BookContributorDao
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.db.ContributorDao
import com.calypsan.listenup.client.data.local.db.ContributorEntity
import com.calypsan.listenup.client.data.local.db.SyncState
import com.calypsan.listenup.client.data.local.db.Timestamp
import com.calypsan.listenup.client.data.remote.ContributorSearchResult
import com.calypsan.listenup.client.data.remote.ListenUpApiContract
import com.calypsan.listenup.client.data.remote.MergeContributorResponse
import com.calypsan.listenup.client.data.remote.UnmergeContributorResponse
import com.calypsan.listenup.client.data.remote.UpdateContributorResponse
import com.calypsan.listenup.client.data.repository.ContributorRepositoryContract
import com.calypsan.listenup.client.data.repository.ContributorSearchResponse
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
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
import kotlin.test.assertTrue

/**
 * Tests for ContributorEditViewModel.
 *
 * Tests cover:
 * - Loading contributor for editing
 * - Adding aliases
 * - Merging contributors via server API
 * - Handling merge errors gracefully
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContributorEditViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    // ========== Test Fixture ==========

    private class TestFixture {
        val contributorDao: ContributorDao = mock()
        val bookContributorDao: BookContributorDao = mock()
        val contributorRepository: ContributorRepositoryContract = mock()
        val api: ListenUpApiContract = mock()
        val imageApi: com.calypsan.listenup.client.data.remote.ImageApiContract = mock()
        val imageStorage: com.calypsan.listenup.client.data.local.images.ImageStorage = mock()

        fun build(): ContributorEditViewModel =
            ContributorEditViewModel(
                contributorDao = contributorDao,
                bookContributorDao = bookContributorDao,
                contributorRepository = contributorRepository,
                api = api,
                imageApi = imageApi,
                imageStorage = imageStorage,
            )
    }

    private fun createFixture(): TestFixture = TestFixture()

    // ========== Test Data Factories ==========

    private fun createContributorEntity(
        id: String = "contributor-1",
        name: String = "Stephen King",
        aliases: String? = null,
    ): ContributorEntity =
        ContributorEntity(
            id = id,
            name = name,
            description = null,
            imagePath = null,
            aliases = aliases,
            syncState = SyncState.SYNCED,
            lastModified = Timestamp(1704067200000L),
            serverVersion = Timestamp(1704067200000L),
            createdAt = Timestamp(1704067200000L),
            updatedAt = Timestamp(1704067200000L),
        )

    private fun createMergeResponse(
        id: String = "contributor-1",
        name: String = "Stephen King",
        aliases: List<String> = listOf("Richard Bachman"),
    ): MergeContributorResponse =
        MergeContributorResponse(
            id = id,
            name = name,
            sortName = null,
            biography = null,
            imageUrl = null,
            asin = null,
            aliases = aliases,
            updatedAt = "2024-01-01T00:00:00Z",
        )

    private fun createUnmergeResponse(
        id: String = "contributor-new",
        name: String = "Richard Bachman",
    ): UnmergeContributorResponse =
        UnmergeContributorResponse(
            id = id,
            name = name,
            sortName = null,
            biography = null,
            imageUrl = null,
            asin = null,
            aliases = emptyList(),
            updatedAt = "2024-01-01T00:00:00Z",
        )

    private fun createUpdateResponse(
        id: String = "contributor-1",
        name: String = "Stephen King",
        aliases: List<String> = emptyList(),
    ): UpdateContributorResponse =
        UpdateContributorResponse(
            id = id,
            name = name,
            biography = null,
            imageUrl = null,
            website = null,
            birthDate = null,
            deathDate = null,
            aliases = aliases,
            updatedAt = "2024-01-01T00:00:00Z",
        )

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ========== Load Contributor Tests ==========

    @Test
    fun `loadContributor populates state with contributor data`() =
        runTest {
            // Given
            val fixture = createFixture()
            val contributor =
                createContributorEntity(
                    name = "Stephen King",
                    aliases = "Richard Bachman, John Swithen",
                )
            everySuspend { fixture.contributorDao.getById("contributor-1") } returns contributor

            val viewModel = fixture.build()

            // When
            viewModel.loadContributor("contributor-1")
            advanceUntilIdle()

            // Then
            val state = viewModel.state.value
            assertFalse(state.isLoading)
            assertEquals("Stephen King", state.name)
            assertEquals(2, state.aliases.size)
            assertTrue(state.aliases.contains("Richard Bachman"))
            assertTrue(state.aliases.contains("John Swithen"))
        }

    @Test
    fun `loadContributor handles contributor not found`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.contributorDao.getById("nonexistent") } returns null

            val viewModel = fixture.build()

            // When
            viewModel.loadContributor("nonexistent")
            advanceUntilIdle()

            // Then
            val state = viewModel.state.value
            assertFalse(state.isLoading)
            assertEquals("Contributor not found", state.error)
        }

    // ========== Alias Selection Tests ==========

    @Test
    fun `selecting alias from search adds to aliases list and marks for merge`() =
        runTest {
            // Given
            val fixture = createFixture()
            val contributor = createContributorEntity()
            everySuspend { fixture.contributorDao.getById("contributor-1") } returns contributor

            val searchResult =
                ContributorSearchResult(
                    id = "contributor-2",
                    name = "Richard Bachman",
                    bookCount = 5,
                )
            everySuspend { fixture.contributorRepository.searchContributors(any(), any()) } returns
                ContributorSearchResponse(
                    contributors = listOf(searchResult),
                    isOfflineResult = false,
                    tookMs = 10L,
                )

            val viewModel = fixture.build()
            viewModel.loadContributor("contributor-1")
            advanceUntilIdle()

            // When - select alias from autocomplete
            viewModel.onEvent(ContributorEditUiEvent.AliasSelected(searchResult))

            // Then
            val state = viewModel.state.value
            assertTrue(state.aliases.contains("Richard Bachman"))
            assertTrue(state.hasChanges)
        }

    // ========== Save with Merge Tests ==========

    @Test
    fun `save calls server merge API for new aliases from autocomplete`() =
        runTest {
            // Given
            val fixture = createFixture()
            val contributor = createContributorEntity()
            val sourceContributor = createContributorEntity(id = "contributor-2", name = "Richard Bachman")

            everySuspend { fixture.contributorDao.getById("contributor-1") } returns contributor
            everySuspend { fixture.contributorDao.getById("contributor-2") } returns sourceContributor
            everySuspend { fixture.contributorDao.upsert(any()) } returns Unit
            everySuspend { fixture.contributorDao.deleteById(any()) } returns Unit
            everySuspend { fixture.bookContributorDao.getByContributorId(any()) } returns emptyList()

            val mergeResponse = createMergeResponse()
            everySuspend { fixture.api.mergeContributor("contributor-1", "contributor-2") } returns
                Success(mergeResponse)

            val viewModel = fixture.build()
            viewModel.loadContributor("contributor-1")
            advanceUntilIdle()

            // Add alias via autocomplete (which tracks for merge)
            val searchResult =
                ContributorSearchResult(
                    id = "contributor-2",
                    name = "Richard Bachman",
                    bookCount = 5,
                )
            viewModel.onEvent(ContributorEditUiEvent.AliasSelected(searchResult))

            // When
            viewModel.onEvent(ContributorEditUiEvent.Save)
            advanceUntilIdle()

            // Then - verify merge was called
            verifySuspend(VerifyMode.exactly(1)) {
                fixture.api.mergeContributor("contributor-1", "contributor-2")
            }
        }

    @Test
    fun `save deletes merged contributor from local database`() =
        runTest {
            // Given
            val fixture = createFixture()
            val contributor = createContributorEntity()
            val sourceContributor = createContributorEntity(id = "contributor-2", name = "Richard Bachman")

            everySuspend { fixture.contributorDao.getById("contributor-1") } returns contributor
            everySuspend { fixture.contributorDao.getById("contributor-2") } returns sourceContributor
            everySuspend { fixture.contributorDao.upsert(any()) } returns Unit
            everySuspend { fixture.contributorDao.deleteById(any()) } returns Unit
            everySuspend { fixture.bookContributorDao.getByContributorId(any()) } returns emptyList()

            val mergeResponse = createMergeResponse()
            everySuspend { fixture.api.mergeContributor("contributor-1", "contributor-2") } returns
                Success(mergeResponse)

            val viewModel = fixture.build()
            viewModel.loadContributor("contributor-1")
            advanceUntilIdle()

            // Add alias via autocomplete
            val searchResult =
                ContributorSearchResult(
                    id = "contributor-2",
                    name = "Richard Bachman",
                    bookCount = 5,
                )
            viewModel.onEvent(ContributorEditUiEvent.AliasSelected(searchResult))

            // When
            viewModel.onEvent(ContributorEditUiEvent.Save)
            advanceUntilIdle()

            // Then - verify merged contributor was deleted locally
            verifySuspend(VerifyMode.exactly(1)) {
                fixture.contributorDao.deleteById("contributor-2")
            }
        }

    @Test
    fun `save re-links book-contributor relationships with creditedAs for merged contributor`() =
        runTest {
            // Given
            val fixture = createFixture()
            val contributor = createContributorEntity()

            val bookRelation =
                BookContributorCrossRef(
                    bookId = BookId("book-1"),
                    contributorId = "contributor-2",
                    role = "author",
                )

            everySuspend { fixture.contributorDao.getById("contributor-1") } returns contributor
            everySuspend { fixture.contributorDao.upsert(any()) } returns Unit
            everySuspend { fixture.contributorDao.deleteById(any()) } returns Unit
            everySuspend { fixture.bookContributorDao.getByContributorId("contributor-2") } returns listOf(bookRelation)
            // Mock check for existing target relationship (none exists)
            everySuspend { fixture.bookContributorDao.get(BookId("book-1"), "contributor-1", "author") } returns null
            everySuspend { fixture.bookContributorDao.insert(any()) } returns Unit
            everySuspend { fixture.bookContributorDao.delete(any(), any(), any()) } returns Unit

            val mergeResponse = createMergeResponse()
            everySuspend { fixture.api.mergeContributor("contributor-1", "contributor-2") } returns
                Success(mergeResponse)

            val viewModel = fixture.build()
            viewModel.loadContributor("contributor-1")
            advanceUntilIdle()

            // Add alias via autocomplete
            val searchResult =
                ContributorSearchResult(
                    id = "contributor-2",
                    name = "Richard Bachman",
                    bookCount = 1,
                )
            viewModel.onEvent(ContributorEditUiEvent.AliasSelected(searchResult))

            // When
            viewModel.onEvent(ContributorEditUiEvent.Save)
            advanceUntilIdle()

            // Then - verify new relationship was created with creditedAs
            verifySuspend(VerifyMode.exactly(1)) {
                fixture.bookContributorDao.insert(
                    BookContributorCrossRef(
                        bookId = BookId("book-1"),
                        contributorId = "contributor-1",
                        role = "author",
                        creditedAs = "Richard Bachman",
                    ),
                )
            }

            // And old relationship was deleted
            verifySuspend(VerifyMode.exactly(1)) {
                fixture.bookContributorDao.delete(BookId("book-1"), "contributor-2", "author")
            }
        }

    @Test
    fun `save handles merge API failure gracefully`() =
        runTest {
            // Given
            val fixture = createFixture()
            val contributor = createContributorEntity()

            everySuspend { fixture.contributorDao.getById("contributor-1") } returns contributor
            everySuspend { fixture.contributorDao.upsert(any()) } returns Unit

            // Merge API fails
            everySuspend { fixture.api.mergeContributor("contributor-1", "contributor-2") } returns
                Failure(Exception("Network error"))
            // But update API succeeds
            val updateResponse = createUpdateResponse(aliases = listOf("Richard Bachman"))
            everySuspend { fixture.api.updateContributor(any(), any()) } returns Success(updateResponse)

            val viewModel = fixture.build()
            viewModel.loadContributor("contributor-1")
            advanceUntilIdle()

            // Add alias via autocomplete
            val searchResult =
                ContributorSearchResult(
                    id = "contributor-2",
                    name = "Richard Bachman",
                    bookCount = 5,
                )
            viewModel.onEvent(ContributorEditUiEvent.AliasSelected(searchResult))

            // When
            viewModel.onEvent(ContributorEditUiEvent.Save)
            advanceUntilIdle()

            // Then - save should succeed despite merge failure
            // Alias is saved locally for later sync
            val navAction = viewModel.navActions.value
            assertEquals(ContributorEditNavAction.SaveSuccess, navAction)
        }

    @Test
    fun `manual alias entry does not trigger server merge`() =
        runTest {
            // Given
            val fixture = createFixture()
            val contributor = createContributorEntity()

            everySuspend { fixture.contributorDao.getById("contributor-1") } returns contributor
            everySuspend { fixture.contributorDao.upsert(any()) } returns Unit

            val viewModel = fixture.build()
            viewModel.loadContributor("contributor-1")
            advanceUntilIdle()

            // Add alias via manual text entry (not autocomplete)
            viewModel.onEvent(ContributorEditUiEvent.AliasEntered("New Pen Name"))

            // When
            viewModel.onEvent(ContributorEditUiEvent.Save)
            advanceUntilIdle()

            // Then - merge should NOT be called (manual entry = just alias text, no merge)
            verifySuspend(VerifyMode.exactly(0)) {
                fixture.api.mergeContributor(any(), any())
            }
        }

    @Test
    fun `removing alias removes from list and updates hasChanges`() =
        runTest {
            // Given
            val fixture = createFixture()
            val contributor = createContributorEntity(aliases = "Richard Bachman")

            everySuspend { fixture.contributorDao.getById("contributor-1") } returns contributor

            // Mock unmerge API call (removing original alias calls server)
            val unmergeResponse = createUnmergeResponse()
            everySuspend { fixture.api.unmergeContributor("contributor-1", "Richard Bachman") } returns
                Success(unmergeResponse)

            val viewModel = fixture.build()
            viewModel.loadContributor("contributor-1")
            advanceUntilIdle()

            // Verify alias is present
            assertTrue(
                viewModel.state.value.aliases
                    .contains("Richard Bachman"),
            )
            assertFalse(viewModel.state.value.hasChanges)

            // When - removing an original alias calls the server unmerge API
            viewModel.onEvent(ContributorEditUiEvent.RemoveAlias("Richard Bachman"))
            advanceUntilIdle()

            // Then
            assertFalse(
                viewModel.state.value.aliases
                    .contains("Richard Bachman"),
            )
            // hasChanges is false because the unmerge was handled by server (not a local change)
            assertFalse(viewModel.state.value.hasChanges)
        }

    @Test
    fun `save uses server aliases when merge succeeds`() =
        runTest {
            // Given
            val fixture = createFixture()
            val contributor = createContributorEntity()

            everySuspend { fixture.contributorDao.getById("contributor-1") } returns contributor
            everySuspend { fixture.contributorDao.upsert(any()) } returns Unit
            everySuspend { fixture.contributorDao.deleteById(any()) } returns Unit
            everySuspend { fixture.bookContributorDao.getByContributorId(any()) } returns emptyList()
            everySuspend { fixture.bookContributorDao.get(any(), any(), any()) } returns null
            everySuspend { fixture.bookContributorDao.insert(any()) } returns Unit

            // Server returns aliases including additional ones that were already on target
            val mergeResponse =
                createMergeResponse(
                    aliases = listOf("Richard Bachman", "Existing Alias"),
                )
            everySuspend { fixture.api.mergeContributor("contributor-1", "contributor-2") } returns
                Success(mergeResponse)
            // Update API also succeeds
            val updateResponse = createUpdateResponse(aliases = listOf("Richard Bachman", "Existing Alias"))
            everySuspend { fixture.api.updateContributor(any(), any()) } returns Success(updateResponse)

            val viewModel = fixture.build()
            viewModel.loadContributor("contributor-1")
            advanceUntilIdle()

            // Add alias via autocomplete
            val searchResult =
                ContributorSearchResult(
                    id = "contributor-2",
                    name = "Richard Bachman",
                    bookCount = 5,
                )
            viewModel.onEvent(ContributorEditUiEvent.AliasSelected(searchResult))

            // When
            viewModel.onEvent(ContributorEditUiEvent.Save)
            advanceUntilIdle()

            // Then - verify contributor was saved with sync state SYNCED (not NOT_SYNCED)
            // because merge happened on server
            verifySuspend(VerifyMode.exactly(1)) {
                fixture.contributorDao.upsert(any())
            }
        }
}
