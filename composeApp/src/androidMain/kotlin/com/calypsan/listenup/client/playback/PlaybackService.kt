package com.calypsan.listenup.client.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.calypsan.listenup.client.data.local.db.BookId
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import android.util.Log
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.koin.android.ext.android.inject
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

private val logger = KotlinLogging.logger {}

/**
 * Background playback service using Media3.
 *
 * Responsibilities:
 * - Manages ExoPlayer instance
 * - Exposes MediaSession for system integration (notification, lock screen, Bluetooth)
 * - Handles audio focus
 * - Survives Activity destruction for background playback
 *
 * Lifecycle:
 * - Tiered idle timeouts (30min pause, 5min sleep timer, 2hr book finish)
 * - Explicit stop via notification
 * - Survives app swipe-away while timer is active
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private var notificationProvider: AudiobookNotificationProvider? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var idleJob: Job? = null
    private var positionUpdateJob: Job? = null

    // Inject dependencies
    private val playbackManager: PlaybackManager by inject()
    private val progressTracker: ProgressTracker by inject()
    private val errorHandler: PlaybackErrorHandler by inject()
    private val tokenProvider: AndroidAudioTokenProvider by inject()
    private val sleepTimerManager: SleepTimerManager by inject()

    // Current book ID is read from PlaybackManager (single source of truth)
    private val currentBookId: BookId?
        get() = playbackManager.currentBookId.value

    /**
     * Get the current book-relative position.
     *
     * ExoPlayer tracks position within the current file, but we need position
     * relative to the entire book for progress tracking. The PlaybackTimeline
     * handles this translation.
     *
     * @return Book-relative position in milliseconds, or 0 if unavailable
     */
    private fun getBookRelativePosition(): Long {
        val player = player ?: return 0L
        val timeline = playbackManager.currentTimeline.value ?: return player.currentPosition
        return timeline.toBookPosition(player.currentMediaItemIndex, player.currentPosition)
    }

    companion object {
        // Idle timeout tiers
        private val IDLE_TIMEOUT_SHORT = 30.minutes // After natural pause
        private val IDLE_TIMEOUT_LONG = 2.hours // After book completion
        private val IDLE_TIMEOUT_SLEEP = 5.minutes // After sleep timer fires

        // Position update interval
        private const val POSITION_UPDATE_INTERVAL = 30_000L // 30 seconds
    }

    override fun onCreate() {
        super.onCreate()
        logger.info { "PlaybackService created" }

        initializePlayer()
        initializeMediaSession()
        initializeNotificationProvider()

        // Register callback for chapter changes to update notification
        playbackManager.onChapterChanged = { chapterInfo ->
            logger.debug { "Chapter changed: ${chapterInfo.title}" }
            updateNotificationForChapter(chapterInfo)
        }
    }

    private fun initializePlayer() {
        // Create OkHttp client with auth interceptor
        val okHttpClient =
            OkHttpClient
                .Builder()
                .addInterceptor(tokenProvider.createInterceptor())
                .connectTimeout(30.seconds.toJavaDuration())
                .readTimeout(0.seconds.toJavaDuration()) // No read timeout for streaming
                .writeTimeout(30.seconds.toJavaDuration())
                .build()

        // Create DataSource factory that uses OkHttp
        val dataSourceFactory: DataSource.Factory = OkHttpDataSource.Factory(okHttpClient)

        // Create media source factory
        val mediaSourceFactory =
            DefaultMediaSourceFactory(this)
                .setDataSourceFactory(dataSourceFactory)

        // Create renderers factory with decoder fallback for better compatibility
        val renderersFactory =
            DefaultRenderersFactory(this)
                .setEnableDecoderFallback(true)

        // Build ExoPlayer with audiobook-optimized settings
        player =
            ExoPlayer
                .Builder(this)
                .setRenderersFactory(renderersFactory)
                .setMediaSourceFactory(mediaSourceFactory)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH) // Audiobooks!
                        .build(),
                    // handleAudioFocus =
                    true,
                ).setHandleAudioBecomingNoisy(true) // Pause when headphones unplugged
                .setWakeMode(C.WAKE_MODE_LOCAL) // Keep CPU awake during playback
                .build()
                .apply {
                    addListener(PlayerListener())

                    // Enable audio offload for battery savings during long listening sessions
                    // DSP-based decoding while CPU sleeps
                    val audioOffloadPreferences =
                        TrackSelectionParameters.AudioOffloadPreferences
                            .Builder()
                            .setAudioOffloadMode(
                                TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED,
                            ).setIsGaplessSupportRequired(true)
                            .build()

                    trackSelectionParameters =
                        trackSelectionParameters
                            .buildUpon()
                            .setAudioOffloadPreferences(audioOffloadPreferences)
                            .build()
                }

        logger.info { "ExoPlayer initialized with audio offload enabled" }
    }

    private fun initializeMediaSession() {
        val sessionIntent =
            packageManager?.getLaunchIntentForPackage(packageName)?.let { intent ->
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }

        val builder = MediaSession.Builder(this, player!!)
        if (sessionIntent != null) {
            builder.setSessionActivity(sessionIntent)
        }

        // Add callback for custom commands (chapter skip, 30s skip)
        builder.setCallback(MediaSessionCallback())

        mediaSession = builder.build()

        logger.info { "MediaSession initialized" }
    }

    private fun initializeNotificationProvider() {
        notificationProvider = AudiobookNotificationProvider(this, playbackManager)
        setMediaNotificationProvider(notificationProvider!!)
        logger.info { "Notification provider initialized" }
    }

    /**
     * Update the notification when chapter changes.
     *
     * We update the MediaMetadata subtitle to reflect the new chapter info,
     * which triggers Media3 to rebuild the notification.
     */
    private fun updateNotificationForChapter(chapterInfo: PlaybackManager.ChapterInfo) {
        val session = mediaSession ?: return
        val player = player ?: return

        // Build chapter subtitle
        val chapterText =
            if (chapterInfo.isGenericTitle) {
                "Chapter ${chapterInfo.index + 1} of ${chapterInfo.totalChapters}"
            } else {
                chapterInfo.title
            }

        val timeRemaining = formatDuration(chapterInfo.remainingMs)
        val subtitle = "$chapterText • $timeRemaining left"

        // Update current media item metadata with chapter info as subtitle
        val currentMetadata = player.mediaMetadata
        val updatedMetadata =
            MediaMetadata
                .Builder()
                .populate(currentMetadata)
                .setSubtitle(subtitle)
                .build()

        // Notify session of metadata change to trigger notification update
        session.setSessionExtras(Bundle().apply { putString("chapter_subtitle", subtitle) })

        logger.debug { "Updated notification: $subtitle" }
    }

    private fun formatDuration(ms: Long): String {
        val totalMinutes = ms / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "< 1m"
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        // Don't stop immediately - keep the idle timer running
        // User can still resume from notification
        if (player == null || (!player.playWhenReady && idleJob == null)) {
            serviceScope.launch {
                saveCurrentPosition()
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        logger.info { "PlaybackService destroying" }

        idleJob?.cancel()
        positionUpdateJob?.cancel()

        // Clear chapter change callback to avoid memory leaks
        playbackManager.onChapterChanged = null

        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        player = null
        notificationProvider = null

        serviceScope.cancel()
        super.onDestroy()
    }

    private fun saveCurrentPosition() {
        val player = player ?: return
        val bookId = currentBookId ?: return

        progressTracker.onPlaybackPaused(
            bookId = bookId,
            positionMs = getBookRelativePosition(),
            speed = player.playbackParameters.speed,
        )
    }

    private fun startIdleTimer(
        timeout: kotlin.time.Duration,
        reason: String,
    ) {
        idleJob?.cancel()
        idleJob =
            serviceScope.launch {
                logger.debug { "Idle timer started: $timeout ($reason)" }
                delay(timeout)
                logger.info { "Idle timeout reached, stopping service ($reason)" }
                saveCurrentPosition()
                stopSelf()
            }
    }

    private fun cancelIdleTimer() {
        idleJob?.cancel()
        idleJob = null
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob =
            serviceScope.launch {
                while (isActive) {
                    delay(POSITION_UPDATE_INTERVAL)
                    val player = player ?: break
                    val bookId = currentBookId ?: break

                    if (player.isPlaying) {
                        progressTracker.onPositionUpdate(
                            bookId = bookId,
                            positionMs = getBookRelativePosition(),
                            speed = player.playbackParameters.speed,
                        )
                    }
                }
            }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    /**
     * Listens to player events for logging and progress tracking.
     */
    private inner class PlayerListener : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val stateName =
                when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN"
                }
            logger.debug { "Playback state: $stateName" }

            when (playbackState) {
                Player.STATE_ENDED -> {
                    // Book finished - longer grace period
                    currentBookId?.let { bookId ->
                        val p = this@PlaybackService.player
                        val timeline = playbackManager.currentTimeline.value
                        val finalPosition =
                            timeline?.totalDurationMs
                                ?: p?.duration
                                ?: 0L
                        progressTracker.onBookFinished(bookId, finalPosition)
                    }
                    startIdleTimer(IDLE_TIMEOUT_LONG, "book_finished")
                }

                Player.STATE_IDLE -> {
                    // Player cleared
                    startIdleTimer(IDLE_TIMEOUT_SHORT, "idle")
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            logger.debug { "Is playing: $isPlaying" }

            val bookId = currentBookId
            val player = player

            Log.d("PlaybackService", "onIsPlayingChanged: isPlaying=$isPlaying, bookId=${bookId?.value}")

            if (isPlaying) {
                cancelIdleTimer()
                startPositionUpdates()

                if (bookId != null && player != null) {
                    val position = getBookRelativePosition()
                    Log.d("PlaybackService", "PLAYBACK STARTED: bookId=${bookId.value}, position=$position")
                    progressTracker.onPlaybackStarted(
                        bookId = bookId,
                        positionMs = position,
                        speed = player.playbackParameters.speed,
                    )
                }
            } else {
                stopPositionUpdates()

                if (bookId != null && player != null) {
                    val position = getBookRelativePosition()
                    Log.d("PlaybackService", "PLAYBACK PAUSED: bookId=${bookId.value}, position=$position - calling progressTracker.onPlaybackPaused")
                    progressTracker.onPlaybackPaused(
                        bookId = bookId,
                        positionMs = position,
                        speed = player.playbackParameters.speed,
                    )
                }

                // Context-aware idle timer based on why playback stopped
                val isSleepTimerPause = sleepTimerManager.state.value is SleepTimerState.FadingOut
                if (isSleepTimerPause) {
                    startIdleTimer(IDLE_TIMEOUT_SLEEP, "sleep_timer")
                } else {
                    startIdleTimer(IDLE_TIMEOUT_SHORT, "paused")
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            logger.error(error) { "Playback error: ${error.message}" }

            serviceScope.launch {
                val classified = errorHandler.classify(error)

                val handled =
                    errorHandler.handle(
                        error = classified,
                        player = player!!,
                        currentBookId = currentBookId,
                        onShowError = { message ->
                            // Report error to PlaybackManager for UI display
                            playbackManager.reportError(
                                message = message,
                                isRecoverable = false,
                            )
                        },
                    )

                if (!handled) {
                    // Error couldn't be recovered
                    startIdleTimer(IDLE_TIMEOUT_SHORT, "error")
                }
            }
        }

        override fun onMediaItemTransition(
            mediaItem: MediaItem?,
            reason: Int,
        ) {
            logger.debug { "Media item transition: ${mediaItem?.mediaId}, reason: $reason" }
        }
    }

    /**
     * MediaSession callback for handling custom commands and playback resumption.
     *
     * Handles audiobook-specific commands:
     * - Skip back/forward 30 seconds
     * - Previous/next chapter
     * - Playback resumption (Android Auto, Wear OS, system notifications)
     */
    private inner class MediaSessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            // Add custom commands to the session
            val customCommands =
                AudiobookNotificationProvider.getCustomCommands().map { command ->
                    command
                }

            return MediaSession.ConnectionResult
                .AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                        .buildUpon()
                        .apply { customCommands.forEach { add(it) } }
                        .build(),
                ).build()
        }

        /**
         * Handle playback resumption from system UI.
         *
         * Called when user taps "Resume ListenUp" from Android Auto, Wear OS,
         * or system notifications after device reboot.
         */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            logger.info { "Playback resumption requested" }

            // Use CallbackToFutureAdapter for proper async handling without blocking
            return androidx.concurrent.futures.CallbackToFutureAdapter.getFuture { completer ->
                serviceScope.launch {
                    try {
                        // Get the last played book from ProgressTracker
                        val lastPlayed = progressTracker.getLastPlayedBook()

                        if (lastPlayed == null) {
                            logger.warn { "No last played book found for resumption" }
                            completer.setException(IllegalStateException("No book to resume"))
                            return@launch
                        }

                        logger.info { "Resuming book: ${lastPlayed.bookId.value} at ${lastPlayed.positionMs}ms" }

                        // Prepare playback for the book
                        val prepareResult = playbackManager.prepareForPlayback(lastPlayed.bookId)

                        if (prepareResult == null) {
                            logger.error { "Failed to prepare book for resumption" }
                            completer.setException(IllegalStateException("Failed to prepare book"))
                            return@launch
                        }

                        // Build MediaItems from timeline
                        val mediaItems =
                            prepareResult.timeline.files.map { file ->
                                MediaItem
                                    .Builder()
                                    .setMediaId(file.audioFileId)
                                    .setUri(file.streamingUrl)
                                    .setMediaMetadata(
                                        MediaMetadata
                                            .Builder()
                                            .setTitle(prepareResult.bookTitle)
                                            .setArtist(prepareResult.bookAuthor)
                                            .setAlbumTitle(prepareResult.seriesName)
                                            .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK)
                                            .build(),
                                    ).build()
                            }

                        // Resolve start position
                        val startPosition = prepareResult.timeline.resolve(prepareResult.resumePositionMs)

                        completer.set(
                            MediaSession.MediaItemsWithStartPosition(
                                mediaItems,
                                startPosition.mediaItemIndex,
                                startPosition.positionInFileMs,
                            ),
                        )
                    } catch (e: Exception) {
                        logger.error(e) { "Playback resumption failed" }
                        completer.setException(e)
                    }
                }
                "PlaybackResumption"
            }
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            val player =
                player ?: return Futures.immediateFuture(
                    SessionResult(SessionResult.RESULT_ERROR_UNKNOWN),
                )
            val chapters = playbackManager.chapters.value
            val currentChapter = playbackManager.currentChapter.value
            val timeline = playbackManager.currentTimeline.value

            when (customCommand.customAction) {
                AudiobookNotificationProvider.COMMAND_SKIP_BACK_30 -> {
                    // Get book-relative position, subtract 30s, seek
                    val currentBookPosition = getBookRelativePosition()
                    val newPosition = (currentBookPosition - 30_000).coerceAtLeast(0)

                    // Resolve new position to mediaItemIndex and filePosition
                    if (timeline != null) {
                        val resolved = timeline.resolve(newPosition)
                        player.seekTo(resolved.mediaItemIndex, resolved.positionInFileMs)
                    } else {
                        // Fallback to simple seek within current file
                        val newFilePosition = (player.currentPosition - 30_000).coerceAtLeast(0)
                        player.seekTo(newFilePosition)
                    }
                    logger.debug { "Skip back 30s: $currentBookPosition -> $newPosition" }
                }

                AudiobookNotificationProvider.COMMAND_SKIP_FORWARD_30 -> {
                    val currentBookPosition = getBookRelativePosition()
                    val maxPosition = timeline?.totalDurationMs ?: player.duration
                    val newPosition = (currentBookPosition + 30_000).coerceAtMost(maxPosition)

                    if (timeline != null) {
                        val resolved = timeline.resolve(newPosition)
                        player.seekTo(resolved.mediaItemIndex, resolved.positionInFileMs)
                    } else {
                        val newFilePosition = (player.currentPosition + 30_000).coerceAtMost(player.duration)
                        player.seekTo(newFilePosition)
                    }
                    logger.debug { "Skip forward 30s: $currentBookPosition -> $newPosition" }
                }

                AudiobookNotificationProvider.COMMAND_PREV_CHAPTER -> {
                    if (currentChapter != null && chapters.isNotEmpty() && timeline != null) {
                        val prevIndex = (currentChapter.index - 1).coerceAtLeast(0)
                        val prevChapter = chapters[prevIndex]
                        val resolved = timeline.resolve(prevChapter.startTime)
                        player.seekTo(resolved.mediaItemIndex, resolved.positionInFileMs)
                        logger.debug { "Previous chapter: ${prevIndex + 1} of ${chapters.size}" }
                    }
                }

                AudiobookNotificationProvider.COMMAND_NEXT_CHAPTER -> {
                    if (currentChapter != null && chapters.isNotEmpty() && timeline != null) {
                        val nextIndex = (currentChapter.index + 1).coerceAtMost(chapters.size - 1)
                        val nextChapter = chapters[nextIndex]
                        val resolved = timeline.resolve(nextChapter.startTime)
                        player.seekTo(resolved.mediaItemIndex, resolved.positionInFileMs)
                        logger.debug { "Next chapter: ${nextIndex + 1} of ${chapters.size}" }
                    }
                }
            }

            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }
}
