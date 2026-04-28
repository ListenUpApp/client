@file:Suppress("MagicNumber", "TooManyFunctions")

package com.calypsan.listenup.client.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.domain.model.Chapter
import com.calypsan.listenup.client.domain.repository.BookRepository
import com.calypsan.listenup.client.domain.repository.PlaybackPreferences
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for Now Playing UI (mini player and full screen).
 *
 * Observes PlaybackManager state and transforms it into UI state.
 * Handles chapter-level position calculations.
 * Uses [PlaybackController] for all command-side operations.
 */
class NowPlayingViewModel(
    private val playbackManager: PlaybackManager,
    private val bookRepository: BookRepository,
    private val sleepTimerManager: SleepTimerManager,
    private val playbackController: PlaybackController,
    private val playbackPreferences: PlaybackPreferences,
) : ViewModel() {
    val state: StateFlow<NowPlayingState>
        field = MutableStateFlow(NowPlayingState())

    val sleepTimerState: StateFlow<SleepTimerState> = sleepTimerManager.state

    private var lastNotifiedChapterIndex: Int = -1

    companion object {
        private const val FADE_DURATION_MS = 3000L
    }

    init {
        // Acquire reference to shared controller
        playbackController.acquire()
        observePlayback()
        observeSleepTimer()
        loadDefaultPlaybackSpeed()
    }

    private fun loadDefaultPlaybackSpeed() {
        viewModelScope.launch {
            val defaultSpeed = playbackPreferences.getDefaultPlaybackSpeed()
            state.update { it.copy(defaultPlaybackSpeed = defaultSpeed) }
        }
    }

    private fun observePlayback() {
        // Observe current book
        viewModelScope.launch {
            playbackManager.currentBookId.collect { bookId ->
                if (bookId != null) {
                    loadBookInfo(bookId)
                } else {
                    state.update { it.copy(isVisible = false, isExpanded = false) }
                }
            }
        }

        // Observe playback state (playing/paused)
        viewModelScope.launch {
            playbackManager.isPlaying.collect { isPlaying ->
                state.update { it.copy(isPlaying = isPlaying) }
            }
        }

        // Observe position updates
        viewModelScope.launch {
            playbackManager.currentPositionMs.collect { positionMs ->
                updatePosition(positionMs)
            }
        }

        // Observe current chapter from PlaybackManager
        viewModelScope.launch {
            playbackManager.currentChapter.collect { chapterInfo ->
                if (chapterInfo != null) {
                    // Notify sleep timer manager of chapter changes (for end-of-chapter mode)
                    if (chapterInfo.index != lastNotifiedChapterIndex) {
                        lastNotifiedChapterIndex = chapterInfo.index
                        sleepTimerManager.onChapterChanged(chapterInfo.index)
                    }

                    state.update {
                        it.copy(
                            chapterIndex = chapterInfo.index,
                            chapterTitle = chapterInfo.title,
                            totalChapters = chapterInfo.totalChapters,
                            chapterDurationMs = chapterInfo.endMs - chapterInfo.startMs,
                        )
                    }
                }
            }
        }

        // Observe playback speed
        viewModelScope.launch {
            playbackManager.playbackSpeed.collect { speed ->
                state.update { it.copy(playbackSpeed = speed) }
            }
        }

        // Observe total duration
        viewModelScope.launch {
            playbackManager.totalDurationMs.collect { durationMs ->
                state.update { it.copy(bookDurationMs = durationMs) }
            }
        }

        // Observe prepare progress (transcode status)
        viewModelScope.launch {
            playbackManager.prepareProgress.collect { progress ->
                state.update {
                    it.copy(
                        isPreparing = progress != null,
                        prepareProgress = progress?.progress ?: 0,
                        prepareMessage = progress?.message,
                    )
                }
            }
        }
    }

    private fun observeSleepTimer() {
        // Handle sleep timer events (fade out and pause)
        viewModelScope.launch {
            sleepTimerManager.sleepEvent.collect {
                fadeOutAndPause()
            }
        }
    }

    /**
     * Gradually reduce volume to zero, then pause.
     * Called when sleep timer fires.
     */
    private suspend fun fadeOutAndPause() {
        logger.info { "Starting volume fade out" }

        val steps = 30
        val stepDelay = FADE_DURATION_MS / steps
        val volumeStep = 1f / steps

        var currentVolume = 1f

        repeat(steps) {
            currentVolume = (currentVolume - volumeStep).coerceAtLeast(0f)
            playbackController.setVolume(currentVolume)
            delay(stepDelay)
        }

        // Pause playback
        playbackController.pause()

        // Brief delay then restore volume for next play
        delay(100)
        playbackController.setVolume(1f)

        logger.info { "Fade complete, playback paused" }
        sleepTimerManager.onFadeCompleted()
    }

    private suspend fun loadBookInfo(bookId: BookId) {
        logger.debug { "loadBookInfo called for bookId=${bookId.value}" }
        val book = bookRepository.getBookListItem(bookId.value)
        if (book == null) {
            logger.warn { "Book not found: $bookId" }
            return
        }

        // Chapters are now loaded by PlaybackManager and observed via currentChapter flow
        val chapters = playbackManager.chapters.value

        state.update {
            it.copy(
                isVisible = true,
                bookId = bookId.value,
                title = book.title,
                author = book.authorNames,
                coverUrl = book.coverPath,
                coverBlurHash = book.coverBlurHash,
                authors = book.authors,
                narrators = book.narrators,
                seriesId = book.seriesId,
                seriesName = book.seriesName,
                bookDurationMs = book.duration,
                totalChapters = chapters.size,
            )
        }

        logger.debug { "Loaded book info: ${book.title}, ${chapters.size} chapters" }
    }

    private fun updatePosition(bookPositionMs: Long) {
        val bookDurationMs = state.value.bookDurationMs
        val bookProgress =
            if (bookDurationMs > 0) {
                bookPositionMs.toFloat() / bookDurationMs
            } else {
                0f
            }

        // Chapter info comes from PlaybackManager.currentChapter observer
        val chapterInfo = playbackManager.currentChapter.value
        val (chapterProgress, chapterPositionMs) =
            if (chapterInfo != null) {
                val posInChapter = bookPositionMs - chapterInfo.startMs
                val chapterDuration = chapterInfo.endMs - chapterInfo.startMs
                val progress =
                    if (chapterDuration > 0) {
                        posInChapter.toFloat() / chapterDuration
                    } else {
                        0f
                    }
                progress.coerceIn(0f, 1f) to posInChapter.coerceAtLeast(0)
            } else {
                0f to 0L
            }

        // Only log errors for invalid progress values
        @Suppress("ComplexCondition")
        if (bookProgress.isNaN() ||
            bookProgress.isInfinite() ||
            chapterProgress.isNaN() ||
            chapterProgress.isInfinite()
        ) {
            logger.error { "Invalid progress: book=$bookProgress, chapter=$chapterProgress, duration=$bookDurationMs" }
            return // Don't update state with invalid values
        }

        // Debounce: only update if position changed by >200ms to prevent excessive recomposition
        val currentState = state.value
        val positionDeltaMs = kotlin.math.abs(bookPositionMs - currentState.bookPositionMs)

        // Skip update if change is too small (time-based, not percentage-based)
        if (positionDeltaMs < 200) {
            return
        }

        state.update {
            it.copy(
                bookProgress = bookProgress.coerceIn(0f, 1f),
                bookPositionMs = bookPositionMs,
                chapterProgress = chapterProgress,
                chapterPositionMs = chapterPositionMs,
            )
        }
    }

    // UI Actions

    fun expand() {
        state.update { it.copy(isExpanded = true) }
    }

    fun collapse() {
        state.update { it.copy(isExpanded = false) }
    }

    fun showChapterPicker() {
        state.update { it.copy(showChapterPicker = true) }
    }

    fun hideChapterPicker() {
        state.update { it.copy(showChapterPicker = false) }
    }

    fun showSpeedPicker() {
        state.update { it.copy(showSpeedPicker = true) }
    }

    fun hideSpeedPicker() {
        state.update { it.copy(showSpeedPicker = false) }
    }

    fun showSleepTimer() {
        state.update { it.copy(showSleepTimer = true) }
    }

    fun hideSleepTimer() {
        state.update { it.copy(showSleepTimer = false) }
    }

    // Sleep Timer Actions

    fun setSleepTimer(mode: SleepTimerMode) {
        sleepTimerManager.setTimer(mode)
        hideSleepTimer()
    }

    fun cancelSleepTimer() {
        sleepTimerManager.cancelTimer()
    }

    fun extendSleepTimer(minutes: Int) {
        sleepTimerManager.extendTimer(minutes)
    }

    // Contributor Picker Actions

    fun showContributorPicker(type: ContributorPickerType) {
        state.update { it.copy(showContributorPicker = type) }
    }

    fun hideContributorPicker() {
        state.update { it.copy(showContributorPicker = null) }
    }

    // Book Actions

    /**
     * Close the book entirely - stops playback and clears state.
     */
    fun closeBook() {
        playbackController.stop()
        playbackManager.clearPlayback()
        sleepTimerManager.cancelTimer()
        collapse()
    }

    // Playback Actions

    fun playPause() {
        if (state.value.isPlaying) {
            playbackController.pause()
        } else {
            playbackController.play()
        }
    }

    fun skipBack(seconds: Int = 10) {
        logger.debug { "skipBack called: seconds=$seconds" }
        val timeline = playbackManager.currentTimeline.value
        if (timeline == null) {
            logger.warn { "skipBack: timeline is null" }
            return
        }

        val currentBookPos = playbackManager.currentPositionMs.value
        val newBookPos = (currentBookPos - (seconds * state.value.playbackSpeed * 1000).toLong()).coerceAtLeast(0)
        logger.debug { "skipBack: currentPos=$currentBookPos, newPos=$newBookPos" }

        playbackController.seekTo(newBookPos)
        // Update PlaybackManager so UI updates immediately (even when paused)
        playbackManager.updatePosition(newBookPos)
    }

    fun skipForward(seconds: Int = 30) {
        logger.debug { "skipForward called: seconds=$seconds" }
        val timeline = playbackManager.currentTimeline.value
        if (timeline == null) {
            logger.warn { "skipForward: timeline is null" }
            return
        }

        val currentBookPos = playbackManager.currentPositionMs.value
        val totalDuration = playbackManager.totalDurationMs.value
        val newBookPos =
            (currentBookPos + (seconds * state.value.playbackSpeed * 1000).toLong()).coerceAtMost(
                totalDuration,
            )
        logger.debug { "skipForward: currentPos=$currentBookPos, newPos=$newBookPos, totalDuration=$totalDuration" }

        playbackController.seekTo(newBookPos)
        // Update PlaybackManager so UI updates immediately (even when paused)
        playbackManager.updatePosition(newBookPos)
    }

    fun previousChapter() {
        logger.debug { "previousChapter called: current=${state.value.chapterIndex}" }
        val newIndex = (state.value.chapterIndex - 1).coerceAtLeast(0)
        seekToChapter(newIndex)
    }

    fun nextChapter() {
        val chapters = playbackManager.chapters.value
        logger.debug { "nextChapter called: current=${state.value.chapterIndex}, total=${chapters.size}" }
        val newIndex = (state.value.chapterIndex + 1).coerceAtMost(chapters.lastIndex.coerceAtLeast(0))
        seekToChapter(newIndex)
    }

    fun seekToChapter(index: Int) {
        val chapters = playbackManager.chapters.value
        logger.debug { "seekToChapter called: index=$index, chaptersSize=${chapters.size}" }
        val chapter = chapters.getOrNull(index)
        if (chapter == null) {
            logger.warn { "seekToChapter: chapter at index $index not found" }
            return
        }

        logger.debug { "seekToChapter: chapter='${chapter.title}', startTime=${chapter.startTime}" }
        playbackController.seekTo(chapter.startTime)
        // Update PlaybackManager so UI updates immediately (even when paused)
        playbackManager.updatePosition(chapter.startTime)
        hideChapterPicker()
    }

    fun seekWithinChapter(progress: Float) {
        logger.debug { "seekWithinChapter called: progress=$progress" }
        val chapters = playbackManager.chapters.value
        val currentChapter = chapters.getOrNull(state.value.chapterIndex)
        if (currentChapter == null) {
            logger.warn { "seekWithinChapter: no current chapter" }
            return
        }

        val targetPosition = currentChapter.startTime + (currentChapter.duration * progress).toLong()
        logger.debug { "seekWithinChapter: chapter='${currentChapter.title}', targetPosition=$targetPosition" }

        playbackController.seekTo(targetPosition)
        // Update PlaybackManager so UI updates immediately (even when paused)
        playbackManager.updatePosition(targetPosition)
    }

    /**
     * Set playback speed.
     * Marks the book as having a custom speed (hasCustomSpeed=true).
     */
    fun setSpeed(speed: Float) {
        playbackController.setPlaybackSpeed(speed)
        // Notify PlaybackManager that user explicitly changed speed
        playbackManager.onSpeedChanged(speed)
    }

    /**
     * Reset speed to universal default.
     * Marks the book as using the universal default (hasCustomSpeed=false).
     */
    fun resetSpeedToDefault() {
        viewModelScope.launch {
            val defaultSpeed = playbackPreferences.getDefaultPlaybackSpeed()
            playbackController.setPlaybackSpeed(defaultSpeed)
            // Notify PlaybackManager that user reset to default
            playbackManager.onSpeedReset(defaultSpeed)
        }
    }

    /**
     * Get the universal default playback speed.
     */
    suspend fun getDefaultPlaybackSpeed(): Float = playbackPreferences.getDefaultPlaybackSpeed()

    fun cycleSpeed() {
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)
        val currentSpeed = state.value.playbackSpeed
        val currentIndex = speeds.indexOfFirst { it >= currentSpeed - 0.01f }
        val nextIndex = if (currentIndex == -1 || currentIndex >= speeds.lastIndex) 0 else currentIndex + 1
        setSpeed(speeds[nextIndex])
    }

    fun getChapters(): List<Chapter> = playbackManager.chapters.value

    override fun onCleared() {
        super.onCleared()
        // Release our reference to the shared controller
        playbackController.release()
    }
}
