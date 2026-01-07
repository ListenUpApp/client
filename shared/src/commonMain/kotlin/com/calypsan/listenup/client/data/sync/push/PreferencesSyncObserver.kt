package com.calypsan.listenup.client.data.sync.push

import com.calypsan.listenup.client.data.local.db.OperationType
import com.calypsan.listenup.client.domain.repository.PreferenceChangeEvent
import com.calypsan.listenup.client.domain.repository.PlaybackPreferences
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * Observes preference changes from SettingsRepository and queues sync operations.
 *
 * This class breaks the circular dependency between SettingsRepository and the sync layer:
 * - SettingsRepository emits events when preferences change (no sync dependencies)
 * - This observer listens to those events and queues operations (depends on sync layer)
 *
 * The observer is started once at app initialization and runs for the app's lifetime.
 */
class PreferencesSyncObserver(
    private val playbackPreferences: PlaybackPreferences,
    private val pendingOperationRepository: PendingOperationRepositoryContract,
    private val userPreferencesHandler: UserPreferencesHandler,
) {
    /**
     * Start observing preference changes.
     * Call this once at app startup with the application-scoped CoroutineScope.
     */
    fun start(scope: CoroutineScope) {
        scope.launch {
            playbackPreferences.preferenceChanges.collect { event ->
                handlePreferenceChange(event)
            }
        }
        logger.info { "PreferencesSyncObserver started" }
    }

    private suspend fun handlePreferenceChange(event: PreferenceChangeEvent) {
        when (event) {
            is PreferenceChangeEvent.PlaybackSpeedChanged -> {
                logger.debug { "Queueing playback speed change: ${event.speed}" }
                val payload = UserPreferencesPayload(defaultPlaybackSpeed = event.speed)
                pendingOperationRepository.queue(
                    type = OperationType.USER_PREFERENCES,
                    entityType = null, // Preferences are user-global, not entity-specific
                    entityId = null,
                    payload = payload,
                    handler = userPreferencesHandler,
                )
            }
        }
    }
}
