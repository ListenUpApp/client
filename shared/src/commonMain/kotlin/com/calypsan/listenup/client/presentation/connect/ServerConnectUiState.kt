package com.calypsan.listenup.client.presentation.connect

import com.calypsan.listenup.client.core.error.ServerConnectError

/**
 * Immutable UI state for server connection screen.
 *
 * Represents complete screen state at any moment in time.
 * All state changes flow through ViewModel, ensuring predictable updates.
 *
 * Pattern: Single data class for entire screen state makes it easy to:
 * - Understand what the screen can display
 * - Test state transitions
 * - Implement time-travel debugging
 * - Restore state after process death
 */
data class ServerConnectUiState(
    /**
     * Current server URL input value.
     * Bound to text field, updated on every keystroke.
     */
    val serverUrl: String = "",

    /**
     * Whether server verification is in progress.
     * Shows loading indicator and disables input during verification.
     */
    val isLoading: Boolean = false,

    /**
     * Current error to display, if any.
     * Null means no error. Error cleared when user modifies URL.
     */
    val error: ServerConnectError? = null,

    /**
     * Whether server verification completed successfully.
     * When true, UI should navigate to next screen.
     */
    val isVerified: Boolean = false
) {
    /**
     * Whether the Connect button should be enabled.
     * Disabled when loading or URL is blank.
     * Computed property that recalculates whenever state changes.
     */
    val isConnectEnabled: Boolean
        get() = serverUrl.isNotBlank() && !isLoading
}
