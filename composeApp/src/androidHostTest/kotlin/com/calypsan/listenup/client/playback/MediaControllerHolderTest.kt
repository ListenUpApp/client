package com.calypsan.listenup.client.playback

import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class MediaControllerHolderTest {
    @Test
    fun `onIsPlayingChanged forwards to playbackManager setPlaying`() =
        runTest {
            val playbackManager = mock<PlaybackManager>(MockMode.autoUnit)
            val holder =
                MediaControllerHolder(
                    context = mock(MockMode.autoUnit),
                    playbackManager = playbackManager,
                    scope = this.backgroundScope,
                )

            holder.playerListener.onIsPlayingChanged(true)

            verify(VerifyMode.exactly(1)) { playbackManager.setPlaying(true) }
        }

    @Test
    fun `onPlaybackStateChanged STATE_BUFFERING forwards setBuffering(true)`() =
        runTest {
            val playbackManager = mock<PlaybackManager>(MockMode.autoUnit)
            val holder =
                MediaControllerHolder(
                    context = mock(MockMode.autoUnit),
                    playbackManager = playbackManager,
                    scope = this.backgroundScope,
                )

            holder.playerListener.onPlaybackStateChanged(Player.STATE_BUFFERING)

            verify(VerifyMode.exactly(1)) { playbackManager.setBuffering(true) }
            verify(VerifyMode.exactly(1)) { playbackManager.setPlaybackState(PlaybackState.Buffering) }
        }

    @Test
    fun `onPlaybackStateChanged STATE_READY with idle controller forwards setBuffering(false) + Paused`() =
        runTest {
            val playbackManager = mock<PlaybackManager>(MockMode.autoUnit)
            val holder =
                MediaControllerHolder(
                    context = mock(MockMode.autoUnit),
                    playbackManager = playbackManager,
                    scope = this.backgroundScope,
                )
            // _controller is null at this point — toCommonPlaybackState returns Paused
            holder.playerListener.onPlaybackStateChanged(Player.STATE_READY)

            verify(VerifyMode.exactly(1)) { playbackManager.setBuffering(false) }
            verify(VerifyMode.exactly(1)) { playbackManager.setPlaybackState(PlaybackState.Paused) }
        }

    @Test
    fun `onPlayerError network error forwards friendly message`() =
        runTest {
            val playbackManager = mock<PlaybackManager>(MockMode.autoUnit)
            val holder =
                MediaControllerHolder(
                    context = mock(MockMode.autoUnit),
                    playbackManager = playbackManager,
                    scope = this.backgroundScope,
                )
            val error =
                PlaybackException(
                    "test",
                    null,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                )

            holder.playerListener.onPlayerError(error)

            verify(VerifyMode.exactly(1)) {
                playbackManager.reportError(
                    message = "Couldn't connect to server. Download this book for offline listening.",
                    isRecoverable = true,
                )
            }
            verify(VerifyMode.exactly(1)) { playbackManager.setPlaying(false) }
        }

    @Test
    fun `onPlaybackParametersChanged forwards speed to updateSpeed`() =
        runTest {
            val playbackManager = mock<PlaybackManager>(MockMode.autoUnit)
            val holder =
                MediaControllerHolder(
                    context = mock(MockMode.autoUnit),
                    playbackManager = playbackManager,
                    scope = this.backgroundScope,
                )

            holder.playerListener.onPlaybackParametersChanged(PlaybackParameters(1.5f))

            verify(VerifyMode.exactly(1)) { playbackManager.updateSpeed(1.5f) }
        }
}
