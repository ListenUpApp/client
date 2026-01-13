package com.calypsan.listenup.client.domain.repository

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.domain.model.SyncState
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository contract for library synchronization operations.
 *
 * Abstracts the sync infrastructure behind a domain interface, allowing
 * use cases to remain pure and independent of sync implementation details.
 *
 * Implementations handle:
 * - Coordinating pull/push operations
 * - Managing sync state
 * - Handling network errors and retries
 * - Library identity verification
 *
 * Part of the domain layer - implementations live in the data layer.
 */
interface SyncRepository {
    /**
     * Observable sync state for progress monitoring.
     *
     * Emits state changes throughout the sync lifecycle:
     * Idle -> Syncing -> Progress -> Success/Error
     *
     * ViewModels observe this to show sync progress in the UI.
     */
    val syncState: StateFlow<SyncState>

    /**
     * Whether the server is currently scanning the library.
     *
     * True from when a ScanStarted SSE event is received until ScanCompleted.
     * UI can use this to show "Scanning your library..." instead of empty state
     * during initial library setup.
     */
    val isServerScanning: StateFlow<Boolean>

    /**
     * Trigger a full library sync with the server.
     *
     * Performs:
     * 1. Pull latest changes from server
     * 2. Push pending local changes
     * 3. Download new cover images
     *
     * Progress can be observed via [syncState].
     *
     * @return Result.Success on completion, Result.Failure on error
     */
    suspend fun sync(): Result<Unit>

    /**
     * Reset local data and sync with a new library.
     *
     * Used when library mismatch is detected (server was reset/changed).
     * This clears all local data and performs a fresh sync.
     *
     * WARNING: This is destructive - any unsynced local changes will be lost.
     *
     * @param newLibraryId The new library ID to sync with
     * @return Result.Success on completion, Result.Failure on error
     */
    suspend fun resetForNewLibrary(newLibraryId: String): Result<Unit>

    /**
     * Refresh all listening events and playback positions from server.
     *
     * Used after importing data (e.g., from Audiobookshelf) to fetch historical
     * events that wouldn't be included in a normal delta sync.
     *
     * This fetches ALL events from the server (ignoring the delta sync cursor)
     * and rebuilds playback positions from them.
     *
     * @return Result.Success on completion, Result.Failure on error
     */
    suspend fun refreshListeningHistory(): Result<Unit>
}
