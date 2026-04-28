package com.calypsan.listenup.client.playback

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private val logger = KotlinLogging.logger {}

/**
 * Apple implementation of [PlaybackController]. Wraps the shared [AudioPlayer]
 * (typically [AvFoundationAudioPlayer]).
 *
 * `acquire`/`release` are no-ops — the AudioPlayer is constructed eagerly and
 * has no service-lifecycle. `isReady` is a constant `true` for the same reason.
 *
 * Currently no Kotlin VM consumes this actual — the iOS app uses Swift-side
 * NowPlayingObserver. This class ships for cross-target compilation.
 *
 * Note: `release` here does NOT call [AudioPlayer.release]; that would tear down the
 * player permanently.
 */
class ApplePlaybackController(
    private val audioPlayer: AudioPlayer,
) : PlaybackController {
    private val _isReady = MutableStateFlow(true)
    override val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    override fun acquire() = Unit

    override fun release() = Unit

    override fun play() = audioPlayer.play()

    override fun pause() = audioPlayer.pause()

    override fun seekTo(positionMs: Long) = audioPlayer.seekTo(positionMs)

    override fun setPlaybackSpeed(speed: Float) = audioPlayer.setSpeed(speed)

    override suspend fun setMediaQueue(
        items: List<PlaybackMediaItem>,
        startPositionMs: Long,
    ) {
        val segments =
            items.map { item ->
                AudioSegment(
                    url = item.uri,
                    localPath = item.localPath,
                    durationMs = item.durationMs,
                    offsetMs = item.offsetMs,
                )
            }
        audioPlayer.load(segments)
        if (startPositionMs > 0L) {
            audioPlayer.seekTo(startPositionMs)
        }
    }
}
