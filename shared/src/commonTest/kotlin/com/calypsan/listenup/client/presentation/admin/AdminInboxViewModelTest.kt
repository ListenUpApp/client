package com.calypsan.listenup.client.presentation.admin

import com.calypsan.listenup.client.data.remote.AdminApiContract
import com.calypsan.listenup.client.data.remote.CollectionRef
import com.calypsan.listenup.client.data.remote.InboxBookResponse
import com.calypsan.listenup.client.data.remote.InboxBooksResponse
import com.calypsan.listenup.client.data.remote.ReleaseInboxBooksResponse
import com.calypsan.listenup.client.data.sync.SSEEventType
import com.calypsan.listenup.client.data.sync.SSEManagerContract
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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

@OptIn(ExperimentalCoroutinesApi::class)
class AdminInboxViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private fun createInboxBook(
        id: String,
        title: String = "Test Book",
        author: String? = "Test Author",
        stagedCollectionIds: List<String> = emptyList(),
    ) = InboxBookResponse(
        id = id,
        title = title,
        author = author,
        coverUrl = null,
        duration = 3600000, // 1 hour
        stagedCollectionIds = stagedCollectionIds,
        stagedCollections = stagedCollectionIds.map { CollectionRef(it, "Collection $it") },
        scannedAt = "2024-01-01T00:00:00Z",
    )

    private fun createMockAdminApi(
        books: List<InboxBookResponse> = emptyList(),
    ): AdminApiContract {
        val adminApi: AdminApiContract = mock()
        everySuspend { adminApi.listInboxBooks() } returns
            InboxBooksResponse(
                books = books,
                total = books.size,
            )
        return adminApi
    }

    private fun createMockSSEManager(): Pair<SSEManagerContract, MutableSharedFlow<SSEEventType>> {
        val sseManager: SSEManagerContract = mock()
        // Use extraBufferCapacity to prevent emit from suspending in tests
        val eventFlow = MutableSharedFlow<SSEEventType>(extraBufferCapacity = 1)
        every { sseManager.eventFlow } returns eventFlow
        return sseManager to eventFlow
    }

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is loading`() =
        runTest {
            val adminApi = createMockAdminApi()
            val (sseManager, _) = createMockSSEManager()

            val viewModel = AdminInboxViewModel(adminApi, sseManager)

            assertTrue(viewModel.state.value.isLoading)
        }

    @Test
    fun `loadInboxBooks fetches books from API`() =
        runTest {
            val books =
                listOf(
                    createInboxBook("book-1", "Book One"),
                    createInboxBook("book-2", "Book Two"),
                )
            val adminApi = createMockAdminApi(books)
            val (sseManager, _) = createMockSSEManager()

            val viewModel = AdminInboxViewModel(adminApi, sseManager)
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isLoading)
            assertEquals(2, viewModel.state.value.books.size)
            assertEquals(
                "book-1",
                viewModel.state.value.books[0]
                    .id,
            )
            assertEquals(
                "book-2",
                viewModel.state.value.books[1]
                    .id,
            )
        }

    @Test
    fun `loadInboxBooks handles error`() =
        runTest {
            val adminApi: AdminApiContract = mock()
            everySuspend { adminApi.listInboxBooks() } throws RuntimeException("Network error")
            val (sseManager, _) = createMockSSEManager()

            val viewModel = AdminInboxViewModel(adminApi, sseManager)
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isLoading)
            assertTrue(
                viewModel.state.value.error
                    ?.contains("Network error") == true ||
                    viewModel.state.value.error
                        ?.contains("Failed to load") == true,
            )
        }

    @Test
    fun `toggleBookSelection adds book to selection`() =
        runTest {
            val books = listOf(createInboxBook("book-1"))
            val adminApi = createMockAdminApi(books)
            val (sseManager, _) = createMockSSEManager()

            val viewModel = AdminInboxViewModel(adminApi, sseManager)
            advanceUntilIdle()

            viewModel.toggleBookSelection("book-1")

            assertTrue(
                viewModel.state.value.selectedBookIds
                    .contains("book-1"),
            )
        }

    @Test
    fun `toggleBookSelection removes book from selection if already selected`() =
        runTest {
            val books = listOf(createInboxBook("book-1"))
            val adminApi = createMockAdminApi(books)
            val (sseManager, _) = createMockSSEManager()

            val viewModel = AdminInboxViewModel(adminApi, sseManager)
            advanceUntilIdle()

            viewModel.toggleBookSelection("book-1")
            assertTrue(
                viewModel.state.value.selectedBookIds
                    .contains("book-1"),
            )

            viewModel.toggleBookSelection("book-1")
            assertFalse(
                viewModel.state.value.selectedBookIds
                    .contains("book-1"),
            )
        }

    @Test
    fun `selectAll selects all books`() =
        runTest {
            val books =
                listOf(
                    createInboxBook("book-1"),
                    createInboxBook("book-2"),
                    createInboxBook("book-3"),
                )
            val adminApi = createMockAdminApi(books)
            val (sseManager, _) = createMockSSEManager()

            val viewModel = AdminInboxViewModel(adminApi, sseManager)
            advanceUntilIdle()

            viewModel.selectAll()

            assertEquals(3, viewModel.state.value.selectedBookIds.size)
            assertTrue(viewModel.state.value.allSelected)
        }

    @Test
    fun `clearSelection clears all selections`() =
        runTest {
            val books = listOf(createInboxBook("book-1"), createInboxBook("book-2"))
            val adminApi = createMockAdminApi(books)
            val (sseManager, _) = createMockSSEManager()

            val viewModel = AdminInboxViewModel(adminApi, sseManager)
            advanceUntilIdle()

            viewModel.selectAll()
            assertEquals(2, viewModel.state.value.selectedBookIds.size)

            viewModel.clearSelection()
            assertTrue(
                viewModel.state.value.selectedBookIds
                    .isEmpty(),
            )
        }

    @Test
    fun `releaseBooks calls API and removes books from local state`() =
        runTest {
            val books = listOf(createInboxBook("book-1"), createInboxBook("book-2"))
            val adminApi = createMockAdminApi(books)
            everySuspend { adminApi.releaseBooks(listOf("book-1")) } returns
                ReleaseInboxBooksResponse(
                    released = 1,
                    public = 1,
                    toCollections = 0,
                )
            val (sseManager, _) = createMockSSEManager()

            val viewModel = AdminInboxViewModel(adminApi, sseManager)
            advanceUntilIdle()

            viewModel.releaseBooks(listOf("book-1"))
            advanceUntilIdle()

            assertEquals(1, viewModel.state.value.books.size)
            assertEquals(
                "book-2",
                viewModel.state.value.books[0]
                    .id,
            )
            assertFalse(viewModel.state.value.isReleasing)
            assertEquals(
                1,
                viewModel.state.value.lastReleaseResult
                    ?.released,
            )
        }

    @Test
    fun `releaseBooks clears selection after success`() =
        runTest {
            val books = listOf(createInboxBook("book-1"), createInboxBook("book-2"))
            val adminApi = createMockAdminApi(books)
            everySuspend { adminApi.releaseBooks(listOf("book-1")) } returns
                ReleaseInboxBooksResponse(
                    released = 1,
                    public = 1,
                    toCollections = 0,
                )
            val (sseManager, _) = createMockSSEManager()

            val viewModel = AdminInboxViewModel(adminApi, sseManager)
            advanceUntilIdle()

            viewModel.toggleBookSelection("book-1")
            assertEquals(1, viewModel.state.value.selectedBookIds.size)

            viewModel.releaseBooks(listOf("book-1"))
            advanceUntilIdle()

            assertTrue(
                viewModel.state.value.selectedBookIds
                    .isEmpty(),
            )
        }

    @Test
    fun `releaseBooks handles error`() =
        runTest {
            val books = listOf(createInboxBook("book-1"))
            val adminApi = createMockAdminApi(books)
            everySuspend { adminApi.releaseBooks(listOf("book-1")) } throws RuntimeException("Failed")
            val (sseManager, _) = createMockSSEManager()

            val viewModel = AdminInboxViewModel(adminApi, sseManager)
            advanceUntilIdle()

            viewModel.releaseBooks(listOf("book-1"))
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isReleasing)
            assertTrue(viewModel.state.value.error != null)
            // Book should still be in the list
            assertEquals(1, viewModel.state.value.books.size)
        }

    @Test
    fun `stageCollection reloads books after success`() =
        runTest {
            val books = listOf(createInboxBook("book-1"))
            val adminApi = createMockAdminApi(books)
            everySuspend { adminApi.stageCollection("book-1", "coll-1") } returns Unit
            val (sseManager, _) = createMockSSEManager()

            val viewModel = AdminInboxViewModel(adminApi, sseManager)
            advanceUntilIdle()

            viewModel.stageCollection("book-1", "coll-1")
            advanceUntilIdle()

            // Verify API was called
            verifySuspend(VerifyMode.atLeast(1)) { adminApi.stageCollection("book-1", "coll-1") }
            // Verify books were reloaded
            verifySuspend(VerifyMode.atLeast(2)) { adminApi.listInboxBooks() }
        }

    @Test
    fun `unstageCollection reloads books after success`() =
        runTest {
            val books = listOf(createInboxBook("book-1", stagedCollectionIds = listOf("coll-1")))
            val adminApi = createMockAdminApi(books)
            everySuspend { adminApi.unstageCollection("book-1", "coll-1") } returns Unit
            val (sseManager, _) = createMockSSEManager()

            val viewModel = AdminInboxViewModel(adminApi, sseManager)
            advanceUntilIdle()

            viewModel.unstageCollection("book-1", "coll-1")
            advanceUntilIdle()

            verifySuspend(VerifyMode.atLeast(1)) { adminApi.unstageCollection("book-1", "coll-1") }
            verifySuspend(VerifyMode.atLeast(2)) { adminApi.listInboxBooks() }
        }

    @Test
    fun `hasSelectedBooksWithoutCollections returns true when book has no staged collections`() =
        runTest {
            val books =
                listOf(
                    createInboxBook("book-1", stagedCollectionIds = emptyList()),
                )
            val adminApi = createMockAdminApi(books)
            val (sseManager, _) = createMockSSEManager()

            val viewModel = AdminInboxViewModel(adminApi, sseManager)
            advanceUntilIdle()

            viewModel.toggleBookSelection("book-1")

            assertTrue(viewModel.hasSelectedBooksWithoutCollections())
        }

    @Test
    fun `hasSelectedBooksWithoutCollections returns false when all books have collections`() =
        runTest {
            val books =
                listOf(
                    createInboxBook("book-1", stagedCollectionIds = listOf("coll-1")),
                )
            val adminApi = createMockAdminApi(books)
            val (sseManager, _) = createMockSSEManager()

            val viewModel = AdminInboxViewModel(adminApi, sseManager)
            advanceUntilIdle()

            viewModel.toggleBookSelection("book-1")

            assertFalse(viewModel.hasSelectedBooksWithoutCollections())
        }

    @Test
    fun `SSE InboxBookReleased event removes book from list`() =
        runTest {
            val books = listOf(createInboxBook("book-1"), createInboxBook("book-2"))
            val adminApi = createMockAdminApi(books)
            val (sseManager, eventFlow) = createMockSSEManager()

            val viewModel = AdminInboxViewModel(adminApi, sseManager)
            advanceUntilIdle()

            assertEquals(2, viewModel.state.value.books.size)

            // Emit SSE event
            eventFlow.emit(SSEEventType.InboxBookReleased("book-1"))
            advanceUntilIdle()

            assertEquals(1, viewModel.state.value.books.size)
            assertEquals(
                "book-2",
                viewModel.state.value.books[0]
                    .id,
            )
        }

    @Test
    fun `SSE InboxBookAdded event reloads books`() =
        runTest {
            val books = listOf(createInboxBook("book-1"))
            val (sseManager, eventFlow) = createMockSSEManager()

            // Create a fresh mock for this test to track call count from scratch
            val adminApi: AdminApiContract = mock()
            everySuspend { adminApi.listInboxBooks() } returns
                InboxBooksResponse(
                    books = books,
                    total = books.size,
                )

            val viewModel = AdminInboxViewModel(adminApi, sseManager)
            advanceUntilIdle()

            // Initial load done - isLoading should be false
            assertFalse(viewModel.state.value.isLoading)
            assertEquals(1, viewModel.state.value.books.size)

            // Track that we're about to reload
            val initialBooks = viewModel.state.value.books

            // Emit SSE event for new book (same pattern as InboxBookReleased test)
            eventFlow.emit(SSEEventType.InboxBookAdded(bookId = "book-2", title = "New Book"))
            advanceUntilIdle()

            // The reload should have been triggered - verify by checking loading state cycled
            // Or simply verify the API was called at least twice
            verifySuspend(VerifyMode.atLeast(2)) { adminApi.listInboxBooks() }
        }

    @Test
    fun `clearError clears error state`() =
        runTest {
            val adminApi: AdminApiContract = mock()
            everySuspend { adminApi.listInboxBooks() } throws RuntimeException("Error")
            val (sseManager, _) = createMockSSEManager()

            val viewModel = AdminInboxViewModel(adminApi, sseManager)
            advanceUntilIdle()

            assertTrue(viewModel.state.value.error != null)

            viewModel.clearError()

            assertNull(viewModel.state.value.error)
        }

    @Test
    fun `clearReleaseResult clears last release result`() =
        runTest {
            val books = listOf(createInboxBook("book-1"))
            val adminApi = createMockAdminApi(books)
            everySuspend { adminApi.releaseBooks(listOf("book-1")) } returns
                ReleaseInboxBooksResponse(
                    released = 1,
                    public = 1,
                    toCollections = 0,
                )
            val (sseManager, _) = createMockSSEManager()

            val viewModel = AdminInboxViewModel(adminApi, sseManager)
            advanceUntilIdle()

            viewModel.releaseBooks(listOf("book-1"))
            advanceUntilIdle()

            assertTrue(viewModel.state.value.lastReleaseResult != null)

            viewModel.clearReleaseResult()

            assertNull(viewModel.state.value.lastReleaseResult)
        }

    @Test
    fun `hasBooks returns true when books exist`() =
        runTest {
            val books = listOf(createInboxBook("book-1"))
            val adminApi = createMockAdminApi(books)
            val (sseManager, _) = createMockSSEManager()

            val viewModel = AdminInboxViewModel(adminApi, sseManager)
            advanceUntilIdle()

            assertTrue(viewModel.state.value.hasBooks)
        }

    @Test
    fun `hasBooks returns false when no books`() =
        runTest {
            val adminApi = createMockAdminApi(emptyList())
            val (sseManager, _) = createMockSSEManager()

            val viewModel = AdminInboxViewModel(adminApi, sseManager)
            advanceUntilIdle()

            assertFalse(viewModel.state.value.hasBooks)
        }

    @Test
    fun `hasSelection returns true when books are selected`() =
        runTest {
            val books = listOf(createInboxBook("book-1"))
            val adminApi = createMockAdminApi(books)
            val (sseManager, _) = createMockSSEManager()

            val viewModel = AdminInboxViewModel(adminApi, sseManager)
            advanceUntilIdle()

            assertFalse(viewModel.state.value.hasSelection)

            viewModel.toggleBookSelection("book-1")

            assertTrue(viewModel.state.value.hasSelection)
        }
}
