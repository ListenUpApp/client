package com.calypsan.listenup.client.playback

import android.content.ContextWrapper
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MediaControllerHolderTest {
    // ---------------------------------------------------------------------------
    // Fake PlaybackStateWriter — records calls for assertion
    // ---------------------------------------------------------------------------

    private class FakePlaybackStateWriter : PlaybackStateWriter {
        val playingHistory = mutableListOf<Boolean>()
        val bufferingHistory = mutableListOf<Boolean>()
        val playbackStateHistory = mutableListOf<PlaybackState>()
        val speedHistory = mutableListOf<Float>()
        val positionHistory = mutableListOf<Long>()

        data class ErrorCall(
            val message: String,
            val isRecoverable: Boolean,
        )

        val errorHistory = mutableListOf<ErrorCall>()

        override fun setPlaying(playing: Boolean) {
            playingHistory += playing
        }

        override fun setBuffering(buffering: Boolean) {
            bufferingHistory += buffering
        }

        override fun setPlaybackState(state: PlaybackState) {
            playbackStateHistory += state
        }

        override fun updatePosition(positionMs: Long) {
            positionHistory += positionMs
        }

        override fun updateSpeed(speed: Float) {
            speedHistory += speed
        }

        override fun reportError(
            message: String,
            isRecoverable: Boolean,
        ) {
            errorHistory += ErrorCall(message, isRecoverable)
        }
    }

    /**
     * Context is only used inside [MediaControllerHolder.connect], which is not called
     * by any test here. A ContextWrapper(null) stub is safe: it will NPE only if accessed,
     * and none of these tests trigger connect().
     */
    private val stubContext = object : ContextWrapper(null) {}

    // ---------------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------------

    @Test
    fun `onIsPlayingChanged forwards to playbackManager setPlaying`() =
        runTest {
            val writer = FakePlaybackStateWriter()
            val holder =
                MediaControllerHolder(
                    context = stubContext,
                    playbackManager = writer,
                    scope = this.backgroundScope,
                )

            holder.playerListener.onIsPlayingChanged(true)
            holder.playerListener.onIsPlayingChanged(false)

            assertEquals(listOf(true, false), writer.playingHistory)
        }

    @Test
    fun `onPlaybackStateChanged STATE_BUFFERING forwards setBuffering(true) and Buffering state`() =
        runTest {
            val writer = FakePlaybackStateWriter()
            val holder =
                MediaControllerHolder(
                    context = stubContext,
                    playbackManager = writer,
                    scope = this.backgroundScope,
                )

            holder.playerListener.onPlaybackStateChanged(Player.STATE_BUFFERING)

            assertEquals(listOf(true), writer.bufferingHistory)
            assertEquals(listOf<PlaybackState>(PlaybackState.Buffering), writer.playbackStateHistory)
        }

    @Test
    fun `onPlaybackStateChanged STATE_READY with idle controller forwards setBuffering(false) and Paused`() =
        runTest {
            val writer = FakePlaybackStateWriter()
            val holder =
                MediaControllerHolder(
                    context = stubContext,
                    playbackManager = writer,
                    scope = this.backgroundScope,
                )
            // _controller is null at this point — toCommonPlaybackState returns Paused
            holder.playerListener.onPlaybackStateChanged(Player.STATE_READY)

            assertEquals(listOf(false), writer.bufferingHistory)
            assertEquals(listOf<PlaybackState>(PlaybackState.Paused), writer.playbackStateHistory)
        }

    // onPlayerError is not tested here: PlaybackException's constructor internally calls
    // android.os.SystemClock.elapsedRealtime(), which is not mocked in JVM host tests
    // (androidHostTest runs without Robolectric). The error-translation logic is covered
    // by integration testing on device / instrumented tests.

    @Test
    fun `onPlaybackParametersChanged forwards speed to updateSpeed`() =
        runTest {
            val writer = FakePlaybackStateWriter()
            val holder =
                MediaControllerHolder(
                    context = stubContext,
                    playbackManager = writer,
                    scope = this.backgroundScope,
                )

            holder.playerListener.onPlaybackParametersChanged(PlaybackParameters(1.5f))

            assertEquals(listOf(1.5f), writer.speedHistory)
        }
}
