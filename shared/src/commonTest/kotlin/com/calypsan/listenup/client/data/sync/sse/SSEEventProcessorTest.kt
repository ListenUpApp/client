package com.calypsan.listenup.client.data.sync.sse

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.data.local.db.BookContributorCrossRef
import com.calypsan.listenup.client.data.local.db.BookContributorDao
import com.calypsan.listenup.client.data.local.db.BookDao
import com.calypsan.listenup.client.data.local.db.BookEntity
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.db.BookSeriesCrossRef
import com.calypsan.listenup.client.data.local.db.BookSeriesDao
import com.calypsan.listenup.client.data.remote.model.BookContributorResponse
import com.calypsan.listenup.client.data.remote.model.BookResponse
import com.calypsan.listenup.client.data.remote.model.BookSeriesInfoResponse
import com.calypsan.listenup.client.data.sync.ImageDownloaderContract
import com.calypsan.listenup.client.data.sync.SSEEventType
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Tests for SSEEventProcessor.
 *
 * Tests cover:
 * - BookCreated event handling
 * - BookUpdated event handling
 * - BookDeleted event handling
 * - Contributor relationship saving
 * - Series relationship saving
 * - Cover download triggering
 * - Error handling
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SSEEventProcessorTest {

    private fun createBookResponse(
        id: String = "book-1",
        title: String = "Test Book",
        contributors: List<BookContributorResponse> = emptyList(),
        seriesInfo: List<BookSeriesInfoResponse> = emptyList(),
    ): BookResponse =
        BookResponse(
            id = id,
            title = title,
            subtitle = null,
            coverImage = null,
            totalDuration = 3_600_000L,
            description = null,
            genres = null,
            publishYear = null,
            seriesInfo = seriesInfo,
            chapters = emptyList(),
            audioFiles = emptyList(),
            contributors = contributors,
            createdAt = "2024-01-01T00:00:00Z",
            updatedAt = "2024-01-01T00:00:00Z",
        )

    private fun createContributor(
        id: String = "contrib-1",
        name: String = "Test Author",
        roles: List<String> = listOf("author"),
    ): BookContributorResponse =
        BookContributorResponse(
            contributorId = id,
            name = name,
            roles = roles,
            creditedAs = null,
        )

    private fun createSeriesInfo(
        seriesId: String = "series-1",
        name: String = "Test Series",
        sequence: String? = "1",
    ): BookSeriesInfoResponse =
        BookSeriesInfoResponse(
            seriesId = seriesId,
            name = name,
            sequence = sequence,
        )

    private class TestFixture(
        val scope: TestScope,
    ) {
        val bookDao: BookDao = mock()
        val bookContributorDao: BookContributorDao = mock()
        val bookSeriesDao: BookSeriesDao = mock()
        val imageDownloader: ImageDownloaderContract = mock()

        init {
            // Default stubs
            everySuspend { bookDao.upsert(any<BookEntity>()) } returns Unit
            everySuspend { bookDao.deleteById(any()) } returns Unit
            everySuspend { bookDao.touchUpdatedAt(any(), any()) } returns Unit
            everySuspend { bookContributorDao.deleteContributorsForBook(any()) } returns Unit
            everySuspend { bookContributorDao.insertAll(any<List<BookContributorCrossRef>>()) } returns Unit
            everySuspend { bookSeriesDao.deleteSeriesForBook(any()) } returns Unit
            everySuspend { bookSeriesDao.insertAll(any<List<BookSeriesCrossRef>>()) } returns Unit
            everySuspend { imageDownloader.downloadCover(any()) } returns Result.Success(false)
        }

        fun build(): SSEEventProcessor =
            SSEEventProcessor(
                bookDao = bookDao,
                bookContributorDao = bookContributorDao,
                bookSeriesDao = bookSeriesDao,
                imageDownloader = imageDownloader,
                scope = scope,
            )
    }

    // ========== BookCreated Tests ==========

    @Test
    fun `BookCreated event upserts book to database`() = runTest {
        // Given
        val fixture = TestFixture(this)
        val processor = fixture.build()
        val bookResponse = createBookResponse(id = "book-1", title = "New Book")

        // When
        processor.process(SSEEventType.BookCreated(bookResponse))
        advanceUntilIdle()

        // Then
        verifySuspend { fixture.bookDao.upsert(any<BookEntity>()) }
    }

    @Test
    fun `BookCreated event saves contributor relationships`() = runTest {
        // Given
        val fixture = TestFixture(this)
        val processor = fixture.build()
        val bookResponse = createBookResponse(
            id = "book-1",
            contributors = listOf(
                createContributor(id = "author-1", name = "Jane Doe", roles = listOf("author")),
                createContributor(id = "narrator-1", name = "John Smith", roles = listOf("narrator")),
            ),
        )

        // When
        processor.process(SSEEventType.BookCreated(bookResponse))
        advanceUntilIdle()

        // Then - should delete old and insert new relationships
        verifySuspend { fixture.bookContributorDao.deleteContributorsForBook(BookId("book-1")) }
        verifySuspend { fixture.bookContributorDao.insertAll(any<List<BookContributorCrossRef>>()) }
    }

    @Test
    fun `BookCreated event saves series relationships`() = runTest {
        // Given
        val fixture = TestFixture(this)
        val processor = fixture.build()
        val bookResponse = createBookResponse(
            id = "book-1",
            seriesInfo = listOf(
                createSeriesInfo(seriesId = "series-1", name = "Epic Fantasy", sequence = "1"),
            ),
        )

        // When
        processor.process(SSEEventType.BookCreated(bookResponse))
        advanceUntilIdle()

        // Then - should delete old and insert new relationships
        verifySuspend { fixture.bookSeriesDao.deleteSeriesForBook(BookId("book-1")) }
        verifySuspend { fixture.bookSeriesDao.insertAll(any<List<BookSeriesCrossRef>>()) }
    }

    @Test
    fun `BookCreated event triggers cover download`() = runTest {
        // Given
        val fixture = TestFixture(this)
        val processor = fixture.build()
        val bookResponse = createBookResponse(id = "book-1")

        // When
        processor.process(SSEEventType.BookCreated(bookResponse))
        advanceUntilIdle()

        // Then
        verifySuspend { fixture.imageDownloader.downloadCover(BookId("book-1")) }
    }

    @Test
    fun `BookCreated event with no contributors skips contributor insert`() = runTest {
        // Given
        val fixture = TestFixture(this)
        val processor = fixture.build()
        val bookResponse = createBookResponse(id = "book-1", contributors = emptyList())

        // When
        processor.process(SSEEventType.BookCreated(bookResponse))
        advanceUntilIdle()

        // Then - should delete but NOT insert (empty list)
        verifySuspend { fixture.bookContributorDao.deleteContributorsForBook(BookId("book-1")) }
        verifySuspend(VerifyMode.not) { fixture.bookContributorDao.insertAll(any<List<BookContributorCrossRef>>()) }
    }

    // ========== BookUpdated Tests ==========

    @Test
    fun `BookUpdated event upserts book to database`() = runTest {
        // Given
        val fixture = TestFixture(this)
        val processor = fixture.build()
        val bookResponse = createBookResponse(id = "book-1", title = "Updated Title")

        // When
        processor.process(SSEEventType.BookUpdated(bookResponse))
        advanceUntilIdle()

        // Then
        verifySuspend { fixture.bookDao.upsert(any<BookEntity>()) }
    }

    @Test
    fun `BookUpdated event replaces contributor relationships`() = runTest {
        // Given
        val fixture = TestFixture(this)
        val processor = fixture.build()
        val bookResponse = createBookResponse(
            id = "book-1",
            contributors = listOf(createContributor(id = "new-author")),
        )

        // When
        processor.process(SSEEventType.BookUpdated(bookResponse))
        advanceUntilIdle()

        // Then - old relationships deleted, new ones inserted
        verifySuspend { fixture.bookContributorDao.deleteContributorsForBook(BookId("book-1")) }
        verifySuspend { fixture.bookContributorDao.insertAll(any<List<BookContributorCrossRef>>()) }
    }

    @Test
    fun `BookUpdated event triggers cover download`() = runTest {
        // Given
        val fixture = TestFixture(this)
        val processor = fixture.build()
        val bookResponse = createBookResponse(id = "book-1")

        // When
        processor.process(SSEEventType.BookUpdated(bookResponse))
        advanceUntilIdle()

        // Then
        verifySuspend { fixture.imageDownloader.downloadCover(BookId("book-1")) }
    }

    // ========== BookDeleted Tests ==========

    @Test
    fun `BookDeleted event deletes book from database`() = runTest {
        // Given
        val fixture = TestFixture(this)
        val processor = fixture.build()

        // When
        processor.process(SSEEventType.BookDeleted(bookId = "book-1", deletedAt = "2024-01-01T00:00:00Z"))
        advanceUntilIdle()

        // Then
        verifySuspend { fixture.bookDao.deleteById(BookId("book-1")) }
    }

    @Test
    fun `BookDeleted event does not trigger cover download`() = runTest {
        // Given
        val fixture = TestFixture(this)
        val processor = fixture.build()

        // When
        processor.process(SSEEventType.BookDeleted(bookId = "book-1", deletedAt = "2024-01-01T00:00:00Z"))
        advanceUntilIdle()

        // Then - no cover download for deleted books
        verifySuspend(VerifyMode.not) { fixture.imageDownloader.downloadCover(any()) }
    }

    // ========== ScanStarted / ScanCompleted Tests ==========

    @Test
    fun `ScanStarted event does not modify database`() = runTest {
        // Given
        val fixture = TestFixture(this)
        val processor = fixture.build()

        // When
        processor.process(SSEEventType.ScanStarted(libraryId = "lib-1", startedAt = "2024-01-01T00:00:00Z"))
        advanceUntilIdle()

        // Then - no database operations
        verifySuspend(VerifyMode.not) { fixture.bookDao.upsert(any<BookEntity>()) }
        verifySuspend(VerifyMode.not) { fixture.bookDao.deleteById(any()) }
    }

    @Test
    fun `ScanCompleted event does not modify database`() = runTest {
        // Given
        val fixture = TestFixture(this)
        val processor = fixture.build()

        // When
        processor.process(
            SSEEventType.ScanCompleted(
                libraryId = "lib-1",
                booksAdded = 5,
                booksUpdated = 3,
                booksRemoved = 1,
            ),
        )
        advanceUntilIdle()

        // Then - no database operations (just logging)
        verifySuspend(VerifyMode.not) { fixture.bookDao.upsert(any<BookEntity>()) }
        verifySuspend(VerifyMode.not) { fixture.bookDao.deleteById(any()) }
    }

    // ========== Heartbeat Tests ==========

    @Test
    fun `Heartbeat event does nothing`() = runTest {
        // Given
        val fixture = TestFixture(this)
        val processor = fixture.build()

        // When
        processor.process(SSEEventType.Heartbeat)
        advanceUntilIdle()

        // Then - no operations
        verifySuspend(VerifyMode.not) { fixture.bookDao.upsert(any<BookEntity>()) }
        verifySuspend(VerifyMode.not) { fixture.bookDao.deleteById(any()) }
        verifySuspend(VerifyMode.not) { fixture.imageDownloader.downloadCover(any()) }
    }

    // ========== Cover Download Success Tests ==========

    @Test
    fun `successful cover download triggers book touch for UI refresh`() = runTest {
        // Given
        val fixture = TestFixture(this)
        everySuspend { fixture.imageDownloader.downloadCover(any()) } returns Result.Success(true)
        val processor = fixture.build()
        val bookResponse = createBookResponse(id = "book-1")

        // When
        processor.process(SSEEventType.BookCreated(bookResponse))
        advanceUntilIdle()

        // Then - book's updatedAt is touched to trigger UI refresh
        verifySuspend { fixture.bookDao.touchUpdatedAt(BookId("book-1"), any()) }
    }

    @Test
    fun `failed cover download does not touch book`() = runTest {
        // Given
        val fixture = TestFixture(this)
        everySuspend { fixture.imageDownloader.downloadCover(any()) } returns
            Result.Failure(exception = Exception("Network error"), message = "Failed")
        val processor = fixture.build()
        val bookResponse = createBookResponse(id = "book-1")

        // When
        processor.process(SSEEventType.BookCreated(bookResponse))
        advanceUntilIdle()

        // Then - book not touched on failure
        verifySuspend(VerifyMode.not) { fixture.bookDao.touchUpdatedAt(any(), any()) }
    }

    // ========== Error Handling Tests ==========

    @Test
    fun `exception during processing does not crash`() = runTest {
        // Given
        val fixture = TestFixture(this)
        everySuspend { fixture.bookDao.upsert(any<BookEntity>()) } throws RuntimeException("Database error")
        val processor = fixture.build()
        val bookResponse = createBookResponse(id = "book-1")

        // When - should not throw
        processor.process(SSEEventType.BookCreated(bookResponse))
        advanceUntilIdle()

        // Then - no exception thrown (error is logged)
    }

    // ========== Multiple Roles Tests ==========

    @Test
    fun `contributor with multiple roles creates multiple cross refs`() = runTest {
        // Given
        val fixture = TestFixture(this)
        val processor = fixture.build()
        val bookResponse = createBookResponse(
            id = "book-1",
            contributors = listOf(
                // Single person with multiple roles
                createContributor(
                    id = "person-1",
                    name = "Multi-Talented",
                    roles = listOf("author", "narrator"),
                ),
            ),
        )

        // When
        processor.process(SSEEventType.BookCreated(bookResponse))
        advanceUntilIdle()

        // Then - insertAll is called (with 2 cross refs for 2 roles)
        verifySuspend { fixture.bookContributorDao.insertAll(any<List<BookContributorCrossRef>>()) }
    }
}
