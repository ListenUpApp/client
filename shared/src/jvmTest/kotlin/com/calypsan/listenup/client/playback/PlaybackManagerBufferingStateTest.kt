package com.calypsan.listenup.client.playback

import com.calypsan.listenup.client.core.ServerUrl
import com.calypsan.listenup.client.data.local.db.ListenUpDatabase
import com.calypsan.listenup.client.device.DeviceContext
import com.calypsan.listenup.client.device.DeviceType
import com.calypsan.listenup.client.domain.repository.BookRepository
import com.calypsan.listenup.client.domain.repository.ImageStorage
import com.calypsan.listenup.client.domain.repository.PlaybackPreferences
import com.calypsan.listenup.client.domain.repository.ServerConfig
import com.calypsan.listenup.client.download.DownloadResult
import com.calypsan.listenup.client.download.DownloadService
import com.calypsan.listenup.client.test.db.createInMemoryTestDatabase
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies the isBuffering and playbackState flows added in W7 Phase E2.1 Task 1.
 *
 * These flows serve as the receiving end for platform-specific state pushes:
 * Android's MediaControllerHolder Player.Listener and Desktop's AudioPlayer.state
 * observation. No consumers are wired yet (Tasks 2-5); this test pins the flows
 * and setters against future regression.
 */
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
}
