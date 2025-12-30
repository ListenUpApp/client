@file:Suppress("MagicNumber", "LongMethod")

package com.calypsan.listenup.client.playback

import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.local.db.BookDao
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.remote.PlaybackApi
import com.calypsan.listenup.client.data.remote.model.AudioFileResponse
import com.calypsan.listenup.client.data.repository.SettingsRepository
import com.calypsan.listenup.client.data.sync.sse.PlaybackStateProvider
import com.calypsan.listenup.client.domain.playback.PlaybackTimeline
import com.calypsan.listenup.client.domain.playback.StreamPrepareResult
import com.calypsan.listenup.client.download.DownloadService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

/**
 * Orchestrates playback startup and coordinates between UI, service, and data layers.
 *
 * Responsibilities:
 * - Parse audio files from BookEntity
 * - Build PlaybackTimeline for position translation
 * - Prepare authentication for streaming
 * - Track current playback state (position, playing status)
 * - Provide central control interface for playback
 * - Trigger background downloads for offline availability
 * - Negotiate audio format with server for transcoding support
 */
class PlaybackManager(
    private val settingsRepository: SettingsRepository,
    private val bookDao: BookDao,
    private val progressTracker: ProgressTracker,
    private val tokenProvider: AudioTokenProvider,
    private val downloadService: DownloadService,
    private val playbackApi: PlaybackApi?,
    private val capabilityDetector: AudioCapabilityDetector?,
    private val scope: CoroutineScope,
) : PlaybackStateProvider {
    private val _currentBookId = MutableStateFlow<BookId?>(null)
    override val currentBookId: StateFlow<BookId?> = _currentBookId

    private val _currentTimeline = MutableStateFlow<PlaybackTimeline?>(null)
    val currentTimeline: StateFlow<PlaybackTimeline?> = _currentTimeline

    val isPlaying: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val currentPositionMs: StateFlow<Long>
        field = MutableStateFlow(0L)

    val totalDurationMs: StateFlow<Long>
        field = MutableStateFlow(0L)

    val playbackSpeed: StateFlow<Float>
        field = MutableStateFlow(1.0f)

    // Transcode preparation progress (0-100, null when not preparing)
    val prepareProgress: StateFlow<PrepareProgress?>
        field = MutableStateFlow<PrepareProgress?>(null)

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

        val audioFiles: List<AudioFileResponse> =
            try {
                json.decodeFromString(audioFilesJson)
            } catch (e: Exception) {
                logger.error(e) { "Failed to parse audio files JSON" }
                return null
            }

        if (audioFiles.isEmpty()) {
            logger.error { "Empty audio files list for book: ${bookId.value}" }
            return null
        }

        // Log detailed audio file info for diagnostics
        logger.debug { "=== Audio Files for book ${bookId.value} ===" }
        var totalDuration = 0L
        audioFiles.forEachIndexed { index, file ->
            logger.debug {
                "  File[$index]: id=${file.id}, filename=${file.filename}, " +
                    "duration=${file.duration}ms (${file.duration / 1000}s), " +
                    "size=${file.size}, format=${file.format}"
            }
            // Check for problematic values
            if (file.duration <= 0) {
                logger.warn { "  ⚠️ WARNING: File[$index] has invalid duration: ${file.duration}" }
            }
            if (file.duration > 86_400_000) { // More than 24 hours - suspicious
                logger.warn {
                    "  ⚠️ WARNING: File[$index] has suspiciously large duration: " +
                        "${file.duration}ms (${file.duration / 3_600_000}h)"
                }
            }
            totalDuration += file.duration
        }
        logger.debug {
            "=== Total calculated duration: ${totalDuration}ms (${totalDuration / 1000}s / ${totalDuration / 60000}min) ==="
        }

        // 5. Build PlaybackTimeline with codec negotiation (if available) or local path resolution
        val timeline =
            if (playbackApi != null && capabilityDetector != null) {
                // Use transcode-aware timeline building
                val capabilities = capabilityDetector.getSupportedCodecs()
                logger.debug { "Client codec capabilities: $capabilities" }

                PlaybackTimeline.buildWithTranscodeSupport(
                    bookId = bookId,
                    audioFiles = audioFiles,
                    baseUrl = serverUrl,
                    resolveLocalPath = { audioFileId -> downloadService.getLocalPath(audioFileId) },
                    prepareStream = { audioFileId, codec ->
                        prepareStreamForFile(bookId.value, audioFileId, codec, capabilities, serverUrl)
                    },
                )
            } else {
                // Fallback to basic local path resolution
                PlaybackTimeline.buildWithLocalPaths(
                    bookId = bookId,
                    audioFiles = audioFiles,
                    baseUrl = serverUrl,
                    resolveLocalPath = { audioFileId -> downloadService.getLocalPath(audioFileId) },
                )
            }
        _currentTimeline.value = timeline
        _currentBookId.value = bookId
        totalDurationMs.value = timeline.totalDurationMs

        logger.info { "Built timeline: ${timeline.files.size} files, ${timeline.totalDurationMs}ms total" }

        // 6. Get resume position and speed
        val savedPosition = progressTracker.getResumePosition(bookId)
        val resumePositionMs = savedPosition?.positionMs ?: 0L

        // Determine playback speed: use book's custom speed if set, otherwise use universal default
        val resumeSpeed =
            if (savedPosition != null && savedPosition.hasCustomSpeed) {
                // Book has a custom speed set by user
                savedPosition.playbackSpeed
            } else {
                // Use universal default speed (synced across devices)
                settingsRepository.getDefaultPlaybackSpeed()
            }

        logger.debug {
            "Resume position: ${resumePositionMs}ms, speed: ${resumeSpeed}x (hasCustomSpeed=${savedPosition?.hasCustomSpeed})"
        }

        // Validate resume position
        if (resumePositionMs < 0) {
            logger.warn { "⚠️ WARNING: Negative resume position: $resumePositionMs" }
        }
        if (resumePositionMs > timeline.totalDurationMs) {
            logger.warn {
                "⚠️ WARNING: Resume position $resumePositionMs exceeds book duration ${timeline.totalDurationMs}"
            }
        }

        // Test timeline.resolve() with resume position
        val resolvedPosition = timeline.resolve(resumePositionMs)
        logger.debug {
            "Resolved resume position: " +
                "mediaItemIndex=${resolvedPosition.mediaItemIndex}, " +
                "positionInFileMs=${resolvedPosition.positionInFileMs}"
        }

        if (resolvedPosition.mediaItemIndex >= timeline.files.size) {
            logger.warn {
                "⚠️ WARNING: Invalid mediaItemIndex ${resolvedPosition.mediaItemIndex} >= ${timeline.files.size}"
            }
        }

        // 7. Trigger background download if not fully downloaded
        // Skip if user explicitly deleted - they chose to stream only
        // Result ignored intentionally - this is best-effort caching, user can still stream
        if (!timeline.isFullyDownloaded && !downloadService.wasExplicitlyDeleted(bookId)) {
            logger.info { "Book not fully downloaded, triggering background download" }
            scope.launch {
                downloadService.downloadBook(bookId) // Result logged in DownloadManager
            }
        } else if (!timeline.isFullyDownloaded) {
            logger.info { "Book was explicitly deleted, streaming only (no auto-download)" }
        }

        return PrepareResult(
            timeline = timeline,
            bookTitle = book.title,
            resumePositionMs = resumePositionMs,
            resumeSpeed = resumeSpeed,
        )
    }

    /**
     * Update playback state.
     * Called by PlayerViewModel when state changes.
     */
    fun setPlaying(playing: Boolean) {
        isPlaying.value = playing
    }

    /**
     * Update current position.
     * Called by PlayerViewModel during position update loop.
     */
    fun updatePosition(positionMs: Long) {
        currentPositionMs.value = positionMs
    }

    /**
     * Update playback speed.
     * Called by PlayerViewModel when speed changes.
     */
    fun updateSpeed(speed: Float) {
        playbackSpeed.value = speed
    }

    /**
     * Called when user explicitly changes playback speed.
     * Updates state and marks the book as having a custom speed.
     */
    fun onSpeedChanged(speed: Float) {
        val bookId = currentBookId.value ?: return
        val positionMs = currentPositionMs.value
        playbackSpeed.value = speed
        progressTracker.onSpeedChanged(bookId, positionMs, speed)
    }

    /**
     * Reset book's speed to universal default.
     * Called when user explicitly resets to default speed.
     *
     * @param defaultSpeed The universal default speed from settings
     */
    fun onSpeedReset(defaultSpeed: Float) {
        val bookId = currentBookId.value ?: return
        val positionMs = currentPositionMs.value
        playbackSpeed.value = defaultSpeed
        progressTracker.onSpeedReset(bookId, positionMs, defaultSpeed)
    }

    /**
     * Clear current playback state.
     * Called when playback stops or when access is revoked.
     */
    override fun clearPlayback() {
        _currentBookId.value = null
        _currentTimeline.value = null
        isPlaying.value = false
        currentPositionMs.value = 0L
        totalDurationMs.value = 0L
        playbackSpeed.value = 1.0f
    }

    /**
     * Negotiate streaming URL for a single audio file.
     *
     * Calls the server's prepare endpoint to get the correct URL
     * based on client capabilities. If transcoding is in progress,
     * polls until ready or timeout, emitting progress updates.
     */
    private suspend fun prepareStreamForFile(
        bookId: String,
        audioFileId: String,
        codec: String,
        capabilities: List<String>,
        baseUrl: String,
    ): StreamPrepareResult {
        val api = playbackApi ?: return fallbackStreamResult(bookId, audioFileId, baseUrl)

        val maxRetries = 120 // ~10 minutes at 5 second intervals
        val retryDelayMs = 5000L

        // Get spatial audio setting from repository
        val spatial = settingsRepository.getSpatialPlayback()

        repeat(maxRetries) { attempt ->
            when (val result = api.preparePlayback(bookId, audioFileId, capabilities, spatial)) {
                is Success -> {
                    val response = result.data
                    logger.debug {
                        "Prepare result for $audioFileId (attempt ${attempt + 1}): " +
                            "ready=${response.ready}, variant=${response.variant}, codec=${response.codec}"
                    }

                    if (response.ready) {
                        // Clear progress when ready
                        prepareProgress.value = null
                        return StreamPrepareResult(
                            streamUrl = response.streamUrl,
                            ready = true,
                            transcodeJobId = response.transcodeJobId,
                        )
                    }

                    // Transcoding in progress - emit progress and retry
                    prepareProgress.value =
                        PrepareProgress(
                            audioFileId = audioFileId,
                            progress = response.progress,
                            message = "Preparing audio... ${response.progress}%",
                        )

                    logger.info {
                        "Transcoding in progress for $audioFileId: " +
                            "jobId=${response.transcodeJobId}, progress=${response.progress}%, " +
                            "waiting ${retryDelayMs}ms before retry..."
                    }
                    kotlinx.coroutines.delay(retryDelayMs)
                }

                is Failure -> {
                    prepareProgress.value = null
                    logger.warn(result.exception) {
                        "Failed to prepare stream for $audioFileId (attempt ${attempt + 1}), " +
                            "using fallback URL"
                    }
                    return fallbackStreamResult(bookId, audioFileId, baseUrl)
                }
            }
        }

        // Timeout - return what we have (stream URL that may 202)
        prepareProgress.value = null
        logger.warn { "Transcode polling timeout for $audioFileId after $maxRetries attempts" }
        return fallbackStreamResult(bookId, audioFileId, baseUrl)
    }

    /**
     * Fallback stream result when prepare endpoint fails.
     */
    private fun fallbackStreamResult(
        bookId: String,
        audioFileId: String,
        baseUrl: String,
    ): StreamPrepareResult =
        StreamPrepareResult(
            streamUrl = "$baseUrl/api/v1/books/$bookId/audio/$audioFileId",
            ready = true,
            transcodeJobId = null,
        )

    /**
     * Result of preparing for playback.
     */
    data class PrepareResult(
        val timeline: PlaybackTimeline,
        val bookTitle: String,
        val resumePositionMs: Long,
        val resumeSpeed: Float,
    )

    /**
     * Progress state during audio preparation (transcoding).
     */
    data class PrepareProgress(
        val audioFileId: String,
        val progress: Int, // 0-100
        val message: String = "Preparing audio...",
    )
}
