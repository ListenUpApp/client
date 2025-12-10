package com.calypsan.listenup.client.playback

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import com.calypsan.listenup.client.data.local.db.BookId
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay

private val logger = KotlinLogging.logger {}

/**
 * Handles playback errors with the principle: "Position is sacred."
 *
 * Error handling strategy:
 * 1. ALWAYS save position before showing error (never lose progress)
 * 2. ALWAYS show clear, actionable error message (never silent failures)
 * 3. AUTO-RETRY network errors (transient failures are common)
 * 4. FAIL FAST on auth/404/codec errors (don't waste time retrying the impossible)
 * 5. LOG everything (debugging > user messaging in Phase 1)
 */
class PlaybackErrorHandler(
    private val progressTracker: ProgressTracker,
    private val tokenProvider: AndroidAudioTokenProvider,
) {
    /**
     * Classifies errors into actionable categories.
     */
    sealed class PlaybackError {
        // Retryable - ExoPlayer handles internally, we just wait
        data class Network(
            val message: String,
        ) : PlaybackError()

        // Retryable once - refresh token, retry request
        data class AuthExpired(
            val message: String,
        ) : PlaybackError()

        // Not retryable - user action required
        data class NotFound(
            val message: String,
        ) : PlaybackError()

        data class Codec(
            val message: String,
        ) : PlaybackError()

        data class Unknown(
            val cause: Throwable,
        ) : PlaybackError()
    }

    /**
     * Maps ExoPlayer exceptions to our error types.
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun classify(error: PlaybackException): PlaybackError =
        when (error.errorCode) {
            // Network errors - ExoPlayer will retry, we just observe
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            -> {
                PlaybackError.Network("Network connection lost")
            }

            // HTTP errors - check status code
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
                val cause = error.cause
                val statusCode = (cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode

                when (statusCode) {
                    401 -> PlaybackError.AuthExpired("Session expired")
                    403 -> PlaybackError.AuthExpired("Access denied")
                    404 -> PlaybackError.NotFound("Audio file not found")
                    in 500..599 -> PlaybackError.Network("Server error, retrying...")
                    else -> PlaybackError.Unknown(error)
                }
            }

            // Decoder errors - file is broken or unsupported
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
            -> {
                PlaybackError.Codec("Cannot play this audio format")
            }

            else -> {
                PlaybackError.Unknown(error)
            }
        }

    /**
     * Handle error based on classification.
     * Returns true if playback should continue (error was handled).
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    suspend fun handle(
        error: PlaybackError,
        player: ExoPlayer,
        currentBookId: BookId?,
        onShowError: (String) -> Unit,
    ): Boolean {
        // ALWAYS save position first - position is sacred
        currentBookId?.let { bookId ->
            progressTracker.savePositionNow(bookId, player.currentPosition)
            logger.debug { "Position saved before error handling: ${player.currentPosition}" }
        }

        return when (error) {
            is PlaybackError.Network -> {
                // ExoPlayer handles retry internally
                // Just log and let it buffer
                logger.info { "Network error, ExoPlayer buffering: ${error.message}" }
                true // Continue, ExoPlayer is handling it
            }

            is PlaybackError.AuthExpired -> {
                logger.warn { "Auth expired during playback" }

                // Try token refresh
                tokenProvider.onUnauthorized()
                delay(1000) // Give refresh a moment

                val newToken = tokenProvider.getToken()
                if (newToken != null) {
                    // Token refreshed, retry
                    logger.info { "Token refreshed, retrying playback" }
                    player.prepare()
                    player.play()
                    true
                } else {
                    // Refresh failed - user needs to re-auth
                    onShowError("Session expired. Please sign in again.")
                    player.pause()
                    false
                }
            }

            is PlaybackError.NotFound -> {
                logger.error { "Audio file not found: ${error.message}" }
                onShowError("This audio file is no longer available.")
                player.pause()
                false
            }

            is PlaybackError.Codec -> {
                logger.error { "Codec error: ${error.message}" }
                onShowError("Cannot play this audio file. Format may be unsupported.")
                player.pause()
                false
            }

            is PlaybackError.Unknown -> {
                logger.error(error.cause) { "Unknown playback error" }
                onShowError("Playback error. Please try again.")
                player.pause()
                false
            }
        }
    }

    /**
     * Get a user-friendly message for an error.
     */
    fun getErrorMessage(error: PlaybackError): String =
        when (error) {
            is PlaybackError.Network -> "Connection lost. Retrying..."
            is PlaybackError.AuthExpired -> "Session expired. Please sign in."
            is PlaybackError.NotFound -> "File not available."
            is PlaybackError.Codec -> "Cannot play this format."
            is PlaybackError.Unknown -> "Playback error."
        }
}
