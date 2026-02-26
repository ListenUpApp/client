package com.calypsan.listenup.client.presentation.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.domain.model.PendingOperation
import com.calypsan.listenup.client.domain.model.PendingOperationStatus
import com.calypsan.listenup.client.domain.model.PendingOperationType
import com.calypsan.listenup.client.domain.model.SyncState
import com.calypsan.listenup.client.domain.repository.PendingOperationRepository
import com.calypsan.listenup.client.domain.repository.SyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * A single pending operation for UI display.
 */
data class PendingOperationUi(
    val id: String,
    val description: String,
    val isFailed: Boolean,
    val error: String?,
)

/**
 * UI state for sync indicator.
 */
data class SyncIndicatorUiState(
    /**
     * Whether a sync operation is currently in progress.
     */
    val isSyncing: Boolean = false,
    /**
     * Description of current operation (if syncing).
     */
    val currentOperationDescription: String? = null,
    /**
     * Number of pending operations waiting to sync.
     * Excludes silent operations (listening events, playback position, preferences).
     */
    val pendingCount: Int = 0,
    /**
     * Failed operations that need user attention.
     */
    val failedOperations: List<PendingOperationUi> = emptyList(),
    /**
     * Whether there are any failed operations.
     */
    val hasErrors: Boolean = false,
)

/**
 * Events from the sync indicator UI.
 */
sealed interface SyncIndicatorUiEvent {
    /**
     * User requested to retry a failed operation.
     */
    data class RetryOperation(
        val operationId: String,
    ) : SyncIndicatorUiEvent

    /**
     * User requested to dismiss a failed operation.
     * This discards local changes and marks the entity for re-sync.
     */
    data class DismissOperation(
        val operationId: String,
    ) : SyncIndicatorUiEvent

    /**
     * User requested to retry all failed operations.
     */
    data object RetryAll : SyncIndicatorUiEvent

    /**
     * User requested to dismiss all failed operations.
     */
    data object DismissAll : SyncIndicatorUiEvent
}

/**
 * ViewModel for sync indicator component.
 *
 * Provides reactive state for displaying sync status:
 * - Whether sync is in progress
 * - Count of pending operations
 * - List of failed operations with retry/dismiss actions
 *
 * The sync indicator typically appears in:
 * - App bar or navigation
 * - Floating indicator when operations are pending
 * - Error sheet when operations fail
 */
class SyncIndicatorViewModel(
    private val pendingOperationRepository: PendingOperationRepository,
    private val syncRepository: SyncRepository,
) : ViewModel() {
    val isExpanded: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val state: StateFlow<SyncIndicatorUiState> =
        combine(
            pendingOperationRepository.observeVisibleOperations(),
            pendingOperationRepository.observeInProgressOperation(),
            pendingOperationRepository.observeFailedOperations(),
            syncRepository.syncState,
        ) { visibleOps, inProgress, failedOps, pullSyncState ->
            val pendingCount =
                visibleOps.count {
                    it.status == PendingOperationStatus.PENDING
                }
            val failedUi =
                failedOps.map { op ->
                    PendingOperationUi(
                        id = op.id,
                        description = describeOperation(op),
                        isFailed = true,
                        error = op.lastError,
                    )
                }

            // Pull sync is active when library sync is in progress
            val isPullSyncing =
                pullSyncState is SyncState.Syncing ||
                    pullSyncState is SyncState.Progress ||
                    pullSyncState is SyncState.Retrying

            val pullSyncDescription =
                when (pullSyncState) {
                    is SyncState.Progress -> pullSyncState.message
                    is SyncState.Syncing -> "Syncing library…"
                    is SyncState.Retrying -> "Retrying sync (${pullSyncState.attempt}/${pullSyncState.maxAttempts})…"
                    else -> null
                }

            SyncIndicatorUiState(
                isSyncing = inProgress != null || isPullSyncing,
                currentOperationDescription =
                    pullSyncDescription
                        ?: inProgress?.let { describeOperation(it) },
                pendingCount = pendingCount,
                failedOperations = failedUi,
                hasErrors = failedOps.isNotEmpty(),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SyncIndicatorUiState(),
        )

    fun onEvent(event: SyncIndicatorUiEvent) {
        when (event) {
            is SyncIndicatorUiEvent.RetryOperation -> retryOperation(event.operationId)
            is SyncIndicatorUiEvent.DismissOperation -> dismissOperation(event.operationId)
            is SyncIndicatorUiEvent.RetryAll -> retryAllFailed()
            is SyncIndicatorUiEvent.DismissAll -> dismissAllFailed()
        }
    }

    fun toggleExpanded() {
        isExpanded.value = !isExpanded.value
    }

    private fun retryOperation(id: String) {
        viewModelScope.launch {
            pendingOperationRepository.retry(id)
        }
    }

    private fun dismissOperation(id: String) {
        viewModelScope.launch {
            pendingOperationRepository.dismiss(id)
        }
    }

    private fun retryAllFailed() {
        viewModelScope.launch {
            state.value.failedOperations.forEach { op ->
                pendingOperationRepository.retry(op.id)
            }
        }
    }

    private fun dismissAllFailed() {
        viewModelScope.launch {
            state.value.failedOperations.forEach { op ->
                pendingOperationRepository.dismiss(op.id)
            }
        }
    }

    /**
     * Generate a human-readable description of an operation.
     */
    private fun describeOperation(operation: PendingOperation): String {
        val entityName = operation.entityId?.take(8) ?: "item"
        return when (operation.operationType) {
            PendingOperationType.BOOK_UPDATE -> "Updating book $entityName"
            PendingOperationType.CONTRIBUTOR_UPDATE -> "Updating contributor $entityName"
            PendingOperationType.SERIES_UPDATE -> "Updating series $entityName"
            PendingOperationType.SET_BOOK_CONTRIBUTORS -> "Setting contributors for book $entityName"
            PendingOperationType.SET_BOOK_SERIES -> "Setting series for book $entityName"
            PendingOperationType.MERGE_CONTRIBUTOR -> "Merging contributors"
            PendingOperationType.UNMERGE_CONTRIBUTOR -> "Unmerging contributor"
            PendingOperationType.LISTENING_EVENT -> "Syncing listening data"
            PendingOperationType.PLAYBACK_POSITION -> "Syncing playback position"
            PendingOperationType.USER_PREFERENCES -> "Syncing preferences"
            PendingOperationType.PROFILE_UPDATE -> "Updating profile"
            PendingOperationType.PROFILE_AVATAR -> "Uploading avatar"
            PendingOperationType.MARK_COMPLETE -> "Marking book complete"
        }
    }
}
