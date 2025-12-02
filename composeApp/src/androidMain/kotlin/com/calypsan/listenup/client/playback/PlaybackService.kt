package com.calypsan.listenup.client.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.calypsan.listenup.client.data.local.db.BookId
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
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

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

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var idleJob: Job? = null
    private var positionUpdateJob: Job? = null

    // Inject dependencies
    private val playbackManager: PlaybackManager by inject()
    private val progressTracker: ProgressTracker by inject()
    private val errorHandler: PlaybackErrorHandler by inject()
    private val tokenProvider: AndroidAudioTokenProvider by inject()

    // Current book ID is read from PlaybackManager (single source of truth)
    private val currentBookId: BookId?
        get() = playbackManager.currentBookId.value

    companion object {
        // Idle timeout tiers
        private val IDLE_TIMEOUT_SHORT = 30.minutes    // After natural pause
        private val IDLE_TIMEOUT_LONG = 2.hours        // After book completion
        private val IDLE_TIMEOUT_SLEEP = 5.minutes     // After sleep timer fires

        // Position update interval
        private val POSITION_UPDATE_INTERVAL = 30_000L // 30 seconds
    }

    override fun onCreate() {
        super.onCreate()
        logger.info { "PlaybackService created" }

        initializePlayer()
        initializeMediaSession()
    }

    private fun initializePlayer() {
        // Create OkHttp client with auth interceptor
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(tokenProvider.createInterceptor())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // No read timeout for streaming
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        // Create DataSource factory that uses OkHttp
        val dataSourceFactory: DataSource.Factory = OkHttpDataSource.Factory(okHttpClient)

        // Create media source factory
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(dataSourceFactory)

        // Build ExoPlayer
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH) // Audiobooks!
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true) // Pause when headphones unplugged
            .build()
            .apply {
                addListener(PlayerListener())
            }

        logger.info { "ExoPlayer initialized" }
    }

    private fun initializeMediaSession() {
        val sessionIntent = packageManager?.getLaunchIntentForPackage(packageName)?.let { intent ->
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        val builder = MediaSession.Builder(this, player!!)
        if (sessionIntent != null) {
            builder.setSessionActivity(sessionIntent)
        }
        mediaSession = builder.build()

        logger.info { "MediaSession initialized" }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

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

        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        player = null

        serviceScope.cancel()
        super.onDestroy()
    }

    private fun saveCurrentPosition() {
        val player = player ?: return
        val bookId = currentBookId ?: return

        progressTracker.onPlaybackPaused(
            bookId = bookId,
            positionMs = player.currentPosition,
            speed = player.playbackParameters.speed
        )
    }

    private fun startIdleTimer(timeout: kotlin.time.Duration, reason: String) {
        idleJob?.cancel()
        idleJob = serviceScope.launch {
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
        positionUpdateJob = serviceScope.launch {
            while (isActive) {
                delay(POSITION_UPDATE_INTERVAL)
                val player = player ?: break
                val bookId = currentBookId ?: break

                if (player.isPlaying) {
                    progressTracker.onPositionUpdate(
                        bookId = bookId,
                        positionMs = player.currentPosition,
                        speed = player.playbackParameters.speed
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
            val stateName = when (playbackState) {
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
                    currentBookId?.let { progressTracker.onBookFinished(it) }
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

            if (isPlaying) {
                cancelIdleTimer()
                startPositionUpdates()

                if (bookId != null && player != null) {
                    progressTracker.onPlaybackStarted(
                        bookId = bookId,
                        positionMs = player.currentPosition,
                        speed = player.playbackParameters.speed
                    )
                }
            } else {
                stopPositionUpdates()

                if (bookId != null && player != null) {
                    progressTracker.onPlaybackPaused(
                        bookId = bookId,
                        positionMs = player.currentPosition,
                        speed = player.playbackParameters.speed
                    )
                }

                // Start idle timer on pause
                startIdleTimer(IDLE_TIMEOUT_SHORT, "paused")
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            logger.error(error) { "Playback error: ${error.message}" }

            serviceScope.launch {
                val classified = errorHandler.classify(error)

                val handled = errorHandler.handle(
                    error = classified,
                    player = player!!,
                    currentBookId = currentBookId,
                    onShowError = { message ->
                        // TODO: Show error notification or update UI state
                        logger.error { "Error to show user: $message" }
                    }
                )

                if (!handled) {
                    // Error couldn't be recovered
                    startIdleTimer(IDLE_TIMEOUT_SHORT, "error")
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            logger.debug { "Media item transition: ${mediaItem?.mediaId}, reason: $reason" }
        }
    }
}
