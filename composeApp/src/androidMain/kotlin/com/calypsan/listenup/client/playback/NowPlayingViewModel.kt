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
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

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
    private val bookRepository: BookRepository
) : ViewModel() {

    private val _state = MutableStateFlow(NowPlayingState())
    val state: StateFlow<NowPlayingState> = _state.asStateFlow()

    private var chapters: List<Chapter> = emptyList()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    init {
        observePlayback()
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

    private suspend fun loadBookInfo(bookId: BookId) {
        val book = bookRepository.getBook(bookId.value)
        if (book == null) {
            logger.warn { "Book not found: $bookId" }
            return
        }

        chapters = bookRepository.getChapters(bookId.value)

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

        logger.debug { "Loaded book info: ${book.title}, ${chapters.size} chapters" }
    }

    private fun updatePosition(bookPositionMs: Long) {
        val currentChapter = findChapterAtPosition(bookPositionMs)
        val chapterIndex = chapters.indexOf(currentChapter).coerceAtLeast(0)

        val bookProgress = if (_state.value.bookDurationMs > 0) {
            bookPositionMs.toFloat() / _state.value.bookDurationMs
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
        val controller = mediaController ?: return
        val timeline = playbackManager.currentTimeline.value ?: return

        val currentBookPos = playbackManager.currentPositionMs.value
        val newBookPos = (currentBookPos - seconds * 1000).coerceAtLeast(0)

        val position = timeline.resolve(newBookPos)
        controller.seekTo(position.mediaItemIndex, position.positionInFileMs)
    }

    fun skipForward(seconds: Int = 30) {
        val controller = mediaController ?: return
        val timeline = playbackManager.currentTimeline.value ?: return

        val currentBookPos = playbackManager.currentPositionMs.value
        val totalDuration = playbackManager.totalDurationMs.value
        val newBookPos = (currentBookPos + seconds * 1000).coerceAtMost(totalDuration)

        val position = timeline.resolve(newBookPos)
        controller.seekTo(position.mediaItemIndex, position.positionInFileMs)
    }

    fun previousChapter() {
        val newIndex = (_state.value.chapterIndex - 1).coerceAtLeast(0)
        seekToChapter(newIndex)
    }

    fun nextChapter() {
        val newIndex = (_state.value.chapterIndex + 1).coerceAtMost(chapters.lastIndex.coerceAtLeast(0))
        seekToChapter(newIndex)
    }

    fun seekToChapter(index: Int) {
        val chapter = chapters.getOrNull(index) ?: return
        val controller = mediaController ?: return
        val timeline = playbackManager.currentTimeline.value ?: return

        val position = timeline.resolve(chapter.startTime)
        controller.seekTo(position.mediaItemIndex, position.positionInFileMs)
        hideChapterPicker()
    }

    fun seekWithinChapter(progress: Float) {
        val currentChapter = chapters.getOrNull(_state.value.chapterIndex) ?: return
        val controller = mediaController ?: return
        val timeline = playbackManager.currentTimeline.value ?: return

        val targetPosition = currentChapter.startTime + (currentChapter.duration * progress).toLong()
        val position = timeline.resolve(targetPosition)
        controller.seekTo(position.mediaItemIndex, position.positionInFileMs)
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
