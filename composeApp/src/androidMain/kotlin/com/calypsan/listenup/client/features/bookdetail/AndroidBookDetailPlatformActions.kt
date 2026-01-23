package com.calypsan.listenup.client.features.bookdetail

import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.domain.model.BookDownloadStatus
import com.calypsan.listenup.client.domain.repository.LocalPreferences
import com.calypsan.listenup.client.domain.repository.NetworkMonitor
import com.calypsan.listenup.client.download.DownloadManager
import com.calypsan.listenup.client.download.DownloadResult
import com.calypsan.listenup.client.playback.PlayerViewModel
import kotlinx.coroutines.flow.Flow

/**
 * Android implementation of BookDetailPlatformActions.
 * Delegates to DownloadManager (WorkManager) and PlayerViewModel (Media3).
 */
class AndroidBookDetailPlatformActions(
    private val downloadManager: DownloadManager,
    private val playerViewModel: PlayerViewModel,
    private val localPreferences: LocalPreferences,
    private val networkMonitor: NetworkMonitor,
) : BookDetailPlatformActions {
    override val isPlaybackAvailable: Boolean = true

    override fun observeBookStatus(bookId: BookId): Flow<BookDownloadStatus> =
        downloadManager.observeBookStatus(bookId)

    override suspend fun downloadBook(bookId: BookId): DownloadResult =
        downloadManager.downloadBook(bookId)

    override suspend fun cancelDownload(bookId: BookId) =
        downloadManager.cancelDownload(bookId)

    override suspend fun deleteDownload(bookId: BookId) =
        downloadManager.deleteDownload(bookId)

    override fun playBook(bookId: BookId) =
        playerViewModel.playBook(bookId)

    override fun observeWifiOnlyDownloads(): Flow<Boolean> =
        localPreferences.wifiOnlyDownloads

    override fun observeIsOnUnmeteredNetwork(): Flow<Boolean> =
        networkMonitor.isOnUnmeteredNetworkFlow
}
