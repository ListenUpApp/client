package com.calypsan.listenup.client.playback

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.domain.playback.PlaybackTimeline
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}
private const val TAG = "PlayerVM"

/**
 * ViewModel for playback UI.
 *
 * Responsibilities:
 * - Uses shared MediaControllerHolder for player connection
 * - Exposes playback state as flows for Compose
 * - Handles position updates using book-relative timeline
 * - Provides control actions
 */
class PlayerViewModel(
    private val playbackManager: PlaybackManager,
    private val mediaControllerHolder: MediaControllerHolder,
) : ViewModel() {
    private var positionUpdateJob: Job? = null
    private var playerListener: Player.Listener? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var currentTimeline: PlaybackTimeline? = null

    init {
        // Acquire reference to shared controller
        mediaControllerHolder.acquire()
    }

    private val mediaController: MediaController?
        get() = mediaControllerHolder.controller

    /**
     * Start playback of a book.
     * Prepares the player, builds media items, and begins playback.
     */
    fun playBook(bookId: BookId) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            val result = playbackManager.prepareForPlayback(bookId)
            if (result == null) {
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        error = "Failed to load book",
                    )
                return@launch
            }

            currentTimeline = result.timeline

            _state.value =
                _state.value.copy(
                    bookTitle = result.bookTitle,
                    totalDurationMs = result.timeline.totalDurationMs,
                    currentPositionMs = result.resumePositionMs,
                    playbackSpeed = result.resumeSpeed,
                )

            // Connect to the player and start
            connectAndPlay(result)
        }
    }

    private fun connectAndPlay(prepareResult: PlaybackManager.PrepareResult) {
        // Use shared controller - wait for it to be available
        mediaControllerHolder.withController { controller ->
            try {
                // Remove old listener if any
                playerListener?.let { controller.removeListener(it) }

                // Add our listener
                val listener = PlayerListener()
                playerListener = listener
                controller.addListener(listener)

                // Build media items from timeline
                Log.d(TAG, "Building ${prepareResult.timeline.files.size} media items")
                val mediaItems =
                    prepareResult.timeline.files.mapIndexed { index, file ->
                        Log.d(
                            TAG,
                            "  MediaItem[$index]: id=${file.audioFileId}, " +
                                "duration=${file.durationMs}ms, " +
                                "url=${file.streamingUrl.takeLast(30)}",
                        )
                        MediaItem
                            .Builder()
                            .setMediaId(file.audioFileId)
                            .setUri(file.streamingUrl)
                            .setMediaMetadata(
                                MediaMetadata
                                    .Builder()
                                    .setTitle(prepareResult.bookTitle)
                                    .setArtist(file.filename)
                                    .build(),
                            ).build()
                    }

                // Set items with initial resume position
                val startPosition = prepareResult.timeline.resolve(prepareResult.resumePositionMs)
                Log.d(
                    TAG,
                    "Starting playback: resumePos=${prepareResult.resumePositionMs}ms -> mediaItem=${startPosition.mediaItemIndex}, posInFile=${startPosition.positionInFileMs}ms",
                )

                // Validate start position before passing to ExoPlayer
                if (startPosition.mediaItemIndex < 0 || startPosition.mediaItemIndex >= mediaItems.size) {
                    Log.e(
                        TAG,
                        "⚠️ INVALID startPosition.mediaItemIndex: ${startPosition.mediaItemIndex}, mediaItems.size=${mediaItems.size}",
                    )
                    _state.value = _state.value.copy(isLoading = false, error = "Invalid start position")
                    return@withController
                }
                if (startPosition.positionInFileMs < 0) {
                    Log.e(TAG, "⚠️ INVALID startPosition.positionInFileMs: ${startPosition.positionInFileMs}")
                    _state.value = _state.value.copy(isLoading = false, error = "Invalid start position")
                    return@withController
                }

                // setMediaItems with startIndex and startPosition to seek atomically
                controller.setMediaItems(mediaItems, startPosition.mediaItemIndex, startPosition.positionInFileMs)
                controller.playbackParameters = PlaybackParameters(prepareResult.resumeSpeed)
                Log.d(TAG, "Calling controller.prepare()...")
                controller.prepare()
                Log.d(TAG, "Calling controller.play()...")
                controller.play()

                playbackManager.setPlaying(true)

                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        isPlaying = true,
                    )

                startPositionUpdates()
                Log.d(TAG, "Playback started successfully for: ${prepareResult.bookTitle}")

                logger.info { "Playback started for: ${prepareResult.bookTitle}" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to start playback" }
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        error = "Failed to start playback",
                    )
            }
        }
    }

    /**
     * Toggle play/pause.
     */
    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    /**
     * Seek to a position in the book timeline.
     */
    fun seekTo(bookPositionMs: Long) {
        val controller = mediaController ?: return
        val timeline = currentTimeline ?: return

        val position = timeline.resolve(bookPositionMs)
        controller.seekTo(position.mediaItemIndex, position.positionInFileMs)
        _state.value = _state.value.copy(currentPositionMs = bookPositionMs)

        // Notify PlaybackManager so NowPlayingViewModel updates immediately (even when paused)
        playbackManager.updatePosition(bookPositionMs)
    }

    /**
     * Skip forward by milliseconds.
     */
    fun skipForward(ms: Long = 30_000) {
        val currentPos = _state.value.currentPositionMs
        val newPos = (currentPos + ms).coerceAtMost(_state.value.totalDurationMs)
        seekTo(newPos)
    }

    /**
     * Skip backward by milliseconds.
     */
    fun skipBackward(ms: Long = 10_000) {
        val currentPos = _state.value.currentPositionMs
        val newPos = (currentPos - ms).coerceAtLeast(0)
        seekTo(newPos)
    }

    /**
     * Set playback speed.
     */
    fun setSpeed(speed: Float) {
        val controller = mediaController ?: return
        controller.playbackParameters = PlaybackParameters(speed)
        _state.value = _state.value.copy(playbackSpeed = speed)
    }

    /**
     * Cycle through common playback speeds.
     */
    fun cycleSpeed() {
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)
        val currentSpeed = _state.value.playbackSpeed
        val currentIndex = speeds.indexOfFirst { it >= currentSpeed - 0.01f }
        val nextIndex = if (currentIndex == -1 || currentIndex >= speeds.lastIndex) 0 else currentIndex + 1
        setSpeed(speeds[nextIndex])
    }

    /**
     * Stop playback and release resources.
     */
    fun stop() {
        mediaController?.stop()
        playbackManager.setPlaying(false)
        playbackManager.clearPlayback()
        _state.value = PlayerUiState()
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob =
            viewModelScope.launch {
                while (isActive) {
                    updatePosition()
                    delay(250) // Update 4 times per second for smooth progress
                }
            }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    private fun updatePosition() {
        val controller = mediaController ?: return
        val timeline = currentTimeline ?: return

        val mediaItemIndex = controller.currentMediaItemIndex
        val positionInFile = controller.currentPosition
        val playbackState = controller.playbackState
        val isPlaying = controller.isPlaying
        val isBuffering = playbackState == Player.STATE_BUFFERING

        // Validate ExoPlayer values before using them (silent validation, only log errors)
        if (mediaItemIndex < 0 || mediaItemIndex >= timeline.files.size || positionInFile < 0) {
            Log.e(
                TAG,
                "⚠️ INVALID position: mediaItem=$mediaItemIndex/${timeline.files.size}, posInFile=$positionInFile",
            )
            return
        }

        val bookPosition =
            timeline.toBookPosition(
                mediaItemIndex = mediaItemIndex,
                positionInFileMs = positionInFile,
            )

        // Validate calculated book position
        if (bookPosition < 0 || bookPosition > timeline.totalDurationMs + 1000) {
            Log.e(TAG, "⚠️ INVALID bookPosition: $bookPosition (duration=${timeline.totalDurationMs})")
            return
        }

        // Debounce: only update state if position changed significantly (>100ms) or playback state changed
        val currentState = _state.value
        val positionDelta = kotlin.math.abs(bookPosition - currentState.currentPositionMs)
        val stateChanged = isPlaying != currentState.isPlaying || isBuffering != currentState.isBuffering

        if (!stateChanged && positionDelta < 100) {
            return // Skip very minor position updates to reduce recomposition
        }

        _state.value =
            currentState.copy(
                currentPositionMs = bookPosition,
                isPlaying = isPlaying,
                isBuffering = isBuffering,
            )

        // Publish position to PlaybackManager for NowPlayingViewModel
        playbackManager.updatePosition(bookPosition)
    }

    private inner class PlayerListener : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(isPlaying = isPlaying)
            playbackManager.setPlaying(isPlaying)

            if (isPlaying) {
                startPositionUpdates()
            } else {
                stopPositionUpdates()
                updatePosition() // One final update
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val isBuffering = playbackState == Player.STATE_BUFFERING
            _state.value = _state.value.copy(isBuffering = isBuffering)

            if (playbackState == Player.STATE_ENDED) {
                _state.value =
                    _state.value.copy(
                        isPlaying = false,
                        isFinished = true,
                    )
            }
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            _state.value = _state.value.copy(playbackSpeed = playbackParameters.speed)
            playbackManager.updateSpeed(playbackParameters.speed)
        }
    }

    override fun onCleared() {
        super.onCleared()
        positionUpdateJob?.cancel()

        // Remove our listener from the shared controller
        playerListener?.let { listener ->
            mediaController?.removeListener(listener)
        }
        playerListener = null

        // Release our reference to the shared controller
        mediaControllerHolder.release()
    }
}

/**
 * UI state for the player screen.
 */
data class PlayerUiState(
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isFinished: Boolean = false,
    val error: String? = null,
    val bookTitle: String = "",
    val currentPositionMs: Long = 0,
    val totalDurationMs: Long = 0,
    val playbackSpeed: Float = 1.0f,
) {
    val progress: Float
        get() =
            if (totalDurationMs > 0) {
                currentPositionMs.toFloat() / totalDurationMs
            } else {
                0f
            }

    val formattedPosition: String
        get() = formatDuration(currentPositionMs)

    val formattedDuration: String
        get() = formatDuration(totalDurationMs)

    val formattedSpeed: String
        get() = "${playbackSpeed}x"
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = totalSeconds % 3600 / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format(java.util.Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}
