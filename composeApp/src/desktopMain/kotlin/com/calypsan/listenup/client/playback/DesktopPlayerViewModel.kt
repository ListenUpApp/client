@file:Suppress("MagicNumber", "TooManyFunctions")

package com.calypsan.listenup.client.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.domain.model.BookListItem
import com.calypsan.listenup.client.domain.model.Chapter
import com.calypsan.listenup.client.domain.repository.BookRepository
import com.calypsan.listenup.client.domain.repository.PlaybackPreferences
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * Desktop playback ViewModel.
 *
 * Composes [PlaybackManager] flows + book metadata + UI ephemera into a single
 * [NowPlayingScreenState] via layered `combine().stateIn(WhileSubscribed)`. Mirrors
 * the Android `NowPlayingViewModel` shape — the heavy upstream pipeline is independent
 * of overlay/expand ephemera, which join only at the screen-state boundary.
 *
 * Side-effect collectors (ProgressTracker pause/resume notifications + 30 s ticker +
 * GStreamer error reporting) live in separate `viewModelScope.launch { collect }` blocks,
 * not in the state-deriving combines.
 *
 * Desktop currently has no UI for overlays / expanded mode / sleep timer, but the screen
 * state still tail-combines those flows for shape parity with Android (and to enable
 * future Desktop UI without re-plumbing).
 */
class DesktopPlayerViewModel(
    private val playbackManager: PlaybackManager,
    private val playbackController: PlaybackController,
    private val audioPlayer: AudioPlayer,
    private val progressTracker: ProgressTracker,
    private val bookRepository: BookRepository,
    private val playbackPreferences: PlaybackPreferences,
    private val sleepTimerManager: SleepTimerManager,
) : ViewModel() {
    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
        const val PROGRESS_TICK_MS = 30_000L
    }

    private val overlayFlow = MutableStateFlow<NowPlayingOverlay>(NowPlayingOverlay.None)
    private val isExpandedFlow = MutableStateFlow(false)
    private val defaultPlaybackSpeedFlow = MutableStateFlow(1.0f)

    private var periodicUpdateJob: Job? = null

    /** Reactive book metadata for the current book id. One-shot fetch on bookId change via flatMapLatest. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val bookFlow: Flow<BookListItem?> =
        playbackManager.currentBookId.flatMapLatest { bookId ->
            flow {
                emit(loadBook(bookId))
            }
        }

    /** Aggregated playback dynamics (4 flows joined into one). */
    private val dynamicsRawFlow: Flow<DynamicsRaw> =
        combine(
            playbackManager.isPlaying,
            playbackManager.isBuffering,
            playbackManager.currentPositionMs,
            playbackManager.totalDurationMs,
        ) { isPlaying, isBuffering, position, duration ->
            DynamicsRaw(
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                currentPositionMs = position,
                totalDurationMs = duration,
            )
        }

    /** Aggregated surface metadata (chapter info + prepare progress + error + default speed). */
    private val surfaceMetadataFlow: Flow<SurfaceMetadata> =
        combine(
            playbackManager.currentChapter,
            playbackManager.prepareProgress,
            playbackManager.playbackError,
            defaultPlaybackSpeedFlow,
        ) { chapter, prepare, error, defaultSpeed ->
            SurfaceMetadata(
                currentChapter = chapter,
                prepareProgress = prepare,
                error = error,
                defaultPlaybackSpeed = defaultSpeed,
            )
        }

    /** Sealed playback state derived from book + dynamics + metadata via the pure mapper. */
    private val nowPlayingState: Flow<NowPlayingState> =
        combine(
            bookFlow,
            dynamicsRawFlow,
            playbackManager.playbackSpeed,
            surfaceMetadataFlow,
        ) { book, dynamicsRaw, speed, metadata ->
            mapToNowPlayingState(
                book = book,
                dynamics =
                    PlaybackDynamics(
                        isPlaying = dynamicsRaw.isPlaying,
                        isBuffering = dynamicsRaw.isBuffering,
                        currentPositionMs = dynamicsRaw.currentPositionMs,
                        totalDurationMs = dynamicsRaw.totalDurationMs,
                        playbackSpeed = speed,
                    ),
                metadata = metadata,
            )
        }

    /** Tail-combined screen state; the only flow the UI subscribes to. */
    val screenState: StateFlow<NowPlayingScreenState> =
        combine(
            nowPlayingState,
            overlayFlow,
            isExpandedFlow,
            sleepTimerManager.state,
        ) { state, overlay, isExpanded, sleepTimer ->
            NowPlayingScreenState(
                state = state,
                overlay = overlay,
                isExpanded = isExpanded,
                sleepTimerState = sleepTimer,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue =
                NowPlayingScreenState(
                    state = NowPlayingState.Idle,
                    overlay = NowPlayingOverlay.None,
                    isExpanded = false,
                    sleepTimerState = sleepTimerManager.state.value,
                ),
        )

    init {
        // Load default playback speed (drives surfaceMetadataFlow)
        viewModelScope.launch {
            defaultPlaybackSpeedFlow.value = playbackPreferences.getDefaultPlaybackSpeed()
        }

        // Side effect: drive periodic ProgressTracker ticks while playing
        viewModelScope.launch {
            playbackManager.isPlaying.collect { isPlaying ->
                if (isPlaying) {
                    startPeriodicUpdates()
                } else {
                    stopPeriodicUpdates()
                }
            }
        }

        // Side effect: surface GStreamer / FFmpeg playback errors through the canonical
        // playbackError flow so the mapper produces NowPlayingState.Error.
        viewModelScope.launch {
            playbackManager.playbackState.collect { playbackState ->
                if (playbackState == PlaybackState.Error) {
                    playbackManager.reportError(
                        message = "Playback error. Check GStreamer installation.",
                        isRecoverable = false,
                    )
                } else if (playbackState == PlaybackState.Playing) {
                    playbackManager.clearError()
                }
            }
        }
    }

    private suspend fun loadBook(bookId: BookId?): BookListItem? {
        if (bookId == null) return null
        return try {
            bookRepository.getBookListItem(bookId.value)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "getBookListItem failed for ${bookId.value}" }
            null
        }
    }

    /**
     * Start playback of a book.
     */
    fun playBook(bookId: BookId) {
        viewModelScope.launch {
            val result = playbackManager.prepareForPlayback(bookId)
            if (result == null) {
                playbackManager.reportError(
                    message = "Failed to prepare playback",
                    isRecoverable = true,
                )
                logger.error { "Failed to prepare playback for $bookId" }
                return@launch
            }

            playbackManager.startPlayback(
                player = audioPlayer,
                resumePositionMs = result.resumePositionMs,
                resumeSpeed = result.resumeSpeed,
            )

            // Check if playback actually started; reportError if not
            if (playbackManager.playbackState.value == PlaybackState.Error) {
                playbackManager.reportError(
                    message = "Playback failed. Check that GStreamer plugins are installed correctly.",
                    isRecoverable = false,
                )
            }
        }
    }

    fun playPause() {
        val currentState = playbackManager.playbackState.value
        if (currentState == PlaybackState.Playing) {
            playbackController.pause()
            // Notify progress tracker of pause
            val bookId = playbackManager.currentBookId.value ?: return
            val positionMs = playbackManager.currentPositionMs.value
            val speed = playbackManager.playbackSpeed.value
            progressTracker.onPlaybackPaused(bookId, positionMs, speed)
        } else {
            playbackController.play()
            // Notify progress tracker of resume
            val bookId = playbackManager.currentBookId.value ?: return
            val positionMs = playbackManager.currentPositionMs.value
            val speed = playbackManager.playbackSpeed.value
            progressTracker.onPlaybackStarted(bookId, positionMs, speed)
        }
    }

    fun skipBack(seconds: Int = 10) {
        val currentPos = playbackManager.currentPositionMs.value
        val speed = playbackManager.playbackSpeed.value
        val newPos = (currentPos - (seconds * speed * 1000).toLong()).coerceAtLeast(0)
        playbackController.seekTo(newPos)
    }

    fun skipForward(seconds: Int = 30) {
        val currentPos = playbackManager.currentPositionMs.value
        val totalDuration = playbackManager.totalDurationMs.value
        val speed = playbackManager.playbackSpeed.value
        val newPos = (currentPos + (seconds * speed * 1000).toLong()).coerceAtMost(totalDuration)
        playbackController.seekTo(newPos)
    }

    fun seekToChapter(index: Int) {
        val chapters = playbackManager.chapters.value
        val chapter = chapters.getOrNull(index) ?: return
        playbackController.seekTo(chapter.startTime)
    }

    fun seekWithinChapter(progress: Float) {
        val chapters = playbackManager.chapters.value
        val currentIndex = playbackManager.currentChapter.value?.index ?: 0
        val currentChapter = chapters.getOrNull(currentIndex) ?: return
        val targetPosition = currentChapter.startTime + (currentChapter.duration * progress).toLong()
        playbackController.seekTo(targetPosition)
    }

    fun setSpeed(speed: Float) {
        playbackController.setPlaybackSpeed(speed)
        playbackManager.onSpeedChanged(speed)
    }

    fun resetSpeedToDefault() {
        viewModelScope.launch {
            val defaultSpeed = playbackPreferences.getDefaultPlaybackSpeed()
            defaultPlaybackSpeedFlow.value = defaultSpeed
            playbackController.setPlaybackSpeed(defaultSpeed)
            playbackManager.onSpeedReset(defaultSpeed)
        }
    }

    fun cycleSpeed() {
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)
        val currentSpeed = playbackManager.playbackSpeed.value
        val currentIndex = speeds.indexOfFirst { it >= currentSpeed - 0.01f }
        val nextIndex = if (currentIndex == -1 || currentIndex >= speeds.lastIndex) 0 else currentIndex + 1
        setSpeed(speeds[nextIndex])
    }

    fun previousChapter() {
        val currentIndex = playbackManager.currentChapter.value?.index ?: 0
        val newIndex = (currentIndex - 1).coerceAtLeast(0)
        seekToChapter(newIndex)
    }

    fun nextChapter() {
        val chapters = playbackManager.chapters.value
        val currentIndex = playbackManager.currentChapter.value?.index ?: 0
        val newIndex = (currentIndex + 1).coerceAtMost(chapters.lastIndex.coerceAtLeast(0))
        seekToChapter(newIndex)
    }

    fun closeBook() {
        playbackController.pause()
        val bookId = playbackManager.currentBookId.value
        if (bookId != null) {
            val positionMs = playbackManager.currentPositionMs.value
            val speed = playbackManager.playbackSpeed.value
            progressTracker.onPlaybackPaused(bookId, positionMs, speed)
        }

        playbackManager.clearPlayback()
    }

    fun getChapters(): List<Chapter> = playbackManager.chapters.value

    // === UI ephemera setters (mirror Android for shape parity) ===

    fun expand() {
        isExpandedFlow.value = true
    }

    fun collapse() {
        isExpandedFlow.value = false
    }

    fun showChapterPicker() {
        overlayFlow.value = NowPlayingOverlay.ChapterPicker
    }

    fun hideChapterPicker() {
        overlayFlow.value = NowPlayingOverlay.None
    }

    fun showSpeedPicker() {
        overlayFlow.value = NowPlayingOverlay.SpeedPicker
    }

    fun hideSpeedPicker() {
        overlayFlow.value = NowPlayingOverlay.None
    }

    fun showSleepTimer() {
        overlayFlow.value = NowPlayingOverlay.SleepTimer
    }

    fun hideSleepTimer() {
        overlayFlow.value = NowPlayingOverlay.None
    }

    fun showContributorPicker(type: ContributorPickerType) {
        overlayFlow.value = NowPlayingOverlay.ContributorPicker(type)
    }

    fun hideContributorPicker() {
        overlayFlow.value = NowPlayingOverlay.None
    }

    /**
     * Periodic updates for ProgressTracker (every 30 seconds during playback).
     */
    private fun startPeriodicUpdates() {
        stopPeriodicUpdates()
        periodicUpdateJob =
            viewModelScope.launch {
                while (isActive) {
                    delay(PROGRESS_TICK_MS)
                    val bookId = playbackManager.currentBookId.value ?: continue
                    val positionMs = playbackManager.currentPositionMs.value
                    val speed = playbackManager.playbackSpeed.value
                    progressTracker.onPositionUpdate(bookId, positionMs, speed)
                }
            }
    }

    private fun stopPeriodicUpdates() {
        periodicUpdateJob?.cancel()
        periodicUpdateJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopPeriodicUpdates()
    }

    private data class DynamicsRaw(
        val isPlaying: Boolean,
        val isBuffering: Boolean,
        val currentPositionMs: Long,
        val totalDurationMs: Long,
    )
}
