package com.calypsan.listenup.client.data.sync.pull

import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.Timestamp
import com.calypsan.listenup.client.data.local.db.BookContributorCrossRef
import com.calypsan.listenup.client.data.local.db.BookContributorDao
import com.calypsan.listenup.client.data.local.db.BookDao
import com.calypsan.listenup.client.data.local.db.BookEntity
import com.calypsan.listenup.client.data.local.db.BookSeriesCrossRef
import com.calypsan.listenup.client.data.local.db.BookSeriesDao
import com.calypsan.listenup.client.data.local.db.BookTagCrossRef
import com.calypsan.listenup.client.data.local.db.ChapterDao
import com.calypsan.listenup.client.data.local.db.ChapterEntity
import com.calypsan.listenup.client.data.local.db.TagDao
import com.calypsan.listenup.client.data.local.db.TagEntity
import com.calypsan.listenup.client.data.remote.SyncApiContract
import com.calypsan.listenup.client.data.remote.model.BookContributorResponse
import com.calypsan.listenup.client.data.remote.model.BookResponse
import com.calypsan.listenup.client.data.remote.model.BookSeriesInfoResponse
import com.calypsan.listenup.client.data.remote.model.ChapterResponse
import com.calypsan.listenup.client.data.remote.model.SyncBooksResponse
import com.calypsan.listenup.client.data.sync.ImageDownloaderContract
import com.calypsan.listenup.client.data.sync.conflict.ConflictDetectorContract
import com.calypsan.listenup.client.data.sync.model.SyncPhase
import com.calypsan.listenup.client.data.sync.model.SyncStatus
import dev.mokkery.answering.returns
import dev.mokkery.answering.sequentially
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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests for BookPuller.
 *
 * Tests cover:
 * - Pagination handling
 * - Progress reporting
 * - Deletion handling
 * - Conflict detection integration
 * - Book upsert
 * - Chapter syncing
 * - Relationship syncing (contributors, series)
 * - Cover downloading
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookPullerTest {
    private fun createBookResponse(
        id: String = "book-1",
        title: String = "Test Book",
        contributors: List<BookContributorResponse> = emptyList(),
        seriesInfo: List<BookSeriesInfoResponse> = emptyList(),
        chapters: List<ChapterResponse> = emptyList(),
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
            chapters = chapters,
            audioFiles = emptyList(),
            contributors = contributors,
            createdAt = "2024-01-01T00:00:00Z",
            updatedAt = "2024-01-01T00:00:00Z",
        )

    private fun createChapterResponse(
        title: String = "Chapter 1",
        startTime: Long = 0L,
        endTime: Long = 60000L,
    ): ChapterResponse =
        ChapterResponse(
            title = title,
            startTime = startTime,
            endTime = endTime,
        )

    private fun createContributorResponse(
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

    private fun createSeriesInfoResponse(
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
        val syncApi: SyncApiContract = mock()
        val bookDao: BookDao = mock()
        val chapterDao: ChapterDao = mock()
        val bookContributorDao: BookContributorDao = mock()
        val bookSeriesDao: BookSeriesDao = mock()
        val tagDao: TagDao = mock()
        val conflictDetector: ConflictDetectorContract = mock()
        val imageDownloader: ImageDownloaderContract = mock()
        val coverDownloadDao: com.calypsan.listenup.client.data.local.db.CoverDownloadDao = mock()

        init {
            // Default stubs
            everySuspend { bookDao.upsertAll(any<List<BookEntity>>()) } returns Unit
            everySuspend { bookDao.deleteByIds(any()) } returns Unit
            everySuspend { bookDao.markConflict(any(), any()) } returns Unit
            everySuspend { bookDao.getById(any()) } returns null // No existing book by default
            everySuspend { chapterDao.upsertAll(any<List<ChapterEntity>>()) } returns Unit
            everySuspend { bookContributorDao.deleteContributorsForBook(any()) } returns Unit
            everySuspend { bookContributorDao.insertAll(any<List<BookContributorCrossRef>>()) } returns Unit
            everySuspend { bookSeriesDao.deleteSeriesForBook(any()) } returns Unit
            everySuspend { bookSeriesDao.insertAll(any<List<BookSeriesCrossRef>>()) } returns Unit
            everySuspend { tagDao.upsertAll(any<List<TagEntity>>()) } returns Unit
            everySuspend { tagDao.deleteTagsForBook(any()) } returns Unit
            everySuspend { tagDao.insertAllBookTags(any<List<BookTagCrossRef>>()) } returns Unit
            everySuspend { conflictDetector.detectBookConflicts(any()) } returns emptyList()
            everySuspend { conflictDetector.shouldPreserveLocalChanges(any()) } returns false
            everySuspend { imageDownloader.deleteCover(any()) } returns Result.Success(Unit)
            everySuspend { coverDownloadDao.enqueueAll(any()) } returns Unit
        }

        fun build(): BookPuller =
            BookPuller(
                syncApi = syncApi,
                bookDao = bookDao,
                chapterDao = chapterDao,
                bookContributorDao = bookContributorDao,
                bookSeriesDao = bookSeriesDao,
                tagDao = tagDao,
                conflictDetector = conflictDetector,
                imageDownloader = imageDownloader,
                coverDownloadDao = coverDownloadDao,
            )
    }

    // ========== Basic Pull Tests ==========

    @Test
    fun `pull with empty response completes successfully`() =
        runTest {
            // Given
            val fixture = TestFixture(this)
            everySuspend { fixture.syncApi.getBooks(any(), any(), any()) } returns
                Result.Success(
                    SyncBooksResponse(
                        books = emptyList(),
                        deletedBookIds = emptyList(),
                        nextCursor = null,
                        hasMore = false,
                    ),
                )
            val puller = fixture.build()

            // When
            puller.pull(null) {}

            // Then - no upserts for empty response
            verifySuspend(VerifyMode.not) { fixture.bookDao.upsertAll(any<List<BookEntity>>()) }
        }

    @Test
    fun `pull upserts books from server`() =
        runTest {
            // Given
            val fixture = TestFixture(this)
            everySuspend { fixture.syncApi.getBooks(any(), any(), any()) } returns
                Result.Success(
                    SyncBooksResponse(
                        books = listOf(createBookResponse(id = "book-1", title = "Test Book")),
                        deletedBookIds = emptyList(),
                        nextCursor = null,
                        hasMore = false,
                    ),
                )
            val puller = fixture.build()

            // When
            puller.pull(null) {}
            advanceUntilIdle()

            // Then
            verifySuspend { fixture.bookDao.upsertAll(any<List<BookEntity>>()) }
        }

    @Test
    fun `pull throws on API failure`() =
        runTest {
            // Given
            val fixture = TestFixture(this)
            everySuspend { fixture.syncApi.getBooks(any(), any(), any()) } returns
                Result.Failure(exception = RuntimeException("API error"), message = "API error")
            val puller = fixture.build()

            // When/Then
            assertFailsWith<RuntimeException> {
                puller.pull(null) {}
            }
        }

    // ========== Pagination Tests ==========

    @Test
    fun `pull handles pagination correctly`() =
        runTest {
            // Given - two pages of results
            val fixture = TestFixture(this)
            everySuspend { fixture.syncApi.getBooks(any(), any(), any()) } sequentially {
                returns(
                    Result.Success(
                        SyncBooksResponse(
                            books = listOf(createBookResponse(id = "book-1")),
                            deletedBookIds = emptyList(),
                            nextCursor = "cursor-1",
                            hasMore = true,
                        ),
                    ),
                )
                returns(
                    Result.Success(
                        SyncBooksResponse(
                            books = listOf(createBookResponse(id = "book-2")),
                            deletedBookIds = emptyList(),
                            nextCursor = null,
                            hasMore = false,
                        ),
                    ),
                )
            }
            val puller = fixture.build()
            val progressUpdates = mutableListOf<SyncStatus>()

            // When
            puller.pull(null) { progressUpdates.add(it) }
            advanceUntilIdle()

            // Then - should have progress updates for both pages
            val bookSyncProgress =
                progressUpdates
                    .filterIsInstance<SyncStatus.Progress>()
                    .filter { it.phase == SyncPhase.SYNCING_BOOKS }
            assertEquals(2, bookSyncProgress.size)
        }

    // ========== Deletion Tests ==========

    @Test
    fun `pull handles book deletions`() =
        runTest {
            // Given
            val fixture = TestFixture(this)
            everySuspend { fixture.syncApi.getBooks(any(), any(), any()) } returns
                Result.Success(
                    SyncBooksResponse(
                        books = emptyList(),
                        deletedBookIds = listOf("deleted-1", "deleted-2"),
                        nextCursor = null,
                        hasMore = false,
                    ),
                )
            val puller = fixture.build()

            // When
            puller.pull(null) {}

            // Then
            verifySuspend { fixture.bookDao.deleteByIds(any()) }
        }

    // ========== Conflict Detection Tests ==========

    @Test
    fun `pull detects and marks conflicts`() =
        runTest {
            // Given
            val fixture = TestFixture(this)
            val serverTimestamp = Timestamp(5000L)
            everySuspend { fixture.syncApi.getBooks(any(), any(), any()) } returns
                Result.Success(
                    SyncBooksResponse(
                        books = listOf(createBookResponse(id = "book-1")),
                        deletedBookIds = emptyList(),
                        nextCursor = null,
                        hasMore = false,
                    ),
                )
            everySuspend { fixture.conflictDetector.detectBookConflicts(any()) } returns
                listOf(BookId("book-1") to serverTimestamp)
            val puller = fixture.build()

            // When
            puller.pull(null) {}
            advanceUntilIdle()

            // Then
            verifySuspend { fixture.bookDao.markConflict(BookId("book-1"), serverTimestamp) }
        }

    @Test
    fun `pull skips books with newer local changes`() =
        runTest {
            // Given
            val fixture = TestFixture(this)
            everySuspend { fixture.syncApi.getBooks(any(), any(), any()) } returns
                Result.Success(
                    SyncBooksResponse(
                        books = listOf(createBookResponse(id = "book-1")),
                        deletedBookIds = emptyList(),
                        nextCursor = null,
                        hasMore = false,
                    ),
                )
            // All books should be preserved (local is newer)
            everySuspend { fixture.conflictDetector.shouldPreserveLocalChanges(any()) } returns true
            val puller = fixture.build()

            // When
            puller.pull(null) {}
            advanceUntilIdle()

            // Then - no upsert because local changes are newer
            verifySuspend(VerifyMode.not) { fixture.bookDao.upsertAll(any<List<BookEntity>>()) }
        }

    // ========== Chapter Sync Tests ==========

    @Test
    fun `pull syncs chapters for books`() =
        runTest {
            // Given
            val fixture = TestFixture(this)
            everySuspend { fixture.syncApi.getBooks(any(), any(), any()) } returns
                Result.Success(
                    SyncBooksResponse(
                        books =
                            listOf(
                                createBookResponse(
                                    id = "book-1",
                                    chapters =
                                        listOf(
                                            createChapterResponse(title = "Chapter 1"),
                                            createChapterResponse(title = "Chapter 2"),
                                        ),
                                ),
                            ),
                        deletedBookIds = emptyList(),
                        nextCursor = null,
                        hasMore = false,
                    ),
                )
            val puller = fixture.build()

            // When
            puller.pull(null) {}
            advanceUntilIdle()

            // Then
            verifySuspend { fixture.chapterDao.upsertAll(any<List<ChapterEntity>>()) }
        }

    // ========== Contributor Relationship Tests ==========

    @Test
    fun `pull syncs book contributors`() =
        runTest {
            // Given
            val fixture = TestFixture(this)
            everySuspend { fixture.syncApi.getBooks(any(), any(), any()) } returns
                Result.Success(
                    SyncBooksResponse(
                        books =
                            listOf(
                                createBookResponse(
                                    id = "book-1",
                                    contributors =
                                        listOf(
                                            createContributorResponse(id = "author-1", roles = listOf("author")),
                                        ),
                                ),
                            ),
                        deletedBookIds = emptyList(),
                        nextCursor = null,
                        hasMore = false,
                    ),
                )
            val puller = fixture.build()

            // When
            puller.pull(null) {}
            advanceUntilIdle()

            // Then - delete old, insert new
            verifySuspend { fixture.bookContributorDao.deleteContributorsForBook(BookId("book-1")) }
            verifySuspend { fixture.bookContributorDao.insertAll(any<List<BookContributorCrossRef>>()) }
        }

    // ========== Series Relationship Tests ==========

    @Test
    fun `pull syncs book series relationships`() =
        runTest {
            // Given
            val fixture = TestFixture(this)
            everySuspend { fixture.syncApi.getBooks(any(), any(), any()) } returns
                Result.Success(
                    SyncBooksResponse(
                        books =
                            listOf(
                                createBookResponse(
                                    id = "book-1",
                                    seriesInfo =
                                        listOf(
                                            createSeriesInfoResponse(seriesId = "series-1"),
                                        ),
                                ),
                            ),
                        deletedBookIds = emptyList(),
                        nextCursor = null,
                        hasMore = false,
                    ),
                )
            val puller = fixture.build()

            // When
            puller.pull(null) {}
            advanceUntilIdle()

            // Then - delete old, insert new
            verifySuspend { fixture.bookSeriesDao.deleteSeriesForBook(BookId("book-1")) }
            verifySuspend { fixture.bookSeriesDao.insertAll(any<List<BookSeriesCrossRef>>()) }
        }

    // ========== Cover Download Tests ==========

    @Test
    fun `pull triggers cover downloads in background`() =
        runTest {
            // Given
            val fixture = TestFixture(this)
            everySuspend { fixture.syncApi.getBooks(any(), any(), any()) } returns
                Result.Success(
                    SyncBooksResponse(
                        books = listOf(createBookResponse(id = "book-1")),
                        deletedBookIds = emptyList(),
                        nextCursor = null,
                        hasMore = false,
                    ),
                )
            val puller = fixture.build()

            // When
            puller.pull(null) {}
            advanceUntilIdle()

            // Then
            verifySuspend { fixture.coverDownloadDao.enqueueAll(any()) }
        }

    // ========== Progress Reporting Tests ==========

    @Test
    fun `pull reports progress for each page`() =
        runTest {
            // Given
            val fixture = TestFixture(this)
            everySuspend { fixture.syncApi.getBooks(any(), any(), any()) } returns
                Result.Success(
                    SyncBooksResponse(
                        books = listOf(createBookResponse(id = "book-1")),
                        deletedBookIds = emptyList(),
                        nextCursor = null,
                        hasMore = false,
                    ),
                )
            val puller = fixture.build()
            val progressUpdates = mutableListOf<SyncStatus>()

            // When
            puller.pull(null) { progressUpdates.add(it) }
            advanceUntilIdle()

            // Then
            assertTrue(progressUpdates.isNotEmpty())
            val bookProgress = progressUpdates.first { it is SyncStatus.Progress }
            assertEquals(SyncPhase.SYNCING_BOOKS, (bookProgress as SyncStatus.Progress).phase)
        }

    // ========== Delta Sync Tests ==========

    @Test
    fun `pull passes updatedAfter for delta sync`() =
        runTest {
            // Given
            val fixture = TestFixture(this)
            val timestamp = "2024-01-01T00:00:00Z"
            everySuspend { fixture.syncApi.getBooks(any(), any(), timestamp) } returns
                Result.Success(
                    SyncBooksResponse(
                        books = emptyList(),
                        deletedBookIds = emptyList(),
                        nextCursor = null,
                        hasMore = false,
                    ),
                )
            val puller = fixture.build()

            // When
            puller.pull(timestamp) {}

            // Then - API called with timestamp
            verifySuspend { fixture.syncApi.getBooks(any(), any(), timestamp) }
        }

    @Test
    fun `pull passes null for full sync`() =
        runTest {
            // Given
            val fixture = TestFixture(this)
            everySuspend { fixture.syncApi.getBooks(any(), any(), null) } returns
                Result.Success(
                    SyncBooksResponse(
                        books = emptyList(),
                        deletedBookIds = emptyList(),
                        nextCursor = null,
                        hasMore = false,
                    ),
                )
            val puller = fixture.build()

            // When
            puller.pull(null) {}

            // Then - API called with null (full sync)
            verifySuspend { fixture.syncApi.getBooks(any(), any(), null) }
        }
}
