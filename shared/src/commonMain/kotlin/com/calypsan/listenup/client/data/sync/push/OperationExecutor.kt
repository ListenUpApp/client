@file:Suppress("LongParameterList")

package com.calypsan.listenup.client.data.sync.push

import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.data.local.db.OperationType
import com.calypsan.listenup.client.data.local.db.PendingOperationEntity
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Contract for operation execution.
 * Enables testing without concrete implementation.
 */
interface OperationExecutorContract {
    suspend fun execute(operations: List<PendingOperationEntity>): Map<String, Result<Unit>>
}

/**
 * Executes pending operations using their type-specific handlers.
 *
 * Dispatches operations to the appropriate handler based on operationType.
 * Supports both single and batch execution for efficiency.
 */
class OperationExecutor(
    private val handlers: Map<OperationType, OperationHandler<*>>,
) : OperationExecutorContract {
    /**
     * Execute a batch of operations.
     *
     * All operations in the batch must have the same operationType.
     * Uses batch execution if the handler supports it.
     *
     * @return Map of operation ID to result
     */
    override suspend fun execute(operations: List<PendingOperationEntity>): Map<String, Result<Unit>> {
        logger.info { "🔧 EXECUTOR: execute() called with ${operations.size} operations" }
        if (operations.isEmpty()) return emptyMap()

        val first = operations.first()
        val handler = handlers[first.operationType]

        if (handler == null) {
            logger.error { "🔧 EXECUTOR: No handler for operation type: ${first.operationType}" }
            return operations.associate {
                it.id to Failure(Exception("No handler for ${first.operationType}"))
            }
        }

        logger.info { "🔧 EXECUTOR: Using handler for ${first.operationType}" }
        return executeWithHandler(operations, handler)
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <P : Any> executeWithHandler(
        operations: List<PendingOperationEntity>,
        handler: OperationHandler<P>,
    ): Map<String, Result<Unit>> {
        // Parse all payloads
        val parsed =
            operations.mapNotNull { op ->
                try {
                    op to handler.parsePayload(op.payload)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to parse payload for operation ${op.id}" }
                    null
                }
            }

        if (parsed.isEmpty()) {
            return operations.associate {
                it.id to Failure(Exception("Failed to parse payload"))
            }
        }

        // Check if this is a batch operation
        val shouldBatch =
            operations.size > 1 ||
                handler.batchKey(parsed.first().second) != null

        return if (shouldBatch) {
            logger.debug { "Executing batch of ${parsed.size} ${handler.operationType} operations" }
            handler.executeBatch(parsed)
        } else {
            val (op, payload) = parsed.first()
            logger.debug { "Executing single ${handler.operationType} operation: ${op.id}" }
            mapOf(op.id to handler.execute(op, payload))
        }
    }

    companion object {
        /**
         * Create an OperationExecutor with all handlers.
         */
        fun create(
            bookUpdateHandler: BookUpdateHandler,
            contributorUpdateHandler: ContributorUpdateHandler,
            seriesUpdateHandler: SeriesUpdateHandler,
            setBookContributorsHandler: SetBookContributorsHandler,
            setBookSeriesHandler: SetBookSeriesHandler,
            mergeContributorHandler: MergeContributorHandler,
            unmergeContributorHandler: UnmergeContributorHandler,
            listeningEventHandler: ListeningEventHandler,
            playbackPositionHandler: PlaybackPositionHandler,
            userPreferencesHandler: UserPreferencesHandler,
        ): OperationExecutor =
            OperationExecutor(
                mapOf(
                    OperationType.BOOK_UPDATE to bookUpdateHandler,
                    OperationType.CONTRIBUTOR_UPDATE to contributorUpdateHandler,
                    OperationType.SERIES_UPDATE to seriesUpdateHandler,
                    OperationType.SET_BOOK_CONTRIBUTORS to setBookContributorsHandler,
                    OperationType.SET_BOOK_SERIES to setBookSeriesHandler,
                    OperationType.MERGE_CONTRIBUTOR to mergeContributorHandler,
                    OperationType.UNMERGE_CONTRIBUTOR to unmergeContributorHandler,
                    OperationType.LISTENING_EVENT to listeningEventHandler,
                    OperationType.PLAYBACK_POSITION to playbackPositionHandler,
                    OperationType.USER_PREFERENCES to userPreferencesHandler,
                ),
            )
    }
}
