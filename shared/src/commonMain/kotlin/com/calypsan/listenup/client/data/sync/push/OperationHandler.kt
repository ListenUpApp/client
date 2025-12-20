package com.calypsan.listenup.client.data.sync.push

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.data.local.db.OperationType
import com.calypsan.listenup.client.data.local.db.PendingOperationEntity

/**
 * Handler for a specific operation type.
 *
 * Each operation type has its own handler that knows how to:
 * - Parse/serialize its payload
 * - Attempt to coalesce operations (returning merged payload or null)
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
     * Attempt to coalesce a new operation with an existing pending one.
     *
     * Called when queuing a new operation that targets the same entity.
     * Returns the merged payload if coalescing succeeded, null if operations
     * cannot be coalesced and should be queued separately.
     *
     * This design follows LSP - all handlers can be called uniformly without
     * throwing exceptions for unsupported operations.
     *
     * @param existing The existing pending operation entity
     * @param existingPayload The parsed payload of the existing operation
     * @param newPayload The new payload to potentially merge
     * @return Merged payload if coalescing succeeded, null otherwise
     */
    fun tryCoalesce(
        existing: PendingOperationEntity,
        existingPayload: P,
        newPayload: P,
    ): P?

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
