package com.calypsan.listenup.client.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.StateFlow

private val logger = KotlinLogging.logger {}

/**
 * Minimal abstraction over [MediaControllerHolder] consumed by [AndroidPlaybackController].
 *
 * Exists solely to make [AndroidPlaybackController] testable in androidHostTest
 * without requiring a real Android [android.content.Context]. [MediaControllerHolder]
 * satisfies this interface directly.
 */
interface ControllerHolder {
    fun acquire()

    fun release()

    val isConnected: StateFlow<Boolean>
    val controller: MediaController?
}

/**
 * Android implementation of [PlaybackController]. Wraps [ControllerHolder] (backed by
 * [MediaControllerHolder] in production) + Media3 [MediaController].
 *
 * Command methods route through `holder.controller?.X()` — if the controller is
 * null (not yet connected, or disconnected), the command is silently dropped
 * with a warn-log. This matches the pre-Phase-E1 VM behavior of
 * `mediaController ?: return`. Throwing would leak Media3 service-lifecycle
 * quirks into VM error handling.
 */
class AndroidPlaybackController(
    private val holder: ControllerHolder,
) : PlaybackController {
    override fun acquire() = holder.acquire()

    override fun release() = holder.release()

    override val isReady: StateFlow<Boolean> = holder.isConnected

    override fun play() {
        holder.controller?.play()
            ?: logger.warn { "AndroidPlaybackController.play: controller not ready" }
    }

    override fun pause() {
        holder.controller?.pause()
            ?: logger.warn { "AndroidPlaybackController.pause: controller not ready" }
    }

    override fun seekTo(positionMs: Long) {
        val controller = holder.controller
        if (controller == null) {
            logger.warn { "AndroidPlaybackController.seekTo($positionMs): controller not ready" }
            return
        }
        // Resolve book-relative position to (mediaItemIndex, positionInItem) using Media3's
        // current timeline. The Media3 timeline exposes per-window durations; we accumulate
        // until we find the window containing positionMs.
        val (index, offset) = resolveSeekPosition(controller, positionMs)
        controller.seekTo(index, offset)
    }

    private fun resolveSeekPosition(
        controller: MediaController,
        bookPositionMs: Long,
    ): Pair<Int, Long> {
        val itemCount = controller.mediaItemCount
        if (itemCount == 0) return 0 to 0L
        var accumulated = 0L
        for (i in 0 until itemCount) {
            val window = Timeline.Window()
            controller.currentTimeline.getWindow(i, window)
            val itemDuration = window.durationMs
            if (itemDuration <= 0L) continue // Skip un-prepared windows
            val itemEnd = accumulated + itemDuration
            if (bookPositionMs in accumulated until itemEnd) {
                return i to bookPositionMs - accumulated
            }
            accumulated = itemEnd
        }
        // Position past end of queue → snap to last item's end
        return itemCount - 1 to controller.duration.coerceAtLeast(0L)
    }

    override fun setPlaybackSpeed(speed: Float) {
        holder.controller?.setPlaybackParameters(PlaybackParameters(speed))
            ?: logger.warn { "AndroidPlaybackController.setPlaybackSpeed($speed): controller not ready" }
    }

    override fun stop() {
        holder.controller?.stop()
            ?: logger.warn { "AndroidPlaybackController.stop: controller not ready" }
    }

    override fun setVolume(volume: Float) {
        holder.controller?.let { it.volume = volume }
            ?: logger.warn { "AndroidPlaybackController.setVolume($volume): controller not ready" }
    }

    override suspend fun setMediaQueue(
        items: List<PlaybackMediaItem>,
        startPositionMs: Long,
    ) {
        val controller = holder.controller
        if (controller == null) {
            logger.warn { "AndroidPlaybackController.setMediaQueue: controller not ready" }
            return
        }
        val mediaItems =
            items.map { item ->
                MediaItem
                    .Builder()
                    .setMediaId(item.mediaId)
                    .setUri(item.uri)
                    .setMediaMetadata(
                        MediaMetadata
                            .Builder()
                            .setTitle(item.title)
                            .setArtist(item.artist)
                            .setAlbumTitle(item.albumTitle)
                            .setArtworkUri(item.artworkUri?.let { Uri.parse(it) })
                            .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK)
                            .build(),
                    ).build()
            }
        val (startIndex, positionInItem) = resolveStartPosition(items, startPositionMs)
        controller.setMediaItems(mediaItems, startIndex, positionInItem)
        controller.prepare()
    }

    internal fun resolveStartPosition(
        items: List<PlaybackMediaItem>,
        bookPositionMs: Long,
    ): Pair<Int, Long> {
        if (items.isEmpty()) return 0 to 0L
        val item = items.firstOrNull { bookPositionMs in it.offsetMs until it.offsetMs + it.durationMs }
        return if (item != null) {
            items.indexOf(item) to bookPositionMs - item.offsetMs
        } else {
            if (bookPositionMs < items.first().offsetMs) {
                0 to 0L
            } else {
                items.size - 1 to items.last().durationMs
            }
        }
    }
}

/**
 * Adapter so that [MediaControllerHolder] satisfies [ControllerHolder] without modification.
 */
fun MediaControllerHolder.asControllerHolder(): ControllerHolder =
    object : ControllerHolder {
        override fun acquire() = this@asControllerHolder.acquire()

        override fun release() = this@asControllerHolder.release()

        override val isConnected: StateFlow<Boolean> = this@asControllerHolder.isConnected
        override val controller: MediaController? get() = this@asControllerHolder.controller
    }
