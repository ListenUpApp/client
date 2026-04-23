package com.calypsan.listenup.client.data.sync.sse

import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.data.local.db.ActiveSessionDao
import com.calypsan.listenup.client.data.local.db.ActivityDao
import com.calypsan.listenup.client.data.local.db.BookContributorDao
import com.calypsan.listenup.client.data.local.db.BookSeriesDao
import com.calypsan.listenup.client.data.local.db.CollectionDao
import com.calypsan.listenup.client.data.local.db.GenreDao
import com.calypsan.listenup.client.data.local.db.ListenUpDatabase
import com.calypsan.listenup.client.data.local.db.ListeningEventDao
import com.calypsan.listenup.client.data.local.db.PlaybackPositionEntity
import com.calypsan.listenup.client.data.local.db.RoomTransactionRunner
import com.calypsan.listenup.client.data.local.db.ShelfDao
import com.calypsan.listenup.client.data.local.db.TagDao
import com.calypsan.listenup.client.data.local.db.UserDao
import com.calypsan.listenup.client.data.local.db.UserProfileDao
import com.calypsan.listenup.client.data.local.db.UserStatsDao
import com.calypsan.listenup.client.data.sync.ImageDownloaderContract
import com.calypsan.listenup.client.data.sync.ProgressPayload
import com.calypsan.listenup.client.data.sync.SSEChannelMessage
import com.calypsan.listenup.client.data.sync.SSEEvent
import com.calypsan.listenup.client.data.sync.SessionDaos
import com.calypsan.listenup.client.data.sync.UserDaos
import com.calypsan.listenup.client.data.sync.sse.BookRelationshipDaos
import com.calypsan.listenup.client.domain.repository.AvatarDownloadRepository
import com.calypsan.listenup.client.domain.repository.CoverDownloadRepository
import com.calypsan.listenup.client.domain.repository.SessionRepository
import com.calypsan.listenup.client.download.DownloadService
import com.calypsan.listenup.client.test.db.createInMemoryTestDatabase
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Regression coverage for Bug 6 (playback position sync flaky ~10%) — Finding 07 D5.
 *
 * Verifies that [SSEEventProcessor.handleProgressUpdated] applies stateful merges
 * over the existing Room row and never silently wipes fields the SSE event doesn't
 * carry. Pre-SP1 the handler constructed a fresh [PlaybackPositionEntity], defaulting
 * `finishedAt`/`startedAt` to null/0 on every cross-device echo — data loss.
 *
 * The Phase B fix preserves `existing.finishedAt` and `existing.startedAt` when the
 * event doesn't carry them, and overwrites them only when the event provides a value
 * (post-SP1 server always provides these; the null-coalesce protects against pre-SP1
 * echoes during rollout).
 */
class SSEEventProcessorProgressMergeTest {
    private val db: ListenUpDatabase = createInMemoryTestDatabase()
    private val playbackPositionDao = db.playbackPositionDao()

    private val noOpCoverDownloadRepository =
        object : CoverDownloadRepository {
            override fun queueCoverDownload(bookId: BookId) = Unit
        }

    private val noOpAvatarDownloadRepository =
        object : AvatarDownloadRepository {
            override fun queueAvatarDownload(userId: String) = Unit

            override fun queueAvatarForceRefresh(userId: String) = Unit

            override suspend fun deleteAvatar(userId: String) = Unit
        }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private fun TestScope.createProcessor(): SSEEventProcessor {
        val bookContributorDao: BookContributorDao = mock()
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
        val sessionRepository: SessionRepository = mock()
        val imageDownloader: ImageDownloaderContract = mock()
        val playbackStateProvider: PlaybackStateProvider = mock()
        val downloadService: DownloadService = mock()

        // The "currently playing" short-circuit must not fire for these tests.
        every { playbackStateProvider.currentBookId } returns MutableStateFlow(null)

        return SSEEventProcessor(
            transactionRunner = RoomTransactionRunner(db),
            bookDao = db.bookDao(),
            bookRelationshipDaos =
                BookRelationshipDaos(
                    bookContributorDao = bookContributorDao,
                    bookSeriesDao = bookSeriesDao,
                    tagDao = tagDao,
                    genreDao = genreDao,
                    audioFileDao = db.audioFileDao(),
                ),
            collectionDao = collectionDao,
            shelfDao = shelfDao,
            userDaos =
                UserDaos(
                    userDao = userDao,
                    userProfileDao = userProfileDao,
                    userStatsDao = userStatsDao,
                ),
            sessionDaos =
                SessionDaos(
                    activeSessionDao = activeSessionDao,
                    listeningEventDao = listeningEventDao,
                    playbackPositionDao = playbackPositionDao,
                ),
            sseExternalServices =
                SSEExternalServices(
                    sessionRepository = sessionRepository,
                    imageDownloader = imageDownloader,
                    playbackStateProvider = playbackStateProvider,
                    downloadService = downloadService,
                ),
            activityDao = activityDao,
            coverDownloadRepository = noOpCoverDownloadRepository,
            avatarDownloadRepository = noOpAvatarDownloadRepository,
        )
    }

    @Test
    fun `handleProgressUpdated preserves finishedAt when event does not carry it (pre-SP1 echo)`() =
        runTest {
            val processor = createProcessor()
            val bookId = BookId("b1")
            val preservedFinishedAt = 1_700_000_000_000L

            // Pre-seed an entity that's locally marked finished.
            playbackPositionDao.save(
                PlaybackPositionEntity(
                    bookId = bookId,
                    positionMs = 5000L,
                    playbackSpeed = 1.2f,
                    hasCustomSpeed = true,
                    updatedAt = 1_700_000_000_000L,
                    lastPlayedAt = 1_700_000_000_000L,
                    isFinished = true,
                    finishedAt = preservedFinishedAt,
                    startedAt = 1_600_000_000_000L,
                ),
            )

            // Simulate a pre-SP1 progress echo: no finishedAt, no startedAt on the wire.
            val event =
                SSEEvent.ProgressUpdated(
                    timestamp = "2026-04-18T10:00:00Z",
                    data =
                        ProgressPayload(
                            bookId = "b1",
                            currentPositionMs = 6000L,
                            progress = 0.55,
                            totalListenTimeMs = 3000L,
                            isFinished = true,
                            lastPlayedAt = "2026-04-18T10:00:00Z",
                            startedAt = null,
                            finishedAt = null,
                        ),
                )

            processor.process(SSEChannelMessage.Wire(event))

            val merged = playbackPositionDao.get(bookId)
            assertNotNull(merged)
            assertEquals(6000L, merged.positionMs)
            assertEquals(preservedFinishedAt, merged.finishedAt) // THE KEY ASSERTION: NOT wiped.
            assertEquals(1_600_000_000_000L, merged.startedAt) // Also preserved.
            assertEquals(1.2f, merged.playbackSpeed) // .copy() preserves this automatically.
            assertEquals(true, merged.hasCustomSpeed) // Ditto.
        }

    @Test
    fun `handleProgressUpdated overwrites finishedAt when event carries a value (post-SP1 echo)`() =
        runTest {
            val processor = createProcessor()
            val bookId = BookId("b1")

            playbackPositionDao.save(
                PlaybackPositionEntity(
                    bookId = bookId,
                    positionMs = 5000L,
                    playbackSpeed = 1.0f,
                    hasCustomSpeed = false,
                    updatedAt = 1_700_000_000_000L,
                    lastPlayedAt = 1_700_000_000_000L,
                    isFinished = false,
                    finishedAt = null,
                    startedAt = null,
                ),
            )

            val event =
                SSEEvent.ProgressUpdated(
                    timestamp = "2026-04-18T10:00:00Z",
                    data =
                        ProgressPayload(
                            bookId = "b1",
                            currentPositionMs = 10_000L,
                            progress = 1.0,
                            totalListenTimeMs = 5000L,
                            isFinished = true,
                            lastPlayedAt = "2026-04-18T11:00:00Z",
                            startedAt = "2026-04-18T09:00:00Z",
                            finishedAt = "2026-04-18T11:00:00Z",
                        ),
                )

            processor.process(SSEChannelMessage.Wire(event))

            val merged = playbackPositionDao.get(bookId)
            assertNotNull(merged)
            assertNotNull(merged.finishedAt) // NOW set from event.
            assertNotNull(merged.startedAt) // NOW set from event.
            assertEquals(true, merged.isFinished)
        }

    @Test
    fun `handleProgressUpdated creates fresh entity when existing row is null`() =
        runTest {
            val processor = createProcessor()
            val bookId = BookId("b2")

            // No pre-seed for b2.
            val event =
                SSEEvent.ProgressUpdated(
                    timestamp = "2026-04-18T10:00:00Z",
                    data =
                        ProgressPayload(
                            bookId = "b2",
                            currentPositionMs = 1234L,
                            progress = 0.1,
                            totalListenTimeMs = 500L,
                            isFinished = false,
                            lastPlayedAt = "2026-04-18T10:00:00Z",
                            startedAt = "2026-04-18T09:00:00Z",
                            finishedAt = null,
                        ),
                )

            processor.process(SSEChannelMessage.Wire(event))

            val saved = playbackPositionDao.get(bookId)
            assertNotNull(saved)
            assertEquals(1234L, saved.positionMs)
            assertEquals(1.0f, saved.playbackSpeed) // default.
            assertEquals(false, saved.hasCustomSpeed) // default.
            assertNull(saved.finishedAt) // event carried null.
            assertNotNull(saved.startedAt) // event carried a value.
        }

    @Test
    fun `handleProgressUpdated skips when local lastPlayedAt is newer`() =
        runTest {
            val processor = createProcessor()
            val bookId = BookId("b1")
            val localLastPlayedAt = 1_800_000_000_000L

            playbackPositionDao.save(
                PlaybackPositionEntity(
                    bookId = bookId,
                    positionMs = 50_000L,
                    playbackSpeed = 1.0f,
                    hasCustomSpeed = false,
                    updatedAt = localLastPlayedAt,
                    lastPlayedAt = localLastPlayedAt,
                    isFinished = false,
                    finishedAt = null,
                    startedAt = null,
                ),
            )

            // Event's lastPlayedAt is older than local.
            val event =
                SSEEvent.ProgressUpdated(
                    timestamp = "2025-01-01T00:00:00Z",
                    data =
                        ProgressPayload(
                            bookId = "b1",
                            currentPositionMs = 1000L,
                            progress = 0.01,
                            totalListenTimeMs = 100L,
                            isFinished = false,
                            lastPlayedAt = "2025-01-01T00:00:00Z",
                            startedAt = null,
                            finishedAt = null,
                        ),
                )

            processor.process(SSEChannelMessage.Wire(event))

            val unchanged = playbackPositionDao.get(bookId)
            assertNotNull(unchanged)
            assertEquals(50_000L, unchanged.positionMs) // NOT overwritten.
        }

    @Test
    fun `handleProgressUpdated skips row when last_played_at is malformed`() =
        runTest {
            val processor = createProcessor()
            val bookId = BookId("book-malformed-ts")

            val event =
                SSEEvent.ProgressUpdated(
                    timestamp = "2026-04-19T10:00:00Z",
                    data =
                        ProgressPayload(
                            bookId = bookId.value,
                            currentPositionMs = 1000L,
                            progress = 0.01,
                            totalListenTimeMs = 100L,
                            isFinished = false,
                            lastPlayedAt = "not-a-valid-timestamp",
                            startedAt = null,
                            finishedAt = null,
                        ),
                )

            processor.process(SSEChannelMessage.Wire(event))

            assertNull(
                playbackPositionDao.get(bookId),
                "malformed timestamp should cause row skip, not fabricated persistence",
            )
        }

    @Test
    fun `handleProgressUpdated still persists adjacent valid event after malformed one`() =
        runTest {
            val processor = createProcessor()
            val malformedBookId = BookId("book-malformed-ts-adj")
            val validBookId = BookId("book-valid-adj")

            val malformedEvent =
                SSEEvent.ProgressUpdated(
                    timestamp = "2026-04-19T10:00:00Z",
                    data =
                        ProgressPayload(
                            bookId = malformedBookId.value,
                            currentPositionMs = 1000L,
                            progress = 0.01,
                            totalListenTimeMs = 100L,
                            isFinished = false,
                            lastPlayedAt = "2026-99-99T99:99:99Z",
                            startedAt = null,
                            finishedAt = null,
                        ),
                )
            val validEvent =
                SSEEvent.ProgressUpdated(
                    timestamp = "2026-04-19T10:00:01Z",
                    data =
                        ProgressPayload(
                            bookId = validBookId.value,
                            currentPositionMs = 2000L,
                            progress = 0.02,
                            totalListenTimeMs = 200L,
                            isFinished = false,
                            lastPlayedAt = "2026-04-19T10:00:00Z",
                            startedAt = null,
                            finishedAt = null,
                        ),
                )

            processor.process(SSEChannelMessage.Wire(malformedEvent))
            processor.process(SSEChannelMessage.Wire(validEvent))

            assertNull(playbackPositionDao.get(malformedBookId), "malformed row should be skipped")
            assertNotNull(playbackPositionDao.get(validBookId), "adjacent valid row should still persist")
        }
}
