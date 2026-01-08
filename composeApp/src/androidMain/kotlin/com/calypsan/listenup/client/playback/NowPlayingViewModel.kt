@file:Suppress("MagicNumber", "TooManyFunctions")

package com.calypsan.listenup.client.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackParameters
import androidx.media3.session.MediaController
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
 * Uses shared MediaControllerHolder for playback control.
 */
class NowPlayingViewModel(
    private val playbackManager: PlaybackManager,
    private val bookRepository: BookRepository,
    private val sleepTimerManager: SleepTimerManager,
    private val mediaControllerHolder: MediaControllerHolder,
    private val playbackPreferences: PlaybackPreferences,
) : ViewModel() {
    val state: StateFlow<NowPlayingState>
        field = MutableStateFlow(NowPlayingState())

    val sleepTimerState: StateFlow<SleepTimerState> = sleepTimerManager.state

    private var lastNotifiedChapterIndex: Int = -1

    companion object {
        private const val FADE_DURATION_MS = 3000L
    }

    private val mediaController: MediaController?
        get() = mediaControllerHolder.controller

    init {
        // Acquire reference to shared controller
        mediaControllerHolder.acquire()
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
        val controller = mediaController
        if (controller == null) {
            logger.warn { "Cannot fade out: no MediaController" }
            sleepTimerManager.onFadeCompleted()
            return
        }

        logger.info { "Starting volume fade out" }

        val steps = 30
        val stepDelay = FADE_DURATION_MS / steps
        val volumeStep = 1f / steps

        var currentVolume = 1f

        repeat(steps) {
            currentVolume = (currentVolume - volumeStep).coerceAtLeast(0f)
            controller.volume = currentVolume
            delay(stepDelay)
        }

        // Pause playback
        controller.pause()

        // Brief delay then restore volume for next play
        delay(100)
        controller.volume = 1f

        logger.info { "Fade complete, playback paused" }
        sleepTimerManager.onFadeCompleted()
    }

    private suspend fun loadBookInfo(bookId: BookId) {
        logger.debug { "loadBookInfo called for bookId=${bookId.value}" }
        val book = bookRepository.getBook(bookId.value)
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
        mediaController?.stop()
        playbackManager.clearPlayback()
        sleepTimerManager.cancelTimer()
        collapse()
    }

    // Playback Actions

    fun playPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    fun skipBack(seconds: Int = 10) {
        logger.debug { "skipBack called: seconds=$seconds" }
        val controller = mediaController
        if (controller == null) {
            logger.warn { "skipBack: mediaController is null" }
            return
        }
        val timeline = playbackManager.currentTimeline.value
        if (timeline == null) {
            logger.warn { "skipBack: timeline is null" }
            return
        }

        val currentBookPos = playbackManager.currentPositionMs.value
        val newBookPos = (currentBookPos - seconds * 1000).coerceAtLeast(0)
        logger.debug { "skipBack: currentPos=$currentBookPos, newPos=$newBookPos" }

        val position = timeline.resolve(newBookPos)
        logger.debug { "skipBack: resolved to mediaItemIndex=${position.mediaItemIndex}, positionInFile=${position.positionInFileMs}" }

        if (safeSeekTo(controller, position.mediaItemIndex, position.positionInFileMs, "skipBack")) {
            // Update PlaybackManager so UI updates immediately (even when paused)
            playbackManager.updatePosition(newBookPos)
        }
    }

    fun skipForward(seconds: Int = 30) {
        logger.debug { "skipForward called: seconds=$seconds" }
        val controller = mediaController
        if (controller == null) {
            logger.warn { "skipForward: mediaController is null" }
            return
        }
        val timeline = playbackManager.currentTimeline.value
        if (timeline == null) {
            logger.warn { "skipForward: timeline is null" }
            return
        }

        val currentBookPos = playbackManager.currentPositionMs.value
        val totalDuration = playbackManager.totalDurationMs.value
        val newBookPos = (currentBookPos + seconds * 1000).coerceAtMost(totalDuration)
        logger.debug { "skipForward: currentPos=$currentBookPos, newPos=$newBookPos, totalDuration=$totalDuration" }

        val position = timeline.resolve(newBookPos)
        logger.debug { "skipForward: resolved to mediaItemIndex=${position.mediaItemIndex}, positionInFile=${position.positionInFileMs}" }

        if (safeSeekTo(controller, position.mediaItemIndex, position.positionInFileMs, "skipForward")) {
            // Update PlaybackManager so UI updates immediately (even when paused)
            playbackManager.updatePosition(newBookPos)
        }
    }

    /**
     * Safe wrapper around MediaController.seekTo that validates parameters
     * before calling to prevent native crashes from invalid values.
     *
     * @return true if seek was attempted, false if validation failed
     */
    private fun safeSeekTo(
        controller: androidx.media3.session.MediaController,
        mediaItemIndex: Int,
        positionMs: Long,
        caller: String,
    ): Boolean {
        // Validate mediaItemIndex
        val mediaItemCount = controller.mediaItemCount
        if (mediaItemIndex < 0) {
            logger.error { "$caller: INVALID mediaItemIndex=$mediaItemIndex (negative!)" }
            return false
        }
        if (mediaItemIndex >= mediaItemCount) {
            logger.error { "$caller: INVALID mediaItemIndex=$mediaItemIndex >= mediaItemCount=$mediaItemCount" }
            return false
        }

        // Validate position
        if (positionMs < 0) {
            logger.error { "$caller: INVALID positionMs=$positionMs (negative!)" }
            return false
        }

        logger.debug {
            "$caller: safeSeekTo - mediaItemCount=$mediaItemCount, " +
                "currentMediaItemIndex=${controller.currentMediaItemIndex}, " +
                "playbackState=${controller.playbackState}"
        }

        return try {
            controller.seekTo(mediaItemIndex, positionMs)
            logger.debug { "$caller: seekTo completed successfully" }
            true
        } catch (e: Exception) {
            logger.error(e) { "$caller: seekTo threw exception" }
            false
        }
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
        val controller = mediaController
        if (controller == null) {
            logger.warn { "seekToChapter: mediaController is null" }
            return
        }
        val timeline = playbackManager.currentTimeline.value
        if (timeline == null) {
            logger.warn { "seekToChapter: timeline is null" }
            return
        }

        logger.debug { "seekToChapter: chapter='${chapter.title}', startTime=${chapter.startTime}" }
        val position = timeline.resolve(chapter.startTime)
        logger.debug { "seekToChapter: resolved to mediaItemIndex=${position.mediaItemIndex}, positionInFile=${position.positionInFileMs}" }

        if (safeSeekTo(controller, position.mediaItemIndex, position.positionInFileMs, "seekToChapter")) {
            // Update PlaybackManager so UI updates immediately (even when paused)
            playbackManager.updatePosition(chapter.startTime)
        }
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
        val controller = mediaController
        if (controller == null) {
            logger.warn { "seekWithinChapter: mediaController is null" }
            return
        }
        val timeline = playbackManager.currentTimeline.value
        if (timeline == null) {
            logger.warn { "seekWithinChapter: timeline is null" }
            return
        }

        val targetPosition = currentChapter.startTime + (currentChapter.duration * progress).toLong()
        logger.debug { "seekWithinChapter: chapter='${currentChapter.title}', targetPosition=$targetPosition" }

        val position = timeline.resolve(targetPosition)
        logger.debug { "seekWithinChapter: resolved to mediaItemIndex=${position.mediaItemIndex}, positionInFile=${position.positionInFileMs}" }

        if (safeSeekTo(controller, position.mediaItemIndex, position.positionInFileMs, "seekWithinChapter")) {
            // Update PlaybackManager so UI updates immediately (even when paused)
            playbackManager.updatePosition(targetPosition)
        }
    }

    /**
     * Set playback speed.
     * Marks the book as having a custom speed (hasCustomSpeed=true).
     */
    fun setSpeed(speed: Float) {
        mediaController?.playbackParameters = PlaybackParameters(speed)
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
            mediaController?.playbackParameters = PlaybackParameters(defaultSpeed)
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
        mediaControllerHolder.release()
    }
}
