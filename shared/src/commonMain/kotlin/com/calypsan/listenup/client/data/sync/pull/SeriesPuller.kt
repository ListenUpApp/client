package com.calypsan.listenup.client.data.sync.pull

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.exceptionOrFromMessage
import com.calypsan.listenup.client.data.local.db.SeriesDao
import com.calypsan.listenup.client.data.remote.SyncApiContract
import com.calypsan.listenup.client.data.remote.model.toEntity
import com.calypsan.listenup.client.data.sync.ImageDownloaderContract
import com.calypsan.listenup.client.data.sync.model.SyncPhase
import com.calypsan.listenup.client.data.sync.model.SyncStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * Handles paginated series fetching and cover downloads.
 */
class SeriesPuller(
    private val syncApi: SyncApiContract,
    private val seriesDao: SeriesDao,
    private val imageDownloader: ImageDownloaderContract,
    private val scope: CoroutineScope,
) : Puller {
    /**
     * Pull all series from server with pagination.
     *
     * @param updatedAfter ISO timestamp for delta sync, null for full sync
     * @param onProgress Callback for progress updates
     */
    override suspend fun pull(
        updatedAfter: String?,
        onProgress: (SyncStatus) -> Unit,
    ) {
        var cursor: String? = null
        var hasMore = true
        val limit = 100
        var pageCount = 0
        var totalDeleted = 0

        val seriesWithCovers =
            buildList {
                while (hasMore) {
                    onProgress(
                        SyncStatus.Progress(
                            phase = SyncPhase.SYNCING_SERIES,
                            current = pageCount,
                            total = -1,
                            message = "Syncing series (page ${pageCount + 1})...",
                        ),
                    )

                    when (val result = syncApi.getSeries(limit = limit, cursor = cursor, updatedAfter = updatedAfter)) {
                        is Result.Success -> {
                            val response = result.data
                            cursor = response.nextCursor
                            hasMore = response.hasMore
                            pageCount++

                            val serverSeries = response.series.map { it.toEntity() }
                            val deletedSeriesIds = response.deletedSeriesIds

                            // Track series with cover images for later download
                            response.series
                                .filter { it.coverImage != null }
                                .forEach { add(it.id) }

                            logger.debug {
                                "Fetched page $pageCount: ${serverSeries.size} series, ${deletedSeriesIds.size} deletions"
                            }

                            // Handle deletions
                            if (deletedSeriesIds.isNotEmpty()) {
                                seriesDao.deleteByIds(deletedSeriesIds)
                                totalDeleted += deletedSeriesIds.size
                                logger.info { "Removed ${deletedSeriesIds.size} series deleted on server" }
                            }

                            if (serverSeries.isNotEmpty()) {
                                seriesDao.upsertAll(serverSeries)
                            }
                        }

                        is Result.Failure -> {
                            throw result.exceptionOrFromMessage()
                        }
                    }
                }
            }

        logger.info { "Series sync complete: $pageCount pages processed, $totalDeleted deleted" }

        // Download series covers in background
        if (seriesWithCovers.isNotEmpty()) {
            scope.launch {
                logger.info { "Starting cover downloads for ${seriesWithCovers.size} series..." }
                imageDownloader.downloadSeriesCovers(seriesWithCovers)
            }
        }
    }
}
