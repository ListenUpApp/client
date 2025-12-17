@file:OptIn(ExperimentalForeignApi::class)
@file:Suppress("MagicNumber")

package com.calypsan.listenup.client.download

import com.calypsan.listenup.client.data.local.db.BookDao
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.db.DownloadDao
import com.calypsan.listenup.client.data.local.db.DownloadEntity
import com.calypsan.listenup.client.data.local.db.DownloadState
import com.calypsan.listenup.client.data.remote.model.AudioFileResponse
import com.calypsan.listenup.client.data.repository.SettingsRepository
import com.calypsan.listenup.client.playback.AudioTokenProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import platform.Foundation.NSData
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setValue
import platform.Foundation.writeToFile
import kotlin.coroutines.resume
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val logger = KotlinLogging.logger {}

/**
 * iOS implementation of DownloadService.
 *
 * Uses URLSession for downloads with token-based authentication.
 * Downloads are performed in-app (not background URLSession yet).
 *
 * Future enhancement: Background URLSession with NSURLSessionDownloadTask
 * for downloads that continue when app is backgrounded.
 */
@OptIn(ExperimentalTime::class)
class IosDownloadService(
    private val downloadDao: DownloadDao,
    private val bookDao: BookDao,
    private val settingsRepository: SettingsRepository,
    private val tokenProvider: AudioTokenProvider,
    private val fileManager: DownloadFileManager,
    private val scope: CoroutineScope,
) : DownloadService {
    private val json = Json { ignoreUnknownKeys = true }
    private val urlSession = NSURLSession.sharedSession

    /**
     * Get local file path for an audio file (if downloaded).
     * Returns null if not downloaded or file missing.
     */
    override suspend fun getLocalPath(audioFileId: String): String? {
        val path = downloadDao.getLocalPath(audioFileId) ?: return null

        // Verify file still exists
        if (fileManager.fileExists(path)) return path

        // File was deleted externally - clean up database
        logger.warn { "Downloaded file missing, cleaning up: $audioFileId" }
        downloadDao.updateError(audioFileId, "File missing - deleted externally")
        return null
    }

    /**
     * Check if user explicitly deleted downloads for this book.
     */
    override suspend fun wasExplicitlyDeleted(bookId: BookId): Boolean = downloadDao.hasDeletedRecords(bookId.value)

    /**
     * Download a book's audio files.
     *
     * Downloads files sequentially using URLSession.
     * Progress is tracked in the database.
     */
    @Suppress("ReturnCount")
    override suspend fun downloadBook(bookId: BookId): DownloadResult {
        // Check if already downloaded
        val existing = downloadDao.getForBook(bookId.value)
        if (existing.isNotEmpty() && existing.all { it.state == DownloadState.COMPLETED }) {
            logger.info { "Book ${bookId.value} already downloaded" }
            return DownloadResult.AlreadyDownloaded
        }

        // Get book entity
        val bookEntity =
            bookDao.getById(bookId) ?: run {
                logger.error { "Book not found: ${bookId.value}" }
                return DownloadResult.Error("Book not found")
            }

        // Parse audio files
        val audioFilesJson = bookEntity.audioFilesJson
        if (audioFilesJson.isNullOrBlank()) {
            logger.warn { "No audio files JSON for book ${bookId.value}" }
            return DownloadResult.Error("No audio files available")
        }

        val audioFiles: List<AudioFileResponse> =
            try {
                json.decodeFromString(audioFilesJson)
            } catch (e: Exception) {
                logger.error(e) { "Failed to parse audio files JSON" }
                return DownloadResult.Error("Failed to parse audio files")
            }

        if (audioFiles.isEmpty()) {
            return DownloadResult.Error("No audio files available")
        }

        // Filter out already completed
        val completedIds =
            existing
                .filter { it.state == DownloadState.COMPLETED }
                .map { it.audioFileId }
                .toSet()

        val toDownload = audioFiles.filterNot { it.id in completedIds }

        // Check storage
        val requiredBytes = toDownload.sumOf { it.size }
        val availableBytes = fileManager.getAvailableSpace()
        val requiredWithBuffer = (requiredBytes * 1.1).toLong()

        if (availableBytes < requiredWithBuffer) {
            logger.warn {
                "Insufficient storage: need ${requiredBytes / 1_000_000}MB, have ${availableBytes / 1_000_000}MB"
            }
            return DownloadResult.InsufficientStorage(requiredBytes, availableBytes)
        }

        // Get server URL and token
        val serverUrl =
            settingsRepository.getServerUrl()?.value ?: run {
                logger.error { "No server URL configured" }
                return DownloadResult.Error("No server configured")
            }

        val token =
            tokenProvider.getToken() ?: run {
                logger.error { "No auth token available" }
                return DownloadResult.Error("Not authenticated")
            }

        // Create download entries
        val now = Clock.System.now().toEpochMilliseconds()
        val entities =
            toDownload.mapIndexed { _, file ->
                DownloadEntity(
                    audioFileId = file.id,
                    bookId = bookId.value,
                    filename = file.filename,
                    fileIndex = audioFiles.indexOfFirst { it.id == file.id },
                    state = DownloadState.QUEUED,
                    localPath = null,
                    totalBytes = file.size,
                    downloadedBytes = 0,
                    queuedAt = now,
                    startedAt = null,
                    completedAt = null,
                    errorMessage = null,
                    retryCount = 0,
                )
            }

        downloadDao.insertAll(entities)

        // Start downloads in background
        scope.launch {
            for (file in toDownload) {
                try {
                    downloadFile(
                        bookId = bookId.value,
                        audioFile = file,
                        serverUrl = serverUrl,
                        token = token,
                    )
                } catch (e: Exception) {
                    logger.error(e) { "Failed to download file: ${file.id}" }
                    downloadDao.updateError(file.id, e.message ?: "Download failed")
                }
            }
        }

        logger.info { "Queued ${toDownload.size} files for download: ${bookId.value}" }
        return DownloadResult.Success
    }

    private suspend fun downloadFile(
        bookId: String,
        audioFile: AudioFileResponse,
        serverUrl: String,
        token: String,
    ) = withContext(Dispatchers.IO) {
        val audioFileId = audioFile.id
        val filename = audioFile.filename

        // Update state to downloading
        downloadDao.updateState(
            audioFileId,
            DownloadState.DOWNLOADING,
            Clock.System.now().toEpochMilliseconds(),
        )

        // Build URL
        val url = "$serverUrl/api/stream/$audioFileId"
        val nsUrl = NSURL.URLWithString(url) ?: error("Invalid URL: $url")

        // Create request with auth header
        val request =
            NSMutableURLRequest.requestWithURL(nsUrl).apply {
                setValue("Bearer $token", forHTTPHeaderField = "Authorization")
            }

        // Download using URLSession
        val data = downloadData(request)

        if (data == null) {
            downloadDao.updateError(audioFileId, "Download returned no data")
            return@withContext
        }

        // Write to file
        val destPath = fileManager.getDownloadPath(bookId, audioFileId, filename)
        val success = data.writeToFile(destPath.toString(), atomically = true)

        if (success) {
            downloadDao.markCompleted(
                audioFileId,
                destPath.toString(),
                Clock.System.now().toEpochMilliseconds(),
            )
            logger.info { "Downloaded: $filename" }
        } else {
            downloadDao.updateError(audioFileId, "Failed to write file")
            logger.error { "Failed to write file: $filename" }
        }
    }

    private suspend fun downloadData(request: NSMutableURLRequest): NSData? =
        suspendCancellableCoroutine { continuation ->
            val task: NSURLSessionDataTask =
                urlSession.dataTaskWithRequest(request) { data, response, error ->
                    if (error != null) {
                        logger.error { "Download error: ${error.localizedDescription}" }
                        continuation.resume(null)
                        return@dataTaskWithRequest
                    }

                    val httpResponse = response as? NSHTTPURLResponse
                    if (httpResponse != null && httpResponse.statusCode !in 200..299) {
                        logger.error { "HTTP error: ${httpResponse.statusCode}" }
                        continuation.resume(null)
                        return@dataTaskWithRequest
                    }

                    continuation.resume(data)
                }

            continuation.invokeOnCancellation {
                task.cancel()
            }

            task.resume()
        }
}
