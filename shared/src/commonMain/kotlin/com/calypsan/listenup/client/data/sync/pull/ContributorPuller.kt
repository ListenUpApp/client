package com.calypsan.listenup.client.data.sync.pull

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.data.local.db.ContributorDao
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
 * Handles paginated contributor fetching and image downloads.
 */
class ContributorPuller(
    private val syncApi: SyncApiContract,
    private val contributorDao: ContributorDao,
    private val imageDownloader: ImageDownloaderContract,
    private val scope: CoroutineScope,
) : Puller {
    /**
     * Pull all contributors from server with pagination.
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
        val contributorsWithImages = mutableListOf<String>()

        while (hasMore) {
            onProgress(
                SyncStatus.Progress(
                    phase = SyncPhase.SYNCING_CONTRIBUTORS,
                    current = pageCount,
                    total = -1,
                    message = "Syncing contributors (page ${pageCount + 1})...",
                ),
            )

            when (val result = syncApi.getContributors(limit = limit, cursor = cursor, updatedAfter = updatedAfter)) {
                is Result.Success -> {
                    val response = result.data
                    cursor = response.nextCursor
                    hasMore = response.hasMore
                    pageCount++

                    val serverContributors = response.contributors.map { it.toEntity() }
                    val deletedContributorIds = response.deletedContributorIds

                    // Track contributors with images for later download
                    response.contributors
                        .filter { !it.imageUrl.isNullOrBlank() }
                        .forEach { contributorsWithImages.add(it.id) }

                    logger.debug {
                        "Fetched page $pageCount: ${serverContributors.size} contributors, ${deletedContributorIds.size} deletions"
                    }

                    // Handle deletions
                    if (deletedContributorIds.isNotEmpty()) {
                        contributorDao.deleteByIds(deletedContributorIds)
                        totalDeleted += deletedContributorIds.size
                        logger.info { "Removed ${deletedContributorIds.size} contributors deleted on server" }
                    }

                    if (serverContributors.isNotEmpty()) {
                        contributorDao.upsertAll(serverContributors)
                    }
                }

                is Result.Failure -> {
                    throw result.exception
                }
            }
        }

        logger.info { "Contributors sync complete: $pageCount pages processed, $totalDeleted deleted" }

        // Download contributor images in background
        if (contributorsWithImages.isNotEmpty()) {
            scope.launch {
                logger.info { "Starting image downloads for ${contributorsWithImages.size} contributors..." }
                val downloadedIds = imageDownloader.downloadContributorImages(contributorsWithImages)

                if (downloadedIds is Result.Success && downloadedIds.data.isNotEmpty()) {
                    downloadedIds.data.forEach { contributorId ->
                        val localPath = imageDownloader.getContributorImagePath(contributorId)
                        if (localPath != null) {
                            contributorDao.updateImagePath(contributorId, localPath)
                        }
                    }
                    logger.info { "Updated ${downloadedIds.data.size} contributors with local image paths" }
                }
            }
        }
    }
}
