package com.calypsan.listenup.client.data.sync.pull

import com.calypsan.listenup.client.data.local.db.SyncDao
import com.calypsan.listenup.client.data.local.db.getLastSyncTime
import com.calypsan.listenup.client.data.sync.SyncCoordinator
import com.calypsan.listenup.client.data.sync.model.SyncPhase
import com.calypsan.listenup.client.data.sync.model.SyncStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

private val logger = KotlinLogging.logger {}

/**
 * Coordinates parallel entity pulls with retry logic and progress reporting.
 */
@Suppress("LongParameterList")
class PullSyncOrchestrator(
    private val bookPuller: Puller,
    private val seriesPuller: Puller,
    private val contributorPuller: Puller,
    private val tagPuller: Puller,
    private val genrePuller: Puller,
    private val listeningEventPuller: ListeningEventPullerContract,
    private val activeSessionsPuller: Puller,
    private val coordinator: SyncCoordinator,
    private val syncDao: SyncDao,
) {
    /**
     * Pull all entities from server with retry logic.
     *
     * Runs pulls for Books, Series, and Contributors in parallel for performance.
     * Tags are pulled sequentially after books (since they depend on book data).
     * Uses proper cancellation if any job fails.
     *
     * @param onProgress Callback for progress updates
     */
    suspend fun pull(onProgress: (SyncStatus) -> Unit) =
        coroutineScope {
            logger.debug { "Pulling changes from server" }

            // Get last sync time for delta sync
            val lastSyncTime = syncDao.getLastSyncTime()
            val updatedAfter = lastSyncTime?.toIsoString()

            val syncType = if (updatedAfter != null) "delta" else "full"
            logger.debug { "Pull sync strategy: $syncType" }

            onProgress(
                SyncStatus.Progress(
                    phase = SyncPhase.FETCHING_METADATA,
                    current = 0,
                    total = 5,
                    message = "Preparing sync...",
                ),
            )

            // Run with retry logic
            coordinator.withRetry(
                onRetry = { attempt, max ->
                    onProgress(SyncStatus.Retrying(attempt = attempt, maxAttempts = max))
                },
            ) {
                // Run independent sync operations in parallel
                val booksJob = async { bookPuller.pull(updatedAfter, onProgress) }
                val seriesJob = async { seriesPuller.pull(updatedAfter, onProgress) }
                val contributorsJob = async { contributorPuller.pull(updatedAfter, onProgress) }

                // Wait for all to complete - if any fails, others will be cancelled
                try {
                    awaitAll(booksJob, seriesJob, contributorsJob)
                } catch (e: Exception) {
                    booksJob.cancel()
                    seriesJob.cancel()
                    contributorsJob.cancel()
                    throw e
                }

                // Pull tags after books are synced (tags need book data)
                tagPuller.pull(updatedAfter, onProgress)

                // Pull genres after books (genres are book categorization)
                genrePuller.pull(updatedAfter, onProgress)

                // Pull listening events (from other devices) for offline stats
                listeningEventPuller.pull(updatedAfter, onProgress)

                // Pull active sessions for "What Others Are Listening To" discovery section
                activeSessionsPuller.pull(updatedAfter, onProgress)
            }

            onProgress(
                SyncStatus.Progress(
                    phase = SyncPhase.FINALIZING,
                    current = 5,
                    total = 5,
                    message = "Finalizing sync...",
                ),
            )
        }

    /**
     * Refresh all listening events and playback positions.
     *
     * Fetches ALL events from the server (ignoring delta sync cursor)
     * and rebuilds playback positions from them.
     *
     * Used after importing historical data (e.g., from Audiobookshelf).
     */
    suspend fun refreshListeningHistory() {
        logger.info { "Refreshing all listening history..." }
        listeningEventPuller.pullAll()
    }
}
