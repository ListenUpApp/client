package com.calypsan.listenup.client.playback

import android.util.Log
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
private const val TAG = "PlaybackManager"

/**
 * Orchestrates playback startup and coordinates between UI, service, and data layers.
 *
 * Responsibilities:
 * - Parse audio files from BookEntity
 * - Build PlaybackTimeline for position translation
 * - Prepare authentication for streaming
 * - Track current playback state (position, playing status)
 * - Provide central control interface for playback
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

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _totalDurationMs = MutableStateFlow(0L)
    val totalDurationMs: StateFlow<Long> = _totalDurationMs.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

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

        // Log detailed audio file info to diagnose book-specific crashes
        Log.d(TAG, "=== Audio Files for book ${bookId.value} ===")
        var totalDuration = 0L
        audioFiles.forEachIndexed { index, file ->
            Log.d(TAG, "  File[$index]: id=${file.id}, filename=${file.filename}, duration=${file.duration}ms (${file.duration / 1000}s), size=${file.size}, format=${file.format}")
            // Check for problematic values
            if (file.duration <= 0) {
                Log.e(TAG, "  ⚠️ WARNING: File[$index] has invalid duration: ${file.duration}")
            }
            if (file.duration > 86400000) { // More than 24 hours - suspicious
                Log.e(TAG, "  ⚠️ WARNING: File[$index] has suspiciously large duration: ${file.duration}ms (${file.duration / 3600000}h)")
            }
            totalDuration += file.duration
        }
        Log.d(TAG, "=== Total calculated duration: ${totalDuration}ms (${totalDuration / 1000}s / ${totalDuration / 60000}min) ===")

        // 5. Build PlaybackTimeline
        val timeline = PlaybackTimeline.build(bookId, audioFiles, serverUrl)
        _currentTimeline.value = timeline
        _currentBookId.value = bookId
        _totalDurationMs.value = timeline.totalDurationMs

        Log.d(TAG, "Built timeline: ${timeline.files.size} files, ${timeline.totalDurationMs}ms total")
        logger.info { "Built timeline: ${timeline.files.size} files, ${timeline.totalDurationMs}ms total" }

        // 6. Get resume position
        val savedPosition = progressTracker.getResumePosition(bookId)
        val resumePositionMs = savedPosition?.positionMs ?: 0L
        val resumeSpeed = savedPosition?.playbackSpeed ?: 1.0f

        Log.d(TAG, "Resume position: ${resumePositionMs}ms, speed: ${resumeSpeed}x")

        // Validate resume position
        if (resumePositionMs < 0) {
            Log.e(TAG, "⚠️ WARNING: Negative resume position: $resumePositionMs")
        }
        if (resumePositionMs > timeline.totalDurationMs) {
            Log.e(TAG, "⚠️ WARNING: Resume position $resumePositionMs exceeds book duration ${timeline.totalDurationMs}")
        }

        // Test timeline.resolve() with resume position
        val resolvedPosition = timeline.resolve(resumePositionMs)
        Log.d(TAG, "Resolved resume position: mediaItemIndex=${resolvedPosition.mediaItemIndex}, positionInFileMs=${resolvedPosition.positionInFileMs}")

        if (resolvedPosition.mediaItemIndex >= timeline.files.size) {
            Log.e(TAG, "⚠️ WARNING: Invalid mediaItemIndex ${resolvedPosition.mediaItemIndex} >= ${timeline.files.size}")
        }

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
     * Update current position.
     * Called by PlayerViewModel during position update loop.
     */
    fun updatePosition(positionMs: Long) {
        _currentPositionMs.value = positionMs
    }

    /**
     * Update playback speed.
     * Called by PlayerViewModel when speed changes.
     */
    fun updateSpeed(speed: Float) {
        _playbackSpeed.value = speed
    }

    /**
     * Clear current playback state.
     * Called when playback stops.
     */
    fun clearPlayback() {
        _currentBookId.value = null
        _currentTimeline.value = null
        _isPlaying.value = false
        _currentPositionMs.value = 0L
        _totalDurationMs.value = 0L
        _playbackSpeed.value = 1.0f
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
