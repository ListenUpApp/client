package com.calypsan.listenup.client.presentation.connect

/**
 * User actions on server connection screen.
 *
 * Event-driven architecture pattern:
 * - UI dispatches events to ViewModel
 * - ViewModel processes events and updates state
 * - UI reacts to state changes
 *
 * Benefits:
 * - Clear separation between user intent and business logic
 * - Easy to test (just verify state transitions for each event)
 * - Audit trail of user actions for debugging
 * - Can replay events for testing/debugging
 */
sealed interface ServerConnectUiEvent {
    /**
     * User modified the server URL text field.
     *
     * Triggers:
     * - Update serverUrl in state
     * - Clear any existing error (fresh start)
     * - No network call (just local state update)
     *
     * @property newUrl The new URL value from text field
     */
    data class UrlChanged(
        val newUrl: String,
    ) : ServerConnectUiEvent

    /**
     * User clicked the Connect button.
     *
     * Triggers:
     * - Local URL validation (format, localhost on device)
     * - If valid: Network verification (/api/v1/instance)
     * - Update state with loading/error/success
     * - On success: Save to ServerConfig and navigate
     */
    data object ConnectClicked : ServerConnectUiEvent
}
