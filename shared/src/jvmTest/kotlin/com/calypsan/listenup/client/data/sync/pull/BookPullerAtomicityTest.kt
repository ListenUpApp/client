package com.calypsan.listenup.client.data.sync.pull

import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.local.db.CoverDownloadDao
import com.calypsan.listenup.client.data.local.db.GenreEntity
import com.calypsan.listenup.client.data.local.db.ListenUpDatabase
import com.calypsan.listenup.client.data.local.db.RoomTransactionRunner
import com.calypsan.listenup.client.data.remote.SyncApiContract
import com.calypsan.listenup.client.data.remote.model.AudioFileResponse
import com.calypsan.listenup.client.data.remote.model.BookContributorResponse
import com.calypsan.listenup.client.data.remote.model.BookResponse
import com.calypsan.listenup.client.data.remote.model.BookSeriesInfoResponse
import com.calypsan.listenup.client.data.remote.model.ChapterResponse
import com.calypsan.listenup.client.data.remote.model.SyncBooksResponse
import com.calypsan.listenup.client.data.sync.ImageDownloaderContract
import com.calypsan.listenup.client.data.sync.conflict.ConflictDetectorContract
import com.calypsan.listenup.client.test.db.createInMemoryTestDatabase
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Proves `BookPuller.processServerBooks` is atomic — when a late write step (cover enqueue)
 * fails, earlier writes to books/chapters/book_contributors/book_series must roll back so
 * the DB never holds a partial book.
 *
 * Per Finding 05 D2 of the architecture audit. See W4.2 in the restoration roadmap.
 *
 * Uses a real in-memory [ListenUpDatabase] so transaction semantics are exercised end-to-end;
 * fakes cannot prove rollback. `CoverDownloadDao` is mocked to throw, forcing the failure
 * after all other writes have landed.
 */
class BookPullerAtomicityTest {
    private val db: ListenUpDatabase = createInMemoryTestDatabase()

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun `rollback when cover enqueue throws mid-page`() =
        runTest {
            val syncApi: SyncApiContract = mock()
            val conflictDetector: ConflictDetectorContract = mock()
            val imageDownloader: ImageDownloaderContract = mock()
            val failingCoverDownloadDao: CoverDownloadDao = mock()

            val bookResponse =
                BookResponse(
                    id = "book-rollback",
                    title = "Rollback Test",
                    subtitle = null,
                    coverImage = null,
                    totalDuration = 3_600_000L,
                    description = null,
                    genres = listOf("Fantasy"),
                    publishYear = null,
                    seriesInfo =
                        listOf(
                            BookSeriesInfoResponse(
                                seriesId = "series-1",
                                name = "Series",
                                sequence = "1",
                            ),
                        ),
                    chapters =
                        listOf(
                            ChapterResponse(
                                title = "Ch 1",
                                startTime = 0L,
                                endTime = 1_000L,
                            ),
                        ),
                    audioFiles =
                        listOf(
                            AudioFileResponse(
                                id = "af-rollback-1",
                                filename = "chapter01.m4b",
                                format = "m4b",
                                codec = "aac",
                                duration = 1_800_000L,
                                size = 45_000_000L,
                            ),
                        ),
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

            everySuspend { syncApi.getBooks(any(), any(), any()) } returns
                Success(
                    SyncBooksResponse(
                        books = listOf(bookResponse),
                        deletedBookIds = emptyList(),
                        nextCursor = null,
                        hasMore = false,
                    ),
                )
            everySuspend { conflictDetector.detectBookConflicts(any()) } returns emptyList()
            everySuspend { conflictDetector.shouldPreserveLocalChanges(any()) } returns false
            everySuspend { imageDownloader.deleteCover(any()) } returns Success(Unit)
            everySuspend { failingCoverDownloadDao.enqueueAll(any()) } throws
                RuntimeException("boom — cover enqueue failed")

            // Seed the genre catalog so name resolution succeeds for the book in this test
            db.genreDao().upsertAll(
                listOf(
                    GenreEntity(
                        id = "g-fantasy",
                        name = "Fantasy",
                        slug = "fantasy",
                        path = "/fantasy",
                        bookCount = 0,
                        parentId = null,
                        depth = 0,
                        sortOrder = 0,
                    ),
                ),
            )

            val bookRelationshipWriter =
                BookRelationshipWriter(
                    bookContributorDao = db.bookContributorDao(),
                    bookSeriesDao = db.bookSeriesDao(),
                    tagDao = db.tagDao(),
                    genreDao = db.genreDao(),
                    audioFileDao = db.audioFileDao(),
                )

            val puller =
                BookPuller(
                    transactionRunner = RoomTransactionRunner(db),
                    syncApi = syncApi,
                    bookDao = db.bookDao(),
                    chapterDao = db.chapterDao(),
                    genreDao = db.genreDao(),
                    bookRelationshipWriter = bookRelationshipWriter,
                    conflictDetector = conflictDetector,
                    imageDownloader = imageDownloader,
                    coverDownloadDao = failingCoverDownloadDao,
                )

            assertFailsWith<RuntimeException> { puller.pull(null) {} }

            val bookId = BookId("book-rollback")
            assertEquals(0, db.bookDao().count(), "book write must roll back")
            assertEquals(
                0,
                db.chapterDao().getChaptersForBook(bookId).size,
                "chapter write must roll back",
            )
            assertEquals(
                0,
                db.bookContributorDao().getByContributorId("contrib-1").size,
                "contributor cross-ref write must roll back",
            )
            assertEquals(
                0,
                db.bookSeriesDao().getSeriesForBook(bookId).size,
                "series cross-ref write must roll back",
            )
            assertEquals(
                0,
                db.genreDao().getGenresForBook(bookId).size,
                "genre cross-ref write must roll back",
            )
            assertEquals(
                0,
                db.audioFileDao().getForBook("book-rollback").size,
                "audio_files rows must roll back when cover enqueue throws",
            )
        }
}
