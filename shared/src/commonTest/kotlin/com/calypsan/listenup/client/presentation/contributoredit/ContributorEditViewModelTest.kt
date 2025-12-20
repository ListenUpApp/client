package com.calypsan.listenup.client.presentation.contributoredit

import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.local.db.ContributorDao
import com.calypsan.listenup.client.data.local.db.ContributorEntity
import com.calypsan.listenup.client.data.local.db.SyncState
import com.calypsan.listenup.client.data.local.db.Timestamp
import com.calypsan.listenup.client.data.remote.ContributorSearchResult
import com.calypsan.listenup.client.data.repository.ContributorEditRepositoryContract
import com.calypsan.listenup.client.data.repository.ContributorRepositoryContract
import com.calypsan.listenup.client.data.repository.ContributorSearchResponse
import com.calypsan.listenup.client.data.repository.ContributorUpdateRequest
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
 * - Merging contributors via offline-first repository
 * - Handling merge errors gracefully
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContributorEditViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    // ========== Test Fixture ==========

    private class TestFixture {
        val contributorDao: ContributorDao = mock()
        val contributorRepository: ContributorRepositoryContract = mock()
        val contributorEditRepository: ContributorEditRepositoryContract = mock()
        val imageApi: com.calypsan.listenup.client.data.remote.ImageApiContract = mock()
        val imageStorage: com.calypsan.listenup.client.data.local.images.ImageStorage = mock()

        fun build(): ContributorEditViewModel =
            ContributorEditViewModel(
                contributorDao = contributorDao,
                contributorRepository = contributorRepository,
                contributorEditRepository = contributorEditRepository,
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
    fun `save calls repository merge for new aliases from autocomplete`() =
        runTest {
            // Given
            val fixture = createFixture()
            val contributor = createContributorEntity()

            everySuspend { fixture.contributorDao.getById("contributor-1") } returns contributor
            everySuspend { fixture.contributorEditRepository.mergeContributor(any(), any()) } returns
                Success(Unit)
            everySuspend { fixture.contributorEditRepository.updateContributor(any(), any()) } returns
                Success(Unit)

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

            // Then - verify merge was called via repository
            verifySuspend(VerifyMode.exactly(1)) {
                fixture.contributorEditRepository.mergeContributor("contributor-1", "contributor-2")
            }
        }

    @Test
    fun `save handles merge failure gracefully`() =
        runTest {
            // Given
            val fixture = createFixture()
            val contributor = createContributorEntity()

            everySuspend { fixture.contributorDao.getById("contributor-1") } returns contributor
            // Merge fails
            everySuspend { fixture.contributorEditRepository.mergeContributor(any(), any()) } returns
                Failure(Exception("Network error"))
            // Update succeeds
            everySuspend { fixture.contributorEditRepository.updateContributor(any(), any()) } returns
                Success(Unit)

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
    fun `manual alias entry does not trigger repository merge`() =
        runTest {
            // Given
            val fixture = createFixture()
            val contributor = createContributorEntity()

            everySuspend { fixture.contributorDao.getById("contributor-1") } returns contributor
            everySuspend { fixture.contributorEditRepository.updateContributor(any(), any()) } returns
                Success(Unit)

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
                fixture.contributorEditRepository.mergeContributor(any(), any())
            }
        }

    @Test
    fun `removing alias calls repository unmerge`() =
        runTest {
            // Given
            val fixture = createFixture()
            val contributor = createContributorEntity(aliases = "Richard Bachman")

            everySuspend { fixture.contributorDao.getById("contributor-1") } returns contributor
            everySuspend { fixture.contributorEditRepository.unmergeContributor(any(), any()) } returns
                Success(Unit)

            val viewModel = fixture.build()
            viewModel.loadContributor("contributor-1")
            advanceUntilIdle()

            // Verify alias is present
            assertTrue(
                viewModel.state.value.aliases
                    .contains("Richard Bachman"),
            )
            assertFalse(viewModel.state.value.hasChanges)

            // When - removing an original alias calls the repository unmerge
            viewModel.onEvent(ContributorEditUiEvent.RemoveAlias("Richard Bachman"))
            advanceUntilIdle()

            // Then
            assertFalse(
                viewModel.state.value.aliases
                    .contains("Richard Bachman"),
            )
            // Verify unmerge was called
            verifySuspend(VerifyMode.exactly(1)) {
                fixture.contributorEditRepository.unmergeContributor("contributor-1", "Richard Bachman")
            }
        }

    @Test
    fun `save calls repository update with correct parameters`() =
        runTest {
            // Given
            val fixture = createFixture()
            val contributor = createContributorEntity()

            everySuspend { fixture.contributorDao.getById("contributor-1") } returns contributor
            everySuspend { fixture.contributorEditRepository.updateContributor(any(), any()) } returns
                Success(Unit)

            val viewModel = fixture.build()
            viewModel.loadContributor("contributor-1")
            advanceUntilIdle()

            // Change name
            viewModel.onEvent(ContributorEditUiEvent.NameChanged("Stephen Edwin King"))

            // When
            viewModel.onEvent(ContributorEditUiEvent.Save)
            advanceUntilIdle()

            // Then - verify repository update was called
            verifySuspend(VerifyMode.exactly(1)) {
                fixture.contributorEditRepository.updateContributor(
                    "contributor-1",
                    ContributorUpdateRequest(
                        name = "Stephen Edwin King",
                        biography = null,
                        website = null,
                        birthDate = null,
                        deathDate = null,
                        aliases = emptyList(),
                        imagePath = null,
                    ),
                )
            }

            // Verify navigation
            assertEquals(ContributorEditNavAction.SaveSuccess, viewModel.navActions.value)
        }
}
