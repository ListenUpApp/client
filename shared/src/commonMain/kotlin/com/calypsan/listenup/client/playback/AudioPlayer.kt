package com.calypsan.listenup.client.playback

import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-agnostic audio player interface.
 *
 * Implementations handle actual audio output (ExoPlayer on Android, JavaFX on Desktop).
 * PlaybackManager drives this after timeline preparation.
 * All positions are book-relative (translated internally to segment coordinates).
 */
interface AudioPlayer {
    /** Current playback state. */
    val state: StateFlow<PlaybackState>

    /** Current position in book timeline (milliseconds). */
    val positionMs: StateFlow<Long>

    /** Total duration of loaded content (milliseconds). */
    val durationMs: StateFlow<Long>

    /**
     * Load audio segments for playback.
     * Prepares the first segment but does not start playback.
     */
    suspend fun load(segments: List<AudioSegment>)

    /** Start or resume playback. */
    fun play()

    /** Pause playback. */
    fun pause()

    /**
     * Seek to a book-relative position.
     * Internally translates to the correct segment and offset.
     */
    fun seekTo(positionMs: Long)

    /** Set playback speed multiplier. */
    fun setSpeed(speed: Float)

    /** Release all resources. Call when playback session ends. */
    fun release()
}

/**
 * Represents a single audio file segment in the playback timeline.
 *
 * @property url Streaming URL for this segment (with auth via token provider)
 * @property localPath Local file path if downloaded (preferred over streaming)
 * @property durationMs Duration of this segment in milliseconds
 * @property offsetMs Where this segment starts in the book timeline
 */
data class AudioSegment(
    val url: String,
    val localPath: String?,
    val durationMs: Long,
    val offsetMs: Long,
)

/**
 * Playback state reported by the audio player.
 */
enum class PlaybackState {
    /** No content loaded. */
    Idle,

    /** Content is loading/buffering. */
    Buffering,

    /** Actively playing audio. */
    Playing,

    /** Playback is paused. */
    Paused,

    /** Playback completed (reached end of all segments). */
    Ended,

    /** An error occurred during playback. */
    Error,
}
