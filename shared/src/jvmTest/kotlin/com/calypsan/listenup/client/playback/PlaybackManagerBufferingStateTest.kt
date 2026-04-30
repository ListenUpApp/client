package com.calypsan.listenup.client.playback

import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.core.ServerUrl
import com.calypsan.listenup.client.core.Timestamp
import com.calypsan.listenup.client.data.local.db.AudioFileEntity
import com.calypsan.listenup.client.data.local.db.BookEntity
import com.calypsan.listenup.client.data.local.db.ListenUpDatabase
import com.calypsan.listenup.client.data.local.db.SyncState
import com.calypsan.listenup.client.device.DeviceContext
import com.calypsan.listenup.client.device.DeviceType
import com.calypsan.listenup.client.domain.repository.BookRepository
import com.calypsan.listenup.client.domain.repository.ImageStorage
import com.calypsan.listenup.client.domain.repository.PlaybackPreferences
import com.calypsan.listenup.client.domain.repository.ServerConfig
import com.calypsan.listenup.client.download.DownloadResult
import com.calypsan.listenup.client.download.DownloadService
import com.calypsan.listenup.client.test.db.createInMemoryTestDatabase
import com.calypsan.listenup.client.test.fake.FakePlayer
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Verifies the isBuffering and playbackState flows added in W7 Phase E2.1 Task 1,
 * and the AudioPlayer state observation wired in Task 4.
 *
 * These flows serve as the receiving end for platform-specific state pushes:
 * Android's MediaControllerHolder Player.Listener and Desktop's AudioPlayer.state
 * observation in startPlayback.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackManagerBufferingStateTest {
    private val db: ListenUpDatabase = createInMemoryTestDatabase()

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun `setBuffering updates isBuffering flow`() =
        runTest {
            val sut = createPlaybackManager()

            assertEquals(false, sut.isBuffering.value)

            sut.setBuffering(true)
            assertEquals(true, sut.isBuffering.value)

            sut.setBuffering(false)
            assertEquals(false, sut.isBuffering.value)
        }

    @Test
    fun `setPlaybackState updates playbackState flow`() =
        runTest {
            val sut = createPlaybackManager()

            assertEquals(PlaybackState.Idle, sut.playbackState.value)

            sut.setPlaybackState(PlaybackState.Playing)
            assertEquals(PlaybackState.Playing, sut.playbackState.value)

            sut.setPlaybackState(PlaybackState.Buffering)
            assertEquals(PlaybackState.Buffering, sut.playbackState.value)
        }

    @Test
    fun `startPlayback subscribes to AudioPlayer state and forwards to PlaybackManager`() =
        runTest {
            seedBookAndAudioFiles()

            // Use coroutineContext + Job() so launched coroutines share the
            // TestCoroutineScheduler (and are advanced by advanceUntilIdle()) while the
            // Job is independent of the TestScope — allowing explicit cancellation at the
            // end without tearing down the test harness.
            val managerScope = CoroutineScope(coroutineContext + Job())
            val sut = createPlaybackManager(scope = managerScope)

            val player = FakePlayer()

            val prepareResult = sut.prepareForPlayback(BookId("book-1"))
            checkNotNull(prepareResult) { "prepareForPlayback must succeed" }
            sut.activateBook(BookId("book-1"))

            // startPlayback launches the observation coroutines on managerScope.
            sut.startPlayback(player = player, resumePositionMs = 0L, resumeSpeed = 1.0f)

            // FakePlayer.load() drives state → Buffering; play() drives state → Playing.
            // Drain pending coroutines so the collectors process the latest emission.
            advanceUntilIdle()

            // After play(), FakePlayer emits Playing — expect state forwarded.
            assertEquals(PlaybackState.Playing, sut.playbackState.value)
            assertFalse(sut.isBuffering.value, "Playing state must clear isBuffering")

            // Now drive the player to Buffering and verify forwarding.
            player.emitState(PlaybackState.Buffering)
            advanceUntilIdle()

            assertEquals(PlaybackState.Buffering, sut.playbackState.value)
            assertEquals(true, sut.isBuffering.value)

            // clearPlayback must cancel observations — further emissions must not propagate.
            sut.clearPlayback()
            player.emitState(PlaybackState.Playing)
            advanceUntilIdle()

            // clearPlayback resets to Idle regardless of what the player emits.
            assertEquals(PlaybackState.Idle, sut.playbackState.value)
            assertFalse(sut.isBuffering.value, "clearPlayback must reset isBuffering to false")

            // Cancel to stop the infinite collect coroutines and let runTest complete.
            managerScope.coroutineContext[Job]?.cancel()
        }

    @Test
    fun `playerObservationJob surfaces PlaybackState_Error as PlaybackError on playbackError flow`() =
        runTest {
            seedBookAndAudioFiles()

            val managerScope = CoroutineScope(coroutineContext + Job())
            val sut = createPlaybackManager(scope = managerScope)
            val player = FakePlayer()

            val prepareResult = sut.prepareForPlayback(BookId("book-1"))
            checkNotNull(prepareResult) { "prepareForPlayback must succeed" }
            sut.activateBook(BookId("book-1"))

            sut.startPlayback(player = player, resumePositionMs = 0L, resumeSpeed = 1.0f)
            advanceUntilIdle()

            assertNull(sut.playbackError.value)

            player.emitState(PlaybackState.Error(message = "GStreamer hosed", isRecoverable = false))
            advanceUntilIdle()

            val error = sut.playbackError.value
            assertNotNull(error)
            assertEquals("GStreamer hosed", error.message)
            assertEquals(false, error.isRecoverable)

            managerScope.coroutineContext[Job]?.cancel()
        }

    @Test
    fun `playerObservationJob clears playbackError on transition to Playing`() =
        runTest {
            seedBookAndAudioFiles()

            val managerScope = CoroutineScope(coroutineContext + Job())
            val sut = createPlaybackManager(scope = managerScope)
            val player = FakePlayer()

            val prepareResult = sut.prepareForPlayback(BookId("book-1"))
            checkNotNull(prepareResult) { "prepareForPlayback must succeed" }
            sut.activateBook(BookId("book-1"))

            sut.startPlayback(player = player, resumePositionMs = 0L, resumeSpeed = 1.0f)
            advanceUntilIdle()

            player.emitState(PlaybackState.Error(message = "transient", isRecoverable = true))
            advanceUntilIdle()
            assertNotNull(sut.playbackError.value)

            player.emitState(PlaybackState.Playing)
            advanceUntilIdle()
            assertNull(sut.playbackError.value)

            managerScope.coroutineContext[Job]?.cancel()
        }

    // =========================================================================
    // Fixture helpers
    // =========================================================================

    private fun createPlaybackManager(
        scope: CoroutineScope = CoroutineScope(Job()),
    ): PlaybackManager {
        val tokenProvider: AudioTokenProvider = mock()
        everySuspend { tokenProvider.prepareForPlayback() } returns Unit

        val serverConfig: ServerConfig = mock()
        everySuspend { serverConfig.getServerUrl() } returns ServerUrl("https://example.test")

        val imageStorage: ImageStorage = mock()
        every { imageStorage.exists(any()) } returns false

        val downloadService: DownloadService = mock()
        everySuspend { downloadService.getLocalPath(any()) } returns null
        everySuspend { downloadService.wasExplicitlyDeleted(any()) } returns false
        everySuspend { downloadService.downloadBook(any()) } returns DownloadResult.AlreadyDownloaded

        val playbackPreferences: PlaybackPreferences = mock()
        everySuspend { playbackPreferences.getDefaultPlaybackSpeed() } returns 1.0f

        return PlaybackManager(
            serverConfig = serverConfig,
            playbackPreferences = playbackPreferences,
            bookDao = db.bookDao(),
            audioFileDao = db.audioFileDao(),
            chapterDao = db.chapterDao(),
            imageStorage = imageStorage,
            progressTracker = buildProgressTracker(scope = scope),
            tokenProvider = tokenProvider,
            deviceContext = DeviceContext(type = DeviceType.Phone),
            downloadService = downloadService,
            playbackApi = null,
            capabilityDetector = null,
            syncApi = null,
            scope = scope,
            bookRepository = mock<BookRepository>(),
        )
    }

    private suspend fun seedBookAndAudioFiles() {
        db.bookDao().upsert(
            BookEntity(
                id = BookId("book-1"),
                title = "Test Book",
                sortTitle = "Test Book",
                subtitle = null,
                coverUrl = null,
                coverBlurHash = null,
                dominantColor = null,
                darkMutedColor = null,
                vibrantColor = null,
                totalDuration = 1_800_000L,
                description = null,
                publishYear = null,
                publisher = null,
                language = null,
                isbn = null,
                asin = null,
                abridged = false,
                syncState = SyncState.SYNCED,
                lastModified = Timestamp(1L),
                serverVersion = Timestamp(1L),
                createdAt = Timestamp(1L),
                updatedAt = Timestamp(1L),
            ),
        )
        db.audioFileDao().upsertAll(
            listOf(
                AudioFileEntity(
                    bookId = BookId("book-1"),
                    index = 0,
                    id = "af-0",
                    filename = "chapter1.m4b",
                    format = "m4b",
                    codec = "aac",
                    duration = 1_800_000L,
                    size = 45_000_000L,
                ),
            ),
        )
    }
}
