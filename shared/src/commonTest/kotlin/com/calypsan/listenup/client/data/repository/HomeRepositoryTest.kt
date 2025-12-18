package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.db.PlaybackPositionDao
import com.calypsan.listenup.client.data.local.db.PlaybackPositionEntity
import com.calypsan.listenup.client.data.local.db.Timestamp
import com.calypsan.listenup.client.data.local.db.UserDao
import com.calypsan.listenup.client.data.local.db.UserEntity
import com.calypsan.listenup.client.data.remote.SyncApiContract
import com.calypsan.listenup.client.domain.model.Book
import com.calypsan.listenup.client.domain.model.Contributor
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for HomeRepository.
 *
 * Tests cover:
 * - Continue listening list generation
 * - Progress calculation
 * - Filtering of completed books
 * - User observation
 *
 * Uses Mokkery for mocking BookRepositoryContract and DAOs.
 */
class HomeRepositoryTest {
    // ========== Test Fixtures ==========

    private class TestFixture {
        val bookRepository: BookRepositoryContract = mock()
        val playbackPositionDao: PlaybackPositionDao = mock()
        val syncApi: SyncApiContract = mock()
        val userDao: UserDao = mock()

        val userFlow = MutableStateFlow<UserEntity?>(null)

        fun build(): HomeRepository =
            HomeRepository(
                bookRepository = bookRepository,
                playbackPositionDao = playbackPositionDao,
                syncApi = syncApi,
                userDao = userDao,
            )
    }

    private fun createFixture(): TestFixture {
        val fixture = TestFixture()

        // Default stubs
        everySuspend { fixture.playbackPositionDao.getRecentPositions(any()) } returns emptyList()
        everySuspend { fixture.playbackPositionDao.get(any()) } returns null
        everySuspend { fixture.playbackPositionDao.save(any()) } returns Unit
        everySuspend { fixture.bookRepository.getBook(any()) } returns null
        everySuspend { fixture.syncApi.getContinueListening(any()) } returns Success(emptyList())
        every { fixture.userDao.observeCurrentUser() } returns fixture.userFlow

        return fixture
    }

    // ========== Test Data Factories ==========

    private fun createPlaybackPosition(
        bookId: String,
        positionMs: Long,
        updatedAt: Long = System.currentTimeMillis(),
    ): PlaybackPositionEntity =
        PlaybackPositionEntity(
            bookId = BookId(bookId),
            positionMs = positionMs,
            playbackSpeed = 1.0f,
            updatedAt = updatedAt,
        )

    private fun createBook(
        id: String = "book-1",
        title: String = "Test Book",
        duration: Long = 10_000L,
        authorNames: String = "Author Name",
        coverPath: String? = null,
    ): Book =
        Book(
            id = BookId(id),
            title = title,
            subtitle = null,
            authors = listOf(Contributor(id = "author-1", name = authorNames)),
            narrators = emptyList(),
            duration = duration,
            coverPath = coverPath,
            addedAt = Timestamp(1704067200000L),
            updatedAt = Timestamp(1704067200000L),
        )

    private fun createUserEntity(
        id: String = "user-1",
        displayName: String = "John Smith",
    ): UserEntity =
        UserEntity(
            id = id,
            email = "john@example.com",
            displayName = displayName,
            isRoot = false,
            createdAt = 1704067200000L,
            updatedAt = 1704067200000L,
        )

    // ========== Continue Listening Tests ==========

    @Test
    fun `getContinueListening returns empty list when no positions`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.playbackPositionDao.getRecentPositions(10) } returns emptyList()
            val repository = fixture.build()

            // When
            val result = repository.getContinueListening(10)

            // Then
            assertIs<Success<*>>(result)
            assertTrue((result.data as List<*>).isEmpty())
        }

    @Test
    fun `getContinueListening returns books with correct progress`() =
        runTest {
            // Given
            val fixture = createFixture()
            val position = createPlaybackPosition("book-1", positionMs = 5000L)
            val book = createBook(id = "book-1", title = "Test Book", duration = 10_000L)

            everySuspend { fixture.playbackPositionDao.getRecentPositions(10) } returns listOf(position)
            everySuspend { fixture.bookRepository.getBook("book-1") } returns book
            val repository = fixture.build()

            // When
            val result = repository.getContinueListening(10)

            // Then
            assertIs<Success<*>>(result)
            val books = result.data as List<*>
            assertEquals(1, books.size)
            val continueBook = books[0] as com.calypsan.listenup.client.domain.model.ContinueListeningBook
            assertEquals("book-1", continueBook.bookId)
            assertEquals("Test Book", continueBook.title)
            assertEquals(0.5f, continueBook.progress)
            assertEquals(5000L, continueBook.currentPositionMs)
            assertEquals(10_000L, continueBook.totalDurationMs)
        }

    @Test
    fun `getContinueListening filters out books not found in repository`() =
        runTest {
            // Given
            val fixture = createFixture()
            val position1 = createPlaybackPosition("book-1", positionMs = 5000L)
            val position2 = createPlaybackPosition("book-2", positionMs = 3000L)
            val book1 = createBook(id = "book-1", title = "Book One", duration = 10_000L)

            everySuspend { fixture.playbackPositionDao.getRecentPositions(10) } returns listOf(position1, position2)
            everySuspend { fixture.bookRepository.getBook("book-1") } returns book1
            everySuspend { fixture.bookRepository.getBook("book-2") } returns null // Not found
            val repository = fixture.build()

            // When
            val result = repository.getContinueListening(10)

            // Then
            assertIs<Success<*>>(result)
            val books = result.data as List<*>
            assertEquals(1, books.size)
        }

    @Test
    fun `getContinueListening filters out completed books (99%+)`() =
        runTest {
            // Given
            val fixture = createFixture()
            val almostDone = createPlaybackPosition("book-1", positionMs = 9900L) // 99%
            val book = createBook(id = "book-1", duration = 10_000L)

            everySuspend { fixture.playbackPositionDao.getRecentPositions(10) } returns listOf(almostDone)
            everySuspend { fixture.bookRepository.getBook("book-1") } returns book
            val repository = fixture.build()

            // When
            val result = repository.getContinueListening(10)

            // Then
            assertIs<Success<*>>(result)
            val books = result.data as List<*>
            assertTrue(books.isEmpty())
        }

    @Test
    fun `getContinueListening includes books at 98% progress`() =
        runTest {
            // Given
            val fixture = createFixture()
            val position = createPlaybackPosition("book-1", positionMs = 9800L) // 98%
            val book = createBook(id = "book-1", duration = 10_000L)

            everySuspend { fixture.playbackPositionDao.getRecentPositions(10) } returns listOf(position)
            everySuspend { fixture.bookRepository.getBook("book-1") } returns book
            val repository = fixture.build()

            // When
            val result = repository.getContinueListening(10)

            // Then
            assertIs<Success<*>>(result)
            val books = result.data as List<*>
            assertEquals(1, books.size)
        }

    @Test
    fun `getContinueListening handles zero duration book`() =
        runTest {
            // Given
            val fixture = createFixture()
            val position = createPlaybackPosition("book-1", positionMs = 5000L)
            val book = createBook(id = "book-1", duration = 0L) // Zero duration

            everySuspend { fixture.playbackPositionDao.getRecentPositions(10) } returns listOf(position)
            everySuspend { fixture.bookRepository.getBook("book-1") } returns book
            val repository = fixture.build()

            // When
            val result = repository.getContinueListening(10)

            // Then - progress should be 0, book not filtered (progress < 0.99)
            assertIs<Success<*>>(result)
            val books = result.data as List<*>
            assertEquals(1, books.size)
            val continueBook = books[0] as com.calypsan.listenup.client.domain.model.ContinueListeningBook
            assertEquals(0f, continueBook.progress)
        }

    @Test
    fun `getContinueListening includes author names`() =
        runTest {
            // Given
            val fixture = createFixture()
            val position = createPlaybackPosition("book-1", positionMs = 5000L)
            val book = createBook(id = "book-1", duration = 10_000L, authorNames = "Stephen King")

            everySuspend { fixture.playbackPositionDao.getRecentPositions(10) } returns listOf(position)
            everySuspend { fixture.bookRepository.getBook("book-1") } returns book
            val repository = fixture.build()

            // When
            val result = repository.getContinueListening(10)

            // Then
            assertIs<Success<*>>(result)
            val books = result.data as List<*>
            val continueBook = books[0] as com.calypsan.listenup.client.domain.model.ContinueListeningBook
            assertEquals("Stephen King", continueBook.authorNames)
        }

    @Test
    fun `getContinueListening includes cover path`() =
        runTest {
            // Given
            val fixture = createFixture()
            val position = createPlaybackPosition("book-1", positionMs = 5000L)
            val book = createBook(id = "book-1", duration = 10_000L, coverPath = "/path/to/cover.jpg")

            everySuspend { fixture.playbackPositionDao.getRecentPositions(10) } returns listOf(position)
            everySuspend { fixture.bookRepository.getBook("book-1") } returns book
            val repository = fixture.build()

            // When
            val result = repository.getContinueListening(10)

            // Then
            assertIs<Success<*>>(result)
            val books = result.data as List<*>
            val continueBook = books[0] as com.calypsan.listenup.client.domain.model.ContinueListeningBook
            assertEquals("/path/to/cover.jpg", continueBook.coverPath)
        }

    @Test
    fun `getContinueListening respects limit parameter`() =
        runTest {
            // Given
            val fixture = createFixture()
            val positions = (1..20).map { createPlaybackPosition("book-$it", positionMs = 5000L) }
            val books = (1..20).map { createBook(id = "book-$it", duration = 10_000L) }

            everySuspend { fixture.playbackPositionDao.getRecentPositions(5) } returns positions.take(5)
            positions.take(5).forEachIndexed { index, _ ->
                everySuspend { fixture.bookRepository.getBook("book-${index + 1}") } returns books[index]
            }
            val repository = fixture.build()

            // When
            val result = repository.getContinueListening(5)

            // Then
            assertIs<Success<*>>(result)
            val returnedBooks = result.data as List<*>
            assertEquals(5, returnedBooks.size)
        }

    // ========== User Observation Tests ==========

    @Test
    fun `observeCurrentUser returns user from userDao`() =
        runTest {
            // Given
            val fixture = createFixture()
            val user = createUserEntity(displayName = "John Doe")
            fixture.userFlow.value = user
            val repository = fixture.build()

            // When
            val result = repository.observeCurrentUser().first()

            // Then
            assertEquals("John Doe", result?.displayName)
        }

    @Test
    fun `observeCurrentUser returns null when no user`() =
        runTest {
            // Given
            val fixture = createFixture()
            fixture.userFlow.value = null
            val repository = fixture.build()

            // When
            val result = repository.observeCurrentUser().first()

            // Then
            assertNull(result)
        }
}
