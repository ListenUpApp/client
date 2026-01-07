@file:Suppress("MagicNumber", "NestedBlockDepth")

package com.calypsan.listenup.client.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.local.db.DownloadDao
import com.calypsan.listenup.client.data.local.db.DownloadState
import com.calypsan.listenup.client.data.remote.PlaybackApi
import com.calypsan.listenup.client.domain.repository.SettingsRepository
import com.calypsan.listenup.client.playback.AudioCapabilityDetector
import com.calypsan.listenup.client.playback.AudioTokenProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

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
    private val downloadDao: DownloadDao,
    private val fileManager: DownloadFileManager,
    private val tokenProvider: AudioTokenProvider,
    private val settingsRepository: SettingsRepository,
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
        private const val MAX_RETRIES = 3
    }

    override suspend fun doWork(): Result {
        val audioFileId = inputData.getString(KEY_AUDIO_FILE_ID) ?: return Result.failure()
        val bookId = inputData.getString(KEY_BOOK_ID) ?: return Result.failure()
        val filename = inputData.getString(KEY_FILENAME) ?: return Result.failure()
        val expectedSize = inputData.getLong(KEY_FILE_SIZE, 0L)

        logger.info { "Starting download: $audioFileId ($filename)" }

        downloadDao.updateState(audioFileId, DownloadState.DOWNLOADING, System.currentTimeMillis())

        return try {
            downloadFile(audioFileId, bookId, filename, expectedSize)
            logger.info { "Download complete: $audioFileId" }
            Result.success()
        } catch (e: CancellationException) {
            logger.info { "Download cancelled: $audioFileId" }
            downloadDao.updateState(audioFileId, DownloadState.PAUSED)
            Result.failure()
        } catch (e: IOException) {
            // Check if this is a storage-related error (no retry for these)
            val message = e.message?.lowercase() ?: ""
            val isStorageError =
                message.contains("no space") ||
                    message.contains("enospc") ||
                    message.contains("disk full") ||
                    message.contains("storage")

            if (isStorageError) {
                logger.error { "Download failed due to insufficient storage: $audioFileId" }
                downloadDao.updateError(audioFileId, "Insufficient storage space")
                downloadDao.updateState(audioFileId, DownloadState.FAILED)
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
        logger.error(e) { "Download failed: $audioFileId" }
        downloadDao.updateError(audioFileId, e.message ?: "Unknown error")

        return if (runAttemptCount < MAX_RETRIES) {
            logger.info { "Will retry download: $audioFileId (attempt ${runAttemptCount + 1})" }
            Result.retry()
        } else {
            downloadDao.updateState(audioFileId, DownloadState.FAILED)
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
        // Ensure fresh token before download
        tokenProvider.prepareForPlayback()

        val token =
            tokenProvider.getToken()
                ?: error("No auth token available")

        val serverUrl =
            settingsRepository.getServerUrl()?.value
                ?: error("No server URL configured")

        // Get the correct download URL via the prepare endpoint
        // This ensures we download the transcoded version if the device
        // doesn't support the source codec
        val url = resolveDownloadUrl(bookId, audioFileId, serverUrl)

        // Get paths from file manager and convert to java.io.File for I/O operations
        val destPath = fileManager.getDownloadPath(bookId, audioFileId, filename)
        val tempPath = fileManager.getTempPath(bookId, audioFileId, filename)
        val tempFile = File(tempPath.toString())

        // Resume support: check for partial download
        val startByte = if (tempFile.exists()) tempFile.length() else 0L

        val client =
            OkHttpClient
                .Builder()
                .connectTimeout(30.seconds.toJavaDuration())
                .readTimeout(60.seconds.toJavaDuration())
                .build()

        val requestBuilder =
            Request
                .Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")

        if (startByte > 0) {
            requestBuilder.addHeader("Range", "bytes=$startByte-")
            logger.debug { "Resuming download from byte $startByte" }
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful && response.code != 206) {
                throw IOException("Download failed: HTTP ${response.code}")
            }

            val body = response.body ?: throw IOException("Empty response body")

            val contentLength = body.contentLength()
            val totalSize =
                if (startByte > 0 && response.code == 206) {
                    startByte + contentLength
                } else {
                    contentLength
                }

            // Update total size in database
            if (totalSize > 0) {
                downloadDao.updateProgress(audioFileId, startByte, totalSize)
            }

            FileOutputStream(tempFile, startByte > 0).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    var totalBytesRead = startByte
                    var lastProgressUpdate = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        // Check for cancellation
                        if (isStopped) {
                            throw CancellationException("Download stopped")
                        }

                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        // Update progress periodically
                        val now = System.currentTimeMillis()
                        if (now - lastProgressUpdate > PROGRESS_INTERVAL_MS) {
                            downloadDao.updateProgress(audioFileId, totalBytesRead, totalSize)
                            setProgress(
                                workDataOf(
                                    "progress" to totalBytesRead,
                                    "total" to totalSize,
                                ),
                            )
                            lastProgressUpdate = now
                        }
                    }
                }
            }

            // Verify size if known
            if (expectedSize > 0 && tempFile.length() != expectedSize) {
                tempFile.delete()
                throw IOException("Size mismatch: expected $expectedSize, got ${tempFile.length()}")
            }

            // Move temp to final destination using FileManager
            if (!fileManager.moveFile(tempPath, destPath)) {
                throw IOException("Failed to move temp file to destination")
            }

            // Mark complete in database
            downloadDao.markCompleted(
                audioFileId = audioFileId,
                localPath = destPath.toString(),
                completedAt = System.currentTimeMillis(),
            )
        }
    }

    /**
     * Resolve the correct download URL via the prepare endpoint.
     *
     * This negotiates with the server to get the appropriate URL:
     * - If the device supports the source codec, returns the original URL
     * - If not, returns the transcoded variant URL
     * - If transcoding is in progress, waits for it to complete
     *
     * Falls back to the original URL on any error.
     */
    private suspend fun resolveDownloadUrl(
        bookId: String,
        audioFileId: String,
        serverUrl: String,
    ): String {
        val capabilities = capabilityDetector.getSupportedCodecs()

        // Get spatial audio setting from repository
        val spatial = settingsRepository.getSpatialPlayback()

        // Call prepare endpoint
        val result = playbackApi.preparePlayback(bookId, audioFileId, capabilities, spatial)

        if (result !is Success) {
            logger.warn { "Prepare call failed, using original URL" }
            return "$serverUrl/api/v1/books/$bookId/audio/$audioFileId"
        }

        val response = result.data

        // If transcoding is in progress, wait for it to complete
        if (!response.ready && response.transcodeJobId != null) {
            logger.info {
                "Transcoding in progress for $audioFileId, waiting... " +
                    "(jobId=${response.transcodeJobId}, progress=${response.progress}%)"
            }

            // Poll until ready or timeout (max 30 minutes for long files)
            val maxWaitMs = 30 * 60 * 1000L
            val startTime = System.currentTimeMillis()
            var lastProgress = response.progress

            while (System.currentTimeMillis() - startTime < maxWaitMs) {
                if (isStopped) {
                    throw CancellationException("Download cancelled while waiting for transcode")
                }

                delay(5000) // Poll every 5 seconds

                val checkResult = playbackApi.preparePlayback(bookId, audioFileId, capabilities, spatial)
                if (checkResult is Success) {
                    val checkResponse = checkResult.data
                    if (checkResponse.ready) {
                        logger.info { "Transcode completed for $audioFileId" }
                        return buildFullUrl(serverUrl, checkResponse.streamUrl)
                    }
                    // Log progress updates
                    if (checkResponse.progress > lastProgress) {
                        logger.debug { "Transcode progress: ${checkResponse.progress}%" }
                        lastProgress = checkResponse.progress
                    }
                }
            }

            // Timeout - fall back to original (will fail if truly unsupported)
            logger.warn { "Transcode timeout for $audioFileId, using original URL" }
            return "$serverUrl/api/v1/books/$bookId/audio/$audioFileId"
        }

        // Transcode is ready or not needed
        logger.debug {
            "Using ${response.variant} variant for $audioFileId (codec: ${response.codec})"
        }
        return buildFullUrl(serverUrl, response.streamUrl)
    }

    /**
     * Build full URL from server URL and possibly relative path.
     */
    private fun buildFullUrl(
        serverUrl: String,
        streamUrl: String,
    ): String =
        if (streamUrl.startsWith("/")) {
            "$serverUrl$streamUrl"
        } else if (streamUrl.startsWith("http")) {
            streamUrl
        } else {
            "$serverUrl/$streamUrl"
        }
}
