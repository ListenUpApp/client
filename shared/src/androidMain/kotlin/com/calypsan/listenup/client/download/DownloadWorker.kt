package com.calypsan.listenup.client.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.calypsan.listenup.client.core.error.AppException
import com.calypsan.listenup.client.core.error.DownloadError
import com.calypsan.listenup.client.core.error.ErrorBus
import com.calypsan.listenup.client.core.error.ServerError
import com.calypsan.listenup.client.data.remote.PlaybackApiContract
import com.calypsan.listenup.client.domain.repository.DownloadRepository
import com.calypsan.listenup.client.domain.repository.PlaybackPreferences
import com.calypsan.listenup.client.playback.AudioCapabilityDetector
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException

private val logger = KotlinLogging.logger {}

/**
 * WorkManager worker that downloads a single audio file.
 *
 * Features:
 * - Codec negotiation (downloads transcoded variant if needed)
 * - Resume support (Range headers)
 * - Progress updates
 * - Cancellation handling
 * - Automatic retry (up to 3 times)
 */
class DownloadWorker(
    context: Context,
    params: WorkerParameters,
    private val downloadRepository: DownloadRepository,
    private val fileManager: DownloadFileManager,
    private val httpClient: HttpClient,
    private val playbackPreferences: PlaybackPreferences,
    private val playbackApi: PlaybackApiContract,
    private val capabilityDetector: AudioCapabilityDetector,
) : CoroutineWorker(context, params) {
    companion object {
        const val KEY_AUDIO_FILE_ID = "audio_file_id"
        const val KEY_BOOK_ID = "book_id"
        const val KEY_FILENAME = "filename"
        const val KEY_FILE_SIZE = "file_size"

        private const val MAX_RETRIES = 3
        private const val HTTP_UNAUTHORIZED = 401
    }

    override suspend fun doWork(): Result {
        val audioFileId = inputData.getString(KEY_AUDIO_FILE_ID) ?: return Result.failure()
        val bookId = inputData.getString(KEY_BOOK_ID) ?: return Result.failure()
        val filename = inputData.getString(KEY_FILENAME) ?: return Result.failure()
        val expectedSize = inputData.getLong(KEY_FILE_SIZE, 0L)

        logger.info { "Starting download: $audioFileId ($filename)" }

        downloadRepository.markDownloading(audioFileId, System.currentTimeMillis())

        return try {
            downloadFile(audioFileId, bookId, filename, expectedSize)
            logger.info { "Download complete: $audioFileId" }
            Result.success()
        } catch (e: CancellationException) {
            logger.info { "Download cancelled: $audioFileId" }
            downloadRepository.markPaused(audioFileId)
            Result.failure()
        } catch (e: AppException) {
            // installListenUpErrorHandling() rewraps every ResponseException as AppException,
            // so the old `catch (e: ResponseException)` block was dead code. Detect the 401
            // case via the already-mapped AppError (ErrorMapper maps 401 → ServerError(401)).
            // Restoration: pre-Phase-C the worker called markPaused on auth failure; preserve
            // that semantic to avoid burning the 3-attempt retry budget on unrecoverable auth.
            val serverError = e.error as? ServerError
            if (serverError?.statusCode == HTTP_UNAUTHORIZED) {
                logger.warn(e) { "Download paused due to auth failure: $audioFileId" }
                downloadRepository.markPaused(audioFileId)
                Result.failure()
            } else {
                handleRetryableError(audioFileId, e)
            }
        } catch (e: IOException) {
            // Check if this is a storage-related error (no retry for these)
            val message = e.message?.lowercase() ?: ""
            val isStorageError =
                message.contains("no space") ||
                    message.contains("enospc") ||
                    message.contains("disk full") ||
                    message.contains("storage")

            if (isStorageError) {
                ErrorBus.emit(DownloadError.InsufficientStorage(debugInfo = e.message))
                logger.error { "Download failed due to insufficient storage: $audioFileId" }
                downloadRepository.markFailed(audioFileId, DownloadError.InsufficientStorage(debugInfo = e.message))
                Result.failure()
            } else {
                // Regular IO error - may be transient, allow retry
                handleRetryableError(audioFileId, e)
            }
        } catch (e: Exception) {
            handleRetryableError(audioFileId, e)
        }
    }

    private suspend fun handleRetryableError(
        audioFileId: String,
        e: Exception,
    ): Result {
        ErrorBus.emit(DownloadError.DownloadFailed(debugInfo = e.message))
        logger.error(e) { "Download failed: $audioFileId" }
        // markFailed sets state=FAILED + writes errorMessage + increments retryCount in one call,
        // collapsing the previous redundant updateError + updateState(FAILED) writes (the prior
        // updateError already set state=FAILED via its underlying query — no behavior change).
        downloadRepository.markFailed(audioFileId, DownloadError.DownloadFailed(debugInfo = e.message))

        return if (runAttemptCount < MAX_RETRIES) {
            logger.info { "Will retry download: $audioFileId (attempt ${runAttemptCount + 1})" }
            Result.retry()
        } else {
            Result.failure()
        }
    }

    private suspend fun downloadFile(
        audioFileId: String,
        bookId: String,
        filename: String,
        expectedSize: Long,
    ) = downloadAudioFile(
        audioFileId = audioFileId,
        bookId = bookId,
        filename = filename,
        expectedSize = expectedSize,
        httpClient = httpClient,
        repository = downloadRepository,
        fileManager = fileManager,
        playbackApi = playbackApi,
        playbackPreferences = playbackPreferences,
        capabilityDetector = capabilityDetector,
        isStopped = { isStopped },
        setProgress = { downloaded, total ->
            setProgress(workDataOf("progress" to downloaded, "total" to total))
        },
    )
}
