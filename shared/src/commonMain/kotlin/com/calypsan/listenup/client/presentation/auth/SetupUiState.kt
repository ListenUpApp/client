package com.calypsan.listenup.client.presentation.auth

/**
 * UI state for the root user setup screen.
 *
 * This screen is shown when the server has no users configured yet.
 * The user creates the initial admin account.
 */
data class SetupUiState(
    val status: SetupStatus = SetupStatus.Idle,
)

/**
 * Status of the setup operation.
 */
sealed interface SetupStatus {
    /**
     * Initial state - ready for user input.
     */
    data object Idle : SetupStatus

    /**
     * Setup request in progress.
     */
    data object Loading : SetupStatus

    /**
     * Setup completed successfully.
     * Navigation will happen automatically via AuthState change.
     */
    data object Success : SetupStatus

    /**
     * Setup failed with an error.
     */
    data class Error(
        val type: SetupErrorType,
    ) : SetupStatus
}

/**
 * Types of errors that can occur during setup.
 * These are semantic error types - platforms decide how to display them.
 */
sealed interface SetupErrorType {
    /**
     * Network connection error.
     */
    data object NetworkError : SetupErrorType

    /**
     * Server returned an error (500, etc).
     */
    data object ServerError : SetupErrorType

    /**
     * Client-side validation error on a specific field.
     */
    data class ValidationError(
        val field: SetupField,
    ) : SetupErrorType

    /**
     * Server is already configured (shouldn't happen in normal flow).
     */
    data object AlreadyConfigured : SetupErrorType
}

/**
 * Fields in the setup form.
 */
enum class SetupField {
    FIRST_NAME,
    LAST_NAME,
    EMAIL,
    PASSWORD,
    PASSWORD_CONFIRM,
}
