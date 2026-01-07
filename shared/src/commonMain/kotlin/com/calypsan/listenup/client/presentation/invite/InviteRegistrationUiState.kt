package com.calypsan.listenup.client.presentation.invite

import com.calypsan.listenup.client.domain.model.InviteDetails

/**
 * UI state for the invite registration screen.
 *
 * Holds both the loaded invite details and the current submission status.
 */
data class InviteRegistrationUiState(
    val loadingState: InviteLoadingState = InviteLoadingState.Loading,
    val submissionStatus: InviteSubmissionStatus = InviteSubmissionStatus.Idle,
)

/**
 * State of loading the invite details.
 */
sealed interface InviteLoadingState {
    /** Loading invite details from server */
    data object Loading : InviteLoadingState

    /** Invite details loaded successfully */
    data class Loaded(
        val details: InviteDetails,
    ) : InviteLoadingState

    /** Invite is no longer valid (claimed, expired, or not found) */
    data class Invalid(
        val reason: String,
    ) : InviteLoadingState

    /** Failed to load invite (network error) */
    data class Error(
        val message: String,
    ) : InviteLoadingState
}

/**
 * Status of the registration form submission.
 */
sealed interface InviteSubmissionStatus {
    /** Not yet submitted or ready for retry */
    data object Idle : InviteSubmissionStatus

    /** Submitting registration to server */
    data object Submitting : InviteSubmissionStatus

    /** Registration completed successfully */
    data object Success : InviteSubmissionStatus

    /** Submission failed with an error */
    data class Error(
        val type: InviteErrorType,
    ) : InviteSubmissionStatus
}

/**
 * Types of errors that can occur during invite registration.
 */
sealed interface InviteErrorType {
    /** Password doesn't meet requirements */
    data class ValidationError(
        val field: InviteField,
    ) : InviteErrorType

    /** Passwords don't match */
    data object PasswordMismatch : InviteErrorType

    /** Network connection error */
    data class NetworkError(
        val detail: String?,
    ) : InviteErrorType

    /** Server returned an error */
    data class ServerError(
        val detail: String?,
    ) : InviteErrorType

    /** Invite is no longer valid */
    data object InviteInvalid : InviteErrorType
}

/**
 * Fields in the invite registration form that can have validation errors.
 */
enum class InviteField {
    PASSWORD,
    CONFIRM_PASSWORD,
}
