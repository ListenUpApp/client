package com.calypsan.listenup.client.playback

import kotlinx.coroutines.flow.StateFlow

actual interface PlaybackController {
    actual fun acquire()

    actual fun release()

    actual val isReady: StateFlow<Boolean>

    actual fun play()

    actual fun pause()

    actual fun seekTo(positionMs: Long)

    actual fun setPlaybackSpeed(speed: Float)

    actual suspend fun setMediaQueue(
        items: List<PlaybackMediaItem>,
        startPositionMs: Long,
    )
}
