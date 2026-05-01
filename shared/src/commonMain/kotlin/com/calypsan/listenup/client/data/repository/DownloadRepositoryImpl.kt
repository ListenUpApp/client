package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.AppResult
import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.core.error.DownloadError
import com.calypsan.listenup.client.core.suspendRunCatching
import com.calypsan.listenup.client.data.local.db.DownloadDao
import com.calypsan.listenup.client.data.local.db.DownloadEntity
import com.calypsan.listenup.client.data.local.db.DownloadState
import com.calypsan.listenup.client.domain.model.BookDownloadStatus
import com.calypsan.listenup.client.domain.model.DownloadOutcome
import com.calypsan.listenup.client.domain.model.DownloadedBookSummary
import com.calypsan.listenup.client.domain.repository.BookRepository
import com.calypsan.listenup.client.domain.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Single seam for download state + aggregation. All download writes go through this class
 * (Sync Engine Rule 5). Aggregation reducer lives here so platforms share the same state-machine.
 *
 * **Phase B scope:** state transitions + aggregation + reads. Orchestration methods
 * (`enqueueForBook`, `cancelForBook`, `resumeForAudioFile`, `resumeIncompleteDownloads`)
 * throw [NotImplementedError] in Phase B — Phase C / Phase D move platform code onto them.
 *
 * Cross-phase aliases per W8 design:
 * - [markCancelled] writes [DownloadState.PAUSED]; Phase D switches to a new `CANCELLED` entry.
 * - [markWaitingForServer] is a no-op; Phase D writes a new `WAITING_FOR_SERVER` entry.
 * - [recheckWaitingForServer] is a no-op; Phase D wires the SSE-reconnect hook.
 */
class DownloadRepositoryImpl(
    private val downloadDao: DownloadDao,
    private val bookRepository: BookRepository,
) : DownloadRepository {
    // --- Reads ---

    override fun observeForBook(bookId: BookId): Flow<List<DownloadEntity>> = downloadDao.observeForBook(bookId.value)

    override fun observeAll(): Flow<List<DownloadEntity>> = downloadDao.observeAll()

    override fun observeBookStatus(bookId: BookId): Flow<BookDownloadStatus> =
        downloadDao.observeForBook(bookId.value).map { aggregate(bookId.value, it) }

    override fun observeAllStatuses(): Flow<Map<String, BookDownloadStatus>> =
        downloadDao.observeAll().map { downloads ->
            downloads
                .groupBy { it.bookId }
                .mapValues { (bookId, files) -> aggregate(bookId, files) }
        }

    override fun observeDownloadedBooks(): Flow<List<DownloadedBookSummary>> =
        downloadDao.observeAll().map { downloads ->
            val completedByBook =
                downloads
                    .filter { it.state == DownloadState.COMPLETED }
                    .groupBy { it.bookId }

            if (completedByBook.isEmpty()) return@map emptyList()

            val books =
                bookRepository
                    .getBookListItems(completedByBook.keys.toList())
                    .associateBy { it.id.value }

            completedByBook
                .mapNotNull { (bookId, files) ->
                    val book = books[bookId] ?: return@mapNotNull null
                    DownloadedBookSummary(
                        bookId = bookId,
                        title = book.title,
                        authorNames = book.authorNames,
                        coverBlurHash = book.coverBlurHash,
                        sizeBytes = files.sumOf { it.downloadedBytes },
                        fileCount = files.size,
                    )
                }.sortedByDescending { it.sizeBytes }
        }

    override suspend fun getLocalPath(audioFileId: String): String? = downloadDao.getLocalPath(audioFileId)

    // --- State-transition writes ---

    override suspend fun markDownloading(
        audioFileId: String,
        startedAt: Long,
    ): AppResult<Unit> =
        suspendRunCatching {
            downloadDao.updateState(audioFileId, DownloadState.DOWNLOADING, startedAt)
        }

    override suspend fun updateProgress(
        audioFileId: String,
        downloadedBytes: Long,
        totalBytes: Long,
    ): AppResult<Unit> =
        suspendRunCatching {
            downloadDao.updateProgress(audioFileId, downloadedBytes, totalBytes)
        }

    override suspend fun markCompleted(
        audioFileId: String,
        localPath: String,
        completedAt: Long,
    ): AppResult<Unit> =
        suspendRunCatching {
            downloadDao.markCompleted(audioFileId, localPath, completedAt)
        }

    override suspend fun markPaused(audioFileId: String): AppResult<Unit> =
        suspendRunCatching {
            downloadDao.updateState(audioFileId, DownloadState.PAUSED)
        }

    // Phase B alias: cancellation collapses into PAUSED. Phase D adds DownloadState.CANCELLED.
    override suspend fun markCancelled(audioFileId: String): AppResult<Unit> =
        suspendRunCatching {
            downloadDao.updateState(audioFileId, DownloadState.PAUSED)
        }

    override suspend fun markFailed(
        audioFileId: String,
        error: DownloadError,
    ): AppResult<Unit> =
        suspendRunCatching {
            downloadDao.updateError(audioFileId, error.message)
        }

    override suspend fun markWaitingForServer(
        audioFileId: String,
        transcodeJobId: String,
    ): AppResult<Unit> =
        suspendRunCatching {
            downloadDao.markWaitingForServer(audioFileId, transcodeJobId)
        }

    // --- Orchestration (Phase B: stub; Phase C/D move platform code onto these) ---

    @Suppress("NotImplementedDeclaration") // Phase C/D scope — intentional stub per W8 design
    override suspend fun enqueueForBook(bookId: BookId): AppResult<DownloadOutcome> =
        throw NotImplementedError(
            "DownloadRepository.enqueueForBook is Phase C/D scope. Phase B keeps " +
                "orchestration on DownloadManager (Android) and AppleDownloadService (iOS).",
        )

    @Suppress("NotImplementedDeclaration") // Phase C/D scope — intentional stub per W8 design
    override suspend fun cancelForBook(bookId: BookId): AppResult<Unit> =
        throw NotImplementedError(
            "DownloadRepository.cancelForBook is Phase C/D scope. Phase B keeps cancellation " +
                "on the DownloadService impls.",
        )

    override suspend fun deleteForBook(bookId: String) {
        downloadDao.deleteForBook(bookId)
    }

    @Suppress("NotImplementedDeclaration") // Phase D scope — intentional stub per W8 design
    override suspend fun resumeForAudioFile(audioFileId: String): AppResult<Unit> =
        throw NotImplementedError(
            "DownloadRepository.resumeForAudioFile is Phase D scope (Bug 4 SSE handler).",
        )

    @Suppress("NotImplementedDeclaration") // Phase C/D scope — intentional stub per W8 design
    override suspend fun resumeIncompleteDownloads(): AppResult<Unit> =
        throw NotImplementedError(
            "DownloadRepository.resumeIncompleteDownloads is Phase C/D scope. Phase B keeps " +
                "this on the DownloadService impls.",
        )

    // Phase B alias: no-op. Phase D wires the SSE-reconnect hook.
    override suspend fun recheckWaitingForServer(): AppResult<Unit> = AppResult.Success(Unit)

    // --- Aggregation reducer (shared across platforms) ---

    private fun aggregate(
        bookId: String,
        downloads: List<DownloadEntity>,
    ): BookDownloadStatus {
        if (downloads.isEmpty()) {
            return BookDownloadStatus.NotDownloaded(bookId)
        }
        val activeDownloads = downloads.filter { it.state != DownloadState.DELETED }
        if (activeDownloads.isEmpty()) {
            return BookDownloadStatus.NotDownloaded(bookId)
        }
        val totalFiles = activeDownloads.size
        val completedFiles = activeDownloads.count { it.state == DownloadState.COMPLETED }
        val totalBytes = activeDownloads.sumOf { it.totalBytes }
        val downloadedBytes = activeDownloads.sumOf { it.downloadedBytes }
        return when {
            activeDownloads.all { it.state == DownloadState.COMPLETED } -> {
                BookDownloadStatus.Completed(bookId = bookId, totalBytes = totalBytes)
            }

            activeDownloads.any { it.state == DownloadState.FAILED } -> {
                BookDownloadStatus.Failed(
                    bookId = bookId,
                    errorMessage =
                        activeDownloads
                            .firstOrNull { it.state == DownloadState.FAILED }
                            ?.errorMessage
                            ?: "Download failed",
                    partiallyDownloadedFiles = completedFiles,
                )
            }

            activeDownloads.all { it.state == DownloadState.PAUSED } -> {
                BookDownloadStatus.Paused(
                    bookId = bookId,
                    pausedFiles = activeDownloads.size,
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes,
                )
            }

            else -> {
                BookDownloadStatus.InProgress(
                    bookId = bookId,
                    totalFiles = totalFiles,
                    downloadingFiles = activeDownloads.count { it.state == DownloadState.DOWNLOADING },
                    waitingForServerFiles = 0,
                    completedFiles = completedFiles,
                    totalBytes = totalBytes,
                    downloadedBytes = downloadedBytes,
                )
            }
        }
    }
}
