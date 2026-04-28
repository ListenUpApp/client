@file:Suppress("MagicNumber")

package com.calypsan.listenup.client.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.domain.repository.NetworkMonitor
import com.calypsan.listenup.client.domain.playback.PlaybackTimeline
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for playback UI.
 *
 * Responsibilities:
 * - Uses PlaybackController seam for player commands
 * - Exposes playback state as flows for Compose
 * - Handles position updates using book-relative timeline
 * - Provides control actions
 *
 * Note: [Player.Listener] registration and direct [MediaController] position polling
 * ([updatePosition]) are retained via [mediaControllerHolder] because the events
 * observed (onPlaybackStateChanged buffering/ended, onPlayerError with Media3-specific
 * error codes, currentMediaItemIndex/currentPosition polling) are NOT surfaced by
 * [PlaybackManager] or [PlaybackController]. Removing them would require extending
 * PlaybackManager with isBuffering, error, and per-item position flows — a separate
 * task deferred beyond Task 6.
 */
class PlayerViewModel(
    private val playbackManager: PlaybackManager,
    private val playbackController: PlaybackController,
    private val networkMonitor: NetworkMonitor,
    // TODO(Task-6-followup): mediaControllerHolder retained solely for Player.Listener
    //  registration and updatePosition() polling. Remove once PlaybackManager exposes
    //  isBuffering, playback errors, and per-mediaItem position flows.
    private val mediaControllerHolder: MediaControllerHolder,
) : ViewModel() {
    private var positionUpdateJob: Job? = null
    private var playerListener: Player.Listener? = null

    val state: StateFlow<PlayerUiState>
        field = MutableStateFlow(PlayerUiState())

    private var currentTimeline: PlaybackTimeline? = null

    init {
        // Acquire reference to shared controller
        playbackController.acquire()
    }

    private val mediaController: androidx.media3.session.MediaController?
        get() = mediaControllerHolder.controller

    /**
     * Start playback of a book.
     * Prepares the player, builds media items, and begins playback.
     */
    fun playBook(bookId: BookId) {
        viewModelScope.launch {
            state.value = state.value.copy(isLoading = true, error = null)

            // Observe prepare progress during timeline building
            val progressJob =
                launch {
                    playbackManager.prepareProgress.collect { progress ->
                        if (progress != null) {
                            state.value =
                                state.value.copy(
                                    prepareProgress = progress.progress,
                                    prepareMessage = progress.message,
                                )
                        } else {
                            state.value =
                                state.value.copy(
                                    prepareProgress = null,
                                    prepareMessage = null,
                                )
                        }
                    }
                }

            val result = playbackManager.prepareForPlayback(bookId)
            progressJob.cancel() // Stop observing once prepare is done

            if (result == null) {
                val errorMessage =
                    if (!networkMonitor.isOnline()) {
                        "Can't play this book offline. Download it first."
                    } else {
                        "Failed to load book"
                    }
                state.value =
                    state.value.copy(
                        isLoading = false,
                        prepareProgress = null,
                        prepareMessage = null,
                        error = errorMessage,
                    )
                return@launch
            }

            currentTimeline = result.timeline

            state.value =
                state.value.copy(
                    bookTitle = result.bookTitle,
                    totalDurationMs = result.timeline.totalDurationMs,
                    currentPositionMs = result.resumePositionMs,
                    playbackSpeed = result.resumeSpeed,
                    prepareProgress = null,
                    prepareMessage = null,
                )

            // Activate book ID now — NowPlaying observes this to show UI.
            // This must happen AFTER prepare succeeds (UI layer gates reachability).
            playbackManager.activateBook(bookId)

            // Connect to the player and start
            connectAndPlay(result)
        }
    }

    private suspend fun connectAndPlay(prepareResult: PlaybackManager.PrepareResult) {
        try {
            // Remove old listener if any, then register fresh one.
            // TODO(Task-6-followup): listener registration uses mediaControllerHolder
            //  directly because Player.Listener surfaces events (isBuffering, error codes,
            //  playback state) not yet exposed through PlaybackController or PlaybackManager.
            mediaControllerHolder.withController { controller ->
                playerListener?.let { controller.removeListener(it) }
                val listener = PlayerListener()
                playerListener = listener
                controller.addListener(listener)
            }

            // Build queue from timeline and hand off to PlaybackController.
            logger.debug { "Building ${prepareResult.timeline.files.size} media items" }
            val items = buildPlaybackMediaItems(prepareResult)
            playbackController.setMediaQueue(items, prepareResult.resumePositionMs)
            playbackController.setPlaybackSpeed(prepareResult.resumeSpeed)
            logger.debug { "Calling playbackController.play()..." }
            playbackController.play()

            playbackManager.setPlaying(true)

            state.value =
                state.value.copy(
                    isLoading = false,
                    isPlaying = true,
                )

            startPositionUpdates()
            logger.info { "Playback started for: ${prepareResult.bookTitle}" }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Failed to start playback" }
            state.value =
                state.value.copy(
                    isLoading = false,
                    error = "Failed to start playback",
                )
        }
    }

    private fun buildPlaybackMediaItems(prepareResult: PlaybackManager.PrepareResult): List<PlaybackMediaItem> =
        prepareResult.timeline.files.map { file ->
            PlaybackMediaItem(
                mediaId = file.audioFileId,
                uri = file.playbackUri,
                localPath = file.localPath,
                durationMs = file.durationMs,
                offsetMs = file.startOffsetMs,
                title = prepareResult.bookTitle,
                artist = prepareResult.bookAuthor,
                albumTitle = prepareResult.seriesName,
                artworkUri = prepareResult.coverPath?.let { "file://$it" },
            )
        }

    /**
     * Toggle play/pause.
     */
    fun togglePlayPause() {
        if (state.value.isPlaying) {
            playbackController.pause()
        } else {
            playbackController.play()
        }
    }

    /**
     * Seek to a position in the book timeline.
     */
    fun seekTo(bookPositionMs: Long) {
        playbackController.seekTo(bookPositionMs)
        state.value = state.value.copy(currentPositionMs = bookPositionMs)

        // Notify PlaybackManager so NowPlayingViewModel updates immediately (even when paused)
        playbackManager.updatePosition(bookPositionMs)
    }

    /**
     * Skip forward by milliseconds.
     */
    fun skipForward(ms: Long = 30_000) {
        val currentPos = state.value.currentPositionMs
        val newPos = (currentPos + ms).coerceAtMost(state.value.totalDurationMs)
        seekTo(newPos)
    }

    /**
     * Skip backward by milliseconds.
     */
    fun skipBackward(ms: Long = 10_000) {
        val currentPos = state.value.currentPositionMs
        val newPos = (currentPos - ms).coerceAtLeast(0)
        seekTo(newPos)
    }

    /**
     * Set playback speed.
     * Marks the book as having a custom speed (hasCustomSpeed=true).
     */
    fun setSpeed(speed: Float) {
        playbackController.setPlaybackSpeed(speed)
        state.value = state.value.copy(playbackSpeed = speed)
        // Notify PlaybackManager that user explicitly changed speed
        playbackManager.onSpeedChanged(speed)
    }

    /**
     * Reset speed to universal default.
     * Marks the book as using the universal default (hasCustomSpeed=false).
     *
     * @param defaultSpeed The universal default speed from settings
     */
    fun resetSpeedToDefault(defaultSpeed: Float) {
        playbackController.setPlaybackSpeed(defaultSpeed)
        state.value = state.value.copy(playbackSpeed = defaultSpeed)
        // Notify PlaybackManager that user reset to default
        playbackManager.onSpeedReset(defaultSpeed)
    }

    /**
     * Cycle through common playback speeds.
     */
    fun cycleSpeed() {
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)
        val currentSpeed = state.value.playbackSpeed
        val currentIndex = speeds.indexOfFirst { it >= currentSpeed - 0.01f }
        val nextIndex = if (currentIndex == -1 || currentIndex >= speeds.lastIndex) 0 else currentIndex + 1
        setSpeed(speeds[nextIndex])
    }

    /**
     * Stop playback and release resources.
     */
    fun stop() {
        playbackController.stop()
        playbackManager.setPlaying(false)
        playbackManager.clearPlayback()
        state.value = PlayerUiState()
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
        // TODO(Task-6-followup): position polling reads directly from mediaControllerHolder
        //  because PlaybackController is command-only and PlaybackManager does not expose
        //  per-mediaItem index + position as flows on Android. Migrate once those flows exist.
        val controller = mediaController ?: return
        val timeline = currentTimeline ?: return

        val mediaItemIndex = controller.currentMediaItemIndex
        val positionInFile = controller.currentPosition
        val playbackState = controller.playbackState
        val isPlaying = controller.isPlaying
        val isBuffering = playbackState == Player.STATE_BUFFERING

        // Validate ExoPlayer values before using them (silent validation, only log errors)
        if (mediaItemIndex < 0 || mediaItemIndex >= timeline.files.size || positionInFile < 0) {
            logger.error {
                "INVALID position: mediaItem=$mediaItemIndex/${timeline.files.size}, posInFile=$positionInFile"
            }
            return
        }

        val bookPosition =
            timeline.toBookPosition(
                mediaItemIndex = mediaItemIndex,
                positionInFileMs = positionInFile,
            )

        // Validate calculated book position
        if (bookPosition < 0 || bookPosition > timeline.totalDurationMs + 1000) {
            logger.error { "INVALID bookPosition: $bookPosition (duration=${timeline.totalDurationMs})" }
            return
        }

        // Debounce: only update state if position changed significantly (>100ms) or playback state changed
        val currentState = state.value
        val positionDelta = kotlin.math.abs(bookPosition - currentState.currentPositionMs)
        val stateChanged = isPlaying != currentState.isPlaying || isBuffering != currentState.isBuffering

        if (!stateChanged && positionDelta < 100) {
            return // Skip very minor position updates to reduce recomposition
        }

        state.value =
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
            state.value = state.value.copy(isPlaying = isPlaying)
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
            state.value = state.value.copy(isBuffering = isBuffering)

            if (playbackState == Player.STATE_ENDED) {
                state.value =
                    state.value.copy(
                        isPlaying = false,
                        isFinished = true,
                    )
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            logger.error { "ExoPlayer error: ${error.errorCodeName} - ${error.message}" }
            val isNetworkError =
                error.errorCode in
                    listOf(
                        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                        PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
                        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                    )
            val message =
                if (isNetworkError) {
                    "Couldn't connect to server. Download this book for offline listening."
                } else {
                    "Playback error: ${error.localizedMessage ?: "Unknown error"}"
                }
            state.value =
                state.value.copy(
                    isPlaying = false,
                    isLoading = false,
                    error = message,
                )
            playbackManager.setPlaying(false)
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            state.value = state.value.copy(playbackSpeed = playbackParameters.speed)
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
        playbackController.release()
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
    // Transcode preparation progress (null = not preparing, 0-100 = preparing)
    val prepareProgress: Int? = null,
    val prepareMessage: String? = null,
) {
    val isPreparing: Boolean get() = prepareProgress != null
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
