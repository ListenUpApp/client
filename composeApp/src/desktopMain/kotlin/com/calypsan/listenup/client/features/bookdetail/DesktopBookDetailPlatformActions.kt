package com.calypsan.listenup.client.features.bookdetail

import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.domain.model.BookDownloadStatus
import com.calypsan.listenup.client.download.DownloadResult
import com.calypsan.listenup.client.playback.DesktopPlayerViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Desktop implementation of BookDetailPlatformActions.
 *
 * Playback is available via JavaFX MediaPlayer when GStreamer is installed. Downloads
 * are not yet implemented on desktop, so download-related methods return stubs.
 */
class DesktopBookDetailPlatformActions(
    private val playerViewModel: DesktopPlayerViewModel,
) : BookDetailPlatformActions {
    override val isPlaybackAvailable: Boolean = true

    override fun observeBookStatus(bookId: BookId): Flow<BookDownloadStatus> =
        flowOf(BookDownloadStatus.notDownloaded(bookId.value))

    override suspend fun downloadBook(bookId: BookId): DownloadResult =
        DownloadResult.Error("Downloads not yet available on desktop")

    override suspend fun cancelDownload(bookId: BookId) {}

    override suspend fun deleteDownload(bookId: BookId) {}

    override fun playBook(bookId: BookId) {
        playerViewModel.playBook(bookId)
    }

    override fun observeWifiOnlyDownloads(): Flow<Boolean> = flowOf(false)

    override fun observeIsOnline(): Flow<Boolean> = flowOf(true)

    override fun observeIsOnUnmeteredNetwork(): Flow<Boolean> = flowOf(true)

    override suspend fun checkServerReachable(): Boolean = true
}
