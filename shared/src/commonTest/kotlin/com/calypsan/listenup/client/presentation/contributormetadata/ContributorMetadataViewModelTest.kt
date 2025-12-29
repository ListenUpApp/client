package com.calypsan.listenup.client.presentation.contributormetadata

import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.local.db.ContributorDao
import com.calypsan.listenup.client.data.local.db.ContributorEntity
import com.calypsan.listenup.client.data.local.db.SyncState
import com.calypsan.listenup.client.data.local.db.Timestamp
import com.calypsan.listenup.client.data.local.images.ImageStorage
import com.calypsan.listenup.client.data.remote.ApplyContributorMetadataResult
import com.calypsan.listenup.client.data.remote.ImageApiContract
import com.calypsan.listenup.client.data.remote.MetadataApiContract
import com.calypsan.listenup.client.data.remote.model.ContributorMetadataProfile
import com.calypsan.listenup.client.data.remote.model.ContributorMetadataSearchResult
import com.calypsan.listenup.client.data.remote.model.ContributorResponse
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
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
 * Tests for ContributorMetadataViewModel.
 *
 * Tests cover:
 * - Initialization and synchronous state reset (prevents stale state bugs)
 * - Search functionality
 * - Candidate selection and profile loading
 * - Metadata application with image download
 * - Error handling
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContributorMetadataViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    // ========== Test Fixture ==========

    private class TestFixture {
        val contributorDao: ContributorDao = mock()
        val metadataApi: MetadataApiContract = mock()
        val imageApi: ImageApiContract = mock()
        val imageStorage: ImageStorage = mock()

        fun build(): ContributorMetadataViewModel =
            ContributorMetadataViewModel(
                contributorDao = contributorDao,
                metadataApi = metadataApi,
                imageApi = imageApi,
                imageStorage = imageStorage,
            )
    }

    private fun createFixture(): TestFixture = TestFixture()

    // ========== Test Data Factories ==========

    private fun createContributorEntity(
        id: String = "contributor-1",
        name: String = "Stephen King",
    ): ContributorEntity =
        ContributorEntity(
            id = id,
            name = name,
            description = null,
            imagePath = null,
            aliases = null,
            syncState = SyncState.SYNCED,
            lastModified = Timestamp(1704067200000L),
            serverVersion = Timestamp(1704067200000L),
            createdAt = Timestamp(1704067200000L),
            updatedAt = Timestamp(1704067200000L),
        )

    private fun createSearchResult(
        asin: String = "B001ASIN01",
        name: String = "Stephen King",
        imageUrl: String? = "https://example.com/image.jpg",
    ): ContributorMetadataSearchResult =
        ContributorMetadataSearchResult(
            asin = asin,
            name = name,
            imageUrl = imageUrl,
            description = "142 titles",
        )

    private fun createProfile(
        asin: String = "B001ASIN01",
        name: String = "Stephen King",
        biography: String? = "Stephen King is a prolific author...",
        imageUrl: String? = "https://example.com/image.jpg",
    ): ContributorMetadataProfile =
        ContributorMetadataProfile(
            asin = asin,
            name = name,
            biography = biography,
            imageUrl = imageUrl,
        )

    private fun createContributorResponse(
        id: String = "contributor-1",
        name: String = "Stephen King",
        imageUrl: String? = "/api/v1/contributors/contributor-1/image",
    ): ContributorResponse =
        ContributorResponse(
            id = id,
            name = name,
            biography = "Stephen King is a prolific author...",
            imageUrl = imageUrl,
            imageBlurHash = null,
            aliases = null,
            website = null,
            birthDate = null,
            deathDate = null,
            createdAt = "2024-01-01T00:00:00Z",
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

    // ========== Initialization Tests ==========

    @Test
    fun `init synchronously resets state to prevent stale state bugs`() =
        runTest {
            // Given - ViewModel with stale state from previous contributor
            val fixture = createFixture()
            val viewModel = fixture.build()

            // Simulate stale state (e.g., applySuccess from previous contributor)
            // We can't set this directly, but we can verify the fix works by checking
            // that init() synchronously sets contributorId before any async work

            val contributor = createContributorEntity(id = "contributor-2", name = "Neil Gaiman")
            everySuspend { fixture.contributorDao.observeById("contributor-2") } returns flowOf(contributor)
            everySuspend { fixture.metadataApi.searchContributors(any(), any()) } returns emptyList()

            // When
            viewModel.init("contributor-2")

            // Then - synchronous state reset happens immediately (before advanceUntilIdle)
            val stateBeforeAsync = viewModel.state.value
            assertEquals("contributor-2", stateBeforeAsync.contributorId)
            assertFalse(stateBeforeAsync.applySuccess) // Reset from default
            assertNull(stateBeforeAsync.currentContributor) // Not loaded yet
            assertEquals("", stateBeforeAsync.searchQuery) // Reset
            assertTrue(stateBeforeAsync.searchResults.isEmpty()) // Reset
        }

    @Test
    fun `init loads contributor and pre-fills search query`() =
        runTest {
            // Given
            val fixture = createFixture()
            val contributor = createContributorEntity(name = "Stephen King")
            everySuspend { fixture.contributorDao.observeById("contributor-1") } returns flowOf(contributor)
            everySuspend { fixture.metadataApi.searchContributors("Stephen King", "us") } returns
                listOf(createSearchResult())

            val viewModel = fixture.build()

            // When
            viewModel.init("contributor-1")
            advanceUntilIdle()

            // Then
            val state = viewModel.state.value
            assertEquals("contributor-1", state.contributorId)
            assertEquals(contributor, state.currentContributor)
            assertEquals("Stephen King", state.searchQuery)
        }

    @Test
    fun `init auto-searches with contributor name`() =
        runTest {
            // Given
            val fixture = createFixture()
            val contributor = createContributorEntity(name = "Stephen King")
            val searchResults = listOf(createSearchResult())

            everySuspend { fixture.contributorDao.observeById("contributor-1") } returns flowOf(contributor)
            everySuspend { fixture.metadataApi.searchContributors("Stephen King", "us") } returns searchResults

            val viewModel = fixture.build()

            // When
            viewModel.init("contributor-1")
            advanceUntilIdle()

            // Then
            val state = viewModel.state.value
            assertEquals(searchResults, state.searchResults)
            verifySuspend(VerifyMode.exactly(1)) {
                fixture.metadataApi.searchContributors("Stephen King", "us")
            }
        }

    @Test
    fun `init does not auto-search when contributor name is blank`() =
        runTest {
            // Given
            val fixture = createFixture()
            val contributor = createContributorEntity(name = "")
            everySuspend { fixture.contributorDao.observeById("contributor-1") } returns flowOf(contributor)

            val viewModel = fixture.build()

            // When
            viewModel.init("contributor-1")
            advanceUntilIdle()

            // Then - search should not be called
            verifySuspend(VerifyMode.exactly(0)) {
                fixture.metadataApi.searchContributors(any(), any())
            }
        }

    // ========== Search Tests ==========

    @Test
    fun `search updates state with results`() =
        runTest {
            // Given
            val fixture = createFixture()
            val contributor = createContributorEntity()
            val searchResults =
                listOf(
                    createSearchResult(asin = "B001", name = "Stephen King"),
                    createSearchResult(asin = "B002", name = "Stephen King Jr"),
                )

            everySuspend { fixture.contributorDao.observeById("contributor-1") } returns flowOf(contributor)
            everySuspend { fixture.metadataApi.searchContributors(any(), any()) } returns searchResults

            val viewModel = fixture.build()
            viewModel.init("contributor-1")
            advanceUntilIdle()

            // When - manual search with different query
            viewModel.updateQuery("Stephen")
            viewModel.search()
            advanceUntilIdle()

            // Then
            val state = viewModel.state.value
            assertEquals(2, state.searchResults.size)
            assertFalse(state.isSearching)
            assertNull(state.searchError)
        }

    @Test
    fun `search handles error gracefully`() =
        runTest {
            // Given
            val fixture = createFixture()
            val contributor = createContributorEntity()

            everySuspend { fixture.contributorDao.observeById("contributor-1") } returns flowOf(contributor)
            everySuspend { fixture.metadataApi.searchContributors(any(), any()) } throws
                RuntimeException("Network error")

            val viewModel = fixture.build()
            viewModel.init("contributor-1")
            advanceUntilIdle()

            // Then
            val state = viewModel.state.value
            assertFalse(state.isSearching)
            assertEquals("Network error", state.searchError)
        }

    @Test
    fun `search with blank query does nothing`() =
        runTest {
            // Given
            val fixture = createFixture()
            val contributor = createContributorEntity(name = "")
            everySuspend { fixture.contributorDao.observeById("contributor-1") } returns flowOf(contributor)

            val viewModel = fixture.build()
            viewModel.init("contributor-1")
            advanceUntilIdle()

            viewModel.updateQuery("   ") // Blank query

            // When
            viewModel.search()
            advanceUntilIdle()

            // Then - search should not be called (only the auto-search check, which is skipped for blank name)
            verifySuspend(VerifyMode.exactly(0)) {
                fixture.metadataApi.searchContributors(any(), any())
            }
        }

    // ========== Candidate Selection Tests ==========

    @Test
    fun `selectCandidate loads profile`() =
        runTest {
            // Given
            val fixture = createFixture()
            val contributor = createContributorEntity()
            val searchResult = createSearchResult()
            val profile = createProfile()

            everySuspend { fixture.contributorDao.observeById("contributor-1") } returns flowOf(contributor)
            everySuspend { fixture.metadataApi.searchContributors(any(), any()) } returns listOf(searchResult)
            everySuspend { fixture.metadataApi.getContributorProfile("B001ASIN01") } returns profile

            val viewModel = fixture.build()
            viewModel.init("contributor-1")
            advanceUntilIdle()

            // When
            viewModel.selectCandidate(searchResult)
            advanceUntilIdle()

            // Then
            val state = viewModel.state.value
            assertEquals(searchResult, state.selectedCandidate)
            assertEquals(profile, state.previewProfile)
            assertFalse(state.isLoadingPreview)
            assertNull(state.previewError)
        }

    @Test
    fun `selectCandidate initializes field selections based on profile`() =
        runTest {
            // Given
            val fixture = createFixture()
            val contributor = createContributorEntity()
            val searchResult = createSearchResult()
            // Profile with name and biography but no image
            val profile = createProfile(imageUrl = null)

            everySuspend { fixture.contributorDao.observeById("contributor-1") } returns flowOf(contributor)
            everySuspend { fixture.metadataApi.searchContributors(any(), any()) } returns listOf(searchResult)
            everySuspend { fixture.metadataApi.getContributorProfile(any()) } returns profile

            val viewModel = fixture.build()
            viewModel.init("contributor-1")
            advanceUntilIdle()

            // When
            viewModel.selectCandidate(searchResult)
            advanceUntilIdle()

            // Then - image selection should be false since no image available
            val selections = viewModel.state.value.selections
            assertTrue(selections.name)
            assertTrue(selections.biography)
            assertFalse(selections.image)
        }

    // ========== Apply Tests ==========

    @Test
    fun `apply successfully updates contributor and downloads image`() =
        runTest {
            // Given
            val fixture = createFixture()
            val contributor = createContributorEntity()
            val searchResult = createSearchResult()
            val profile = createProfile()
            val response = createContributorResponse()
            val imageBytes = byteArrayOf(1, 2, 3, 4)

            everySuspend { fixture.contributorDao.observeById("contributor-1") } returns flowOf(contributor)
            everySuspend { fixture.metadataApi.searchContributors(any(), any()) } returns listOf(searchResult)
            everySuspend { fixture.metadataApi.getContributorProfile(any()) } returns profile
            everySuspend { fixture.metadataApi.applyContributorMetadata(any(), any(), any(), any(), any(), any()) } returns
                ApplyContributorMetadataResult.Success(response)
            everySuspend { fixture.imageApi.downloadContributorImage("contributor-1") } returns Success(imageBytes)
            everySuspend { fixture.imageStorage.saveContributorImage("contributor-1", imageBytes) } returns Success(Unit)
            every { fixture.imageStorage.getContributorImagePath("contributor-1") } returns "/local/path/contributor-1.jpg"
            everySuspend { fixture.contributorDao.upsert(any()) } returns Unit

            val viewModel = fixture.build()
            viewModel.init("contributor-1")
            advanceUntilIdle()
            viewModel.selectCandidate(searchResult)
            advanceUntilIdle()

            // When
            viewModel.apply()
            advanceUntilIdle()

            // Then
            val state = viewModel.state.value
            assertTrue(state.applySuccess)
            assertFalse(state.isApplying)
            assertNull(state.applyError)

            // Verify image was downloaded and saved
            verifySuspend(VerifyMode.exactly(1)) {
                fixture.imageApi.downloadContributorImage("contributor-1")
            }
            verifySuspend(VerifyMode.exactly(1)) {
                fixture.imageStorage.saveContributorImage("contributor-1", imageBytes)
            }
        }

    @Test
    fun `apply handles image download failure gracefully`() =
        runTest {
            // Given
            val fixture = createFixture()
            val contributor = createContributorEntity()
            val searchResult = createSearchResult()
            val profile = createProfile()
            val response = createContributorResponse()

            everySuspend { fixture.contributorDao.observeById("contributor-1") } returns flowOf(contributor)
            everySuspend { fixture.metadataApi.searchContributors(any(), any()) } returns listOf(searchResult)
            everySuspend { fixture.metadataApi.getContributorProfile(any()) } returns profile
            everySuspend { fixture.metadataApi.applyContributorMetadata(any(), any(), any(), any(), any(), any()) } returns
                ApplyContributorMetadataResult.Success(response)
            // Image download fails
            everySuspend { fixture.imageApi.downloadContributorImage("contributor-1") } returns
                Failure(RuntimeException("Network error"))
            everySuspend { fixture.contributorDao.upsert(any()) } returns Unit

            val viewModel = fixture.build()
            viewModel.init("contributor-1")
            advanceUntilIdle()
            viewModel.selectCandidate(searchResult)
            advanceUntilIdle()

            // When
            viewModel.apply()
            advanceUntilIdle()

            // Then - apply should still succeed, just without image
            val state = viewModel.state.value
            assertTrue(state.applySuccess)
            assertNull(state.applyError)
        }

    @Test
    fun `apply handles metadata API error`() =
        runTest {
            // Given
            val fixture = createFixture()
            val contributor = createContributorEntity()
            val searchResult = createSearchResult()
            val profile = createProfile()

            everySuspend { fixture.contributorDao.observeById("contributor-1") } returns flowOf(contributor)
            everySuspend { fixture.metadataApi.searchContributors(any(), any()) } returns listOf(searchResult)
            everySuspend { fixture.metadataApi.getContributorProfile(any()) } returns profile
            everySuspend { fixture.metadataApi.applyContributorMetadata(any(), any(), any(), any(), any(), any()) } returns
                ApplyContributorMetadataResult.Error("Server error")

            val viewModel = fixture.build()
            viewModel.init("contributor-1")
            advanceUntilIdle()
            viewModel.selectCandidate(searchResult)
            advanceUntilIdle()

            // When
            viewModel.apply()
            advanceUntilIdle()

            // Then
            val state = viewModel.state.value
            assertFalse(state.applySuccess)
            assertFalse(state.isApplying)
            assertEquals("Server error", state.applyError)
        }

    @Test
    fun `apply does nothing when no fields selected`() =
        runTest {
            // Given
            val fixture = createFixture()
            val contributor = createContributorEntity()
            val searchResult = createSearchResult()
            // Profile with nothing available
            val profile =
                ContributorMetadataProfile(
                    asin = "B001ASIN01",
                    name = "",
                    biography = null,
                    imageUrl = null,
                )

            everySuspend { fixture.contributorDao.observeById("contributor-1") } returns flowOf(contributor)
            everySuspend { fixture.metadataApi.searchContributors(any(), any()) } returns listOf(searchResult)
            everySuspend { fixture.metadataApi.getContributorProfile(any()) } returns profile

            val viewModel = fixture.build()
            viewModel.init("contributor-1")
            advanceUntilIdle()
            viewModel.selectCandidate(searchResult)
            advanceUntilIdle()

            // When
            viewModel.apply()
            advanceUntilIdle()

            // Then - apply should not be called since no fields selected
            verifySuspend(VerifyMode.exactly(0)) {
                fixture.metadataApi.applyContributorMetadata(any(), any(), any(), any(), any(), any())
            }
        }

    // ========== Field Toggle Tests ==========

    @Test
    fun `toggleField updates selections`() =
        runTest {
            // Given
            val fixture = createFixture()
            val contributor = createContributorEntity()
            everySuspend { fixture.contributorDao.observeById("contributor-1") } returns flowOf(contributor)
            everySuspend { fixture.metadataApi.searchContributors(any(), any()) } returns emptyList()

            val viewModel = fixture.build()
            viewModel.init("contributor-1")
            advanceUntilIdle()

            // Initial state - all true by default
            assertTrue(viewModel.state.value.selections.name)

            // When
            viewModel.toggleField(ContributorMetadataField.NAME)

            // Then
            assertFalse(viewModel.state.value.selections.name)

            // Toggle back
            viewModel.toggleField(ContributorMetadataField.NAME)
            assertTrue(viewModel.state.value.selections.name)
        }

    // ========== Region Change Tests ==========

    @Test
    fun `changeRegion updates state and re-searches`() =
        runTest {
            // Given
            val fixture = createFixture()
            val contributor = createContributorEntity()
            val usResults = listOf(createSearchResult(name = "Stephen King US"))
            val ukResults = listOf(createSearchResult(name = "Stephen King UK"))

            everySuspend { fixture.contributorDao.observeById("contributor-1") } returns flowOf(contributor)
            everySuspend { fixture.metadataApi.searchContributors("Stephen King", "us") } returns usResults
            everySuspend { fixture.metadataApi.searchContributors("Stephen King", "uk") } returns ukResults

            val viewModel = fixture.build()
            viewModel.init("contributor-1")
            advanceUntilIdle()

            // Verify initial region and results
            assertEquals(com.calypsan.listenup.client.presentation.metadata.AudibleRegion.US, viewModel.state.value.selectedRegion)
            assertEquals(usResults, viewModel.state.value.searchResults)

            // When
            viewModel.changeRegion(com.calypsan.listenup.client.presentation.metadata.AudibleRegion.UK)
            advanceUntilIdle()

            // Then
            assertEquals(com.calypsan.listenup.client.presentation.metadata.AudibleRegion.UK, viewModel.state.value.selectedRegion)
            assertEquals(ukResults, viewModel.state.value.searchResults)
        }
}
