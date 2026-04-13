package com.calypsan.listenup.client.data.sync.sse

import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.data.local.db.ActiveSessionDao
import com.calypsan.listenup.client.data.local.db.ActivityDao
import com.calypsan.listenup.client.data.local.db.BookContributorCrossRef
import com.calypsan.listenup.client.data.local.db.BookContributorDao
import com.calypsan.listenup.client.data.local.db.BookSeriesDao
import com.calypsan.listenup.client.data.local.db.CollectionDao
import com.calypsan.listenup.client.data.local.db.GenreDao
import com.calypsan.listenup.client.data.local.db.ListenUpDatabase
import com.calypsan.listenup.client.data.local.db.ListeningEventDao
import com.calypsan.listenup.client.data.local.db.PlaybackPositionDao
import com.calypsan.listenup.client.data.local.db.RoomTransactionRunner
import com.calypsan.listenup.client.data.local.db.ShelfDao
import com.calypsan.listenup.client.data.local.db.TagDao
import com.calypsan.listenup.client.data.local.db.UserDao
import com.calypsan.listenup.client.data.local.db.UserProfileDao
import com.calypsan.listenup.client.data.local.db.UserStatsDao
import com.calypsan.listenup.client.data.remote.model.BookContributorResponse
import com.calypsan.listenup.client.data.remote.model.BookResponse
import com.calypsan.listenup.client.data.remote.model.BookSeriesInfoResponse
import com.calypsan.listenup.client.data.sync.ImageDownloaderContract
import com.calypsan.listenup.client.data.sync.SSEEventType
import com.calypsan.listenup.client.domain.repository.SessionRepository
import com.calypsan.listenup.client.download.DownloadService
import com.calypsan.listenup.client.test.db.createInMemoryTestDatabase
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Proves `SSEEventProcessor.handleBookUpdated` is atomic — when the contributor cross-ref
 * insert throws, the book row write must roll back so the DB never holds a book row
 * whose associated cross-refs are missing.
 *
 * Finding 05 D2 / W4.2. Uses a real in-memory [ListenUpDatabase] for the book DAO so
 * transaction rollback can be verified; the throwing cross-ref DAO is a mock.
 */
class SSEEventProcessorAtomicityTest {
    private val db: ListenUpDatabase = createInMemoryTestDatabase()

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun `rollback when contributor insert throws`() =
        runTest {
            val failingContributorDao: BookContributorDao = mock()
            val bookSeriesDao: BookSeriesDao = mock()
            val collectionDao: CollectionDao = mock()
            val shelfDao: ShelfDao = mock()
            val tagDao: TagDao = mock()
            val genreDao: GenreDao = mock()
            val listeningEventDao: ListeningEventDao = mock()
            val activityDao: ActivityDao = mock()
            val userDao: UserDao = mock()
            val userProfileDao: UserProfileDao = mock()
            val activeSessionDao: ActiveSessionDao = mock()
            val userStatsDao: UserStatsDao = mock()
            val playbackPositionDao: PlaybackPositionDao = mock()
            val sessionRepository: SessionRepository = mock()
            val imageDownloader: ImageDownloaderContract = mock()
            val playbackStateProvider: PlaybackStateProvider = mock()
            val downloadService: DownloadService = mock()

            everySuspend { failingContributorDao.deleteContributorsForBook(any()) } returns Unit
            everySuspend {
                failingContributorDao.insertAll(any<List<BookContributorCrossRef>>())
            } throws RuntimeException("boom — contributor insert failed")
            every { playbackStateProvider.currentBookId } returns MutableStateFlow(null)

            val bookResponse =
                BookResponse(
                    id = "book-sse-rollback",
                    title = "SSE Rollback",
                    subtitle = null,
                    coverImage = null,
                    totalDuration = 3_600_000L,
                    description = null,
                    genres = null,
                    publishYear = null,
                    seriesInfo =
                        listOf(
                            BookSeriesInfoResponse(
                                seriesId = "series-1",
                                name = "Series",
                                sequence = "1",
                            ),
                        ),
                    chapters = emptyList(),
                    audioFiles = emptyList(),
                    contributors =
                        listOf(
                            BookContributorResponse(
                                contributorId = "contrib-1",
                                name = "Author",
                                roles = listOf("author"),
                                creditedAs = null,
                            ),
                        ),
                    createdAt = "2024-01-01T00:00:00Z",
                    updatedAt = "2024-01-01T00:00:00Z",
                )

            val processor =
                SSEEventProcessor(
                    transactionRunner = RoomTransactionRunner(db),
                    bookDao = db.bookDao(),
                    bookContributorDao = failingContributorDao,
                    bookSeriesDao = bookSeriesDao,
                    collectionDao = collectionDao,
                    shelfDao = shelfDao,
                    tagDao = tagDao,
                    genreDao = genreDao,
                    listeningEventDao = listeningEventDao,
                    activityDao = activityDao,
                    userDao = userDao,
                    userProfileDao = userProfileDao,
                    activeSessionDao = activeSessionDao,
                    userStatsDao = userStatsDao,
                    playbackPositionDao = playbackPositionDao,
                    sessionRepository = sessionRepository,
                    imageDownloader = imageDownloader,
                    playbackStateProvider = playbackStateProvider,
                    downloadService = downloadService,
                    scope = CoroutineScope(TestScope(testScheduler).coroutineContext),
                )

            processor.process(SSEEventType.BookUpdated(bookResponse))

            assertEquals(
                0,
                db.bookDao().count(),
                "book row write must roll back when contributor insert fails",
            )
            assertEquals(
                0,
                db.bookSeriesDao().getSeriesForBook(BookId("book-sse-rollback")).size,
                "series rows must not have been written past the failure point",
            )
        }
}
