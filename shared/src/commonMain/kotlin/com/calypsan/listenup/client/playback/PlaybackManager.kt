@file:Suppress(
    "MagicNumber",
    "LongMethod",
    "LongParameterList",
    "CyclomaticComplexMethod",
    "CognitiveComplexMethod",
)

package com.calypsan.listenup.client.playback

import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.local.db.BookDao
import com.calypsan.listenup.client.data.local.db.ChapterDao
import com.calypsan.listenup.client.data.remote.PlaybackApi
import com.calypsan.listenup.client.data.remote.SyncApiContract
import com.calypsan.listenup.client.data.remote.model.AudioFileResponse
import com.calypsan.listenup.client.data.remote.model.toEntity
import com.calypsan.listenup.client.data.sync.sse.PlaybackStateProvider
import com.calypsan.listenup.client.domain.model.AudioFile
import com.calypsan.listenup.client.domain.model.Chapter
import com.calypsan.listenup.client.domain.model.ContributorRole
import com.calypsan.listenup.client.domain.playback.PlaybackTimeline
import com.calypsan.listenup.client.domain.playback.StreamPrepareResult
import com.calypsan.listenup.client.domain.repository.ImageStorage
import com.calypsan.listenup.client.domain.repository.PlaybackPreferences
import com.calypsan.listenup.client.domain.repository.ServerConfig
import com.calypsan.listenup.client.download.DownloadService
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
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
    private val serverConfig: ServerConfig,
    private val playbackPreferences: PlaybackPreferences,
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao,
    private val imageStorage: ImageStorage,
    private val progressTracker: ProgressTracker,
    private val tokenProvider: AudioTokenProvider,
    private val downloadService: DownloadService,
    private val playbackApi: PlaybackApi?,
    private val capabilityDetector: AudioCapabilityDetector?,
    private val syncApi: SyncApiContract?,
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

    // Error state for displaying playback errors to the user
    // Null means no error, non-null means error to display
    private val _playbackError = MutableStateFlow<PlaybackError?>(null)
    val playbackError: StateFlow<PlaybackError?> = _playbackError

    // Chapter state for notification and UI
    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val chapters: StateFlow<List<Chapter>> = _chapters

    private val _currentChapter = MutableStateFlow<ChapterInfo?>(null)
    val currentChapter: StateFlow<ChapterInfo?> = _currentChapter

    // Callback for chapter changes - used by PlaybackService to update notification
    var onChapterChanged: ((ChapterInfo) -> Unit)? = null

    private val json = Json { ignoreUnknownKeys = true }

    /** Set the current book ID — call this only when playback is confirmed to proceed. */
    fun activateBook(bookId: BookId) {
        _currentBookId.value = bookId
    }

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
        val serverUrl = serverConfig.getServerUrl()?.value
        if (serverUrl == null) {
            logger.error { "No server URL configured" }
            return null
        }

        // 3. Get book with contributors from database
        val bookWithContributors = bookDao.getByIdWithContributors(bookId)
        if (bookWithContributors == null) {
            logger.error { "Book not found: ${bookId.value}" }
            return null
        }
        val book = bookWithContributors.book

        // Extract author names (use creditedAs when available for proper attribution)
        val contributorsById = bookWithContributors.contributors.associateBy { it.id }
        val authorNames =
            bookWithContributors.contributorRoles
                .filter { it.role == ContributorRole.AUTHOR.apiValue }
                .mapNotNull { crossRef ->
                    contributorsById[crossRef.contributorId]?.let { entity ->
                        crossRef.creditedAs ?: entity.name
                    }
                }.distinct()
        val bookAuthor = authorNames.joinToString(", ").ifEmpty { "Unknown Author" }

        // Get series name (first series if multiple)
        val seriesName = bookWithContributors.series.firstOrNull()?.name

        // Get cover path (if exists on disk)
        val coverPath =
            if (imageStorage.exists(bookId)) {
                imageStorage.getCoverPath(bookId)
            } else {
                null
            }

        // 4. Parse audio files from JSON, or fetch from server if missing
        var audioFilesJson = book.audioFilesJson
        if (audioFilesJson.isNullOrBlank()) {
            logger.info { "No audio files for book: ${bookId.value}, fetching from server..." }

            // Try to fetch book from server
            val fetchedAudioFiles = fetchBookFromServer(bookId)
            if (fetchedAudioFiles == null) {
                logger.error { "Failed to fetch book from server: ${bookId.value}" }
                return null
            }
            audioFilesJson = fetchedAudioFiles
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
        val domainAudioFiles = audioFiles.map { it.toDomain() }
        val timeline =
            if (playbackApi != null && capabilityDetector != null) {
                // Use transcode-aware timeline building
                val capabilities = capabilityDetector.getSupportedCodecs()
                logger.debug { "Client codec capabilities: $capabilities" }

                PlaybackTimeline.buildWithTranscodeSupport(
                    bookId = bookId,
                    audioFiles = domainAudioFiles,
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
                    audioFiles = domainAudioFiles,
                    baseUrl = serverUrl,
                    resolveLocalPath = { audioFileId -> downloadService.getLocalPath(audioFileId) },
                )
            }
        _currentTimeline.value = timeline
        // Note: currentBookId is set by caller after reachability checks pass
        totalDurationMs.value = timeline.totalDurationMs

        // Load chapters for this book
        loadChapters(bookId)

        logger.info { "Built timeline: ${timeline.files.size} files, ${timeline.totalDurationMs}ms total" }

        // 6. Get resume position and speed
        val savedPosition = progressTracker.getResumePosition(bookId)

        // If book is marked as finished, restart from beginning (re-read scenario)
        // This ensures completed books don't resume near the end where user left off
        val resumePositionMs =
            if (savedPosition?.isFinished == true) {
                logger.info { "Book is marked finished - starting from beginning for re-read" }
                0L
            } else {
                savedPosition?.positionMs ?: 0L
            }

        // Determine playback speed: use book's custom speed if set, otherwise use universal default
        val resumeSpeed =
            if (savedPosition != null && savedPosition.hasCustomSpeed) {
                // Book has a custom speed set by user
                savedPosition.playbackSpeed
            } else {
                // Use universal default speed (synced across devices)
                playbackPreferences.getDefaultPlaybackSpeed()
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
            bookAuthor = bookAuthor,
            seriesName = seriesName,
            coverPath = coverPath,
            totalChapters = _chapters.value.size,
            resumePositionMs = resumePositionMs,
            resumeSpeed = resumeSpeed,
        )
    }

    /**
     * Start playback using a platform AudioPlayer.
     *
     * Bridges the prepared timeline to the AudioPlayer and connects
     * state flows back to PlaybackManager for position tracking.
     *
     * @param player The platform-specific audio player implementation
     * @param resumePositionMs Position to resume from (0 to start from beginning)
     * @param resumeSpeed Playback speed to use
     */
    suspend fun startPlayback(
        player: AudioPlayer,
        resumePositionMs: Long = 0L,
        resumeSpeed: Float = 1.0f,
    ) {
        val timeline = currentTimeline.value
        if (timeline == null) {
            logger.error { "Cannot start playback: no timeline prepared" }
            return
        }

        val bookId = currentBookId.value
        if (bookId == null) {
            logger.error { "Cannot start playback: no book ID" }
            return
        }

        // Build segments from timeline
        val segments =
            timeline.files.map { file ->
                AudioSegment(
                    url = file.streamingUrl,
                    localPath = file.localPath,
                    durationMs = file.durationMs,
                    offsetMs = file.startOffsetMs,
                )
            }

        // Load segments into player
        player.load(segments)

        // Set speed before seeking/playing
        if (resumeSpeed != 1.0f) {
            player.setSpeed(resumeSpeed)
        }
        playbackSpeed.value = resumeSpeed

        // Resume from saved position
        if (resumePositionMs > 0) {
            player.seekTo(resumePositionMs)
        }
        currentPositionMs.value = resumePositionMs

        // Bridge player position back to PlaybackManager
        scope.launch {
            player.positionMs.collect { position ->
                updatePosition(position)
            }
        }

        // Bridge player state back to PlaybackManager
        scope.launch {
            player.state.collect { playbackState ->
                val playing = playbackState == PlaybackState.Playing
                setPlaying(playing)

                if (playbackState == PlaybackState.Ended) {
                    progressTracker.onBookFinished(bookId, totalDurationMs.value)
                }
            }
        }

        // Start playback
        player.play()

        // Notify progress tracker
        progressTracker.onPlaybackStarted(bookId, resumePositionMs, resumeSpeed)
        logger.info { "Playback started via AudioPlayer at position ${resumePositionMs}ms, speed ${resumeSpeed}x" }
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
        updateCurrentChapter(positionMs)
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
     * Check if the server is reachable with a quick health check.
     * Used to warn users before attempting to stream non-downloaded content.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun isServerReachable(): Boolean {
        val url = serverConfig.getActiveUrl()?.value ?: return false
        return try {
            val client = HttpClient {
                install(HttpTimeout) {
                    requestTimeoutMillis = 3_000
                    connectTimeoutMillis = 3_000
                    socketTimeoutMillis = 3_000
                }
            }
            try {
                val response = client.get("$url/health")
                response.status.value in 200..299
            } finally {
                client.close()
            }
        } catch (e: Exception) {
            logger.debug { "Server reachability check failed: ${e.message}" }
            false
        }
    }

    /**
     * Clear current playback state.
     * Called when playback stops or when access is revoked.
     */
    override fun clearPlayback() {
        _currentBookId.value = null
        _currentTimeline.value = null
        _chapters.value = emptyList()
        _currentChapter.value = null
        isPlaying.value = false
        currentPositionMs.value = 0L
        totalDurationMs.value = 0L
        playbackSpeed.value = 1.0f
        _playbackError.value = null
    }

    /**
     * Report a playback error to be displayed to the user.
     * Called by platform-specific error handlers.
     */
    fun reportError(
        message: String,
        isRecoverable: Boolean = false,
    ) {
        _playbackError.value =
            PlaybackError(
                message = message,
                isRecoverable = isRecoverable,
                timestampMs =
                    com.calypsan.listenup.client.core
                        .currentEpochMilliseconds(),
            )
    }

    /**
     * Clear the current playback error.
     * Called when user dismisses the error or error condition is resolved.
     */
    fun clearError() {
        _playbackError.value = null
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
        val spatial = playbackPreferences.getSpatialPlayback()

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
     * Fetch book from server and save to local database.
     *
     * Used as a fallback when local book data is incomplete (e.g., audioFilesJson is missing).
     * This can happen when a user tries to play a book from Continue Listening before
     * the full sync has completed.
     *
     * @param bookId The book ID to fetch
     * @return The audioFilesJson string if successful, null otherwise
     */
    private suspend fun fetchBookFromServer(bookId: BookId): String? {
        val api = syncApi
        if (api == null) {
            logger.error { "SyncApi not available for fetching book" }
            return null
        }

        return when (val result = api.getBook(bookId.value)) {
            is Success -> {
                val bookResponse = result.data
                logger.info { "Fetched book from server: ${bookResponse.title}" }

                // Convert to entity and save to database
                val entity = bookResponse.toEntity()
                bookDao.upsert(entity)
                logger.debug { "Saved fetched book to local database" }

                // Return the audio files JSON
                entity.audioFilesJson
            }

            is Failure -> {
                logger.error(result.exception) { "Failed to fetch book from server: ${bookId.value}" }
                null
            }
        }
    }

    /**
     * Result of preparing for playback.
     */
    data class PrepareResult(
        val timeline: PlaybackTimeline,
        val bookTitle: String,
        val bookAuthor: String,
        val seriesName: String?,
        val coverPath: String?,
        val totalChapters: Int,
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

    /**
     * Playback error for display to the user.
     */
    data class PlaybackError(
        val message: String,
        val isRecoverable: Boolean,
        val timestampMs: Long,
    )

    /**
     * Current chapter information for notification and UI.
     */
    data class ChapterInfo(
        val index: Int,
        val title: String,
        val startMs: Long,
        val endMs: Long,
        val remainingMs: Long,
        val totalChapters: Int,
        val isGenericTitle: Boolean,
    )

    /**
     * Load chapters for a book.
     */
    private suspend fun loadChapters(bookId: BookId) {
        val entities = chapterDao.getChaptersForBook(bookId)
        _chapters.value =
            entities.map { entity ->
                Chapter(
                    id = entity.id.value,
                    title = entity.title,
                    duration = entity.duration,
                    startTime = entity.startTime,
                )
            }
        logger.debug { "Loaded ${_chapters.value.size} chapters for book ${bookId.value}" }
    }

    /**
     * Update current chapter based on position.
     * Called from updatePosition() to track chapter changes.
     */
    internal fun updateCurrentChapter(positionMs: Long) {
        val chapterList = _chapters.value
        if (chapterList.isEmpty()) {
            _currentChapter.value = null
            return
        }

        val index =
            chapterList
                .indexOfLast { it.startTime <= positionMs }
                .coerceAtLeast(0)

        val chapter = chapterList[index]
        val endMs =
            chapterList.getOrNull(index + 1)?.startTime
                ?: currentTimeline.value?.totalDurationMs
                ?: chapter.startTime

        val newChapter =
            ChapterInfo(
                index = index,
                title = chapter.title,
                startMs = chapter.startTime,
                endMs = endMs,
                remainingMs = (endMs - positionMs).coerceAtLeast(0),
                totalChapters = chapterList.size,
                isGenericTitle = isGenericChapterTitle(chapter.title),
            )

        // Only trigger notification update on chapter change
        if (newChapter.index != _currentChapter.value?.index) {
            _currentChapter.value = newChapter
            onChapterChanged?.invoke(newChapter)
        } else {
            // Update remaining time without triggering notification
            _currentChapter.value = newChapter
        }
    }

    /**
     * Detect if a chapter title is generic (e.g., "Chapter 14", "Track 7", or empty).
     */
    private fun isGenericChapterTitle(title: String): Boolean {
        val normalized = title.trim().lowercase()
        return normalized.isEmpty() ||
            normalized.matches(Regex("""^(chapter|part|track|section)\s*\d+$""")) ||
            normalized.matches(Regex("""^\d+$"""))
    }
}

// ========== Type Conversions ==========

/**
 * Convert data layer AudioFileResponse to domain AudioFile.
 */
private fun AudioFileResponse.toDomain(): AudioFile =
    AudioFile(
        id = id,
        filename = filename,
        format = format,
        codec = codec,
        duration = duration,
        size = size,
    )
