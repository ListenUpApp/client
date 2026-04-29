@file:Suppress("MagicNumber")

package com.calypsan.listenup.client.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.domain.repository.NetworkMonitor
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for playback UI.
 *
 * Responsibilities:
 * - Uses PlaybackController seam for player commands
 * - Exposes playback state as flows for Compose
 * - Mirrors PlaybackManager flows into PlayerUiState
 * - Provides control actions
 */
class PlayerViewModel(
    private val playbackManager: PlaybackManager,
    private val playbackController: PlaybackController,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {
    val state: StateFlow<PlayerUiState>
        field = MutableStateFlow(PlayerUiState())

    init {
        playbackController.acquire()

        viewModelScope.launch {
            playbackManager.isPlaying.collect { isPlaying ->
                state.value = state.value.copy(isPlaying = isPlaying)
            }
        }
        viewModelScope.launch {
            playbackManager.isBuffering.collect { isBuffering ->
                state.value = state.value.copy(isBuffering = isBuffering)
            }
        }
        viewModelScope.launch {
            playbackManager.playbackState.collect { playbackState ->
                if (playbackState == PlaybackState.Ended) {
                    state.value = state.value.copy(isPlaying = false, isFinished = true)
                }
            }
        }
        viewModelScope.launch {
            playbackManager.playbackSpeed.collect { speed ->
                state.value = state.value.copy(playbackSpeed = speed)
            }
        }
        viewModelScope.launch {
            playbackManager.currentPositionMs.collect { positionMs ->
                state.value = state.value.copy(currentPositionMs = positionMs)
            }
        }
        viewModelScope.launch {
            playbackManager.totalDurationMs.collect { durationMs ->
                state.value = state.value.copy(totalDurationMs = durationMs)
            }
        }
        viewModelScope.launch {
            playbackManager.playbackError.collect { error ->
                if (error != null) {
                    state.value =
                        state.value.copy(
                            isPlaying = false,
                            isLoading = false,
                            error = error.message,
                        )
                }
            }
        }
    }

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

    override fun onCleared() {
        super.onCleared()
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
