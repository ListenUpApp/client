package com.calypsan.listenup.client.data.repository

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.data.sync.SyncManagerContract
import com.calypsan.listenup.client.domain.model.SyncState
import com.calypsan.listenup.client.domain.repository.SyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.calypsan.listenup.client.data.sync.model.SyncPhase as DataSyncPhase
import com.calypsan.listenup.client.data.sync.model.SyncStatus as DataSyncStatus
import com.calypsan.listenup.client.domain.model.SyncPhase as DomainSyncPhase

/**
 * Implementation of SyncRepository that wraps SyncManagerContract.
 *
 * Maps data layer SyncStatus to domain layer SyncState, keeping
 * sync infrastructure concerns in the data layer while exposing
 * clean domain types to use cases.
 *
 * @property syncManager Data layer sync orchestrator
 * @property scope Coroutine scope for state flow mapping
 */
class SyncRepositoryImpl(
    private val syncManager: SyncManagerContract,
    scope: CoroutineScope,
) : SyncRepository {
    override val syncState: StateFlow<SyncState> =
        syncManager.syncState
            .map { it.toDomain() }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = SyncState.Idle,
            )

    override val isServerScanning: StateFlow<Boolean> = syncManager.isServerScanning

    override suspend fun sync(): Result<Unit> = syncManager.sync()

    override suspend fun resetForNewLibrary(newLibraryId: String): Result<Unit> =
        syncManager.resetForNewLibrary(newLibraryId)

    override suspend fun refreshListeningHistory(): Result<Unit> = syncManager.refreshListeningHistory()

    override suspend fun forceFullResync(): Result<Unit> = syncManager.forceFullResync()
}

/**
 * Map data layer SyncStatus to domain layer SyncState.
 */
private fun DataSyncStatus.toDomain(): SyncState =
    when (this) {
        is DataSyncStatus.Idle -> {
            SyncState.Idle
        }

        is DataSyncStatus.Syncing -> {
            SyncState.Syncing
        }

        is DataSyncStatus.Progress -> {
            SyncState.Progress(
                phase = phase.toDomain(),
                current = current,
                total = total,
                message = message,
            )
        }

        is DataSyncStatus.Retrying -> {
            SyncState.Retrying(
                attempt = attempt,
                maxAttempts = maxAttempts,
            )
        }

        is DataSyncStatus.Success -> {
            SyncState.Success(
                timestamp = timestamp,
            )
        }

        is DataSyncStatus.Error -> {
            SyncState.Error(
                message = exception.message ?: "Sync failed",
                exception = exception,
            )
        }

        is DataSyncStatus.LibraryMismatch -> {
            SyncState.LibraryMismatch(
                expectedLibraryId = expectedLibraryId,
                actualLibraryId = actualLibraryId,
                hasPendingChanges = hasPendingChanges,
            )
        }
    }

/**
 * Map data layer SyncPhase to domain layer SyncPhase.
 */
private fun DataSyncPhase.toDomain(): DomainSyncPhase =
    when (this) {
        DataSyncPhase.FETCHING_METADATA -> DomainSyncPhase.FETCHING_METADATA
        DataSyncPhase.SYNCING_BOOKS -> DomainSyncPhase.SYNCING_BOOKS
        DataSyncPhase.SYNCING_SERIES -> DomainSyncPhase.SYNCING_SERIES
        DataSyncPhase.SYNCING_CONTRIBUTORS -> DomainSyncPhase.SYNCING_CONTRIBUTORS
        DataSyncPhase.SYNCING_TAGS -> DomainSyncPhase.SYNCING_TAGS
        DataSyncPhase.SYNCING_GENRES -> DomainSyncPhase.SYNCING_GENRES
        DataSyncPhase.SYNCING_LISTENING_EVENTS -> DomainSyncPhase.SYNCING_LISTENING_EVENTS
        DataSyncPhase.FINALIZING -> DomainSyncPhase.FINALIZING
    }
