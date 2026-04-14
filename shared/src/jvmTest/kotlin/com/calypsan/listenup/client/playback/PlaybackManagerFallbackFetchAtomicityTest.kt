package com.calypsan.listenup.client.playback

import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.local.db.AudioFileDao
import com.calypsan.listenup.client.data.local.db.DownloadDao
import com.calypsan.listenup.client.data.local.db.ListenUpDatabase
import com.calypsan.listenup.client.data.local.db.ListeningEventDao
import com.calypsan.listenup.client.data.local.db.PlaybackPositionDao
import com.calypsan.listenup.client.data.local.db.RoomTransactionRunner
import com.calypsan.listenup.client.data.remote.SyncApiContract
import com.calypsan.listenup.client.data.remote.model.AudioFileResponse
import com.calypsan.listenup.client.data.remote.model.BookResponse
import com.calypsan.listenup.client.data.sync.push.ListeningEventPayload
import com.calypsan.listenup.client.data.sync.push.OperationHandler
import com.calypsan.listenup.client.data.sync.push.PendingOperationRepositoryContract
import com.calypsan.listenup.client.data.sync.push.PushSyncOrchestratorContract
import com.calypsan.listenup.client.device.DeviceContext
import com.calypsan.listenup.client.device.DeviceType
import com.calypsan.listenup.client.domain.repository.PlaybackPositionRepository
import com.calypsan.listenup.client.test.db.createInMemoryTestDatabase
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Proves [PlaybackManager.fetchBookFromServer] is atomic — when the audio-file
 * junction insert throws, the prior `bookDao.upsert` must roll back so the DB
 * never holds a book row with missing audio files from this refresh.
 *
 * Uses a real in-memory [ListenUpDatabase]; the audio-file DAO is mocked to
 * throw on insert, forcing the failure after the book upsert has landed inside
 * the atomically block.
 *
 * This is the regression coverage for the first atomically-wrapped write in
 * PlaybackManager. Landed as part of W4 Item B.
 */
class PlaybackManagerFallbackFetchAtomicityTest {
    private val db: ListenUpDatabase = createInMemoryTestDatabase()

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun `rollback when audio file insert throws`() =
        runTest {
            val syncApi: SyncApiContract = mock()
            val failingAudioFileDao: AudioFileDao = mock()

            // Stub the failing DAO: delete succeeds, upsert blows up mid-transaction
            // (after bookDao.upsert has already written inside the atomically block).
            everySuspend { failingAudioFileDao.deleteForBook(any()) } returns Unit
            everySuspend { failingAudioFileDao.upsertAll(any()) } throws
                RuntimeException("boom — audio file insert failed")

            everySuspend { syncApi.getBook(any()) } returns
                Success(
                    bookResponseWithAudioFiles(
                        id = "book-rollback",
                        audioFiles =
                            listOf(
                                AudioFileResponse(
                                    id = "af-1",
                                    filename = "chapter01.m4b",
                                    format = "m4b",
                                    codec = "aac",
                                    duration = 1_800_000L,
                                    size = 45_000_000L,
                                ),
                            ),
                    ),
                )

            // ProgressTracker is a final class so Mokkery can't synthesise a mock —
            // construct a real instance whose dependencies are all interface mocks.
            // fetchBookFromServer doesn't touch progressTracker, so we just need
            // something instantiable.
            val progressTracker =
                ProgressTracker(
                    positionDao = mock<PlaybackPositionDao>(),
                    downloadDao = mock<DownloadDao>(),
                    listeningEventDao = mock<ListeningEventDao>(),
                    syncApi = mock<SyncApiContract>(),
                    pendingOperationRepository = mock<PendingOperationRepositoryContract>(),
                    listeningEventHandler = mock<OperationHandler<ListeningEventPayload>>(),
                    pushSyncOrchestrator = mock<PushSyncOrchestratorContract>(),
                    positionRepository = mock<PlaybackPositionRepository>(),
                    deviceId = "test-device",
                    scope = CoroutineScope(Job()),
                )

            val playbackManager =
                PlaybackManager(
                    transactionRunner = RoomTransactionRunner(db),
                    serverConfig = mock(),
                    playbackPreferences = mock(),
                    bookDao = db.bookDao(),
                    audioFileDao = failingAudioFileDao,
                    chapterDao = db.chapterDao(),
                    imageStorage = mock(),
                    progressTracker = progressTracker,
                    tokenProvider = mock(),
                    deviceContext = DeviceContext(type = DeviceType.Phone),
                    downloadService = mock(),
                    playbackApi = null,
                    capabilityDetector = null,
                    syncApi = syncApi,
                    scope = CoroutineScope(Job()),
                )

            assertFailsWith<RuntimeException> {
                playbackManager.fetchBookFromServer(BookId("book-rollback"))
            }

            assertEquals(
                0,
                db.bookDao().count(),
                "bookDao.upsert must roll back when audio file insert throws",
            )
        }

    /**
     * Minimal-valid [BookResponse] factory. Only `id` and `audioFiles` matter for
     * this test — everything else is defaulted to empty/null/zero so the test is
     * insulated from future BookResponse field additions as long as they carry
     * their own defaults.
     *
     * Mirrors the shape used by BookPullerTest.createBookResponse and
     * BookPullerAtomicityTest's inline construction.
     */
    private fun bookResponseWithAudioFiles(
        id: String,
        audioFiles: List<AudioFileResponse>,
    ): BookResponse =
        BookResponse(
            id = id,
            title = "Rollback Test",
            subtitle = null,
            coverImage = null,
            totalDuration = 3_600_000L,
            description = null,
            genres = null,
            publishYear = null,
            seriesInfo = emptyList(),
            chapters = emptyList(),
            audioFiles = audioFiles,
            contributors = emptyList(),
            createdAt = "2024-01-01T00:00:00Z",
            updatedAt = "2024-01-01T00:00:00Z",
        )
}
