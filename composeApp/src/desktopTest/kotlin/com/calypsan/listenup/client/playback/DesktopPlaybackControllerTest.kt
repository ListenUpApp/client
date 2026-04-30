package com.calypsan.listenup.client.playback

import dev.mokkery.mock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Delegation tests for [DesktopPlaybackController].
 *
 * These tests only exercise direct delegation to [AudioPlayer] (play/pause/seek/etc.);
 * none invoke `startPlayback(prepareResult)`, so the [PlaybackManager] dependency is
 * supplied as a never-invoked mokkery mock via [newController].
 *
 * Uses an inline [FakeAudioPlayer] (local to this test file) because [FakePlayer]
 * from shared's commonTest is not on the desktopTest classpath.
 */
class DesktopPlaybackControllerTest {
    // ---------------------------------------------------------------------------
    // Inline fake — mirrors shared's FakePlayer call-tracking API
    // ---------------------------------------------------------------------------

    private sealed interface Call {
        data object Play : Call

        data object Pause : Call

        data class SeekTo(
            val positionMs: Long,
        ) : Call

        data class SetSpeed(
            val speed: Float,
        ) : Call

        data class Load(
            val segments: List<AudioSegment>,
        ) : Call
    }

    private class FakeAudioPlayer : AudioPlayer {
        override val state: StateFlow<PlaybackState> = MutableStateFlow(PlaybackState.Idle)
        override val positionMs: StateFlow<Long> = MutableStateFlow(0L)
        override val durationMs: StateFlow<Long> = MutableStateFlow(0L)

        private val _calls = mutableListOf<Call>()
        val calls: List<Call> get() = _calls.toList()

        override fun play() {
            _calls += Call.Play
        }

        override fun pause() {
            _calls += Call.Pause
        }

        override fun seekTo(positionMs: Long) {
            _calls += Call.SeekTo(positionMs)
        }

        override fun setSpeed(speed: Float) {
            _calls += Call.SetSpeed(speed)
        }

        override suspend fun load(segments: List<AudioSegment>) {
            _calls += Call.Load(segments)
        }

        override fun release() {}
    }

    /** Constructs a [DesktopPlaybackController] with a never-invoked [PlaybackManager] mock. */
    private fun newController(player: AudioPlayer): DesktopPlaybackController = DesktopPlaybackController(audioPlayer = player, playbackManager = mock())

    // ---------------------------------------------------------------------------
    // acquire / release are no-ops
    // ---------------------------------------------------------------------------

    @Test
    fun `acquire is a no-op`() {
        val player = FakeAudioPlayer()
        val sut = newController(player)

        sut.acquire() // must not throw or delegate to player

        assertTrue(player.calls.isEmpty())
    }

    @Test
    fun `release is a no-op`() {
        val player = FakeAudioPlayer()
        val sut = newController(player)

        sut.release()

        assertTrue(player.calls.isEmpty())
    }

    // ---------------------------------------------------------------------------
    // isReady is constant true
    // ---------------------------------------------------------------------------

    @Test
    fun `isReady is always true`() {
        val sut = newController(FakeAudioPlayer())

        assertTrue(sut.isReady.value)
    }

    // ---------------------------------------------------------------------------
    // play / pause / seekTo / setPlaybackSpeed delegate to AudioPlayer
    // ---------------------------------------------------------------------------

    @Test
    fun `play delegates to audioPlayer`() {
        val player = FakeAudioPlayer()
        val sut = newController(player)

        sut.play()

        assertEquals(listOf(Call.Play), player.calls)
    }

    @Test
    fun `pause delegates to audioPlayer`() {
        val player = FakeAudioPlayer()
        val sut = newController(player)

        sut.pause()

        assertEquals(listOf(Call.Pause), player.calls)
    }

    @Test
    fun `seekTo delegates to audioPlayer`() {
        val player = FakeAudioPlayer()
        val sut = newController(player)

        sut.seekTo(45_000L)

        assertEquals(listOf(Call.SeekTo(45_000L)), player.calls)
    }

    @Test
    fun `setPlaybackSpeed delegates to audioPlayer setSpeed`() {
        val player = FakeAudioPlayer()
        val sut = newController(player)

        sut.setPlaybackSpeed(1.5f)

        assertEquals(listOf(Call.SetSpeed(1.5f)), player.calls)
    }

    // ---------------------------------------------------------------------------
    // setMediaQueue
    // ---------------------------------------------------------------------------

    @Test
    fun `setMediaQueue maps items to AudioSegments and calls load`() =
        runTest {
            val player = FakeAudioPlayer()
            val sut = newController(player)

            val items =
                listOf(
                    PlaybackMediaItem("id-0", "url0", "/local/0", 60_000L, 0L, "Ch 1", "Author", "Book", null),
                    PlaybackMediaItem("id-1", "url1", null, 60_000L, 60_000L, "Ch 2", "Author", "Book", null),
                )

            sut.setMediaQueue(items, 0L)

            val expectedSegments =
                listOf(
                    AudioSegment(url = "url0", localPath = "/local/0", durationMs = 60_000L, offsetMs = 0L),
                    AudioSegment(url = "url1", localPath = null, durationMs = 60_000L, offsetMs = 60_000L),
                )
            assertEquals(listOf(Call.Load(expectedSegments)), player.calls)
        }

    @Test
    fun `setMediaQueue seeks after load when startPositionMs is positive`() =
        runTest {
            val player = FakeAudioPlayer()
            val sut = newController(player)

            val items =
                listOf(
                    PlaybackMediaItem("id-0", "url0", null, 120_000L, 0L, "Ch 1", null, null, null),
                )

            sut.setMediaQueue(items, 30_000L)

            assertEquals(2, player.calls.size)
            assertTrue(player.calls[0] is Call.Load)
            assertEquals(Call.SeekTo(30_000L), player.calls[1])
        }

    @Test
    fun `setMediaQueue does not seek when startPositionMs is zero`() =
        runTest {
            val player = FakeAudioPlayer()
            val sut = newController(player)

            val items =
                listOf(
                    PlaybackMediaItem("id-0", "url0", null, 60_000L, 0L, "Ch 1", null, null, null),
                )

            sut.setMediaQueue(items, 0L)

            assertEquals(1, player.calls.size)
            assertTrue(player.calls[0] is Call.Load)
        }

    // ---------------------------------------------------------------------------
    // stop / setVolume
    // ---------------------------------------------------------------------------

    @Test
    fun `stop pauses and seeks to zero`() {
        val player = FakeAudioPlayer()
        val sut = newController(player)

        sut.stop()

        assertEquals(listOf(Call.Pause, Call.SeekTo(0L)), player.calls)
    }

    @Test
    fun `setVolume is a no-op`() {
        val player = FakeAudioPlayer()
        val sut = newController(player)

        sut.setVolume(0.5f)

        assertTrue(player.calls.isEmpty())
    }
}
