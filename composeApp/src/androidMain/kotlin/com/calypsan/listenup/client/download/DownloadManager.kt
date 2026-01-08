@file:Suppress("MagicNumber", "StringLiteralDuplication")

package com.calypsan.listenup.client.download

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.calypsan.listenup.client.data.local.db.BookDao
import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.data.local.db.DownloadDao
import com.calypsan.listenup.client.data.local.db.DownloadEntity
import com.calypsan.listenup.client.data.local.db.DownloadState
import com.calypsan.listenup.client.domain.model.BookDownloadState
import com.calypsan.listenup.client.domain.model.BookDownloadStatus
import com.calypsan.listenup.client.data.remote.model.AudioFileResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

/**
 * Manages audiobook downloads.
 *
 * Responsibilities:
 * - Queue/cancel/delete downloads
 * - Track download state per book
 * - Resolve local paths for offline playback
 * - Calculate storage usage
 * - Respect WiFi-only download constraint via WorkManager
 *
 * Implements [DownloadService] for use by shared code (PlaybackManager).
 *
 * The download queue is implemented via WorkManager's work queue, which:
 * - Persists queued downloads across app restarts
 * - Automatically retries failed downloads with exponential backoff
 * - Respects network constraints (WiFi-only when enabled)
 * - Allows cancellation via unique work names
 */
class DownloadManager(
    private val downloadDao: DownloadDao,
    private val bookDao: BookDao,
    private val workManager: WorkManager,
    private val fileManager: DownloadFileManager,
    private val localPreferences: com.calypsan.listenup.client.domain.repository.LocalPreferences,
) : DownloadService {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Observe download status for a specific book.
     */
    fun observeBookStatus(bookId: BookId): Flow<BookDownloadStatus> =
        downloadDao
            .observeForBook(bookId.value)
            .map { downloads -> aggregateStatus(bookId.value, downloads) }

    /**
     * Observe download status for all books (for library indicators).
     */
    fun observeAllStatuses(): Flow<Map<String, BookDownloadStatus>> =
        downloadDao
            .observeAll()
            .map { downloads ->
                downloads
                    .groupBy { it.bookId }
                    .mapValues { (bookId, files) -> aggregateStatus(bookId, files) }
            }

    private fun aggregateStatus(
        bookId: String,
        downloads: List<DownloadEntity>,
    ): BookDownloadStatus {
        if (downloads.isEmpty()) {
            return BookDownloadStatus.notDownloaded(bookId)
        }

        // If all files are DELETED, treat as not downloaded (show download button)
        if (downloads.all { it.state == DownloadState.DELETED }) {
            return BookDownloadStatus.notDownloaded(bookId)
        }

        // Filter out DELETED entries for counting
        val activeDownloads = downloads.filter { it.state != DownloadState.DELETED }
        if (activeDownloads.isEmpty()) {
            return BookDownloadStatus.notDownloaded(bookId)
        }

        val totalFiles = activeDownloads.size
        val completedFiles = activeDownloads.count { it.state == DownloadState.COMPLETED }
        val totalBytes = activeDownloads.sumOf { it.totalBytes }
        val downloadedBytes = activeDownloads.sumOf { it.downloadedBytes }

        val state =
            when {
                activeDownloads.all { it.state == DownloadState.COMPLETED } -> BookDownloadState.COMPLETED
                activeDownloads.any { it.state == DownloadState.DOWNLOADING } -> BookDownloadState.DOWNLOADING
                activeDownloads.any { it.state == DownloadState.QUEUED } -> BookDownloadState.QUEUED
                activeDownloads.any { it.state == DownloadState.FAILED } -> BookDownloadState.FAILED
                activeDownloads.any { it.state == DownloadState.COMPLETED } -> BookDownloadState.PARTIAL
                else -> BookDownloadState.NOT_DOWNLOADED
            }

        return BookDownloadStatus(
            bookId = bookId,
            state = state,
            totalFiles = totalFiles,
            completedFiles = completedFiles,
            totalBytes = totalBytes,
            downloadedBytes = downloadedBytes,
        )
    }

    /**
     * Download a book (queue all audio files).
     * Called when user taps download button OR starts streaming.
     *
     * @return DownloadResult indicating success, failure reason, or if already downloaded
     */
    override suspend fun downloadBook(bookId: BookId): DownloadResult {
        // Check if already downloading or downloaded
        val existing = downloadDao.getForBook(bookId.value)
        if (existing.isNotEmpty() && existing.all { it.state == DownloadState.COMPLETED }) {
            logger.info { "Book ${bookId.value} already downloaded" }
            return DownloadResult.AlreadyDownloaded
        }

        // Get book entity from database
        val bookEntity =
            bookDao.getById(bookId) ?: run {
                logger.error { "Book not found: ${bookId.value}" }
                return DownloadResult.Error("Book not found")
            }

        // Parse audio files from JSON
        val audioFilesJson = bookEntity.audioFilesJson
        if (audioFilesJson.isNullOrBlank()) {
            logger.warn { "No audio files JSON for book ${bookId.value}" }
            return DownloadResult.Error("No audio files available")
        }

        val audioFiles: List<AudioFileResponse> =
            try {
                json.decodeFromString(audioFilesJson)
            } catch (e: Exception) {
                logger.error(e) { "Failed to parse audio files JSON for book ${bookId.value}" }
                return DownloadResult.Error("Failed to parse audio files")
            }

        if (audioFiles.isEmpty()) {
            logger.warn { "No audio files for book ${bookId.value}" }
            return DownloadResult.Error("No audio files available")
        }

        // Create download entries for files not already completed
        val completedIds =
            existing
                .filter { it.state == DownloadState.COMPLETED }
                .map { it.audioFileId }
                .toSet()

        val toDownload = audioFiles.filterNot { it.id in completedIds }

        // Check available storage before queueing downloads
        val requiredBytes = toDownload.sumOf { it.size }
        val availableBytes = fileManager.getAvailableSpace()
        // Add 10% buffer for safety
        val requiredWithBuffer = (requiredBytes * 1.1).toLong()

        if (availableBytes < requiredWithBuffer) {
            logger.warn {
                "Insufficient storage for book ${bookId.value}: " +
                    "need ${requiredBytes / 1_000_000}MB, have ${availableBytes / 1_000_000}MB"
            }
            return DownloadResult.InsufficientStorage(
                requiredBytes = requiredBytes,
                availableBytes = availableBytes,
            )
        }

        val now = System.currentTimeMillis()
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

        // Determine network constraint based on WiFi-only preference
        // UNMETERED = WiFi/ethernet only, CONNECTED = any network (including cellular)
        val wifiOnly = localPreferences.wifiOnlyDownloads.value
        val requiredNetworkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED

        logger.info {
            "Queueing downloads with network constraint: " +
                if (wifiOnly) "UNMETERED (WiFi only)" else "CONNECTED (any network)"
        }

        // Queue WorkManager jobs
        toDownload.forEach { file ->
            val workRequest =
                OneTimeWorkRequestBuilder<DownloadWorker>()
                    .setInputData(
                        workDataOf(
                            DownloadWorker.KEY_AUDIO_FILE_ID to file.id,
                            DownloadWorker.KEY_BOOK_ID to bookId.value,
                            DownloadWorker.KEY_FILENAME to file.filename,
                            DownloadWorker.KEY_FILE_SIZE to file.size,
                        ),
                    ).setConstraints(
                        Constraints
                            .Builder()
                            .setRequiredNetworkType(requiredNetworkType)
                            .build(),
                    ).addTag("download_${bookId.value}")
                    .addTag("download_file_${file.id}")
                    .build()

            workManager.enqueueUniqueWork(
                "download_${file.id}",
                ExistingWorkPolicy.REPLACE,
                workRequest,
            )
        }

        logger.info { "Queued ${toDownload.size} files for download: ${bookId.value}" }
        return DownloadResult.Success
    }

    /**
     * Cancel active download for a book.
     */
    override suspend fun cancelDownload(bookId: BookId) {
        workManager.cancelAllWorkByTag("download_${bookId.value}")
        downloadDao.updateStateForBook(bookId.value, DownloadState.PAUSED)
        logger.info { "Cancelled download: ${bookId.value}" }
    }

    /**
     * Delete downloaded files for a book.
     * Marks records as DELETED (keeps them for tracking) and removes files.
     * This prevents auto-download on next playback - user must explicitly tap download.
     */
    override suspend fun deleteDownload(bookId: BookId) {
        // Cancel any active downloads first
        workManager.cancelAllWorkByTag("download_${bookId.value}")

        // Delete files from disk
        fileManager.deleteBookFiles(bookId.value)

        // Mark as deleted (don't remove records - used to track explicit deletion)
        downloadDao.markDeletedForBook(bookId.value)

        logger.info { "Deleted download: ${bookId.value}" }
    }

    /**
     * Check if a book was explicitly deleted by user.
     * Used to determine if we should auto-download on playback.
     */
    override suspend fun wasExplicitlyDeleted(bookId: BookId): Boolean = downloadDao.hasDeletedRecords(bookId.value)

    /**
     * Get local file path for an audio file (if downloaded).
     * Returns null if not downloaded or file missing.
     * If file was deleted externally, cleans up database entry.
     */
    override suspend fun getLocalPath(audioFileId: String): String? {
        val path = downloadDao.getLocalPath(audioFileId) ?: return null

        // Verify file still exists
        if (fileManager.fileExists(path)) return path

        // File was deleted externally - clean up database to stay consistent
        logger.warn { "Downloaded file missing, cleaning up: $audioFileId" }
        downloadDao.updateError(audioFileId, "File missing - deleted externally")
        return null
    }

    /**
     * Check if a book is fully downloaded.
     */
    suspend fun isBookDownloaded(bookId: BookId): Boolean {
        val downloads = downloadDao.getForBook(bookId.value)
        return downloads.isNotEmpty() && downloads.all { it.state == DownloadState.COMPLETED }
    }

    /**
     * Get total storage used by all downloads.
     */
    fun getTotalStorageUsed(): Long = fileManager.calculateStorageUsed()

    /**
     * Delete all downloads.
     */
    suspend fun deleteAllDownloads() {
        workManager.cancelAllWork()
        fileManager.deleteAllFiles()
        downloadDao.deleteAll()
        logger.info { "Deleted all downloads" }
    }
}
