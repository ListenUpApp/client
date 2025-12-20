package com.calypsan.listenup.client.data.sync

import com.calypsan.listenup.client.core.Result
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
    private val syncDao: SyncDao,
    private val ftsPopulator: FtsPopulatorContract,
    private val scope: CoroutineScope,
) : SyncManagerContract {
    // Override properties can't use explicit backing fields - must use traditional pattern
    private val _syncState = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    override val syncState: StateFlow<SyncStatus> = _syncState.asStateFlow()

    init {
        // Route SSE events to processor
        scope.launch {
            sseManager.eventFlow.collect { event ->
                sseEventProcessor.process(event)
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
            // Phase 1: Pull changes from server
            pullOrchestrator.pull { updateSyncState(it) }

            // Phase 2: Pull user preferences (non-blocking)
            pullUserPreferences()

            // Phase 3: Push local changes
            pushOrchestrator.flush()

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
}
