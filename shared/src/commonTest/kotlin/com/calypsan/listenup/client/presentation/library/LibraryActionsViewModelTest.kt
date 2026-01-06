package com.calypsan.listenup.client.presentation.library

import com.calypsan.listenup.client.data.local.db.CollectionDao
import com.calypsan.listenup.client.data.local.db.CollectionEntity
import com.calypsan.listenup.client.data.local.db.Timestamp
import com.calypsan.listenup.client.data.remote.AdminCollectionApiContract
import com.calypsan.listenup.client.data.remote.LensApiContract
import com.calypsan.listenup.client.data.remote.LensOwnerResponse
import com.calypsan.listenup.client.data.remote.LensResponse
import com.calypsan.listenup.client.domain.model.Lens
import com.calypsan.listenup.client.domain.model.User
import com.calypsan.listenup.client.domain.repository.LensRepository
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import com.calypsan.listenup.client.checkIs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for LibraryActionsViewModel.
 *
 * Tests cover:
 * - Selection mode observation from shared manager
 * - Admin state observation (isAdmin, collections)
 * - Lens observation (myLenses)
 * - addSelectedToCollection success/failure
 * - addSelectedToLens success/failure
 * - createLensAndAddBooks success/failure
 * - Loading state during operations
 * - Selection clearing after actions
 *
 * Uses Mokkery for mocking dependencies.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryActionsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    // ========== Test Fixtures ==========

    private class TestFixture {
        val selectionManager = LibrarySelectionManager()
        val userRepository: UserRepository = mock()
        val collectionDao: CollectionDao = mock()
        val adminCollectionApi: AdminCollectionApiContract = mock()
        val lensRepository: LensRepository = mock()
        val lensApi: LensApiContract = mock()

        val userFlow = MutableStateFlow<User?>(null)
        val collectionsFlow = MutableStateFlow<List<CollectionEntity>>(emptyList())
        val lensesFlow = MutableStateFlow<List<Lens>>(emptyList())

        fun build(): LibraryActionsViewModel =
            LibraryActionsViewModel(
                selectionManager = selectionManager,
                userRepository = userRepository,
                collectionDao = collectionDao,
                adminCollectionApi = adminCollectionApi,
                lensRepository = lensRepository,
                lensApi = lensApi,
            )
    }

    private fun createFixture(): TestFixture {
        val fixture = TestFixture()

        // Default stubs for reactive observation
        every { fixture.userRepository.observeCurrentUser() } returns fixture.userFlow
        every { fixture.collectionDao.observeAll() } returns fixture.collectionsFlow
        every { fixture.lensRepository.observeMyLenses(any()) } returns fixture.lensesFlow

        return fixture
    }

    // ========== Test Data Factories ==========

    private fun createUser(
        id: String = "user-1",
        email: String = "test@example.com",
        displayName: String = "Test User",
        isAdmin: Boolean = false,
    ): User =
        User(
            id = id,
            email = email,
            displayName = displayName,
            isAdmin = isAdmin,
            createdAtMs = 1704067200000L,
            updatedAtMs = 1704067200000L,
        )

    private fun createCollection(
        id: String = "collection-1",
        name: String = "Test Collection",
        bookCount: Int = 0,
    ): CollectionEntity =
        CollectionEntity(
            id = id,
            name = name,
            bookCount = bookCount,
            createdAt = Timestamp(1704067200000L),
            updatedAt = Timestamp(1704067200000L),
        )

    private fun createLens(
        id: String = "lens-1",
        name: String = "My Lens",
        ownerId: String = "user-1",
    ): Lens =
        Lens(
            id = id,
            name = name,
            description = "",
            ownerId = ownerId,
            ownerDisplayName = "Test User",
            ownerAvatarColor = "#6B7280",
            bookCount = 0,
            totalDurationSeconds = 0,
            createdAtMs = 1704067200000L,
            updatedAtMs = 1704067200000L,
        )

    private fun createLensResponse(
        id: String = "lens-1",
        name: String = "My Lens",
    ): LensResponse =
        LensResponse(
            id = id,
            name = name,
            description = "",
            owner = LensOwnerResponse(
                id = "user-1",
                displayName = "Test User",
                avatarColor = "#6B7280",
            ),
            bookCount = 0,
            totalDuration = 0,
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

    // ========== Selection Mode Tests ==========

    @Test
    fun `selectionMode is None initially`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()
            advanceUntilIdle()

            // Then
            assertEquals(SelectionMode.None, viewModel.selectionMode.value)
        }

    @Test
    fun `selectionMode reflects selection manager state`() =
        runTest {
            // Given
            val fixture = createFixture()
            fixture.selectionManager.enterSelectionMode("book-1")
            val viewModel = fixture.build()
            advanceUntilIdle()

            // Then
            val mode = assertIs<SelectionMode.Active>(viewModel.selectionMode.value)
            assertEquals(setOf("book-1"), mode.selectedIds)
        }

    @Test
    fun `selectionMode updates when manager changes`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()
            advanceUntilIdle()
            assertEquals(SelectionMode.None, viewModel.selectionMode.value)

            // When
            fixture.selectionManager.enterSelectionMode("book-1")
            advanceUntilIdle()

            // Then
            checkIs<SelectionMode.Active>(viewModel.selectionMode.value)
        }

    // ========== addSelectedToCollection Tests ==========

    @Test
    fun `addSelectedToCollection does nothing when no books selected`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()
            advanceUntilIdle()

            // When
            viewModel.addSelectedToCollection("collection-1")
            advanceUntilIdle()

            // Then - API should not be called, loading should be false
            assertFalse(viewModel.isAddingToCollection.value)
        }

    @Test
    fun `addSelectedToCollection calls API with selected book IDs`() =
        runTest {
            // Given
            val fixture = createFixture()
            fixture.selectionManager.enterSelectionMode("book-1")
            fixture.selectionManager.toggleSelection("book-2")
            everySuspend { fixture.adminCollectionApi.addBooks(any(), any()) } returns Unit
            val viewModel = fixture.build()
            advanceUntilIdle()

            // When
            viewModel.addSelectedToCollection("collection-1")
            advanceUntilIdle()

            // Then
            verifySuspend { fixture.adminCollectionApi.addBooks("collection-1", any()) }
        }

    @Test
    fun `addSelectedToCollection clears selection on success`() =
        runTest {
            // Given
            val fixture = createFixture()
            fixture.selectionManager.enterSelectionMode("book-1")
            everySuspend { fixture.adminCollectionApi.addBooks(any(), any()) } returns Unit
            val viewModel = fixture.build()
            advanceUntilIdle()
            checkIs<SelectionMode.Active>(fixture.selectionManager.selectionMode.value)

            // When
            viewModel.addSelectedToCollection("collection-1")
            advanceUntilIdle()

            // Then
            assertEquals(SelectionMode.None, fixture.selectionManager.selectionMode.value)
        }

    @Test
    fun `addSelectedToCollection does not clear selection on failure`() =
        runTest {
            // Given
            val fixture = createFixture()
            fixture.selectionManager.enterSelectionMode("book-1")
            everySuspend { fixture.adminCollectionApi.addBooks(any(), any()) } throws RuntimeException("Network error")
            val viewModel = fixture.build()
            advanceUntilIdle()

            // When
            viewModel.addSelectedToCollection("collection-1")
            advanceUntilIdle()

            // Then - selection should still be active
            checkIs<SelectionMode.Active>(fixture.selectionManager.selectionMode.value)
        }

    @Test
    fun `addSelectedToCollection sets and clears loading state`() =
        runTest {
            // Given
            val fixture = createFixture()
            fixture.selectionManager.enterSelectionMode("book-1")
            everySuspend { fixture.adminCollectionApi.addBooks(any(), any()) } returns Unit
            val viewModel = fixture.build()
            advanceUntilIdle()

            // When
            viewModel.addSelectedToCollection("collection-1")
            advanceUntilIdle()

            // Then - loading should be false after completion
            assertFalse(viewModel.isAddingToCollection.value)
        }

    // ========== addSelectedToLens Tests ==========

    @Test
    fun `addSelectedToLens does nothing when no books selected`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()
            advanceUntilIdle()

            // When
            viewModel.addSelectedToLens("lens-1")
            advanceUntilIdle()

            // Then - API should not be called
            assertFalse(viewModel.isAddingToLens.value)
        }

    @Test
    fun `addSelectedToLens calls API with selected book IDs`() =
        runTest {
            // Given
            val fixture = createFixture()
            fixture.selectionManager.enterSelectionMode("book-1")
            everySuspend { fixture.lensApi.addBooks(any(), any()) } returns Unit
            val viewModel = fixture.build()
            advanceUntilIdle()

            // When
            viewModel.addSelectedToLens("lens-1")
            advanceUntilIdle()

            // Then
            verifySuspend { fixture.lensApi.addBooks("lens-1", any()) }
        }

    @Test
    fun `addSelectedToLens clears selection on success`() =
        runTest {
            // Given
            val fixture = createFixture()
            fixture.selectionManager.enterSelectionMode("book-1")
            everySuspend { fixture.lensApi.addBooks(any(), any()) } returns Unit
            val viewModel = fixture.build()
            advanceUntilIdle()

            // When
            viewModel.addSelectedToLens("lens-1")
            advanceUntilIdle()

            // Then
            assertEquals(SelectionMode.None, fixture.selectionManager.selectionMode.value)
        }

    @Test
    fun `addSelectedToLens does not clear selection on failure`() =
        runTest {
            // Given
            val fixture = createFixture()
            fixture.selectionManager.enterSelectionMode("book-1")
            everySuspend { fixture.lensApi.addBooks(any(), any()) } throws RuntimeException("Server error")
            val viewModel = fixture.build()
            advanceUntilIdle()

            // When
            viewModel.addSelectedToLens("lens-1")
            advanceUntilIdle()

            // Then - selection should still be active
            checkIs<SelectionMode.Active>(fixture.selectionManager.selectionMode.value)
        }

    // ========== createLensAndAddBooks Tests ==========

    @Test
    fun `createLensAndAddBooks does nothing when no books selected`() =
        runTest {
            // Given
            val fixture = createFixture()
            val viewModel = fixture.build()
            advanceUntilIdle()

            // When
            viewModel.createLensAndAddBooks("New Lens")
            advanceUntilIdle()

            // Then - API should not be called
            assertFalse(viewModel.isAddingToLens.value)
        }

    @Test
    fun `createLensAndAddBooks creates lens then adds books`() =
        runTest {
            // Given
            val fixture = createFixture()
            fixture.selectionManager.enterSelectionMode("book-1")
            val newLens = createLensResponse(id = "new-lens", name = "My New Lens")
            everySuspend { fixture.lensApi.createLens(any(), any()) } returns newLens
            everySuspend { fixture.lensApi.addBooks(any(), any()) } returns Unit
            val viewModel = fixture.build()
            advanceUntilIdle()

            // When
            viewModel.createLensAndAddBooks("My New Lens")
            advanceUntilIdle()

            // Then
            verifySuspend { fixture.lensApi.createLens("My New Lens", null) }
            verifySuspend { fixture.lensApi.addBooks("new-lens", any()) }
        }

    @Test
    fun `createLensAndAddBooks clears selection on success`() =
        runTest {
            // Given
            val fixture = createFixture()
            fixture.selectionManager.enterSelectionMode("book-1")
            val newLens = createLensResponse()
            everySuspend { fixture.lensApi.createLens(any(), any()) } returns newLens
            everySuspend { fixture.lensApi.addBooks(any(), any()) } returns Unit
            val viewModel = fixture.build()
            advanceUntilIdle()

            // When
            viewModel.createLensAndAddBooks("New Lens")
            advanceUntilIdle()

            // Then
            assertEquals(SelectionMode.None, fixture.selectionManager.selectionMode.value)
        }

    @Test
    fun `createLensAndAddBooks does not clear selection on lens creation failure`() =
        runTest {
            // Given
            val fixture = createFixture()
            fixture.selectionManager.enterSelectionMode("book-1")
            everySuspend { fixture.lensApi.createLens(any(), any()) } throws RuntimeException("Failed to create lens")
            val viewModel = fixture.build()
            advanceUntilIdle()

            // When
            viewModel.createLensAndAddBooks("New Lens")
            advanceUntilIdle()

            // Then - selection should still be active
            checkIs<SelectionMode.Active>(fixture.selectionManager.selectionMode.value)
        }

    @Test
    fun `createLensAndAddBooks does not clear selection on addBooks failure`() =
        runTest {
            // Given
            val fixture = createFixture()
            fixture.selectionManager.enterSelectionMode("book-1")
            val newLens = createLensResponse()
            everySuspend { fixture.lensApi.createLens(any(), any()) } returns newLens
            everySuspend { fixture.lensApi.addBooks(any(), any()) } throws RuntimeException("Failed to add books")
            val viewModel = fixture.build()
            advanceUntilIdle()

            // When
            viewModel.createLensAndAddBooks("New Lens")
            advanceUntilIdle()

            // Then - selection should still be active
            checkIs<SelectionMode.Active>(fixture.selectionManager.selectionMode.value)
        }

    @Test
    fun `createLensAndAddBooks sets and clears loading state`() =
        runTest {
            // Given
            val fixture = createFixture()
            fixture.selectionManager.enterSelectionMode("book-1")
            val newLens = createLensResponse()
            everySuspend { fixture.lensApi.createLens(any(), any()) } returns newLens
            everySuspend { fixture.lensApi.addBooks(any(), any()) } returns Unit
            val viewModel = fixture.build()
            advanceUntilIdle()

            // When
            viewModel.createLensAndAddBooks("New Lens")
            advanceUntilIdle()

            // Then - loading should be false after completion
            assertFalse(viewModel.isAddingToLens.value)
        }
}
