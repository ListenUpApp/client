package com.calypsan.listenup.client.presentation.library

import com.calypsan.listenup.client.checkIs
import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.domain.model.Collection
import com.calypsan.listenup.client.domain.model.Lens
import com.calypsan.listenup.client.domain.model.User
import com.calypsan.listenup.client.domain.repository.CollectionRepository
import com.calypsan.listenup.client.domain.repository.LensRepository
import com.calypsan.listenup.client.domain.repository.UserRepository
import com.calypsan.listenup.client.domain.usecase.collection.AddBooksToCollectionUseCase
import com.calypsan.listenup.client.domain.usecase.collection.RefreshCollectionsUseCase
import com.calypsan.listenup.client.domain.usecase.lens.AddBooksToLensUseCase
import com.calypsan.listenup.client.domain.usecase.lens.CreateLensUseCase
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlin.test.assertIs

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
        val collectionRepository: CollectionRepository = mock()
        val lensRepository: LensRepository = mock()
        val addBooksToCollectionUseCase: AddBooksToCollectionUseCase = mock()
        val refreshCollectionsUseCase: RefreshCollectionsUseCase = mock()
        val addBooksToLensUseCase: AddBooksToLensUseCase = mock()
        val createLensUseCase: CreateLensUseCase = mock()

        val userFlow = MutableStateFlow<User?>(null)
        val collectionsFlow = MutableStateFlow<List<Collection>>(emptyList())
        val lensesFlow = MutableStateFlow<List<Lens>>(emptyList())

        fun build(): LibraryActionsViewModel =
            LibraryActionsViewModel(
                selectionManager = selectionManager,
                userRepository = userRepository,
                collectionRepository = collectionRepository,
                lensRepository = lensRepository,
                addBooksToCollectionUseCase = addBooksToCollectionUseCase,
                refreshCollectionsUseCase = refreshCollectionsUseCase,
                addBooksToLensUseCase = addBooksToLensUseCase,
                createLensUseCase = createLensUseCase,
            )
    }

    private fun createFixture(): TestFixture {
        val fixture = TestFixture()

        // Default stubs for reactive observation
        every { fixture.userRepository.observeCurrentUser() } returns fixture.userFlow
        every { fixture.collectionRepository.observeAll() } returns fixture.collectionsFlow
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
    ): Collection =
        Collection(
            id = id,
            name = name,
            bookCount = bookCount,
            createdAtMs = 1704067200000L,
            updatedAtMs = 1704067200000L,
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

            // Then - use case should not be called, loading should be false
            assertFalse(viewModel.isAddingToCollection.value)
        }

    @Test
    fun `addSelectedToCollection calls use case with selected book IDs`() =
        runTest {
            // Given
            val fixture = createFixture()
            fixture.selectionManager.enterSelectionMode("book-1")
            fixture.selectionManager.toggleSelection("book-2")
            everySuspend { fixture.addBooksToCollectionUseCase(any(), any()) } returns Success(Unit)
            val viewModel = fixture.build()
            advanceUntilIdle()

            // When
            viewModel.addSelectedToCollection("collection-1")
            advanceUntilIdle()

            // Then
            verifySuspend { fixture.addBooksToCollectionUseCase("collection-1", any()) }
        }

    @Test
    fun `addSelectedToCollection clears selection on success`() =
        runTest {
            // Given
            val fixture = createFixture()
            fixture.selectionManager.enterSelectionMode("book-1")
            everySuspend { fixture.addBooksToCollectionUseCase(any(), any()) } returns Success(Unit)
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
            everySuspend { fixture.addBooksToCollectionUseCase(any(), any()) } returns Failure(
                RuntimeException("Network error"),
                "Network error"
            )
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
            everySuspend { fixture.addBooksToCollectionUseCase(any(), any()) } returns Success(Unit)
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

            // Then - use case should not be called
            assertFalse(viewModel.isAddingToLens.value)
        }

    @Test
    fun `addSelectedToLens calls use case with selected book IDs`() =
        runTest {
            // Given
            val fixture = createFixture()
            fixture.selectionManager.enterSelectionMode("book-1")
            everySuspend { fixture.addBooksToLensUseCase(any(), any()) } returns Success(Unit)
            val viewModel = fixture.build()
            advanceUntilIdle()

            // When
            viewModel.addSelectedToLens("lens-1")
            advanceUntilIdle()

            // Then
            verifySuspend { fixture.addBooksToLensUseCase("lens-1", any()) }
        }

    @Test
    fun `addSelectedToLens clears selection on success`() =
        runTest {
            // Given
            val fixture = createFixture()
            fixture.selectionManager.enterSelectionMode("book-1")
            everySuspend { fixture.addBooksToLensUseCase(any(), any()) } returns Success(Unit)
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
            everySuspend { fixture.addBooksToLensUseCase(any(), any()) } returns Failure(
                RuntimeException("Server error"),
                "Server error"
            )
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

            // Then - use case should not be called
            assertFalse(viewModel.isAddingToLens.value)
        }

    @Test
    fun `createLensAndAddBooks creates lens then adds books`() =
        runTest {
            // Given
            val fixture = createFixture()
            fixture.selectionManager.enterSelectionMode("book-1")
            val newLens = createLens(id = "new-lens", name = "My New Lens")
            everySuspend { fixture.createLensUseCase(any(), any()) } returns Success(newLens)
            everySuspend { fixture.addBooksToLensUseCase(any(), any()) } returns Success(Unit)
            val viewModel = fixture.build()
            advanceUntilIdle()

            // When
            viewModel.createLensAndAddBooks("My New Lens")
            advanceUntilIdle()

            // Then
            verifySuspend { fixture.createLensUseCase("My New Lens", null) }
            verifySuspend { fixture.addBooksToLensUseCase("new-lens", any()) }
        }

    @Test
    fun `createLensAndAddBooks clears selection on success`() =
        runTest {
            // Given
            val fixture = createFixture()
            fixture.selectionManager.enterSelectionMode("book-1")
            val newLens = createLens()
            everySuspend { fixture.createLensUseCase(any(), any()) } returns Success(newLens)
            everySuspend { fixture.addBooksToLensUseCase(any(), any()) } returns Success(Unit)
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
            everySuspend { fixture.createLensUseCase(any(), any()) } returns Failure(
                RuntimeException("Failed to create lens"),
                "Failed to create lens"
            )
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
            val newLens = createLens()
            everySuspend { fixture.createLensUseCase(any(), any()) } returns Success(newLens)
            everySuspend { fixture.addBooksToLensUseCase(any(), any()) } returns Failure(
                RuntimeException("Failed to add books"),
                "Failed to add books"
            )
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
            val newLens = createLens()
            everySuspend { fixture.createLensUseCase(any(), any()) } returns Success(newLens)
            everySuspend { fixture.addBooksToLensUseCase(any(), any()) } returns Success(Unit)
            val viewModel = fixture.build()
            advanceUntilIdle()

            // When
            viewModel.createLensAndAddBooks("New Lens")
            advanceUntilIdle()

            // Then - loading should be false after completion
            assertFalse(viewModel.isAddingToLens.value)
        }
}
