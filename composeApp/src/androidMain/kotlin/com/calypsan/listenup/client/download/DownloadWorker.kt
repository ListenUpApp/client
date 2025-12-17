package com.calypsan.listenup.client.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.calypsan.listenup.client.data.local.db.DownloadDao
import com.calypsan.listenup.client.data.local.db.DownloadState
import com.calypsan.listenup.client.data.repository.SettingsRepository
import com.calypsan.listenup.client.playback.AudioTokenProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * WorkManager worker that downloads a single audio file.
 *
 * Features:
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

        val url = "$serverUrl/api/v1/books/$bookId/audio/$audioFileId"

        // Get paths from file manager and convert to java.io.File for I/O operations
        val destPath = fileManager.getDownloadPath(bookId, audioFileId, filename)
        val tempPath = fileManager.getTempPath(bookId, audioFileId, filename)
        val tempFile = File(tempPath.toString())

        // Resume support: check for partial download
        val startByte = if (tempFile.exists()) tempFile.length() else 0L

        val client =
            OkHttpClient
                .Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
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

            val body = response.body

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
}
