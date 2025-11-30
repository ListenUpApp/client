package com.calypsan.listenup.client.playback

import com.calypsan.listenup.client.data.local.db.BookDao
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.remote.model.AudioFileResponse
import com.calypsan.listenup.client.data.repository.SettingsRepository
import com.calypsan.listenup.client.domain.playback.PlaybackTimeline
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

/**
 * Orchestrates playback startup and coordinates between UI, service, and data layers.
 *
 * Responsibilities:
 * - Parse audio files from BookEntity
 * - Build PlaybackTimeline for position translation
 * - Prepare authentication for streaming
 * - Track current playback state
 */
class PlaybackManager(
    private val settingsRepository: SettingsRepository,
    private val bookDao: BookDao,
    private val progressTracker: ProgressTracker,
    private val tokenProvider: AudioTokenProvider
) {
    private val _currentBookId = MutableStateFlow<BookId?>(null)
    val currentBookId: StateFlow<BookId?> = _currentBookId.asStateFlow()

    private val _currentTimeline = MutableStateFlow<PlaybackTimeline?>(null)
    val currentTimeline: StateFlow<PlaybackTimeline?> = _currentTimeline.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Prepare for playback of a book.
     *
     * Steps:
     * 1. Ensure fresh auth token
     * 2. Get book from database
     * 3. Parse audio files from JSON
     * 4. Build PlaybackTimeline
     * 5. Get resume position
     *
     * @return PrepareResult with timeline and resume position, or null on failure
     */
    suspend fun prepareForPlayback(bookId: BookId): PrepareResult? {
        logger.info { "Preparing playback for book: ${bookId.value}" }

        // 1. Ensure fresh auth token
        tokenProvider.prepareForPlayback()

        // 2. Get server URL
        val serverUrl = settingsRepository.getServerUrl()?.value
        if (serverUrl == null) {
            logger.error { "No server URL configured" }
            return null
        }

        // 3. Get book from database
        val book = bookDao.getById(bookId)
        if (book == null) {
            logger.error { "Book not found: ${bookId.value}" }
            return null
        }

        // 4. Parse audio files from JSON
        val audioFilesJson = book.audioFilesJson
        if (audioFilesJson.isNullOrBlank()) {
            logger.error { "No audio files for book: ${bookId.value}. Try pulling down to force a full re-sync." }
            return null
        }

        val audioFiles: List<AudioFileResponse> = try {
            json.decodeFromString(audioFilesJson)
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse audio files JSON" }
            return null
        }

        if (audioFiles.isEmpty()) {
            logger.error { "Empty audio files list for book: ${bookId.value}" }
            return null
        }

        // 5. Build PlaybackTimeline
        val timeline = PlaybackTimeline.build(bookId, audioFiles, serverUrl)
        _currentTimeline.value = timeline
        _currentBookId.value = bookId

        logger.info { "Built timeline: ${timeline.files.size} files, ${timeline.totalDurationMs}ms total" }

        // 6. Get resume position
        val savedPosition = progressTracker.getResumePosition(bookId)
        val resumePositionMs = savedPosition?.positionMs ?: 0L
        val resumeSpeed = savedPosition?.playbackSpeed ?: 1.0f

        logger.info { "Resume position: ${resumePositionMs}ms, speed: ${resumeSpeed}x" }

        return PrepareResult(
            timeline = timeline,
            bookTitle = book.title,
            resumePositionMs = resumePositionMs,
            resumeSpeed = resumeSpeed
        )
    }

    /**
     * Update playback state.
     * Called by PlayerViewModel when state changes.
     */
    fun setPlaying(playing: Boolean) {
        _isPlaying.value = playing
    }

    /**
     * Clear current playback state.
     * Called when playback stops.
     */
    fun clearPlayback() {
        _currentBookId.value = null
        _currentTimeline.value = null
        _isPlaying.value = false
    }

    /**
     * Result of preparing for playback.
     */
    data class PrepareResult(
        val timeline: PlaybackTimeline,
        val bookTitle: String,
        val resumePositionMs: Long,
        val resumeSpeed: Float
    )
}
