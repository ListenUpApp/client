package com.calypsan.listenup.client.data.sync.push

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.data.local.db.OperationType
import com.calypsan.listenup.client.data.local.db.PendingOperationEntity

/**
 * Handler for a specific operation type.
 *
 * Each operation type has its own handler that knows how to:
 * - Parse/serialize its payload
 * - Determine if operations should coalesce
 * - Merge payloads when coalescing
 * - Execute the operation against the server
 * - Optionally batch operations together
 *
 * @param P The payload type for this operation
 */
interface OperationHandler<P : Any> {
    val operationType: OperationType

    /**
     * Deserialize payload JSON to typed object.
     */
    fun parsePayload(json: String): P

    /**
     * Serialize typed payload to JSON.
     */
    fun serializePayload(payload: P): String

    /**
     * Should this operation coalesce with an existing pending one?
     *
     * Called when queuing a new operation that targets the same entity.
     * Return true to merge the payloads, false to queue separately.
     */
    fun shouldCoalesce(existing: PendingOperationEntity): Boolean

    /**
     * Merge new payload into existing (for coalescing).
     *
     * Only called when shouldCoalesce returns true.
     * The result replaces the existing operation's payload.
     */
    fun coalesce(
        existing: P,
        new: P,
    ): P

    /**
     * Execute the operation against the server.
     *
     * @return Success if operation completed, Failure with error otherwise
     */
    suspend fun execute(
        operation: PendingOperationEntity,
        payload: P,
    ): Result<Unit>

    /**
     * Execute a batch of operations together.
     *
     * Default implementation executes each individually.
     * Override for batch APIs (like listening events).
     *
     * @return Map of operation ID to result
     */
    suspend fun executeBatch(operations: List<Pair<PendingOperationEntity, P>>): Map<String, Result<Unit>> =
        operations.associate { (op, payload) ->
            op.id to execute(op, payload)
        }

    /**
     * Optional batch key for grouping operations.
     *
     * Operations with the same non-null batch key are executed together
     * via executeBatch. Return null for individual execution.
     */
    fun batchKey(payload: P): String? = null
}
