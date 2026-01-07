package com.calypsan.listenup.client.presentation.admin

import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.domain.model.AdminEvent
import com.calypsan.listenup.client.domain.model.InboxBook
import com.calypsan.listenup.client.domain.repository.EventStreamRepository
import com.calypsan.listenup.client.domain.model.InboxReleaseResult
import com.calypsan.listenup.client.domain.model.StagedCollection
import com.calypsan.listenup.client.domain.usecase.admin.LoadInboxBooksUseCase
import com.calypsan.listenup.client.domain.usecase.admin.ReleaseBooksUseCase
import com.calypsan.listenup.client.domain.usecase.admin.StageCollectionUseCase
import com.calypsan.listenup.client.domain.usecase.admin.UnstageCollectionUseCase
import dev.mokkery.answering.returns
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
    ) = InboxBook(
        id = id,
        title = title,
        author = author,
        coverUrl = null,
        duration = 3600000, // 1 hour
        stagedCollectionIds = stagedCollectionIds,
        stagedCollections = stagedCollectionIds.map { StagedCollection(it, "Collection $it") },
        scannedAt = "2024-01-01T00:00:00Z",
    )

    private class TestFixture {
        val loadInboxBooksUseCase: LoadInboxBooksUseCase = mock()
        val releaseBooksUseCase: ReleaseBooksUseCase = mock()
        val stageCollectionUseCase: StageCollectionUseCase = mock()
        val unstageCollectionUseCase: UnstageCollectionUseCase = mock()
        val eventStreamRepository: EventStreamRepository = mock()
        val adminEvents = MutableSharedFlow<AdminEvent>(extraBufferCapacity = 1)

        init {
            every { eventStreamRepository.adminEvents } returns adminEvents
        }

        fun build() = AdminInboxViewModel(
            loadInboxBooksUseCase = loadInboxBooksUseCase,
            releaseBooksUseCase = releaseBooksUseCase,
            stageCollectionUseCase = stageCollectionUseCase,
            unstageCollectionUseCase = unstageCollectionUseCase,
            eventStreamRepository = eventStreamRepository,
        )
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
            val fixture = TestFixture()
            everySuspend { fixture.loadInboxBooksUseCase() } returns Success(emptyList())

            val viewModel = fixture.build()

            assertTrue(viewModel.state.value.isLoading)
        }

    @Test
    fun `loadInboxBooks fetches books from use case`() =
        runTest {
            val books =
                listOf(
                    createInboxBook("book-1", "Book One"),
                    createInboxBook("book-2", "Book Two"),
                )
            val fixture = TestFixture()
            everySuspend { fixture.loadInboxBooksUseCase() } returns Success(books)

            val viewModel = fixture.build()
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isLoading)
            assertEquals(2, viewModel.state.value.books.size)
            assertEquals("book-1", viewModel.state.value.books[0].id)
            assertEquals("book-2", viewModel.state.value.books[1].id)
        }

    @Test
    fun `loadInboxBooks handles error`() =
        runTest {
            val fixture = TestFixture()
            everySuspend { fixture.loadInboxBooksUseCase() } returns Failure(
                RuntimeException("Network error"),
                "Network error",
            )

            val viewModel = fixture.build()
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isLoading)
            assertTrue(
                viewModel.state.value.error?.contains("Network error") == true,
            )
        }

    @Test
    fun `toggleBookSelection adds book to selection`() =
        runTest {
            val books = listOf(createInboxBook("book-1"))
            val fixture = TestFixture()
            everySuspend { fixture.loadInboxBooksUseCase() } returns Success(books)

            val viewModel = fixture.build()
            advanceUntilIdle()

            viewModel.toggleBookSelection("book-1")

            assertTrue(viewModel.state.value.selectedBookIds.contains("book-1"))
        }

    @Test
    fun `toggleBookSelection removes book from selection if already selected`() =
        runTest {
            val books = listOf(createInboxBook("book-1"))
            val fixture = TestFixture()
            everySuspend { fixture.loadInboxBooksUseCase() } returns Success(books)

            val viewModel = fixture.build()
            advanceUntilIdle()

            viewModel.toggleBookSelection("book-1")
            assertTrue(viewModel.state.value.selectedBookIds.contains("book-1"))

            viewModel.toggleBookSelection("book-1")
            assertFalse(viewModel.state.value.selectedBookIds.contains("book-1"))
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
            val fixture = TestFixture()
            everySuspend { fixture.loadInboxBooksUseCase() } returns Success(books)

            val viewModel = fixture.build()
            advanceUntilIdle()

            viewModel.selectAll()

            assertEquals(3, viewModel.state.value.selectedBookIds.size)
            assertTrue(viewModel.state.value.allSelected)
        }

    @Test
    fun `clearSelection clears all selections`() =
        runTest {
            val books = listOf(createInboxBook("book-1"), createInboxBook("book-2"))
            val fixture = TestFixture()
            everySuspend { fixture.loadInboxBooksUseCase() } returns Success(books)

            val viewModel = fixture.build()
            advanceUntilIdle()

            viewModel.selectAll()
            assertEquals(2, viewModel.state.value.selectedBookIds.size)

            viewModel.clearSelection()
            assertTrue(viewModel.state.value.selectedBookIds.isEmpty())
        }

    @Test
    fun `releaseBooks calls use case and removes books from local state`() =
        runTest {
            val books = listOf(createInboxBook("book-1"), createInboxBook("book-2"))
            val fixture = TestFixture()
            everySuspend { fixture.loadInboxBooksUseCase() } returns Success(books)
            everySuspend { fixture.releaseBooksUseCase(listOf("book-1")) } returns Success(
                InboxReleaseResult(
                    released = 1,
                    publicCount = 1,
                    toCollections = 0,
                ),
            )

            val viewModel = fixture.build()
            advanceUntilIdle()

            viewModel.releaseBooks(listOf("book-1"))
            advanceUntilIdle()

            assertEquals(1, viewModel.state.value.books.size)
            assertEquals("book-2", viewModel.state.value.books[0].id)
            assertFalse(viewModel.state.value.isReleasing)
            assertEquals(1, viewModel.state.value.lastReleaseResult?.released)
        }

    @Test
    fun `releaseBooks clears selection after success`() =
        runTest {
            val books = listOf(createInboxBook("book-1"), createInboxBook("book-2"))
            val fixture = TestFixture()
            everySuspend { fixture.loadInboxBooksUseCase() } returns Success(books)
            everySuspend { fixture.releaseBooksUseCase(listOf("book-1")) } returns Success(
                InboxReleaseResult(
                    released = 1,
                    publicCount = 1,
                    toCollections = 0,
                ),
            )

            val viewModel = fixture.build()
            advanceUntilIdle()

            viewModel.toggleBookSelection("book-1")
            assertEquals(1, viewModel.state.value.selectedBookIds.size)

            viewModel.releaseBooks(listOf("book-1"))
            advanceUntilIdle()

            assertTrue(viewModel.state.value.selectedBookIds.isEmpty())
        }

    @Test
    fun `releaseBooks handles error`() =
        runTest {
            val books = listOf(createInboxBook("book-1"))
            val fixture = TestFixture()
            everySuspend { fixture.loadInboxBooksUseCase() } returns Success(books)
            everySuspend { fixture.releaseBooksUseCase(listOf("book-1")) } returns Failure(
                RuntimeException("Failed"),
                "Failed",
            )

            val viewModel = fixture.build()
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
            val fixture = TestFixture()
            everySuspend { fixture.loadInboxBooksUseCase() } returns Success(books)
            everySuspend { fixture.stageCollectionUseCase("book-1", "coll-1") } returns Success(Unit)

            val viewModel = fixture.build()
            advanceUntilIdle()

            viewModel.stageCollection("book-1", "coll-1")
            advanceUntilIdle()

            // Verify use case was called
            verifySuspend(VerifyMode.atLeast(1)) { fixture.stageCollectionUseCase("book-1", "coll-1") }
            // Verify books were reloaded (called at least twice - init + after stage)
            verifySuspend(VerifyMode.atLeast(2)) { fixture.loadInboxBooksUseCase() }
        }

    @Test
    fun `unstageCollection reloads books after success`() =
        runTest {
            val books = listOf(createInboxBook("book-1", stagedCollectionIds = listOf("coll-1")))
            val fixture = TestFixture()
            everySuspend { fixture.loadInboxBooksUseCase() } returns Success(books)
            everySuspend { fixture.unstageCollectionUseCase("book-1", "coll-1") } returns Success(Unit)

            val viewModel = fixture.build()
            advanceUntilIdle()

            viewModel.unstageCollection("book-1", "coll-1")
            advanceUntilIdle()

            verifySuspend(VerifyMode.atLeast(1)) { fixture.unstageCollectionUseCase("book-1", "coll-1") }
            verifySuspend(VerifyMode.atLeast(2)) { fixture.loadInboxBooksUseCase() }
        }

    @Test
    fun `hasSelectedBooksWithoutCollections returns true when book has no staged collections`() =
        runTest {
            val books =
                listOf(
                    createInboxBook("book-1", stagedCollectionIds = emptyList()),
                )
            val fixture = TestFixture()
            everySuspend { fixture.loadInboxBooksUseCase() } returns Success(books)

            val viewModel = fixture.build()
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
            val fixture = TestFixture()
            everySuspend { fixture.loadInboxBooksUseCase() } returns Success(books)

            val viewModel = fixture.build()
            advanceUntilIdle()

            viewModel.toggleBookSelection("book-1")

            assertFalse(viewModel.hasSelectedBooksWithoutCollections())
        }

    @Test
    fun `SSE InboxBookReleased event removes book from list`() =
        runTest {
            val books = listOf(createInboxBook("book-1"), createInboxBook("book-2"))
            val fixture = TestFixture()
            everySuspend { fixture.loadInboxBooksUseCase() } returns Success(books)

            val viewModel = fixture.build()
            advanceUntilIdle()

            assertEquals(2, viewModel.state.value.books.size)

            // Emit SSE event
            fixture.adminEvents.emit(AdminEvent.InboxBookReleased("book-1"))
            advanceUntilIdle()

            assertEquals(1, viewModel.state.value.books.size)
            assertEquals("book-2", viewModel.state.value.books[0].id)
        }

    @Test
    fun `SSE InboxBookAdded event reloads books`() =
        runTest {
            val books = listOf(createInboxBook("book-1"))
            val fixture = TestFixture()
            everySuspend { fixture.loadInboxBooksUseCase() } returns Success(books)

            val viewModel = fixture.build()
            advanceUntilIdle()

            // Initial load done
            assertFalse(viewModel.state.value.isLoading)
            assertEquals(1, viewModel.state.value.books.size)

            // Emit SSE event for new book
            fixture.adminEvents.emit(AdminEvent.InboxBookAdded(bookId = "book-2", title = "New Book"))
            advanceUntilIdle()

            // The reload should have been triggered - verify by checking use case was called twice
            verifySuspend(VerifyMode.atLeast(2)) { fixture.loadInboxBooksUseCase() }
        }

    @Test
    fun `clearError clears error state`() =
        runTest {
            val fixture = TestFixture()
            everySuspend { fixture.loadInboxBooksUseCase() } returns Failure(
                RuntimeException("Error"),
                "Error",
            )

            val viewModel = fixture.build()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.error != null)

            viewModel.clearError()

            assertNull(viewModel.state.value.error)
        }

    @Test
    fun `clearReleaseResult clears last release result`() =
        runTest {
            val books = listOf(createInboxBook("book-1"))
            val fixture = TestFixture()
            everySuspend { fixture.loadInboxBooksUseCase() } returns Success(books)
            everySuspend { fixture.releaseBooksUseCase(listOf("book-1")) } returns Success(
                InboxReleaseResult(
                    released = 1,
                    publicCount = 1,
                    toCollections = 0,
                ),
            )

            val viewModel = fixture.build()
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
            val fixture = TestFixture()
            everySuspend { fixture.loadInboxBooksUseCase() } returns Success(books)

            val viewModel = fixture.build()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.hasBooks)
        }

    @Test
    fun `hasBooks returns false when no books`() =
        runTest {
            val fixture = TestFixture()
            everySuspend { fixture.loadInboxBooksUseCase() } returns Success(emptyList())

            val viewModel = fixture.build()
            advanceUntilIdle()

            assertFalse(viewModel.state.value.hasBooks)
        }

    @Test
    fun `hasSelection returns true when books are selected`() =
        runTest {
            val books = listOf(createInboxBook("book-1"))
            val fixture = TestFixture()
            everySuspend { fixture.loadInboxBooksUseCase() } returns Success(books)

            val viewModel = fixture.build()
            advanceUntilIdle()

            assertFalse(viewModel.state.value.hasSelection)

            viewModel.toggleBookSelection("book-1")

            assertTrue(viewModel.state.value.hasSelection)
        }
}
