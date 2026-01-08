package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.IODispatcher
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.core.Timestamp
import com.calypsan.listenup.client.data.local.db.EntityType
import com.calypsan.listenup.client.data.local.db.OperationType
import com.calypsan.listenup.client.data.local.db.SeriesDao
import com.calypsan.listenup.client.data.local.db.SyncState
import com.calypsan.listenup.client.data.sync.push.PendingOperationRepositoryContract
import com.calypsan.listenup.client.data.sync.push.SeriesUpdateHandler
import com.calypsan.listenup.client.data.sync.push.SeriesUpdatePayload
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.withContext

private val logger = KotlinLogging.logger {}

/**
 * Contract for series editing operations.
 *
 * Provides methods for modifying series metadata.
 * Uses offline-first pattern: changes are applied locally immediately
 * and queued for sync to server.
 */
interface SeriesEditRepositoryContract {
    /**
     * Update series metadata.
     *
     * Applies update locally and queues for server sync.
     * Only non-null fields are updated (PATCH semantics).
     *
     * @param seriesId ID of the series to update
     * @param name New name (null = don't change)
     * @param description New description (null = don't change)
     * @return Result indicating success or failure
     */
    suspend fun updateSeries(
        seriesId: String,
        name: String?,
        description: String?,
    ): Result<Unit>
}

/**
 * Repository for series editing operations using offline-first pattern.
 *
 * Handles the edit flow:
 * 1. Apply optimistic update to local database (syncState = PENDING)
 * 2. Queue operation for server sync via PendingOperationRepository
 * 3. Return success immediately
 *
 * The PushSyncOrchestrator will later:
 * - Send changes to server
 * - Handle conflicts if server version is newer
 * - Mark entity as SYNCED on success
 *
 * @property seriesDao Room DAO for series operations
 * @property pendingOperationRepository Repository for queuing sync operations
 * @property seriesUpdateHandler Handler for series update operations
 */
class SeriesEditRepository(
    private val seriesDao: SeriesDao,
    private val pendingOperationRepository: PendingOperationRepositoryContract,
    private val seriesUpdateHandler: SeriesUpdateHandler,
) : SeriesEditRepositoryContract,
    com.calypsan.listenup.client.domain.repository.SeriesEditRepository {
    /**
     * Update series metadata.
     *
     * Flow:
     * 1. Get existing series from local database
     * 2. Apply optimistic update with syncState = PENDING
     * 3. Queue operation (coalesces with any pending update)
     * 4. Return success immediately
     */
    override suspend fun updateSeries(
        seriesId: String,
        name: String?,
        description: String?,
    ): Result<Unit> =
        withContext(IODispatcher) {
            logger.debug { "Updating series (offline-first): $seriesId" }

            // Get existing series
            val existing = seriesDao.getById(seriesId)
            if (existing == null) {
                logger.error { "Series not found: $seriesId" }
                return@withContext Failure(Exception("Series not found: $seriesId"))
            }

            // Apply optimistic update
            val updated =
                existing.copy(
                    name = name ?: existing.name,
                    description = description ?: existing.description,
                    syncState = SyncState.NOT_SYNCED,
                    lastModified = Timestamp.now(),
                )
            seriesDao.upsert(updated)

            // Queue operation (coalesces if pending update exists for this series)
            val payload =
                SeriesUpdatePayload(
                    name = name,
                    description = description,
                )
            pendingOperationRepository.queue(
                type = OperationType.SERIES_UPDATE,
                entityType = EntityType.SERIES,
                entityId = seriesId,
                payload = payload,
                handler = seriesUpdateHandler,
            )

            logger.info { "Series update queued: $seriesId" }
            Success(Unit)
        }
}
