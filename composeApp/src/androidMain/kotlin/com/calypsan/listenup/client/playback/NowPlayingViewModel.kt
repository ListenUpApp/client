package com.calypsan.listenup.client.playback

import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.repository.BookRepository
import com.calypsan.listenup.client.domain.model.Chapter
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import android.util.Log
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}
private const val TAG = "NowPlayingVM"

/**
 * ViewModel for Now Playing UI (mini player and full screen).
 *
 * Observes PlaybackManager state and transforms it into UI state.
 * Handles chapter-level position calculations.
 * Connects independently to MediaController for playback control.
 */
class NowPlayingViewModel(
    private val context: Context,
    private val playbackManager: PlaybackManager,
    private val bookRepository: BookRepository,
    private val sleepTimerManager: SleepTimerManager
) : ViewModel() {

    private val _state = MutableStateFlow(NowPlayingState())
    val state: StateFlow<NowPlayingState> = _state.asStateFlow()

    val sleepTimerState: StateFlow<SleepTimerState> = sleepTimerManager.state

    private var chapters: List<Chapter> = emptyList()
    private var lastNotifiedChapterIndex: Int = -1

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    companion object {
        private const val FADE_DURATION_MS = 3000L
    }

    init {
        observePlayback()
        observeSleepTimer()
    }

    private fun observePlayback() {
        // Observe current book
        viewModelScope.launch {
            playbackManager.currentBookId.collect { bookId ->
                if (bookId != null) {
                    loadBookInfo(bookId)
                    connectToMediaController()
                } else {
                    _state.update { it.copy(isVisible = false, isExpanded = false) }
                    chapters = emptyList()
                    disconnectMediaController()
                }
            }
        }

        // Observe playback state (playing/paused)
        viewModelScope.launch {
            playbackManager.isPlaying.collect { isPlaying ->
                _state.update { it.copy(isPlaying = isPlaying) }
            }
        }

        // Observe position updates
        viewModelScope.launch {
            playbackManager.currentPositionMs.collect { positionMs ->
                updatePosition(positionMs)
            }
        }

        // Observe playback speed
        viewModelScope.launch {
            playbackManager.playbackSpeed.collect { speed ->
                _state.update { it.copy(playbackSpeed = speed) }
            }
        }

        // Observe total duration
        viewModelScope.launch {
            playbackManager.totalDurationMs.collect { durationMs ->
                _state.update { it.copy(bookDurationMs = durationMs) }
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
        Log.d(TAG, "loadBookInfo called for bookId=${bookId.value}")
        val book = bookRepository.getBook(bookId.value)
        if (book == null) {
            Log.w(TAG, "Book not found: $bookId")
            logger.warn { "Book not found: $bookId" }
            return
        }

        chapters = bookRepository.getChapters(bookId.value)

        // Log chapter data to diagnose crashes
        Log.d(TAG, "=== Chapters for book ${bookId.value} (${book.title}) ===")
        Log.d(TAG, "Book duration: ${book.duration}ms, Chapter count: ${chapters.size}")
        chapters.forEachIndexed { index, chapter ->
            Log.d(TAG, "  Chapter[$index]: '${chapter.title}', start=${chapter.startTime}ms, duration=${chapter.duration}ms, end=${chapter.startTime + chapter.duration}ms")
            // Warn if chapter extends beyond book duration
            if (chapter.startTime + chapter.duration > book.duration) {
                Log.w(TAG, "  ⚠️ Chapter[$index] extends beyond book duration!")
            }
            if (chapter.startTime > book.duration) {
                Log.e(TAG, "  ⚠️ Chapter[$index] starts AFTER book ends!")
            }
        }

        _state.update {
            it.copy(
                isVisible = true,
                bookId = bookId.value,
                title = book.title,
                author = book.authorNames,
                coverUrl = book.coverPath,
                bookDurationMs = book.duration,
                totalChapters = chapters.size
            )
        }

        Log.d(TAG, "loadBookInfo complete: ${book.title}")
        logger.debug { "Loaded book info: ${book.title}, ${chapters.size} chapters" }
    }

    private fun updatePosition(bookPositionMs: Long) {
        val currentChapter = findChapterAtPosition(bookPositionMs)
        val chapterIndex = chapters.indexOf(currentChapter).coerceAtLeast(0)

        val bookDurationMs = _state.value.bookDurationMs
        val bookProgress = if (bookDurationMs > 0) {
            bookPositionMs.toFloat() / bookDurationMs
        } else 0f

        val (chapterProgress, chapterPositionMs) = if (currentChapter != null) {
            val posInChapter = bookPositionMs - currentChapter.startTime
            val progress = if (currentChapter.duration > 0) {
                posInChapter.toFloat() / currentChapter.duration
            } else 0f
            progress.coerceIn(0f, 1f) to posInChapter.coerceAtLeast(0)
        } else {
            0f to 0L
        }

        // Only log errors for invalid progress values
        if (bookProgress.isNaN() || bookProgress.isInfinite() ||
            chapterProgress.isNaN() || chapterProgress.isInfinite()) {
            Log.e(TAG, "⚠️ Invalid progress: book=$bookProgress, chapter=$chapterProgress, duration=$bookDurationMs")
            return // Don't update state with invalid values
        }

        // Debounce: only update if position changed by >200ms to prevent excessive recomposition
        val currentState = _state.value
        val positionDeltaMs = kotlin.math.abs(bookPositionMs - currentState.bookPositionMs)
        val chapterChanged = chapterIndex != currentState.chapterIndex

        // Notify sleep timer manager of chapter changes (for end-of-chapter mode)
        if (chapterIndex != lastNotifiedChapterIndex) {
            lastNotifiedChapterIndex = chapterIndex
            sleepTimerManager.onChapterChanged(chapterIndex)
        }

        // Skip update if change is too small (time-based, not percentage-based)
        if (!chapterChanged && positionDeltaMs < 200) {
            return
        }

        _state.update {
            it.copy(
                bookProgress = bookProgress.coerceIn(0f, 1f),
                bookPositionMs = bookPositionMs,
                chapterIndex = chapterIndex,
                chapterTitle = currentChapter?.title,
                chapterProgress = chapterProgress,
                chapterPositionMs = chapterPositionMs,
                chapterDurationMs = currentChapter?.duration ?: 0L
            )
        }
    }

    private fun findChapterAtPosition(positionMs: Long): Chapter? {
        return chapters.lastOrNull { it.startTime <= positionMs }
    }

    private fun connectToMediaController() {
        if (mediaController != null) return

        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )

        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                logger.debug { "NowPlayingViewModel connected to MediaController" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to connect to MediaController" }
            }
        }, MoreExecutors.directExecutor())
    }

    private fun disconnectMediaController() {
        mediaController = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }

    // UI Actions

    fun expand() {
        _state.update { it.copy(isExpanded = true) }
    }

    fun collapse() {
        _state.update { it.copy(isExpanded = false) }
    }

    fun showChapterPicker() {
        _state.update { it.copy(showChapterPicker = true) }
    }

    fun hideChapterPicker() {
        _state.update { it.copy(showChapterPicker = false) }
    }

    fun showSpeedPicker() {
        _state.update { it.copy(showSpeedPicker = true) }
    }

    fun hideSpeedPicker() {
        _state.update { it.copy(showSpeedPicker = false) }
    }

    fun showSleepTimer() {
        _state.update { it.copy(showSleepTimer = true) }
    }

    fun hideSleepTimer() {
        _state.update { it.copy(showSleepTimer = false) }
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
        Log.d(TAG, "skipBack called: seconds=$seconds")
        val controller = mediaController
        if (controller == null) {
            Log.w(TAG, "skipBack: mediaController is null")
            return
        }
        val timeline = playbackManager.currentTimeline.value
        if (timeline == null) {
            Log.w(TAG, "skipBack: timeline is null")
            return
        }

        val currentBookPos = playbackManager.currentPositionMs.value
        val newBookPos = (currentBookPos - seconds * 1000).coerceAtLeast(0)
        Log.d(TAG, "skipBack: currentPos=$currentBookPos, newPos=$newBookPos")

        val position = timeline.resolve(newBookPos)
        Log.d(TAG, "skipBack: resolved to mediaItemIndex=${position.mediaItemIndex}, positionInFile=${position.positionInFileMs}")

        if (safeSeekTo(controller, position.mediaItemIndex, position.positionInFileMs, "skipBack")) {
            // Update PlaybackManager so UI updates immediately (even when paused)
            playbackManager.updatePosition(newBookPos)
        }
    }

    fun skipForward(seconds: Int = 30) {
        Log.d(TAG, "skipForward called: seconds=$seconds")
        val controller = mediaController
        if (controller == null) {
            Log.w(TAG, "skipForward: mediaController is null")
            return
        }
        val timeline = playbackManager.currentTimeline.value
        if (timeline == null) {
            Log.w(TAG, "skipForward: timeline is null")
            return
        }

        val currentBookPos = playbackManager.currentPositionMs.value
        val totalDuration = playbackManager.totalDurationMs.value
        val newBookPos = (currentBookPos + seconds * 1000).coerceAtMost(totalDuration)
        Log.d(TAG, "skipForward: currentPos=$currentBookPos, newPos=$newBookPos, totalDuration=$totalDuration")

        val position = timeline.resolve(newBookPos)
        Log.d(TAG, "skipForward: resolved to mediaItemIndex=${position.mediaItemIndex}, positionInFile=${position.positionInFileMs}")

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
        caller: String
    ): Boolean {
        // Validate mediaItemIndex
        val mediaItemCount = controller.mediaItemCount
        if (mediaItemIndex < 0) {
            Log.e(TAG, "$caller: INVALID mediaItemIndex=$mediaItemIndex (negative!)")
            return false
        }
        if (mediaItemIndex >= mediaItemCount) {
            Log.e(TAG, "$caller: INVALID mediaItemIndex=$mediaItemIndex >= mediaItemCount=$mediaItemCount")
            return false
        }

        // Validate position
        if (positionMs < 0) {
            Log.e(TAG, "$caller: INVALID positionMs=$positionMs (negative!)")
            return false
        }

        // Log player state for debugging
        Log.d(TAG, "$caller: safeSeekTo - mediaItemCount=$mediaItemCount, currentMediaItemIndex=${controller.currentMediaItemIndex}, playbackState=${controller.playbackState}")

        try {
            controller.seekTo(mediaItemIndex, positionMs)
            Log.d(TAG, "$caller: seekTo completed successfully")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "$caller: seekTo threw exception", e)
            return false
        }
    }

    fun previousChapter() {
        Log.d(TAG, "previousChapter called: current=${_state.value.chapterIndex}")
        val newIndex = (_state.value.chapterIndex - 1).coerceAtLeast(0)
        seekToChapter(newIndex)
    }

    fun nextChapter() {
        Log.d(TAG, "nextChapter called: current=${_state.value.chapterIndex}, total=${chapters.size}")
        val newIndex = (_state.value.chapterIndex + 1).coerceAtMost(chapters.lastIndex.coerceAtLeast(0))
        seekToChapter(newIndex)
    }

    fun seekToChapter(index: Int) {
        Log.d(TAG, "seekToChapter called: index=$index, chaptersSize=${chapters.size}")
        val chapter = chapters.getOrNull(index)
        if (chapter == null) {
            Log.w(TAG, "seekToChapter: chapter at index $index not found")
            return
        }
        val controller = mediaController
        if (controller == null) {
            Log.w(TAG, "seekToChapter: mediaController is null")
            return
        }
        val timeline = playbackManager.currentTimeline.value
        if (timeline == null) {
            Log.w(TAG, "seekToChapter: timeline is null")
            return
        }

        Log.d(TAG, "seekToChapter: chapter='${chapter.title}', startTime=${chapter.startTime}")
        val position = timeline.resolve(chapter.startTime)
        Log.d(TAG, "seekToChapter: resolved to mediaItemIndex=${position.mediaItemIndex}, positionInFile=${position.positionInFileMs}")

        if (safeSeekTo(controller, position.mediaItemIndex, position.positionInFileMs, "seekToChapter")) {
            // Update PlaybackManager so UI updates immediately (even when paused)
            playbackManager.updatePosition(chapter.startTime)
        }
        hideChapterPicker()
    }

    fun seekWithinChapter(progress: Float) {
        Log.d(TAG, "seekWithinChapter called: progress=$progress")
        val currentChapter = chapters.getOrNull(_state.value.chapterIndex)
        if (currentChapter == null) {
            Log.w(TAG, "seekWithinChapter: no current chapter")
            return
        }
        val controller = mediaController
        if (controller == null) {
            Log.w(TAG, "seekWithinChapter: mediaController is null")
            return
        }
        val timeline = playbackManager.currentTimeline.value
        if (timeline == null) {
            Log.w(TAG, "seekWithinChapter: timeline is null")
            return
        }

        val targetPosition = currentChapter.startTime + (currentChapter.duration * progress).toLong()
        Log.d(TAG, "seekWithinChapter: chapter='${currentChapter.title}', targetPosition=$targetPosition")

        val position = timeline.resolve(targetPosition)
        Log.d(TAG, "seekWithinChapter: resolved to mediaItemIndex=${position.mediaItemIndex}, positionInFile=${position.positionInFileMs}")

        if (safeSeekTo(controller, position.mediaItemIndex, position.positionInFileMs, "seekWithinChapter")) {
            // Update PlaybackManager so UI updates immediately (even when paused)
            playbackManager.updatePosition(targetPosition)
        }
    }

    fun setSpeed(speed: Float) {
        mediaController?.playbackParameters = PlaybackParameters(speed)
    }

    fun cycleSpeed() {
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)
        val currentSpeed = _state.value.playbackSpeed
        val currentIndex = speeds.indexOfFirst { it >= currentSpeed - 0.01f }
        val nextIndex = if (currentIndex == -1 || currentIndex >= speeds.lastIndex) 0 else currentIndex + 1
        setSpeed(speeds[nextIndex])
    }

    fun getChapters(): List<Chapter> = chapters

    override fun onCleared() {
        super.onCleared()
        disconnectMediaController()
    }
}
