package com.calypsan.listenup.client.data.sync.pull

import com.calypsan.listenup.client.data.local.db.BookDao
import com.calypsan.listenup.client.data.local.db.ContributorDao
import com.calypsan.listenup.client.data.local.db.SeriesDao
import com.calypsan.listenup.client.data.local.db.SyncDao
import com.calypsan.listenup.client.data.local.db.getLastSyncTime
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.data.remote.SyncApiContract
import com.calypsan.listenup.client.data.sync.SyncCoordinator
import com.calypsan.listenup.client.data.sync.model.SyncPhase
import com.calypsan.listenup.client.data.sync.model.SyncStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

private val logger = KotlinLogging.logger {}

/**
 * Coordinates parallel entity pulls with retry logic and progress reporting.
 *
 * Fetches the sync manifest first to determine total item counts, then
 * reports real progress as each entity is synced: "Syncing books: 50 of 131"
 * and aggregate: "Syncing: 180 of 350 items".
 */
@Suppress("LongParameterList")
class PullSyncOrchestrator(
    private val bookPuller: Puller,
    private val seriesPuller: Puller,
    private val contributorPuller: Puller,
    private val tagPuller: Puller,
    private val genrePuller: Puller,
    private val shelfPuller: Puller,
    private val listeningEventPuller: ListeningEventPullerContract,
    private val progressPuller: Puller,
    private val activeSessionsPuller: Puller,
    private val readingSessionsPuller: Puller,
    private val coordinator: SyncCoordinator,
    private val syncDao: SyncDao,
    private val syncApi: SyncApiContract,
    private val bookDao: BookDao,
    private val seriesDao: SeriesDao,
    private val contributorDao: ContributorDao,
) {
    /**
     * Pull all entities from server with retry logic.
     *
     * Fetches the manifest first to know total counts, then pulls each entity
     * type while reporting real per-item progress.
     *
     * @param onProgress Callback for progress updates
     */
    @Suppress("CognitiveComplexMethod")
    suspend fun pull(onProgress: (SyncStatus) -> Unit) =
        coroutineScope {
            logger.debug { "Pulling changes from server" }

            val lastSyncTime = syncDao.getLastSyncTime()
            val updatedAfter = lastSyncTime?.toIsoString()
            val syncType = if (updatedAfter != null) "delta" else "full"
            logger.info { "Pull sync strategy: $syncType" }

            // Fetch manifest to get total counts for progress reporting
            val manifest =
                try {
                    val result = syncApi.getManifest()
                    if (result is Result.Success) {
                        result.data
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to fetch sync manifest, progress will be approximate" }
                    null
                }

            val totalBooks = manifest?.counts?.books ?: -1
            val totalSeries = manifest?.counts?.series ?: -1
            val totalContributors = manifest?.counts?.contributors ?: -1
            // We don't know totals for tags, genres, shelves, events, etc.
            // but we can count what we know
            val knownTotal =
                if (manifest != null) {
                    totalBooks + totalSeries + totalContributors
                } else {
                    -1
                }

            logger.info {
                "Sync manifest: $totalBooks books, $totalSeries series, $totalContributors contributors"
            }

            // Atomic counter for aggregate progress across parallel pulls
            val itemsSynced = atomic(0)

            // Helper to create progress callbacks that track per-phase AND aggregate
            fun phaseProgress(
                phase: SyncPhase,
                phaseTotal: Int,
                phaseName: String,
            ): (Int) -> Unit =
                { phaseItemsSynced ->
                    val aggregate = itemsSynced.value
                    onProgress(
                        SyncStatus.Progress(
                            phase = phase,
                            phaseItemsSynced = phaseItemsSynced,
                            phaseTotalItems = phaseTotal,
                            totalItemsSynced = aggregate,
                            totalItems = knownTotal,
                            message =
                                if (phaseTotal > 0) {
                                    "Syncing $phaseName: $phaseItemsSynced of $phaseTotal"
                                } else {
                                    "Syncing $phaseName..."
                                },
                        ),
                    )
                }

            onProgress(
                SyncStatus.Progress(
                    phase = SyncPhase.FETCHING_METADATA,
                    message = "Preparing sync...",
                    totalItems = knownTotal,
                ),
            )

            // Run with retry logic
            coordinator.withRetry(
                onRetry = { attempt, max ->
                    onProgress(SyncStatus.Retrying(attempt = attempt, maxAttempts = max))
                },
            ) {
                // Phase 1: Series + Contributors in parallel
                val seriesJob =
                    async {
                        seriesPuller.pull(updatedAfter) { status ->
                            if (status is SyncStatus.Progress) {
                                val count = status.phaseItemsSynced
                                itemsSynced.value = count // series contributes to aggregate
                                onProgress(
                                    status.copy(
                                        totalItemsSynced = itemsSynced.value,
                                        totalItems = knownTotal,
                                    ),
                                )
                            }
                        }
                    }
                val contributorsJob =
                    async {
                        contributorPuller.pull(updatedAfter) { status ->
                            if (status is SyncStatus.Progress) {
                                onProgress(
                                    status.copy(
                                        totalItemsSynced = itemsSynced.value,
                                        totalItems = knownTotal,
                                    ),
                                )
                            }
                        }
                    }

                try {
                    awaitAll(seriesJob, contributorsJob)
                } catch (e: Exception) {
                    seriesJob.cancel()
                    contributorsJob.cancel()
                    throw e
                }

                // Phase 2: Books
                bookPuller.pull(updatedAfter) { status ->
                    if (status is SyncStatus.Progress) {
                        onProgress(
                            status.copy(
                                totalItemsSynced = itemsSynced.value,
                                totalItems = knownTotal,
                            ),
                        )
                    }
                }

                // Remaining phases — no known totals, just pass through
                tagPuller.pull(updatedAfter, onProgress)
                genrePuller.pull(updatedAfter, onProgress)
                shelfPuller.pull(updatedAfter, onProgress)
                progressPuller.pull(updatedAfter, onProgress)
                listeningEventPuller.pull(updatedAfter, onProgress)
                activeSessionsPuller.pull(updatedAfter, onProgress)
                readingSessionsPuller.pull(updatedAfter, onProgress)
            }

            // Self-healing: if local count < manifest count, the delta sync filtered out entities
            // whose updated_at predates the client checkpoint. Re-pull those entity types in full.
            if (manifest != null) {
                if (totalBooks > 0 && bookDao.count() < totalBooks) {
                    logger.warn { "Book count mismatch: local=${bookDao.count()}, server=$totalBooks — re-pulling books in full" }
                    bookPuller.pull(null) {}
                }
                if (totalSeries > 0 && seriesDao.count() < totalSeries) {
                    logger.warn { "Series count mismatch: local=${seriesDao.count()}, server=$totalSeries — re-pulling series in full" }
                    seriesPuller.pull(null) {}
                }
                if (totalContributors > 0 && contributorDao.count() < totalContributors) {
                    logger.warn { "Contributor count mismatch: local=${contributorDao.count()}, server=$totalContributors — re-pulling contributors in full" }
                    contributorPuller.pull(null) {}
                }
            }

            onProgress(
                SyncStatus.Progress(
                    phase = SyncPhase.FINALIZING,
                    totalItemsSynced = itemsSynced.value,
                    totalItems = knownTotal,
                    message = "Finalizing sync...",
                ),
            )
        }

    /**
     * Refresh all listening events and playback positions.
     */
    suspend fun refreshListeningHistory() {
        logger.info { "Refreshing all listening history..." }
        progressPuller.pull(null) {}
        listeningEventPuller.pullAll()
    }
}
