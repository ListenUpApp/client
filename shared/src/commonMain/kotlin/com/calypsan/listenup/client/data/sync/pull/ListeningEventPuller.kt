package com.calypsan.listenup.client.data.sync.pull

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.currentEpochMilliseconds
import com.calypsan.listenup.client.data.local.db.ListeningEventDao
import com.calypsan.listenup.client.data.local.db.ListeningEventEntity
import com.calypsan.listenup.client.data.local.db.SyncState
import com.calypsan.listenup.client.data.remote.SyncApiContract
import com.calypsan.listenup.client.data.sync.model.SyncPhase
import com.calypsan.listenup.client.data.sync.model.SyncStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Instant

private val logger = KotlinLogging.logger {}

/**
 * Handles syncing listening events from server.
 *
 * Fetches listening events from other devices to populate local stats.
 * Events are synced as SYNCED since they already exist on the server.
 */
class ListeningEventPuller(
    private val syncApi: SyncApiContract,
    private val listeningEventDao: ListeningEventDao,
) : Puller {
    /**
     * Pull listening events from server.
     *
     * Uses the latest local event timestamp as a cursor for delta sync,
     * so only new events from other devices are fetched.
     *
     * @param updatedAfter ISO timestamp for delta sync (not used - we use our own cursor)
     * @param onProgress Callback for progress updates
     */
    override suspend fun pull(
        updatedAfter: String?,
        onProgress: (SyncStatus) -> Unit,
    ) {
        logger.debug { "Starting listening events sync..." }

        onProgress(
            SyncStatus.Progress(
                phase = SyncPhase.SYNCING_LISTENING_EVENTS,
                current = 0,
                total = -1,
                message = "Syncing listening events...",
            ),
        )

        try {
            // Get the latest local event timestamp to use as a cursor
            // This ensures we only fetch events we don't already have
            val sinceMs = listeningEventDao.getLatestEventTimestamp()

            logger.debug { "Fetching events since: $sinceMs" }

            when (val result = syncApi.getListeningEvents(sinceMs)) {
                is Result.Success -> {
                    val events = result.data.events
                    logger.info { "Fetched ${events.size} listening events from server" }

                    if (events.isEmpty()) {
                        logger.debug { "No new listening events to sync" }
                        return
                    }

                    // Convert to entities and upsert
                    val entities = events.map { event ->
                        ListeningEventEntity(
                            id = event.id,
                            bookId = event.bookId,
                            startPositionMs = event.startPositionMs,
                            endPositionMs = event.endPositionMs,
                            startedAt = parseTimestamp(event.startedAt),
                            endedAt = parseTimestamp(event.endedAt),
                            playbackSpeed = event.playbackSpeed,
                            deviceId = event.deviceId,
                            syncState = SyncState.SYNCED, // Already on server
                            createdAt = parseTimestamp(event.endedAt), // Use endedAt as createdAt
                        )
                    }

                    listeningEventDao.upsertAll(entities)
                    logger.info { "Listening events sync complete: ${entities.size} events synced" }
                }

                is Result.Failure -> {
                    logger.warn(result.exception) { "Failed to fetch listening events" }
                    // Don't throw - listening events are not critical for sync
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to sync listening events" }
            // Don't throw - listening events are not critical for sync
        }
    }

    /**
     * Parse ISO timestamp string to epoch milliseconds.
     */
    private fun parseTimestamp(isoTimestamp: String): Long {
        return try {
            Instant.parse(isoTimestamp).toEpochMilliseconds()
        } catch (e: Exception) {
            logger.warn { "Failed to parse timestamp: $isoTimestamp, using current time" }
            currentEpochMilliseconds()
        }
    }
}
