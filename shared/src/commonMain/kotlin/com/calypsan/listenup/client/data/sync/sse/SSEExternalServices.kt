package com.calypsan.listenup.client.data.sync.sse

import com.calypsan.listenup.client.data.sync.ImageDownloaderContract
import com.calypsan.listenup.client.domain.repository.SessionRepository
import com.calypsan.listenup.client.download.DownloadService

/**
 * Bundle of non-DAO dependencies [SSEEventProcessor] delegates to — session repository,
 * image downloader, playback state, and download service.
 *
 * Extracted to keep [SSEEventProcessor]'s constructor under detekt's `LongParameterList` threshold.
 */
data class SSEExternalServices(
    val sessionRepository: SessionRepository,
    val imageDownloader: ImageDownloaderContract,
    val playbackStateProvider: PlaybackStateProvider,
    val downloadService: DownloadService,
)
