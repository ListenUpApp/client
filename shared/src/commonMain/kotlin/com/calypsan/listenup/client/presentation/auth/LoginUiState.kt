package com.calypsan.listenup.client.presentation.auth

/**
 * UI state for the login screen.
 *
 * This screen is shown when the server has users but the app doesn't have valid tokens.
 */
data class LoginUiState(
    val status: LoginStatus = LoginStatus.Idle,
)

/**
 * Status of the login operation.
 */
sealed interface LoginStatus {
    /**
     * Initial state - ready for user input.
     */
    data object Idle : LoginStatus

    /**
     * Login request in progress.
     */
    data object Loading : LoginStatus

    /**
     * Login completed successfully.
     * Navigation will happen automatically via AuthState change.
     */
    data object Success : LoginStatus

    /**
     * Login failed with an error.
     */
    data class Error(val type: LoginErrorType) : LoginStatus
}

/**
 * Types of errors that can occur during login.
 * These are semantic error types - platforms decide how to display them.
 */
sealed interface LoginErrorType {
    /**
     * Invalid credentials (wrong email or password).
     */
    data object InvalidCredentials : LoginErrorType

    /**
     * Network connection error.
     */
    data object NetworkError : LoginErrorType

    /**
     * Server returned an error (500, etc).
     */
    data object ServerError : LoginErrorType

    /**
     * Client-side validation error on a specific field.
     */
    data class ValidationError(val field: LoginField) : LoginErrorType
}

/**
 * Fields in the login form.
 */
enum class LoginField {
    EMAIL,
    PASSWORD
}
