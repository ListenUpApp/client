package com.calypsan.listenup.client.data.sync

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.getOrNull
import com.calypsan.listenup.client.data.local.db.PendingOperationDao
import com.calypsan.listenup.client.data.local.db.SyncDao
import com.calypsan.listenup.client.data.local.db.Syncable
import com.calypsan.listenup.client.data.local.db.Timestamp
import com.calypsan.listenup.client.data.local.db.clearLastSyncTime
import com.calypsan.listenup.client.data.local.db.setLastSyncTime
import com.calypsan.listenup.client.data.remote.UserPreferencesApiContract
import com.calypsan.listenup.client.data.repository.SettingsRepositoryContract
import com.calypsan.listenup.client.data.sync.model.SyncStatus
import com.calypsan.listenup.client.data.sync.pull.PullSyncOrchestrator
import com.calypsan.listenup.client.data.sync.push.PushSyncOrchestrator
import com.calypsan.listenup.client.data.sync.sse.SSEEventProcessor
import com.calypsan.listenup.client.domain.repository.InstanceRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * Contract for sync operations.
 *
 * Defines the public API for syncing data and observing sync status.
 * Used by ViewModels and enables testing via fake implementations.
 */
interface SyncManagerContract {
    /**
     * Current synchronization status.
     */
    val syncState: StateFlow<SyncStatus>

    /**
     * Perform full synchronization with server.
     */
    suspend fun sync(): Result<Unit>

    /**
     * Handle library mismatch by clearing local data and resyncing.
     *
     * Called when the user confirms they want to discard local data
     * and resync with the server's new library.
     *
     * @param newLibraryId The new library ID to sync with
     */
    suspend fun resetForNewLibrary(newLibraryId: String): Result<Unit>
}

/**
 * Orchestrates synchronization between local Room database and server.
 *
 * This is a thin orchestrator that coordinates:
 * - Pull sync via [PullSyncOrchestrator]
 * - Push sync via [PushSyncOrchestrator]
 * - SSE event processing via [SSEEventProcessor]
 * - User preferences sync
 *
 * Sync strategy:
 * 1. Pull changes from server (books, series, contributors in parallel)
 * 2. Pull user preferences (non-blocking)
 * 3. Push local changes (future)
 * 4. Update last sync timestamp
 * 5. Rebuild FTS tables for offline search
 * 6. Connect to SSE stream for real-time updates
 */
@Suppress("LongParameterList")
class SyncManager(
    private val pullOrchestrator: PullSyncOrchestrator,
    private val pushOrchestrator: PushSyncOrchestrator,
    private val sseEventProcessor: SSEEventProcessor,
    private val coordinator: SyncCoordinator,
    private val sseManager: SSEManagerContract,
    private val userPreferencesApi: UserPreferencesApiContract,
    private val settingsRepository: SettingsRepositoryContract,
    private val instanceRepository: InstanceRepository,
    private val pendingOperationDao: PendingOperationDao,
    private val libraryResetHelper: LibraryResetHelperContract,
    private val syncDao: SyncDao,
    private val ftsPopulator: FtsPopulatorContract,
    private val syncMutex: SyncMutex,
    private val scope: CoroutineScope,
) : SyncManagerContract {
    // Override properties can't use explicit backing fields - must use traditional pattern
    private val _syncState = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    override val syncState: StateFlow<SyncStatus> = _syncState.asStateFlow()

    init {
        // Route SSE events to processor
        // Mutex ensures SSE writes don't race with push sync conflict detection
        scope.launch {
            sseManager.eventFlow.collect { event ->
                // Handle reconnection event - trigger delta sync to catch missed events
                if (event is SSEEventType.Reconnected) {
                    handleReconnection()
                } else {
                    syncMutex.withLock {
                        sseEventProcessor.process(event)
                    }
                }
            }
        }

        // Handle user deletion events
        scope.launch {
            sseEventProcessor.userDeletedEvent.collect { deletedInfo ->
                logger.warn { "User account deleted: ${deletedInfo.userId}, reason: ${deletedInfo.reason}" }
                // Disconnect SSE first to stop any further events
                sseManager.disconnect()
                // Clear auth tokens - this will trigger AuthState.NeedsLogin
                settingsRepository.clearAuthTokens()
            }
        }
    }

    /**
     * Handle SSE reconnection by triggering a delta sync.
     *
     * When SSE disconnects and reconnects, events during the gap are lost.
     * We catch up by:
     * 1. Flushing pending local operations first (so local changes aren't lost)
     * 2. Pulling changes since the last sync checkpoint
     */
    private fun handleReconnection() {
        logger.info { "SSE reconnected - triggering delta sync to catch missed events" }

        // Launch delta sync in background (non-blocking)
        // Mutex ensures delta sync writes don't race with SSE event processing
        scope.launch {
            try {
                // Only sync if we're not already syncing
                if (_syncState.value is SyncStatus.Syncing) {
                    logger.debug { "Skipping reconnection sync - already syncing" }
                    return@launch
                }

                syncMutex.withLock {
                    // Flush pending operations first - ensures local changes reach server
                    // before we pull potentially conflicting server changes
                    pushOrchestrator.flush()

                    // Pull changes since last sync (uses syncDao.getLastSyncTime() internally)
                    pullOrchestrator.pull { /* suppress progress updates for background sync */ }

                    // Update sync timestamp
                    val now =
                        com.calypsan.listenup.client.data.local.db.Timestamp
                            .now()
                    syncDao.setLastSyncTime(now)
                }

                logger.info { "Reconnection delta sync completed" }
            } catch (e: Exception) {
                logger.warn(e) { "Reconnection delta sync failed, will retry on next reconnect" }
                // Don't update sync state - this is a background operation
            }
        }
    }

    /**
     * Perform full synchronization with server.
     */
    override suspend fun sync(): Result<Unit> {
        logger.debug { "Starting sync operation" }
        _syncState.value = SyncStatus.Syncing

        return try {
            // Phase 0: Verify library identity
            val mismatch = verifyLibraryIdentity()
            if (mismatch != null) {
                logger.warn {
                    "Library mismatch detected: expected=${mismatch.expectedLibraryId}, actual=${mismatch.actualLibraryId}"
                }
                _syncState.value = mismatch
                return Result.Failure(
                    exception = LibraryMismatchException(mismatch.expectedLibraryId, mismatch.actualLibraryId),
                    message = "Server library has changed. Local data needs to be reset.",
                )
            }

            // Phase 1: Pull changes from server
            pullOrchestrator.pull { updateSyncState(it) }

            // Phase 2: Pull user preferences (non-blocking)
            pullUserPreferences()

            // Phase 3: Push local changes
            // Mutex ensures conflict detection reads consistent data (not mid-SSE-write)
            syncMutex.withLock {
                pushOrchestrator.flush()
            }

            // Phase 4: Update last sync time
            val now = Timestamp.now()
            syncDao.setLastSyncTime(now)

            // Phase 5: Rebuild FTS tables for offline search
            try {
                ftsPopulator.rebuildAll()
            } catch (e: Exception) {
                logger.warn(e) { "FTS rebuild failed, offline search may be incomplete" }
            }

            // Phase 6: Connect to SSE stream for real-time updates
            sseManager.connect()

            logger.info { "Sync completed successfully" }
            _syncState.value = SyncStatus.Success(timestamp = now)
            Result.Success(Unit)
        } catch (e: CancellationException) {
            logger.debug { "Sync was cancelled" }
            _syncState.value = SyncStatus.Idle
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Sync failed after retries" }
            _syncState.value = SyncStatus.Error(exception = e)

            if (coordinator.isServerUnreachableError(e)) {
                logger.warn { "Server unreachable - continuing with local data" }
            }

            Result.Failure(exception = e, message = "Sync failed: ${e.message}")
        }
    }

    /**
     * Force a full sync by clearing the sync checkpoint and re-syncing.
     */
    suspend fun forceFullSync(): Result<Unit> {
        logger.info { "Forcing full sync - clearing sync checkpoint" }
        syncDao.clearLastSyncTime()
        return sync()
    }

    /**
     * Handle library mismatch by clearing local data and resyncing.
     *
     * Called when the user confirms they want to discard local data
     * and resync with the server's new library.
     *
     * @param newLibraryId The new library ID to sync with
     */
    override suspend fun resetForNewLibrary(newLibraryId: String): Result<Unit> {
        logger.info { "Resetting for new library: $newLibraryId" }
        _syncState.value = SyncStatus.Syncing

        try {
            // Clear all local library data and store new library ID
            libraryResetHelper.resetForNewLibrary(newLibraryId)

            // Perform fresh sync
            return sync()
        } catch (e: Exception) {
            logger.error(e) { "Failed to reset for new library" }
            _syncState.value = SyncStatus.Error(exception = e)
            return Result.Failure(exception = e, message = "Failed to reset library: ${e.message}")
        }
    }

    /**
     * Queue an entity for synchronization with server.
     */
    suspend fun queueUpdate(entity: Syncable) {
        // TODO: Implement via PendingOperationRepository
        logger.debug { "Queued update for entity: $entity" }
    }

    /**
     * Pull user preferences from server and cache locally.
     */
    private suspend fun pullUserPreferences() {
        try {
            logger.debug { "Pulling user preferences from server" }
            when (val result = userPreferencesApi.getPreferences()) {
                is Result.Success -> {
                    val prefs = result.data
                    settingsRepository.setDefaultPlaybackSpeed(prefs.defaultPlaybackSpeed)
                    logger.info { "User preferences synced: defaultPlaybackSpeed=${prefs.defaultPlaybackSpeed}" }
                }

                is Result.Failure -> {
                    logger.warn { "Failed to fetch user preferences: ${result.exception.message}" }
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "User preferences sync failed, using cached values" }
        }
    }

    /**
     * Internal function to update sync state.
     * Called by PullSyncOrchestrator for progress updates.
     */
    internal fun updateSyncState(status: SyncStatus) {
        _syncState.value = status
    }

    /**
     * Verify that the server's library ID matches what we're synced with.
     *
     * @return LibraryMismatch status if IDs don't match, null if verification passes
     */
    private suspend fun verifyLibraryIdentity(): SyncStatus.LibraryMismatch? {
        val storedLibraryId = settingsRepository.getConnectedLibraryId()

        // First sync? Fetch and store the library ID, then continue
        if (storedLibraryId == null) {
            logger.info { "First sync - fetching library ID from server" }
            val instance = instanceRepository.getInstance(forceRefresh = true).getOrNull()
            if (instance != null) {
                settingsRepository.setConnectedLibraryId(instance.id.value)
                logger.info { "Stored library ID: ${instance.id.value}" }
            }
            return null
        }

        // Compare with server's current library ID
        val instance = instanceRepository.getInstance(forceRefresh = true).getOrNull()
        if (instance == null) {
            // Can't reach server - allow sync to proceed with cached data
            logger.warn { "Could not fetch instance for library verification, proceeding with cached data" }
            return null
        }

        val serverLibraryId = instance.id.value
        if (serverLibraryId != storedLibraryId) {
            // Library mismatch! Check if there are pending local changes
            val hasPendingChanges = pendingOperationDao.getOldestPending() != null

            return SyncStatus.LibraryMismatch(
                expectedLibraryId = storedLibraryId,
                actualLibraryId = serverLibraryId,
                hasPendingChanges = hasPendingChanges,
            )
        }

        return null
    }
}

/**
 * Exception thrown when the server's library ID doesn't match what the client was synced with.
 */
class LibraryMismatchException(
    val expectedLibraryId: String,
    val actualLibraryId: String,
) : Exception("Library mismatch: expected $expectedLibraryId, got $actualLibraryId")
