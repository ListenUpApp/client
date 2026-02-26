package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.data.local.db.OperationStatus
import com.calypsan.listenup.client.data.local.db.OperationType
import com.calypsan.listenup.client.data.local.db.PendingOperationEntity
import com.calypsan.listenup.client.data.sync.push.PendingOperationRepositoryContract
import com.calypsan.listenup.client.domain.model.PendingOperation
import com.calypsan.listenup.client.domain.model.PendingOperationStatus
import com.calypsan.listenup.client.domain.model.PendingOperationType
import com.calypsan.listenup.client.domain.repository.PendingOperationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Domain repository implementation for pending operations.
 *
 * Wraps the data layer sync repository and converts entities to domain models.
 * This maintains Clean Architecture boundaries by preventing entity leakage
 * into the presentation layer.
 */
class PendingOperationRepositoryImpl(
    private val dataRepository: PendingOperationRepositoryContract,
) : PendingOperationRepository {
    override fun observeVisibleOperations(): Flow<List<PendingOperation>> =
        dataRepository.observeVisibleOperations().map { entities ->
            entities.map { it.toDomain() }
        }

    override fun observeInProgressOperation(): Flow<PendingOperation?> =
        dataRepository.observeInProgressOperation().map { entity ->
            entity?.toDomain()
        }

    override fun observeFailedOperations(): Flow<List<PendingOperation>> =
        dataRepository.observeFailedOperations().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun retry(id: String) {
        dataRepository.retry(id)
    }

    override suspend fun dismiss(id: String) {
        dataRepository.dismiss(id)
    }
}

/**
 * Convert PendingOperationEntity to PendingOperation domain model.
 */
private fun PendingOperationEntity.toDomain(): PendingOperation =
    PendingOperation(
        id = id,
        operationType = operationType.toDomain(),
        entityId = entityId,
        status = status.toDomain(),
        lastError = lastError,
    )

/**
 * Convert OperationType to PendingOperationType domain enum.
 */
private fun OperationType.toDomain(): PendingOperationType =
    when (this) {
        OperationType.BOOK_UPDATE -> PendingOperationType.BOOK_UPDATE
        OperationType.CONTRIBUTOR_UPDATE -> PendingOperationType.CONTRIBUTOR_UPDATE
        OperationType.SERIES_UPDATE -> PendingOperationType.SERIES_UPDATE
        OperationType.SET_BOOK_CONTRIBUTORS -> PendingOperationType.SET_BOOK_CONTRIBUTORS
        OperationType.SET_BOOK_SERIES -> PendingOperationType.SET_BOOK_SERIES
        OperationType.MERGE_CONTRIBUTOR -> PendingOperationType.MERGE_CONTRIBUTOR
        OperationType.UNMERGE_CONTRIBUTOR -> PendingOperationType.UNMERGE_CONTRIBUTOR
        OperationType.LISTENING_EVENT -> PendingOperationType.LISTENING_EVENT
        OperationType.PLAYBACK_POSITION -> PendingOperationType.PLAYBACK_POSITION
        OperationType.USER_PREFERENCES -> PendingOperationType.USER_PREFERENCES
        OperationType.PROFILE_UPDATE -> PendingOperationType.PROFILE_UPDATE
        OperationType.PROFILE_AVATAR -> PendingOperationType.PROFILE_AVATAR
        OperationType.MARK_COMPLETE -> PendingOperationType.MARK_COMPLETE
    }

/**
 * Convert OperationStatus to PendingOperationStatus domain enum.
 */
private fun OperationStatus.toDomain(): PendingOperationStatus =
    when (this) {
        OperationStatus.PENDING -> PendingOperationStatus.PENDING
        OperationStatus.IN_PROGRESS -> PendingOperationStatus.IN_PROGRESS
        OperationStatus.FAILED -> PendingOperationStatus.FAILED
    }
