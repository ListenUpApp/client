package com.calypsan.listenup.client.platform

import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.domain.model.BookDownloadStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import com.calypsan.listenup.client.download.DownloadResult
import com.calypsan.listenup.client.download.DownloadService

/**
 * Stub implementation of [DownloadService] for desktop.
 *
 * Desktop downloads are not yet implemented. This stub allows the app
 * to compile and run with streaming-only playback.
 *
 * TODO: Implement background downloads using kotlinx-io and coroutines.
 */
class StubDownloadService : DownloadService {
    override suspend fun getLocalPath(audioFileId: String): String? = null

    override suspend fun wasExplicitlyDeleted(bookId: BookId): Boolean = false

    override suspend fun downloadBook(bookId: BookId): DownloadResult =
        DownloadResult.Error("Downloads not yet supported on desktop")

    override suspend fun cancelDownload(bookId: BookId) {
        // No-op: downloads not supported
    }

    override suspend fun deleteDownload(bookId: BookId) {
        // No-op: downloads not supported
    }

    override fun observeBookStatus(bookId: BookId): Flow<BookDownloadStatus> =
        flowOf(BookDownloadStatus.notDownloaded(bookId.value))
}
