package com.calypsan.listenup.client.playback

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private val logger = KotlinLogging.logger {}

/**
 * Desktop implementation of [PlaybackController]. Wraps the existing shared [AudioPlayer]
 * (typically [FfmpegAudioPlayer] in production).
 *
 * `acquire`/`release` are no-ops — the AudioPlayer is constructed eagerly and
 * has no service-lifecycle. `isReady` is a constant `true` for the same reason.
 *
 * Note: `release` here does NOT call [AudioPlayer.release]; that would tear down the
 * player permanently.
 */
class DesktopPlaybackController(
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
