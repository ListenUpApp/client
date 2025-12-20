package com.calypsan.listenup.client.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for [PendingOperationEntity] operations.
 *
 * Manages the unified queue for all push sync operations.
 * Supports coalescing, batching, and status tracking.
 */
@Dao
interface PendingOperationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(operation: PendingOperationEntity)

    @Update
    suspend fun update(operation: PendingOperationEntity)

    @Query("SELECT * FROM pending_operations WHERE id = :id")
    suspend fun getById(id: String): PendingOperationEntity?

    /**
     * Find existing pending operation for coalescing.
     * Used when queuing a new operation to check if we should merge.
     */
    @Query(
        """
        SELECT * FROM pending_operations
        WHERE operationType = :type
          AND entityId = :entityId
          AND status = ${OperationStatus.PENDING_ORDINAL}
        LIMIT 1
        """,
    )
    suspend fun findPendingByTypeAndEntity(
        type: OperationType,
        entityId: String,
    ): PendingOperationEntity?

    /**
     * Find existing pending preferences operation for coalescing.
     * Preferences have no entityId, so we match by type only.
     */
    @Query(
        """
        SELECT * FROM pending_operations
        WHERE operationType = ${OperationType.USER_PREFERENCES_ORDINAL}
          AND status = ${OperationStatus.PENDING_ORDINAL}
        LIMIT 1
        """,
    )
    suspend fun findPendingPreferences(): PendingOperationEntity?

    /**
     * Get the oldest pending operation (for determining next batch).
     */
    @Query(
        """
        SELECT * FROM pending_operations
        WHERE status = ${OperationStatus.PENDING_ORDINAL}
        ORDER BY createdAt ASC
        LIMIT 1
        """,
    )
    suspend fun getOldestPending(): PendingOperationEntity?

    /**
     * Get all pending operations with a specific batch key.
     * Used for batching listening events together.
     */
    @Query(
        """
        SELECT * FROM pending_operations
        WHERE batchKey = :batchKey
          AND status = ${OperationStatus.PENDING_ORDINAL}
        ORDER BY createdAt ASC
        LIMIT :limit
        """,
    )
    suspend fun getPendingByBatchKey(
        batchKey: String,
        limit: Int,
    ): List<PendingOperationEntity>

    /**
     * Get pending operations (when not batching).
     */
    @Query(
        """
        SELECT * FROM pending_operations
        WHERE status = ${OperationStatus.PENDING_ORDINAL}
        ORDER BY createdAt ASC
        LIMIT :limit
        """,
    )
    suspend fun getPending(limit: Int): List<PendingOperationEntity>

    /**
     * Mark operations as in progress.
     */
    @Query(
        """
        UPDATE pending_operations
        SET status = ${OperationStatus.IN_PROGRESS_ORDINAL}
        WHERE id IN (:ids)
        """,
    )
    suspend fun markInProgress(ids: List<String>)

    /**
     * Delete completed operations.
     */
    @Query("DELETE FROM pending_operations WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    /**
     * Mark operation as failed with error message.
     */
    @Query(
        """
        UPDATE pending_operations
        SET status = ${OperationStatus.FAILED_ORDINAL},
            lastError = :error,
            attemptCount = attemptCount + 1
        WHERE id = :id
        """,
    )
    suspend fun markFailed(
        id: String,
        error: String,
    )

    /**
     * Reset operation for retry (user action).
     */
    @Query(
        """
        UPDATE pending_operations
        SET status = ${OperationStatus.PENDING_ORDINAL},
            attemptCount = 0,
            lastError = NULL
        WHERE id = :id
        """,
    )
    suspend fun resetForRetry(id: String)

    /**
     * Delete a single operation (for dismiss).
     */
    @Query("DELETE FROM pending_operations WHERE id = :id")
    suspend fun delete(id: String)

    /**
     * Observe count of pending operations.
     */
    @Query(
        """
        SELECT COUNT(*) FROM pending_operations
        WHERE status = ${OperationStatus.PENDING_ORDINAL}
        """,
    )
    fun observePendingCount(): Flow<Int>

    /**
     * Observe failed operations.
     */
    @Query(
        """
        SELECT * FROM pending_operations
        WHERE status = ${OperationStatus.FAILED_ORDINAL}
        ORDER BY updatedAt DESC
        """,
    )
    fun observeFailed(): Flow<List<PendingOperationEntity>>

    /**
     * Observe current in-progress operation (if any).
     */
    @Query(
        """
        SELECT * FROM pending_operations
        WHERE status = ${OperationStatus.IN_PROGRESS_ORDINAL}
        LIMIT 1
        """,
    )
    fun observeInProgress(): Flow<PendingOperationEntity?>

    /**
     * Observe all operations (for sync indicator dropdown).
     */
    @Query("SELECT * FROM pending_operations ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PendingOperationEntity>>

    /**
     * Delete all pending operations.
     * Used for testing and account logout.
     */
    @Query("DELETE FROM pending_operations")
    suspend fun deleteAll()

    /**
     * Reset any stuck in-progress operations back to pending.
     * Called on app startup in case we crashed mid-sync.
     */
    @Query(
        """
        UPDATE pending_operations
        SET status = ${OperationStatus.PENDING_ORDINAL}
        WHERE status = ${OperationStatus.IN_PROGRESS_ORDINAL}
        """,
    )
    suspend fun resetStuckOperations()
}
