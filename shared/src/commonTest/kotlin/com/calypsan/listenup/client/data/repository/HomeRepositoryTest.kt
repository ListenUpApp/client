package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.core.Timestamp
import com.calypsan.listenup.client.data.local.db.PlaybackPositionDao
import com.calypsan.listenup.client.data.local.db.PlaybackPositionEntity
import com.calypsan.listenup.client.data.remote.SyncApiContract
import com.calypsan.listenup.client.domain.model.Book
import com.calypsan.listenup.client.domain.model.BookContributor
import com.calypsan.listenup.client.domain.repository.BookRepository
import com.calypsan.listenup.client.domain.repository.NetworkMonitor
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Tests for HomeRepository.
 *
 * @OptIn(ExperimentalTime::class) needed for kotlin.time.Instant usage.
 *
 * Tests cover:
 * - Continue listening list generation
 * - Progress calculation
 * - Filtering of completed books
 *
 * Uses Mokkery for mocking BookRepositoryContract and DAOs.
 */
@OptIn(ExperimentalTime::class)
class HomeRepositoryTest {
    // ========== Test Fixtures ==========

    private class TestFixture {
        val bookRepository: BookRepository = mock()
        val playbackPositionDao: PlaybackPositionDao = mock()
        val syncApi: SyncApiContract = mock()
        val networkMonitor: NetworkMonitor = mock()

        fun build(): HomeRepositoryImpl =
            HomeRepositoryImpl(
                bookRepository = bookRepository,
                playbackPositionDao = playbackPositionDao,
                syncApi = syncApi,
                networkMonitor = networkMonitor,
            )
    }

    private fun createFixture(): TestFixture {
        val fixture = TestFixture()

        // Default stubs
        everySuspend { fixture.playbackPositionDao.getRecentPositions(any()) } returns emptyList()
        everySuspend { fixture.playbackPositionDao.get(any()) } returns null
        everySuspend { fixture.playbackPositionDao.save(any()) } returns Unit
        everySuspend { fixture.bookRepository.getBook(any()) } returns null
        // Default: offline mode - tests local fallback behavior
        every { fixture.networkMonitor.isOnline() } returns false
        everySuspend { fixture.syncApi.getContinueListening(any()) } returns Failure(Exception("Offline"))

        return fixture
    }

    // ========== Test Data Factories ==========

    private fun createPlaybackPosition(
        bookId: String,
        positionMs: Long,
        updatedAt: Long = Clock.System.now().toEpochMilliseconds(),
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
            authors = listOf(BookContributor(id = "author-1", name = authorNames)),
            narrators = emptyList(),
            duration = duration,
            coverPath = coverPath,
            addedAt = Timestamp(1704067200000L),
            updatedAt = Timestamp(1704067200000L),
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
            val success = assertIs<Success<*>>(result)
            assertTrue((success.data as List<*>).isEmpty())
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
            val success = assertIs<Success<*>>(result)
            val books = success.data as List<*>
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
            val success = assertIs<Success<*>>(result)
            val books = success.data as List<*>
            assertEquals(1, books.size)
        }

    @Test
    fun `getContinueListening filters out completed books at 99 percent or more`() =
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
            val success = assertIs<Success<*>>(result)
            val books = success.data as List<*>
            assertTrue(books.isEmpty())
        }

    @Test
    fun `getContinueListening includes books at 98 percent progress`() =
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
            val success = assertIs<Success<*>>(result)
            val books = success.data as List<*>
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
            val success = assertIs<Success<*>>(result)
            val books = success.data as List<*>
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
            val success = assertIs<Success<*>>(result)
            val books = success.data as List<*>
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
            val success = assertIs<Success<*>>(result)
            val books = success.data as List<*>
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
            val success = assertIs<Success<*>>(result)
            val returnedBooks = success.data as List<*>
            assertEquals(5, returnedBooks.size)
        }

    // ========== Regression Test: lastPlayedAt must be ISO 8601 ==========

    @Test
    fun `getContinueListening returns lastPlayedAt as ISO 8601 timestamp`() =
        runTest {
            // Given: A position with specific lastPlayedAt timestamp
            // Issue #3: lastPlayedAt was being returned as raw epoch milliseconds
            // instead of ISO 8601 format, breaking UI display
            val fixture = createFixture()
            // Jan 1, 2024 12:00:00 UTC = 1704110400000L
            val lastPlayedAtMs = 1704110400000L
            val position =
                PlaybackPositionEntity(
                    bookId = BookId("book-1"),
                    positionMs = 5000L,
                    playbackSpeed = 1.0f,
                    updatedAt = lastPlayedAtMs - 1000L, // Entity update time (different)
                    lastPlayedAt = lastPlayedAtMs, // Actual last play time
                )
            val book = createBook(id = "book-1", duration = 10_000L)

            everySuspend { fixture.playbackPositionDao.getRecentPositions(10) } returns listOf(position)
            everySuspend { fixture.bookRepository.getBook("book-1") } returns book
            val repository = fixture.build()

            // When
            val result = repository.getContinueListening(10)

            // Then: lastPlayedAt should be ISO 8601, not raw milliseconds
            val success = assertIs<Success<*>>(result)
            val books = success.data as List<*>
            assertEquals(1, books.size)
            val continueBook = books[0] as com.calypsan.listenup.client.domain.model.ContinueListeningBook
            // Should be ISO 8601 format, not "1704110400000"
            assertTrue(
                continueBook.lastPlayedAt.contains("2024-01-01"),
                "Expected ISO 8601 timestamp containing '2024-01-01', got: ${continueBook.lastPlayedAt}",
            )
            assertTrue(
                continueBook.lastPlayedAt.contains("T"),
                "Expected ISO 8601 timestamp with 'T' separator, got: ${continueBook.lastPlayedAt}",
            )
        }

    @Test
    fun `getContinueListening uses updatedAt as fallback when lastPlayedAt is null`() =
        runTest {
            // Given: A position without lastPlayedAt (legacy data before migration)
            val fixture = createFixture()
            val updatedAtMs = 1704110400000L // Jan 1, 2024 12:00:00 UTC
            val position =
                PlaybackPositionEntity(
                    bookId = BookId("book-1"),
                    positionMs = 5000L,
                    playbackSpeed = 1.0f,
                    updatedAt = updatedAtMs,
                    lastPlayedAt = null, // Not set (legacy data)
                )
            val book = createBook(id = "book-1", duration = 10_000L)

            everySuspend { fixture.playbackPositionDao.getRecentPositions(10) } returns listOf(position)
            everySuspend { fixture.bookRepository.getBook("book-1") } returns book
            val repository = fixture.build()

            // When
            val result = repository.getContinueListening(10)

            // Then: Should fall back to updatedAt, still as ISO 8601
            val success = assertIs<Success<*>>(result)
            val books = success.data as List<*>
            val continueBook = books[0] as com.calypsan.listenup.client.domain.model.ContinueListeningBook
            assertTrue(
                continueBook.lastPlayedAt.contains("2024-01-01"),
                "Expected ISO 8601 from updatedAt fallback, got: ${continueBook.lastPlayedAt}",
            )
        }

}
