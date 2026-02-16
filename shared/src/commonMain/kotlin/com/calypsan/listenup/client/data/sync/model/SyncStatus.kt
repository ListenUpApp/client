package com.calypsan.listenup.client.data.sync.model

import com.calypsan.listenup.client.core.Timestamp

/**
 * Phases of the sync operation for progress reporting.
 */
enum class SyncPhase {
    FETCHING_METADATA,
    SYNCING_BOOKS,
    SYNCING_SERIES,
    SYNCING_CONTRIBUTORS,
    SYNCING_TAGS,
    SYNCING_GENRES,
    SYNCING_LISTENING_EVENTS,
    FINALIZING,
}

/**
 * Synchronization status with progress information.
 *
 * Sealed interface for type-safe state management. UI can observe
 * this via StateFlow to show sync progress, errors, etc.
 */
sealed interface SyncStatus {
    /**
     * Idle state - no sync in progress.
     */
    data object Idle : SyncStatus

    /**
     * Sync operation starting.
     */
    data object Syncing : SyncStatus

    /**
     * Sync in progress with detailed progress information.
     *
     * Reports both per-phase and aggregate progress. The aggregate values
     * let UI show "Syncing: 180 of 350 items" across all phases, while
     * phase-specific values enable "Syncing books: 50 of 131".
     *
     * @property phase Current sync phase
     * @property phaseItemsSynced Items completed in current phase
     * @property phaseTotalItems Total items expected in current phase (-1 if unknown)
     * @property totalItemsSynced Items completed across ALL phases so far
     * @property totalItems Total items expected across ALL phases (-1 if unknown)
     * @property message Human-readable progress message
     */
    data class Progress(
        val phase: SyncPhase,
        val phaseItemsSynced: Int = 0,
        val phaseTotalItems: Int = -1,
        val totalItemsSynced: Int = 0,
        val totalItems: Int = -1,
        val message: String,
    ) : SyncStatus {
        /** For backward compatibility with old current/total pattern */
        val current: Int get() = totalItemsSynced
        val total: Int get() = totalItems
    }

    /**
     * Retrying after a transient failure.
     *
     * @property attempt Current retry attempt number (1-based)
     * @property maxAttempts Maximum retry attempts
     */
    data class Retrying(
        val attempt: Int,
        val maxAttempts: Int,
    ) : SyncStatus

    /**
     * Sync completed successfully.
     *
     * @property timestamp Type-safe timestamp of completion
     */
    data class Success(
        val timestamp: Timestamp,
    ) : SyncStatus

    /**
     * Sync failed with error.
     *
     * @property exception The exception that caused failure
     */
    data class Error(
        val exception: Exception,
    ) : SyncStatus

    /**
     * Library identity mismatch detected.
     *
     * The server's library ID doesn't match what this client was synced with.
     * This happens when:
     * - Server was reinstalled/reset (database wiped)
     * - Server restored from a different backup
     * - Client connected to a different server instance
     *
     * UI should prompt the user to resync, optionally warning about
     * unsaved local changes.
     *
     * @property expectedLibraryId The library ID the client was synced with
     * @property actualLibraryId The library ID the server now has
     * @property hasPendingChanges Whether there are unsync'd local changes that would be lost
     */
    data class LibraryMismatch(
        val expectedLibraryId: String,
        val actualLibraryId: String,
        val hasPendingChanges: Boolean,
    ) : SyncStatus
}
