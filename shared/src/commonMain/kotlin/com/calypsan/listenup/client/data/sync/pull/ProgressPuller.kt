package com.calypsan.listenup.client.data.sync.pull

import com.calypsan.listenup.client.core.BookId
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.data.local.db.PendingOperationDao
import com.calypsan.listenup.client.data.local.db.PlaybackPositionDao
import com.calypsan.listenup.client.data.remote.SyncApiContract
import com.calypsan.listenup.client.data.sync.model.SyncPhase
import com.calypsan.listenup.client.data.sync.model.SyncStatus
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Handles syncing playback progress from server.
 *
 * Fetches all progress records including isFinished status to ensure
 * the client has accurate finished state for Continue Listening filtering.
 *
 * This is essential for:
 * - Cross-device sync: User finishes book on phone, tablet needs to know
 * - Fresh install: User reinstalls app, all finished state should sync
 * - ABS import: Imported progress includes isFinished from Audiobookshelf
 */
class ProgressPuller(
    private val syncApi: SyncApiContract,
    private val playbackPositionDao: PlaybackPositionDao,
    private val pendingOperationDao: PendingOperationDao,
) : Puller {
    /**
     * Pull all progress from server and upsert locally.
     *
     * Merges server progress with local records, preserving local-only fields
     * (playbackSpeed, hasCustomSpeed) while syncing server fields (position,
     * isFinished, lastPlayedAt).
     *
     * @param updatedAfter Ignored - always fetches all progress
     * @param onProgress Callback for progress updates
     */
    override suspend fun pull(
        updatedAfter: String?,
        onProgress: (SyncStatus) -> Unit,
    ) {
        logger.debug { "Starting progress sync..." }

        onProgress(
            SyncStatus.Progress(
                phase = SyncPhase.SYNCING_LISTENING_EVENTS,
                phaseItemsSynced = 0,
                phaseTotalItems = -1,
                message = "Syncing playback progress...",
            ),
        )

        try {
            when (val result = syncApi.getAllProgress()) {
                is Result.Success -> {
                    val items = result.data.items
                    logger.info { "Fetched ${items.size} progress records from server" }

                    if (items.isEmpty()) {
                        logger.debug { "No progress records to sync" }
                        return
                    }

                    // Batch fetch existing local positions to preserve local-only fields
                    val bookIds = items.map { BookId(it.bookId) }
                    val existingPositions =
                        playbackPositionDao
                            .getByBookIds(bookIds)
                            .associateBy { it.bookId.value }

                    var created = 0
                    var updated = 0
                    var finishedCount = 0

                    // Convert to entities, preserving local-only fields
                    val entities =
                        items.map { item ->
                            val existing = existingPositions[item.bookId]
                            if (existing == null) created++ else updated++
                            if (item.isFinished) finishedCount++
                            item.toEntity(existing)
                        }

                    // Debug: Verify entities have correct isFinished values before save
                    val finishedEntities = entities.filter { it.isFinished }
                    logger.debug {
                        "Entities with isFinished=true BEFORE saveAll: ${finishedEntities.size}"
                    }
                    if (finishedEntities.isNotEmpty()) {
                        val sample = finishedEntities.take(3)
                        logger.debug {
                            "Sample finished entities: ${sample.map {
                                "${it.bookId.value}: isFinished=${it.isFinished}"
                            }}"
                        }
                    }

                    // Guard: don't overwrite isFinished for books with pending MARK_COMPLETE
                    val pendingCompleteBookIds =
                        pendingOperationDao.getPendingMarkCompleteBookIds().toSet()
                    val guardedEntities =
                        if (pendingCompleteBookIds.isEmpty()) {
                            entities
                        } else {
                            entities.map { entity ->
                                if (entity.bookId.value in pendingCompleteBookIds && !entity.isFinished) {
                                    // Server says not finished but we have a pending local markComplete.
                                    // The pending op is the source of truth — unconditionally preserve isFinished.
                                    logger.info {
                                        "Preserving isFinished=true for ${entity.bookId.value} (pending MARK_COMPLETE)"
                                    }
                                    entity.copy(isFinished = true)
                                } else {
                                    entity
                                }
                            }
                        }

                    playbackPositionDao.saveAll(guardedEntities)

                    // Debug: Verify what's in DB after save
                    val dbCheck = playbackPositionDao.getByBookIds(entities.map { it.bookId })
                    val dbFinishedCount = dbCheck.count { it.isFinished }
                    logger.debug {
                        "DB check AFTER saveAll: ${dbCheck.size} positions, $dbFinishedCount finished"
                    }
                    if (dbFinishedCount == 0 && finishedEntities.isNotEmpty()) {
                        logger.warn {
                            "BUG: isFinished not persisting! ${finishedEntities.size} entities had isFinished=true but DB shows 0"
                        }
                    }

                    logger.info {
                        "Progress sync complete: $created created, $updated updated, " +
                            "$finishedCount marked finished"
                    }
                }

                is Result.Failure -> {
                    logger.warn(result.exception) { "Failed to fetch progress" }
                    // Don't throw - progress sync is not critical for basic functionality
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to sync progress" }
            // Don't throw - progress sync is not critical for basic functionality
        }
    }
}
