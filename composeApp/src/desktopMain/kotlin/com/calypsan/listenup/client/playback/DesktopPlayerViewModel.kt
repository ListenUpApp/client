@file:Suppress("MagicNumber")

package com.calypsan.listenup.client.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.domain.model.Chapter
import com.calypsan.listenup.client.domain.repository.BookRepository
import com.calypsan.listenup.client.domain.repository.PlaybackPreferences
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * Desktop playback ViewModel.
 *
 * Thin bridge between UI and PlaybackManager/AudioPlayer.
 * Observes PlaybackManager state flows and forwards user actions to AudioPlayer.
 * Handles ProgressTracker integration for pause/resume events.
 */
class DesktopPlayerViewModel(
    private val playbackManager: PlaybackManager,
    private val audioPlayer: AudioPlayer,
    private val progressTracker: ProgressTracker,
    private val bookRepository: BookRepository,
    private val playbackPreferences: PlaybackPreferences,
) : ViewModel() {
    val state: StateFlow<NowPlayingState>
        field = MutableStateFlow(NowPlayingState())

    private var periodicUpdateJob: Job? = null

    init {
        observePlayback()
    }

    private fun observePlayback() {
        // Observe current book
        viewModelScope.launch {
            playbackManager.currentBookId.collect { bookId ->
                if (bookId != null) {
                    loadBookInfo(bookId)
                } else {
                    state.update { it.copy(isVisible = false) }
                }
            }
        }

        // Observe playback state
        viewModelScope.launch {
            playbackManager.isPlaying.collect { isPlaying ->
                state.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) {
                    startPeriodicUpdates()
                } else {
                    stopPeriodicUpdates()
                }
            }
        }

        // Observe position
        viewModelScope.launch {
            playbackManager.currentPositionMs.collect { positionMs ->
                updatePosition(positionMs)
            }
        }

        // Observe chapter
        viewModelScope.launch {
            playbackManager.currentChapter.collect { chapterInfo ->
                if (chapterInfo != null) {
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

        // Observe speed
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

        // Observe prepare progress
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

        // Observe audio player errors
        viewModelScope.launch {
            audioPlayer.state.collect { playbackState ->
                if (playbackState == PlaybackState.Error) {
                    state.update {
                        it.copy(errorMessage = "Playback error. Check GStreamer installation.")
                    }
                } else if (playbackState == PlaybackState.Playing) {
                    state.update { it.copy(errorMessage = null) }
                }
            }
        }
    }

    /**
     * Start playback of a book.
     */
    fun playBook(bookId: BookId) {
        viewModelScope.launch {
            state.update { it.copy(isPreparing = true, errorMessage = null) }

            val result = playbackManager.prepareForPlayback(bookId)
            if (result == null) {
                state.update { it.copy(isPreparing = false, errorMessage = "Failed to prepare playback") }
                logger.error { "Failed to prepare playback for $bookId" }
                return@launch
            }

            state.update { it.copy(isPreparing = false) }

            playbackManager.startPlayback(
                player = audioPlayer,
                resumePositionMs = result.resumePositionMs,
                resumeSpeed = result.resumeSpeed,
            )

            // Check if playback actually started
            if (audioPlayer.state.value == PlaybackState.Error) {
                state.update { it.copy(errorMessage = "Playback failed. Check that GStreamer plugins are installed correctly.") }
            }
        }
    }

    fun playPause() {
        val currentState = audioPlayer.state.value
        if (currentState == PlaybackState.Playing) {
            audioPlayer.pause()
            // Notify progress tracker of pause
            val bookId = playbackManager.currentBookId.value ?: return
            val positionMs = playbackManager.currentPositionMs.value
            val speed = playbackManager.playbackSpeed.value
            progressTracker.onPlaybackPaused(bookId, positionMs, speed)
        } else {
            audioPlayer.play()
            // Notify progress tracker of resume
            val bookId = playbackManager.currentBookId.value ?: return
            val positionMs = playbackManager.currentPositionMs.value
            val speed = playbackManager.playbackSpeed.value
            progressTracker.onPlaybackStarted(bookId, positionMs, speed)
        }
    }

    fun skipBack(seconds: Int = 10) {
        val currentPos = playbackManager.currentPositionMs.value
        val newPos = (currentPos - seconds * 1000L).coerceAtLeast(0)
        audioPlayer.seekTo(newPos)
    }

    fun skipForward(seconds: Int = 30) {
        val currentPos = playbackManager.currentPositionMs.value
        val totalDuration = playbackManager.totalDurationMs.value
        val newPos = (currentPos + seconds * 1000L).coerceAtMost(totalDuration)
        audioPlayer.seekTo(newPos)
    }

    fun seekToChapter(index: Int) {
        val chapters = playbackManager.chapters.value
        val chapter = chapters.getOrNull(index) ?: return
        audioPlayer.seekTo(chapter.startTime)
    }

    fun seekWithinChapter(progress: Float) {
        val chapters = playbackManager.chapters.value
        val currentChapter = chapters.getOrNull(state.value.chapterIndex) ?: return
        val targetPosition = currentChapter.startTime + (currentChapter.duration * progress).toLong()
        audioPlayer.seekTo(targetPosition)
    }

    fun setSpeed(speed: Float) {
        audioPlayer.setSpeed(speed)
        playbackManager.onSpeedChanged(speed)
    }

    fun resetSpeedToDefault() {
        viewModelScope.launch {
            val defaultSpeed = playbackPreferences.getDefaultPlaybackSpeed()
            audioPlayer.setSpeed(defaultSpeed)
            playbackManager.onSpeedReset(defaultSpeed)
        }
    }

    fun cycleSpeed() {
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)
        val currentSpeed = state.value.playbackSpeed
        val currentIndex = speeds.indexOfFirst { it >= currentSpeed - 0.01f }
        val nextIndex = if (currentIndex == -1 || currentIndex >= speeds.lastIndex) 0 else currentIndex + 1
        setSpeed(speeds[nextIndex])
    }

    fun previousChapter() {
        val newIndex = (state.value.chapterIndex - 1).coerceAtLeast(0)
        seekToChapter(newIndex)
    }

    fun nextChapter() {
        val chapters = playbackManager.chapters.value
        val newIndex = (state.value.chapterIndex + 1).coerceAtMost(chapters.lastIndex.coerceAtLeast(0))
        seekToChapter(newIndex)
    }

    fun closeBook() {
        audioPlayer.pause()
        val bookId = playbackManager.currentBookId.value
        if (bookId != null) {
            val positionMs = playbackManager.currentPositionMs.value
            val speed = playbackManager.playbackSpeed.value
            progressTracker.onPlaybackPaused(bookId, positionMs, speed)
        }
        audioPlayer.release()
        playbackManager.clearPlayback()
    }

    fun getChapters(): List<Chapter> = playbackManager.chapters.value

    private suspend fun loadBookInfo(bookId: BookId) {
        val book = bookRepository.getBook(bookId.value) ?: return
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
    }

    private fun updatePosition(bookPositionMs: Long) {
        val bookDurationMs = state.value.bookDurationMs
        val bookProgress = if (bookDurationMs > 0) {
            bookPositionMs.toFloat() / bookDurationMs
        } else {
            0f
        }

        val chapterInfo = playbackManager.currentChapter.value
        val (chapterProgress, chapterPositionMs) = if (chapterInfo != null) {
            val posInChapter = bookPositionMs - chapterInfo.startMs
            val chapterDuration = chapterInfo.endMs - chapterInfo.startMs
            val progress = if (chapterDuration > 0) {
                posInChapter.toFloat() / chapterDuration
            } else {
                0f
            }
            progress.coerceIn(0f, 1f) to posInChapter.coerceAtLeast(0)
        } else {
            0f to 0L
        }

        // Debounce: skip tiny changes
        val positionDeltaMs = kotlin.math.abs(bookPositionMs - state.value.bookPositionMs)
        if (positionDeltaMs < 200) return

        state.update {
            it.copy(
                bookProgress = bookProgress.coerceIn(0f, 1f),
                bookPositionMs = bookPositionMs,
                chapterProgress = chapterProgress,
                chapterPositionMs = chapterPositionMs,
            )
        }
    }

    /**
     * Periodic updates for ProgressTracker (every 30 seconds during playback).
     */
    private fun startPeriodicUpdates() {
        stopPeriodicUpdates()
        periodicUpdateJob = viewModelScope.launch {
            while (isActive) {
                delay(30_000)
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
}
