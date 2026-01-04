package com.calypsan.listenup.client.data.sync.push

import com.calypsan.listenup.client.core.currentEpochMilliseconds
import com.calypsan.listenup.client.data.local.db.BookDao
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.db.ContributorDao
import com.calypsan.listenup.client.data.local.db.EntityType
import com.calypsan.listenup.client.data.local.db.OperationStatus
import com.calypsan.listenup.client.data.local.db.OperationType
import com.calypsan.listenup.client.data.local.db.PendingOperationDao
import com.calypsan.listenup.client.data.local.db.PendingOperationEntity
import com.calypsan.listenup.client.data.local.db.SeriesDao
import com.calypsan.listenup.client.data.local.db.SyncState
import com.calypsan.listenup.client.util.NanoId
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map

private val logger = KotlinLogging.logger {}

// Operation types that are silent (not shown in sync indicator)
private val SILENT_TYPES =
    setOf(
        OperationType.LISTENING_EVENT,
        OperationType.PLAYBACK_POSITION,
        OperationType.USER_PREFERENCES,
    )

/**
 * Contract for pending operation repository.
 */
interface PendingOperationRepositoryContract {
    /**
     * Flow that emits whenever a new operation is queued.
     * Used by PushSyncOrchestrator to trigger immediate flush when online.
     */
    val newOperationQueued: kotlinx.coroutines.flow.Flow<Unit>

    /**
     * Queue an operation (coalesces if applicable).
     */
    suspend fun <P : Any> queue(
        type: OperationType,
        entityType: EntityType?,
        entityId: String?,
        payload: P,
        handler: OperationHandler<P>,
    )

    /**
     * Get next batch to sync (respects batchKey grouping).
     */
    suspend fun getNextBatch(limit: Int = 50): List<PendingOperationEntity>

    /**
     * Mark operations as in progress.
     */
    suspend fun markInProgress(ids: List<String>)

    /**
     * Delete completed operations.
     */
    suspend fun markCompleted(ids: List<String>)

    /**
     * Mark operation as failed with error.
     */
    suspend fun markFailed(
        id: String,
        error: String,
    )

    /**
     * User action: retry a failed operation.
     */
    suspend fun retry(id: String)

    /**
     * User action: dismiss a failed operation.
     * Discards local changes and marks entity for re-sync.
     */
    suspend fun dismiss(id: String)

    /**
     * Observe count of pending operations.
     */
    fun observePendingCount(): Flow<Int>

    /**
     * Observe failed operations (for sync indicator).
     */
    fun observeFailedOperations(): Flow<List<PendingOperationEntity>>

    /**
     * Observe current in-progress operation.
     */
    fun observeInProgressOperation(): Flow<PendingOperationEntity?>

    /**
     * Observe visible operations (excludes silent types).
     */
    fun observeVisibleOperations(): Flow<List<PendingOperationEntity>>

    /**
     * Reset any stuck in-progress operations (call on app startup).
     */
    suspend fun resetStuckOperations()
}

/**
 * Repository for managing pending push operations.
 *
 * Handles queuing, coalescing, and status management for all
 * operations that need to sync to the server.
 */
class PendingOperationRepository(
    private val dao: PendingOperationDao,
    private val bookDao: BookDao,
    private val contributorDao: ContributorDao,
    private val seriesDao: SeriesDao,
) : PendingOperationRepositoryContract {
    private val _newOperationQueued = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val newOperationQueued: Flow<Unit> = _newOperationQueued.asSharedFlow()

    override suspend fun <P : Any> queue(
        type: OperationType,
        entityType: EntityType?,
        entityId: String?,
        payload: P,
        handler: OperationHandler<P>,
    ) {
        val now = currentEpochMilliseconds()

        // Check for existing operation to coalesce with
        val existing = findExistingForCoalesce(type, entityId)

        // Try to coalesce if there's an existing operation
        val merged =
            existing?.let {
                val existingPayload = handler.parsePayload(it.payload)
                handler.tryCoalesce(it, existingPayload, payload)
            }

        if (existing != null && merged != null) {
            // Coalesce succeeded - update existing operation with merged payload
            val updated =
                existing.copy(
                    payload = handler.serializePayload(merged),
                    updatedAt = now,
                    attemptCount = 0,
                    status = OperationStatus.PENDING,
                    lastError = null,
                )
            dao.update(updated)
            logger.debug { "Coalesced operation: ${type.name} for entity $entityId" }
        } else {
            // No existing operation or coalesce not possible - insert new
            val batchKey = handler.batchKey(payload)
            val operation =
                PendingOperationEntity(
                    id = NanoId.generate("op"),
                    operationType = type,
                    entityType = entityType,
                    entityId = entityId,
                    payload = handler.serializePayload(payload),
                    batchKey = batchKey,
                    status = OperationStatus.PENDING,
                    createdAt = now,
                    updatedAt = now,
                    attemptCount = 0,
                    lastError = null,
                )
            dao.insert(operation)
            logger.debug { "Queued operation: ${type.name} for entity $entityId (batch=$batchKey)" }
        }

        // Notify orchestrator that a new operation was queued (triggers flush if online)
        _newOperationQueued.tryEmit(Unit)
    }

    private suspend fun findExistingForCoalesce(
        type: OperationType,
        entityId: String?,
    ): PendingOperationEntity? =
        when {
            // Preferences coalesce globally (no entityId)
            type == OperationType.USER_PREFERENCES -> dao.findPendingPreferences()

            // Entity operations coalesce by entityId
            entityId != null -> dao.findPendingByTypeAndEntity(type, entityId)

            // Can't coalesce without entityId
            else -> null
        }

    override suspend fun getNextBatch(limit: Int): List<PendingOperationEntity> {
        val first = dao.getOldestPending() ?: return emptyList()

        return if (first.batchKey != null) {
            // Fetch all with same batchKey (e.g., listening events)
            dao.getPendingByBatchKey(first.batchKey, limit)
        } else {
            // Single operation
            listOf(first)
        }
    }

    override suspend fun markInProgress(ids: List<String>) {
        dao.markInProgress(ids)
    }

    override suspend fun markCompleted(ids: List<String>) {
        dao.deleteByIds(ids)
        logger.debug { "Completed ${ids.size} operation(s)" }
    }

    override suspend fun markFailed(
        id: String,
        error: String,
    ) {
        dao.markFailed(id, error)
        logger.warn { "Operation $id failed: $error" }
    }

    override suspend fun retry(id: String) {
        dao.resetForRetry(id)
        logger.info { "Retry queued for operation: $id" }
    }

    override suspend fun dismiss(id: String) {
        val operation = dao.getById(id) ?: return

        // Delete the pending operation
        dao.delete(id)

        // Mark the entity as needing re-sync (if applicable)
        // The next full sync will refresh it from server
        markEntityForResync(operation)

        logger.info { "Dismissed operation: $id (entity will be refreshed on next sync)" }
    }

    /**
     * Mark an entity as needing re-sync after dismissing its pending operation.
     * Sets syncState to NOT_SYNCED so it will be refreshed on next sync.
     */
    private suspend fun markEntityForResync(operation: PendingOperationEntity) {
        when (operation.entityType) {
            EntityType.BOOK -> {
                operation.entityId?.let { id ->
                    bookDao.getById(BookId(id))?.let { book ->
                        bookDao.upsert(book.copy(syncState = SyncState.NOT_SYNCED))
                    }
                }
            }

            EntityType.CONTRIBUTOR -> {
                operation.entityId?.let { id ->
                    contributorDao.getById(id)?.let { contributor ->
                        contributorDao.upsert(contributor.copy(syncState = SyncState.NOT_SYNCED))
                    }
                }
            }

            EntityType.SERIES -> {
                operation.entityId?.let { id ->
                    seriesDao.getById(id)?.let { series ->
                        seriesDao.upsert(series.copy(syncState = SyncState.NOT_SYNCED))
                    }
                }
            }

            EntityType.USER -> {
                // User profile doesn't have syncState - profile will be refreshed
                // from server on next app launch or profile screen open
            }

            null -> {
                // No entity to mark (preferences, etc.)
            }
        }
    }

    override fun observePendingCount(): Flow<Int> = dao.observePendingCount()

    override fun observeFailedOperations(): Flow<List<PendingOperationEntity>> = dao.observeFailed()

    override fun observeInProgressOperation(): Flow<PendingOperationEntity?> = dao.observeInProgress()

    override fun observeVisibleOperations(): Flow<List<PendingOperationEntity>> =
        dao.observeAll().map { ops ->
            ops.filter { it.operationType !in SILENT_TYPES }
        }

    override suspend fun resetStuckOperations() {
        dao.resetStuckOperations()
        logger.debug { "Reset any stuck in-progress operations" }
    }
}
