@file:Suppress("SwallowedException")

package com.calypsan.listenup.client.data.sync.pull

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.currentEpochMilliseconds
import com.calypsan.listenup.client.data.local.db.ReadingSessionDao
import com.calypsan.listenup.client.data.local.db.ReadingSessionEntity
import com.calypsan.listenup.client.data.remote.SyncApiContract
import com.calypsan.listenup.client.data.sync.model.SyncPhase
import com.calypsan.listenup.client.data.sync.model.SyncStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Handles syncing reading sessions from server.
 *
 * Fetches all book reader summaries to populate the "Readers" section
 * on book detail pages for offline-first display.
 *
 * Like ActiveSessionsPuller, this does a full sync each time (no delta)
 * because we need the complete current state. SSE events keep the data
 * updated after initial sync.
 */
class ReadingSessionPuller(
    private val syncApi: SyncApiContract,
    private val readingSessionDao: ReadingSessionDao,
) : Puller {
    /**
     * Pull reading sessions from server.
     *
     * Replaces all existing reading sessions with the current server state.
     * This ensures any stale sessions (from missed SSE events) are cleared.
     *
     * @param updatedAfter Ignored - always fetches all reading sessions
     * @param onProgress Callback for progress updates
     */
    override suspend fun pull(
        updatedAfter: String?,
        onProgress: (SyncStatus) -> Unit,
    ) {
        logger.debug { "Starting reading sessions sync..." }

        onProgress(
            SyncStatus.Progress(
                phase = SyncPhase.SYNCING_LISTENING_EVENTS,
                current = 0,
                total = -1,
                message = "Syncing reading sessions...",
            ),
        )

        try {
            when (val result = syncApi.getReadingSessions()) {
                is Result.Success -> {
                    val readers = result.data.readers
                    logger.info { "Fetched ${readers.size} reading sessions from server" }

                    // Clear existing sessions and replace with fresh data
                    readingSessionDao.deleteAll()

                    if (readers.isEmpty()) {
                        logger.debug { "No reading sessions to sync" }
                        return
                    }

                    val now = currentEpochMilliseconds()

                    // Convert to entities and insert
                    val entities =
                        readers.map { reader ->
                            ReadingSessionEntity(
                                id = "${reader.bookId}-${reader.userId}",
                                bookId = reader.bookId,
                                oduserId = reader.userId,
                                userDisplayName = reader.displayName,
                                userAvatarColor = reader.avatarColor,
                                userAvatarType = reader.avatarType,
                                userAvatarValue = reader.avatarValue,
                                isCurrentlyReading = reader.isCurrentlyReading,
                                currentProgress = reader.currentProgress,
                                startedAt = parseTimestamp(reader.startedAt),
                                finishedAt = reader.finishedAt?.let { parseTimestamp(it) },
                                completionCount = reader.completionCount,
                                updatedAt = now,
                            )
                        }

                    readingSessionDao.upsertAll(entities)
                    logger.info { "Reading sessions sync complete: ${entities.size} sessions synced" }
                }

                is Result.Failure -> {
                    logger.warn(result.exception) { "Failed to fetch reading sessions" }
                    // Don't throw - reading sessions are not critical for sync
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to sync reading sessions" }
            // Don't throw - reading sessions are not critical for sync
        }
    }

    /**
     * Parse ISO timestamp string to epoch milliseconds.
     */
    private fun parseTimestamp(isoTimestamp: String): Long =
        try {
            Instant.parse(isoTimestamp).toEpochMilliseconds()
        } catch (e: Exception) {
            logger.warn { "Failed to parse timestamp: $isoTimestamp, using current time" }
            currentEpochMilliseconds()
        }
}
