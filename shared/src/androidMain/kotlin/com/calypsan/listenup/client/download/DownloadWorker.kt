@file:Suppress("MagicNumber", "NestedBlockDepth")

package com.calypsan.listenup.client.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.calypsan.listenup.client.core.error.DownloadError
import com.calypsan.listenup.client.core.error.ErrorBus
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.remote.PlaybackApi
import com.calypsan.listenup.client.domain.repository.DownloadRepository
import com.calypsan.listenup.client.domain.repository.PlaybackPreferences
import com.calypsan.listenup.client.playback.AudioCapabilityDetector
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import java.io.File
import java.io.FileOutputStream

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
    private val playbackApi: PlaybackApi,
    private val capabilityDetector: AudioCapabilityDetector,
) : CoroutineWorker(context, params) {
    companion object {
        const val KEY_AUDIO_FILE_ID = "audio_file_id"
        const val KEY_BOOK_ID = "book_id"
        const val KEY_FILENAME = "filename"
        const val KEY_FILE_SIZE = "file_size"

        private const val PROGRESS_INTERVAL_MS = 500L
        private const val BUFFER_SIZE = 8 * 1024
        private const val PROGRESS_BYTES_INTERVAL = 256 * 1024L // 256KB — emit progress at least every quarter MB
        private const val MAX_RETRIES = 3
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
        } catch (e: ResponseException) {
            // 401 after Auth plugin's refreshTokens returned null — treat as auth failure and pause.
            // Other 4xx fall through to handleRetryableError for typed-error reporting.
            if (e.response.status.value == HttpStatusCode.Unauthorized.value) {
                logger.warn(e) { "Download paused due to auth failure: $audioFileId" }
                downloadRepository.markFailed(
                    audioFileId,
                    DownloadError.DownloadFailed(debugInfo = e.message),
                )
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

    @Suppress("CyclomaticComplexMethod", "CognitiveComplexMethod")
    private suspend fun downloadFile(
        audioFileId: String,
        bookId: String,
        filename: String,
        expectedSize: Long,
    ) = withContext(Dispatchers.IO) {
        // Resolve the download URL (relative — Ktor's defaultRequest provides the base).
        // The transcode-poll path inside resolveDownloadUrl is preserved for Phase C; Phase D
        // rewrites it to use markWaitingForServer + SSE re-enqueue.
        val url = resolveDownloadUrl(bookId, audioFileId)

        val destPath = fileManager.getDownloadPath(bookId, audioFileId, filename)
        val tempPath = fileManager.getTempPath(bookId, audioFileId, filename)
        val tempFile = File(tempPath.toString())

        // Resume support: if a partial tempFile exists, send Range header.
        val startByte = if (tempFile.exists()) tempFile.length() else 0L

        httpClient
            .prepareGet(url) {
                if (startByte > 0) {
                    header(HttpHeaders.Range, "bytes=$startByte-")
                    logger.debug { "Resuming download from byte $startByte" }
                }
            }.execute { response ->
                // HttpResponseValidator (installed by ApiClientFactory) raises typed exceptions on
                // non-2xx; we only see successful or partial-content responses here. Status code 206
                // is success per RFC 7233; treat it the same as 200.

                val contentLength = response.contentLength() ?: -1L
                val totalSize =
                    if (startByte > 0 && response.status == HttpStatusCode.PartialContent) {
                        startByte + contentLength
                    } else {
                        contentLength
                    }

                // Update total size in DB up front so the UI shows progress against the right denominator.
                if (totalSize > 0) {
                    downloadRepository.updateProgress(audioFileId, startByte, totalSize)
                }

                // Stream the body into the temp file. Append mode iff we're resuming.
                val channel = response.bodyAsChannel()
                FileOutputStream(tempFile, startByte > 0).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var totalBytesRead = startByte
                    var lastProgressUpdate = 0L
                    var lastProgressBytes = startByte

                    while (!channel.isClosedForRead) {
                        if (isStopped) {
                            throw CancellationException("Download stopped")
                        }

                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read <= 0) continue
                        output.write(buffer, 0, read)
                        totalBytesRead += read

                        val now = System.currentTimeMillis()
                        val sinceLastProgress = totalBytesRead - lastProgressBytes
                        if (now - lastProgressUpdate > PROGRESS_INTERVAL_MS ||
                            sinceLastProgress >= PROGRESS_BYTES_INTERVAL
                        ) {
                            if (totalSize > 0) {
                                downloadRepository.updateProgress(audioFileId, totalBytesRead, totalSize)
                            }
                            setProgress(
                                workDataOf(
                                    "progress" to totalBytesRead,
                                    "total" to totalSize,
                                ),
                            )
                            lastProgressUpdate = now
                            lastProgressBytes = totalBytesRead
                        }
                    }
                }

                // Verify size if known.
                if (expectedSize > 0 && tempFile.length() != expectedSize) {
                    tempFile.delete()
                    throw IOException("Size mismatch: expected $expectedSize, got ${tempFile.length()}")
                }

                // Move temp to final destination via FileManager.
                if (!fileManager.moveFile(tempPath, destPath)) {
                    throw IOException("Failed to move temp file to destination")
                }

                // Mark complete via repository.
                downloadRepository.markCompleted(
                    audioFileId = audioFileId,
                    localPath = destPath.toString(),
                    completedAt = System.currentTimeMillis(),
                )
            }
    }

    /**
     * Resolve the correct download URL via the prepare endpoint.
     *
     * Returns a relative URL (Ktor's defaultRequest provides the base server URL via the
     * authenticated HttpClient). Phase C preserves the existing 30-minute polling loop;
     * Phase D rewrites this path to use markWaitingForServer + SSE re-enqueue.
     */
    private suspend fun resolveDownloadUrl(
        bookId: String,
        audioFileId: String,
    ): String {
        val capabilities = capabilityDetector.getSupportedCodecs()
        val spatial = playbackPreferences.getSpatialPlayback()
        val result = playbackApi.preparePlayback(bookId, audioFileId, capabilities, spatial)

        if (result !is Success) {
            logger.warn { "Prepare call failed, using original URL" }
            return "/api/v1/books/$bookId/audio/$audioFileId"
        }

        val response = result.data

        // Phase C preserves the existing transcode-poll path. Phase D rewrites this.
        if (!response.ready && response.transcodeJobId != null) {
            logger.info {
                "Transcoding in progress for $audioFileId, waiting... " +
                    "(jobId=${response.transcodeJobId}, progress=${response.progress}%)"
            }

            val maxWaitMs = 30 * 60 * 1000L
            val startTime = System.currentTimeMillis()
            var lastProgress = response.progress

            while (System.currentTimeMillis() - startTime < maxWaitMs) {
                if (isStopped) {
                    throw CancellationException("Download cancelled while waiting for transcode")
                }

                delay(5000)

                val checkResult = playbackApi.preparePlayback(bookId, audioFileId, capabilities, spatial)
                if (checkResult is Success) {
                    val checkResponse = checkResult.data
                    if (checkResponse.ready) {
                        logger.info { "Transcode completed for $audioFileId" }
                        return relativizeUrl(checkResponse.streamUrl)
                    }
                    if (checkResponse.progress > lastProgress) {
                        logger.debug { "Transcode progress: ${checkResponse.progress}%" }
                        lastProgress = checkResponse.progress
                    }
                }
            }

            logger.warn { "Transcode timeout for $audioFileId, using original URL" }
            return "/api/v1/books/$bookId/audio/$audioFileId"
        }

        logger.debug {
            "Using ${response.variant} variant for $audioFileId (codec: ${response.codec})"
        }
        return relativizeUrl(response.streamUrl)
    }

    /** Strip an absolute server URL prefix off [streamUrl] so we always pass a relative URL to Ktor. */
    private fun relativizeUrl(streamUrl: String): String =
        when {
            streamUrl.startsWith("/") -> {
                streamUrl
            }

            streamUrl.startsWith("http") -> {
                val pathStart = streamUrl.indexOf('/', startIndex = "https://".length)
                if (pathStart > 0) streamUrl.substring(pathStart) else "/$streamUrl"
            }

            else -> {
                "/$streamUrl"
            }
        }
}
