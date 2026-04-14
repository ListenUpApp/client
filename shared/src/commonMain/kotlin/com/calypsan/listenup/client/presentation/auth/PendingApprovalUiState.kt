package com.calypsan.listenup.client.presentation.auth

/**
 * UI state for the pending approval screen.
 *
 * Sealed hierarchy — the screen is always in exactly one of these states.
 * Shown while waiting for admin approval after registration.
 */
sealed interface PendingApprovalUiState {
    /** Waiting for admin approval. */
    data object Waiting : PendingApprovalUiState

    /** Approved, logging in automatically. */
    data object LoggingIn : PendingApprovalUiState

    /** Login successful, will navigate to home. */
    data object LoginSuccess : PendingApprovalUiState

    /** Approved but auto-login failed. User should log in manually. */
    data class ApprovedManualLogin(
        val message: String,
    ) : PendingApprovalUiState

    /** Registration was denied. */
    data class Denied(
        val message: String,
    ) : PendingApprovalUiState
}
