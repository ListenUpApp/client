package com.calypsan.listenup.client.playback

import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-agnostic command-side abstraction for the playback layer.
 *
 * Android wraps Media3's [androidx.media3.session.MediaController] (asynchronous,
 * service-bound) via [MediaControllerHolder]. Desktop and Apple wrap the shared
 * [AudioPlayer] interface directly.
 *
 * VMs consume this interface for all command-side operations (play/pause, seek,
 * speed, queue management). Read-side state (currentBookId, isPlaying, position
 * polling, etc.) continues to flow through [PlaybackManager].
 *
 * Lifecycle: [acquire]/[release] are refcounted on Android (Media3 service binding);
 * no-ops on Desktop/Apple (AudioPlayer instances are eagerly ready).
 *
 * Note: [release] is a refcount decrement, NOT a permanent resource release.
 * Do NOT confuse with [AudioPlayer.release] which tears down the player permanently.
 */
expect interface PlaybackController {
    /** Refcounted reference acquisition. Android: connects to Media3 service if first acquire. Desktop/Apple: no-op. */
    fun acquire()

    /** Refcounted reference release. Android: disconnects from Media3 service when refcount hits zero. Desktop/Apple: no-op. */
    fun release()

    /** Emits true when the underlying player is connected and accepting commands. */
    val isReady: StateFlow<Boolean>

    /** Start or resume playback of the current queue. */
    fun play()

    /** Pause playback. */
    fun pause()

    /** Seek to absolute book position. */
    fun seekTo(positionMs: Long)

    /** Set playback speed multiplier (1.0 = normal). */
    fun setPlaybackSpeed(speed: Float)

    /**
     * Replace the active queue with [items], starting playback at [startPositionMs] inside the queue (book-relative).
     * Suspends until the queue is loaded and prepared.
     */
    suspend fun setMediaQueue(
        items: List<PlaybackMediaItem>,
        startPositionMs: Long,
    )
}

/**
 * One queue entry consumed by [PlaybackController.setMediaQueue].
 *
 * Carries both per-segment data (used by all platforms) and book-level metadata
 * (used by Android's Media3 [androidx.media3.common.MediaMetadata] for Android
 * Auto, lock screen, notification). Desktop and Apple actuals ignore the
 * metadata fields when constructing their internal [AudioSegment].
 */
data class PlaybackMediaItem(
    val mediaId: String,
    val uri: String,
    val localPath: String?,
    val durationMs: Long,
    val offsetMs: Long,
    val title: String,
    val artist: String?,
    val albumTitle: String?,
    val artworkUri: String?,
)
