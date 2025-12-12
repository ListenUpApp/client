package com.calypsan.listenup.client.data.sync

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.data.local.db.BookContributorDao
import com.calypsan.listenup.client.data.local.db.BookDao
import com.calypsan.listenup.client.data.local.db.BookEntity
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.db.BookSeriesDao
import com.calypsan.listenup.client.data.local.db.ChapterDao
import com.calypsan.listenup.client.data.local.db.ContributorDao
import com.calypsan.listenup.client.data.local.db.SeriesDao
import com.calypsan.listenup.client.data.local.db.SyncDao
import com.calypsan.listenup.client.data.local.db.SyncMetadataEntity
import com.calypsan.listenup.client.data.local.db.SyncState
import com.calypsan.listenup.client.data.local.db.Timestamp
import com.calypsan.listenup.client.data.remote.SyncApiContract
import com.calypsan.listenup.client.data.remote.model.BookResponse
import com.calypsan.listenup.client.data.remote.model.SyncBooksResponse
import com.calypsan.listenup.client.data.remote.model.SyncContributorsResponse
import com.calypsan.listenup.client.data.remote.model.SyncSeriesResponse
import dev.mokkery.answering.returns
import dev.mokkery.answering.sequentially
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for SyncManager.
 *
 * Tests cover:
 * - Sync happy path and state transitions
 * - Retry logic with exponential backoff
 * - Delta sync vs full sync
 * - Error handling and cancellation
 * - forceFullSync behavior
 * - Pagination handling
 *
 * Uses Mokkery for mocking interfaces (SyncApiContract, DAOs, and sync contracts).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncManagerTest {
    private val testDispatcher = StandardTestDispatcher()

    // ========== Test Fixtures ==========

    private class TestFixture(
        private val scope: CoroutineScope,
    ) {
        val syncApi: SyncApiContract = mock()
        val bookDao: BookDao = mock()
        val seriesDao: SeriesDao = mock()
        val contributorDao: ContributorDao = mock()
        val chapterDao: ChapterDao = mock()
        val bookContributorDao: BookContributorDao = mock()
        val bookSeriesDao: BookSeriesDao = mock()
        val syncDao: SyncDao = mock()
        val imageDownloader: ImageDownloaderContract = mock()
        val sseManager: SSEManagerContract = mock()
        val ftsPopulator: FtsPopulatorContract = mock()

        // Fake SSE event flow for SyncManager's init block
        val sseEventFlow = MutableSharedFlow<SSEEventType>()

        init {
            // Default stubs for SSE manager
            every { sseManager.eventFlow } returns sseEventFlow
            every { sseManager.connect() } returns Unit
        }

        fun build(): SyncManager =
            SyncManager(
                syncApi = syncApi,
                bookDao = bookDao,
                seriesDao = seriesDao,
                contributorDao = contributorDao,
                chapterDao = chapterDao,
                bookContributorDao = bookContributorDao,
                bookSeriesDao = bookSeriesDao,
                syncDao = syncDao,
                imageDownloader = imageDownloader,
                sseManager = sseManager,
                ftsPopulator = ftsPopulator,
                scope = scope,
            )
    }

    private fun TestScope.createFixture(): TestFixture {
        // Use backgroundScope so SyncManager's init coroutine doesn't block test completion
        val fixture = TestFixture(backgroundScope)
        stubSuccessfulSync(fixture)
        return fixture
    }

    private fun stubSuccessfulSync(fixture: TestFixture) {
        // SyncDao - no previous sync (full sync)
        everySuspend { fixture.syncDao.getValue(SyncDao.KEY_LAST_SYNC_BOOKS) } returns null
        everySuspend { fixture.syncDao.upsert(any<SyncMetadataEntity>()) } returns Unit

        // Books API - empty response (no books)
        everySuspend { fixture.syncApi.getBooks(any(), any(), any()) } returns
            Result.Success(
                SyncBooksResponse(
                    books = emptyList(),
                    deletedBookIds = emptyList(),
                    nextCursor = null,
                    hasMore = false,
                ),
            )

        // Series API - empty response
        everySuspend { fixture.syncApi.getSeries(any(), any(), any()) } returns
            Result.Success(
                SyncSeriesResponse(
                    series = emptyList(),
                    deletedSeriesIds = emptyList(),
                    nextCursor = null,
                    hasMore = false,
                ),
            )

        // Contributors API - empty response
        everySuspend { fixture.syncApi.getContributors(any(), any(), any()) } returns
            Result.Success(
                SyncContributorsResponse(
                    contributors = emptyList(),
                    deletedContributorIds = emptyList(),
                    nextCursor = null,
                    hasMore = false,
                ),
            )

        // FTS populator
        everySuspend { fixture.ftsPopulator.rebuildAll() } returns Unit

        // Image downloader
        everySuspend { fixture.imageDownloader.downloadCovers(any()) } returns Result.Success(emptyList())
    }

    // ========== Test Data Factories ==========

    private fun createBookResponse(
        id: String = "book-1",
        title: String = "Test Book",
        duration: Long = 3_600_000L,
    ): BookResponse =
        BookResponse(
            id = id,
            title = title,
            subtitle = null,
            coverImage = null,
            totalDuration = duration,
            description = null,
            genres = null,
            seriesId = null,
            seriesName = null,
            sequence = null,
            publishYear = null,
            audioFiles = emptyList(),
            chapters = emptyList(),
            contributors = emptyList(),
            createdAt = "2024-01-01T00:00:00Z",
            updatedAt = "2024-01-01T00:00:00Z",
        )

    private fun createBookEntity(
        id: String = "book-1",
        title: String = "Test Book",
        syncState: SyncState = SyncState.SYNCED,
        lastModified: Timestamp = Timestamp(1000L),
        serverVersion: Timestamp = Timestamp(1000L),
    ): BookEntity =
        BookEntity(
            id = BookId(id),
            title = title,
            subtitle = null,
            coverUrl = null,
            totalDuration = 3_600_000L,
            description = null,
            genres = null,
            publishYear = null,
            audioFilesJson = null,
            syncState = syncState,
            lastModified = lastModified,
            serverVersion = serverVersion,
            createdAt = Timestamp(1000L),
            updatedAt = Timestamp(2000L),
        )

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ========== Sync Happy Path Tests ==========

    @Test
    fun `sync returns success when all operations complete`() =
        runTest {
            // Given
            val fixture = createFixture()
            val syncManager = fixture.build()
            advanceUntilIdle()

            // When
            val result = syncManager.sync()
            advanceUntilIdle()

            // Then
            assertIs<Result.Success<Unit>>(result)
        }

    @Test
    fun `sync transitions state from Idle to Syncing to Success`() =
        runTest {
            // Given
            val fixture = createFixture()
            val syncManager = fixture.build()
            advanceUntilIdle()

            // Initial state should be Idle
            assertEquals(SyncStatus.Idle, syncManager.syncState.value)

            // When
            syncManager.sync()
            advanceUntilIdle()

            // Then - final state should be Success
            assertIs<SyncStatus.Success>(syncManager.syncState.value)
        }

    @Test
    fun `sync updates last sync time on success`() =
        runTest {
            // Given
            val fixture = createFixture()
            val syncManager = fixture.build()
            advanceUntilIdle()

            // When
            syncManager.sync()
            advanceUntilIdle()

            // Then
            verifySuspend { fixture.syncDao.upsert(any<SyncMetadataEntity>()) }
        }

    @Test
    fun `sync rebuilds FTS tables on success`() =
        runTest {
            // Given
            val fixture = createFixture()
            val syncManager = fixture.build()
            advanceUntilIdle()

            // When
            syncManager.sync()
            advanceUntilIdle()

            // Then
            verifySuspend { fixture.ftsPopulator.rebuildAll() }
        }

    @Test
    fun `sync connects to SSE stream on success`() =
        runTest {
            // Given
            val fixture = createFixture()
            val syncManager = fixture.build()
            advanceUntilIdle()

            // When
            syncManager.sync()
            advanceUntilIdle()

            // Then
            verify { fixture.sseManager.connect() }
        }

    @Test
    fun `sync continues when FTS rebuild fails`() =
        runTest {
            // Given - FTS rebuild throws exception
            val fixture = createFixture()
            everySuspend { fixture.ftsPopulator.rebuildAll() } throws RuntimeException("FTS error")
            val syncManager = fixture.build()
            advanceUntilIdle()

            // When
            val result = syncManager.sync()
            advanceUntilIdle()

            // Then - Sync should still succeed (FTS is non-fatal)
            assertIs<Result.Success<Unit>>(result)
            assertIs<SyncStatus.Success>(syncManager.syncState.value)
        }

    // ========== Error Handling Tests ==========

    @Test
    fun `sync returns failure when API fails after retries`() =
        runTest {
            // Given
            val fixture = createFixture()
            val error = RuntimeException("Network error")
            everySuspend { fixture.syncApi.getBooks(any(), any(), any()) } returns Result.Failure(error)
            val syncManager = fixture.build()
            advanceUntilIdle()

            // When
            val result = syncManager.sync()
            // Advance time for retries (1s + 2s + buffer)
            advanceTimeBy(10.seconds)
            advanceUntilIdle()

            // Then
            assertIs<Result.Failure>(result)
            assertEquals("Network error", result.exception.message)
        }

    @Test
    fun `sync transitions to Error state on failure`() =
        runTest {
            // Given
            val fixture = createFixture()
            val error = RuntimeException("Network error")
            everySuspend { fixture.syncApi.getBooks(any(), any(), any()) } returns Result.Failure(error)
            val syncManager = fixture.build()
            advanceUntilIdle()

            // When
            syncManager.sync()
            advanceTimeBy(10.seconds)
            advanceUntilIdle()

            // Then
            val state = syncManager.syncState.value
            assertIs<SyncStatus.Error>(state)
            assertEquals("Network error", state.exception.message)
        }

    // ========== Cancellation Tests ==========

    @Test
    fun `sync rethrows CancellationException`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.syncApi.getBooks(any(), any(), any()) } throws CancellationException("Cancelled")
            val syncManager = fixture.build()
            advanceUntilIdle()

            // When/Then
            var caughtCancellation = false
            try {
                syncManager.sync()
            } catch (e: CancellationException) {
                caughtCancellation = true
            }
            advanceUntilIdle()

            assertTrue(caughtCancellation)
        }

    @Test
    fun `sync transitions to Idle on cancellation`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.syncApi.getBooks(any(), any(), any()) } throws CancellationException("Cancelled")
            val syncManager = fixture.build()
            advanceUntilIdle()

            // When
            try {
                syncManager.sync()
            } catch (_: CancellationException) {
                // Expected
            }
            advanceUntilIdle()

            // Then
            assertEquals(SyncStatus.Idle, syncManager.syncState.value)
        }

    // ========== Retry Logic Tests ==========

    @Test
    fun `sync succeeds on retry after transient failure`() =
        runTest {
            // Given - First call fails, second succeeds
            val fixture = createFixture()
            everySuspend { fixture.syncApi.getBooks(any(), any(), any()) } sequentially {
                returns(Result.Failure(RuntimeException("Transient error")))
                returns(
                    Result.Success(
                        SyncBooksResponse(
                            books = emptyList(),
                            deletedBookIds = emptyList(),
                            nextCursor = null,
                            hasMore = false,
                        ),
                    ),
                )
            }
            val syncManager = fixture.build()
            advanceUntilIdle()

            // When
            syncManager.sync()
            advanceTimeBy(5.seconds)
            advanceUntilIdle()

            // Then
            assertIs<SyncStatus.Success>(syncManager.syncState.value)
        }

    // ========== Delta Sync vs Full Sync Tests ==========

    @Test
    fun `sync performs full sync when no last sync time`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.syncDao.getValue(SyncDao.KEY_LAST_SYNC_BOOKS) } returns null
            val syncManager = fixture.build()
            advanceUntilIdle()

            // When
            syncManager.sync()
            advanceUntilIdle()

            // Then - API should be called with null updatedAfter
            verifySuspend { fixture.syncApi.getBooks(limit = 100, cursor = null, updatedAfter = null) }
        }

    @Test
    fun `sync performs delta sync when last sync time exists`() =
        runTest {
            // Given
            val lastSyncTimeMs = "1704067200000" // Milliseconds as stored in DB
            val fixture = createFixture()
            everySuspend { fixture.syncDao.getValue(SyncDao.KEY_LAST_SYNC_BOOKS) } returns lastSyncTimeMs
            val syncManager = fixture.build()
            advanceUntilIdle()

            // When
            syncManager.sync()
            advanceUntilIdle()

            // Then - API should be called with updatedAfter timestamp
            // The timestamp is converted via Timestamp.toIsoString()
            verifySuspend { fixture.syncApi.getBooks(limit = 100, cursor = null, updatedAfter = any()) }
        }

    // ========== forceFullSync Tests ==========

    @Test
    fun `forceFullSync clears last sync time before syncing`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.syncDao.delete(SyncDao.KEY_LAST_SYNC_BOOKS) } returns Unit
            val syncManager = fixture.build()
            advanceUntilIdle()

            // When
            syncManager.forceFullSync()
            advanceUntilIdle()

            // Then
            verifySuspend { fixture.syncDao.delete(SyncDao.KEY_LAST_SYNC_BOOKS) }
        }

    @Test
    fun `forceFullSync returns sync result`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.syncDao.delete(SyncDao.KEY_LAST_SYNC_BOOKS) } returns Unit
            val syncManager = fixture.build()
            advanceUntilIdle()

            // When
            val result = syncManager.forceFullSync()
            advanceUntilIdle()

            // Then
            assertIs<Result.Success<Unit>>(result)
        }

    // ========== Pagination Tests ==========

    @Test
    fun `sync handles pagination for books`() =
        runTest {
            // Given - Two pages of books
            val fixture = createFixture()
            everySuspend { fixture.syncApi.getBooks(any(), any(), any()) } sequentially {
                // First page
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
                // Second page
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

            // Stub book DAO operations
            everySuspend { fixture.bookDao.getById(any()) } returns null
            everySuspend { fixture.bookDao.upsertAll(any()) } returns Unit
            everySuspend { fixture.chapterDao.upsertAll(any()) } returns Unit
            everySuspend { fixture.bookContributorDao.deleteContributorsForBook(any()) } returns Unit
            everySuspend { fixture.bookContributorDao.insertAll(any()) } returns Unit
            everySuspend { fixture.bookSeriesDao.deleteSeriesForBook(any()) } returns Unit
            everySuspend { fixture.bookSeriesDao.insertAll(any()) } returns Unit

            val syncManager = fixture.build()
            advanceUntilIdle()

            // When
            val result = syncManager.sync()
            advanceUntilIdle()

            // Then - Should have completed successfully after processing both pages
            assertIs<Result.Success<Unit>>(result)
        }

    // ========== Deletion Tests ==========

    @Test
    fun `sync handles book deletions from server`() =
        runTest {
            // Given - Server returns deleted book IDs
            val fixture = createFixture()
            everySuspend { fixture.syncApi.getBooks(any(), any(), any()) } returns
                Result.Success(
                    SyncBooksResponse(
                        books = emptyList(),
                        deletedBookIds = listOf("deleted-1", "deleted-2"),
                        nextCursor = null,
                        hasMore = false,
                    ),
                )
            everySuspend { fixture.bookDao.deleteByIds(any()) } returns Unit
            val syncManager = fixture.build()
            advanceUntilIdle()

            // When
            syncManager.sync()
            advanceUntilIdle()

            // Then
            verifySuspend {
                fixture.bookDao.deleteByIds(listOf(BookId("deleted-1"), BookId("deleted-2")))
            }
        }

    @Test
    fun `sync handles series deletions from server`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.syncApi.getSeries(any(), any(), any()) } returns
                Result.Success(
                    SyncSeriesResponse(
                        series = emptyList(),
                        deletedSeriesIds = listOf("deleted-series-1"),
                        nextCursor = null,
                        hasMore = false,
                    ),
                )
            everySuspend { fixture.seriesDao.deleteByIds(any()) } returns Unit
            val syncManager = fixture.build()
            advanceUntilIdle()

            // When
            syncManager.sync()
            advanceUntilIdle()

            // Then
            verifySuspend { fixture.seriesDao.deleteByIds(listOf("deleted-series-1")) }
        }

    @Test
    fun `sync handles contributor deletions from server`() =
        runTest {
            // Given
            val fixture = createFixture()
            everySuspend { fixture.syncApi.getContributors(any(), any(), any()) } returns
                Result.Success(
                    SyncContributorsResponse(
                        contributors = emptyList(),
                        deletedContributorIds = listOf("deleted-contrib-1"),
                        nextCursor = null,
                        hasMore = false,
                    ),
                )
            everySuspend { fixture.contributorDao.deleteByIds(any()) } returns Unit
            val syncManager = fixture.build()
            advanceUntilIdle()

            // When
            syncManager.sync()
            advanceUntilIdle()

            // Then
            verifySuspend { fixture.contributorDao.deleteByIds(listOf("deleted-contrib-1")) }
        }

    // ========== Conflict Detection Tests ==========

    @Test
    fun `sync marks conflict when server has newer version than local unsynced changes`() =
        runTest {
            // Given - Local book is NOT_SYNCED and server has newer version
            val fixture = createFixture()
            val localBook =
                createBookEntity(
                    id = "book-1",
                    syncState = SyncState.NOT_SYNCED,
                    lastModified = Timestamp(1000L),
                    serverVersion = Timestamp(1000L),
                )
            val serverBook = createBookResponse(id = "book-1")

            everySuspend { fixture.syncApi.getBooks(any(), any(), any()) } returns
                Result.Success(
                    SyncBooksResponse(
                        books = listOf(serverBook),
                        deletedBookIds = emptyList(),
                        nextCursor = null,
                        hasMore = false,
                    ),
                )
            everySuspend { fixture.bookDao.getById(BookId("book-1")) } returns localBook
            everySuspend { fixture.bookDao.markConflict(any(), any()) } returns Unit
            everySuspend { fixture.bookDao.upsertAll(any()) } returns Unit
            everySuspend { fixture.chapterDao.upsertAll(any()) } returns Unit
            everySuspend { fixture.bookContributorDao.deleteContributorsForBook(any()) } returns Unit
            everySuspend { fixture.bookContributorDao.insertAll(any()) } returns Unit

            val syncManager = fixture.build()
            advanceUntilIdle()

            // When
            syncManager.sync()
            advanceUntilIdle()

            // Then - Should mark conflict
            verifySuspend { fixture.bookDao.markConflict(BookId("book-1"), any()) }
        }
}
