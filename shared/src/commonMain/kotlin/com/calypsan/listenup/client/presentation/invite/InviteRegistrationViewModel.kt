@file:Suppress("MagicNumber")

package com.calypsan.listenup.client.presentation.invite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.ServerUrl
import com.calypsan.listenup.client.domain.repository.AuthSession
import com.calypsan.listenup.client.domain.repository.InviteRepository
import com.calypsan.listenup.client.domain.repository.ServerConfig
import com.calypsan.listenup.client.domain.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the invite registration screen.
 *
 * Handles:
 * - Loading invite details from server
 * - Validating password input
 * - Submitting registration and storing auth tokens
 *
 * On successful registration, stores tokens which triggers AuthState.Authenticated,
 * causing automatic navigation to the Library screen.
 */
class InviteRegistrationViewModel(
    private val inviteRepository: InviteRepository,
    private val serverConfig: ServerConfig,
    private val authSession: AuthSession,
    private val userRepository: UserRepository,
    private val serverUrl: String,
    private val inviteCode: String,
) : ViewModel() {
    val state: StateFlow<InviteRegistrationUiState>
        field = MutableStateFlow(InviteRegistrationUiState())

    init {
        loadInviteDetails()
    }

    /**
     * Load invite details from the server.
     */
    fun loadInviteDetails() {
        viewModelScope.launch {
            state.value = InviteRegistrationUiState(loadingState = InviteLoadingState.Loading)

            try {
                val details = inviteRepository.getInviteDetails(serverUrl, inviteCode)

                if (!details.valid) {
                    state.value =
                        InviteRegistrationUiState(
                            loadingState =
                                InviteLoadingState.Invalid(
                                    "This invite is no longer valid. It may have already been used or expired.",
                                ),
                        )
                } else {
                    state.value =
                        InviteRegistrationUiState(
                            loadingState = InviteLoadingState.Loaded(details),
                        )
                }
            } catch (e: Exception) {
                val message =
                    when {
                        e.message?.contains("404") == true ||
                            e.message?.contains("not found", ignoreCase = true) == true -> {
                            "Invite not found. Please check the link and try again."
                        }

                        e.message?.contains("connection", ignoreCase = true) == true ||
                            e.message?.contains("network", ignoreCase = true) == true -> {
                            "Could not connect to server. Please check your internet connection."
                        }

                        else -> {
                            e.message ?: "Failed to load invite details"
                        }
                    }
                state.value =
                    InviteRegistrationUiState(
                        loadingState = InviteLoadingState.Error(message),
                    )
            }
        }
    }

    /**
     * Submit the registration with the provided password.
     *
     * @param password The user's chosen password
     * @param confirmPassword The password confirmation (must match)
     */
    fun submitRegistration(
        password: String,
        confirmPassword: String,
    ) {
        // Validate passwords
        if (password.length < 8) {
            state.value =
                state.value.copy(
                    submissionStatus =
                        InviteSubmissionStatus.Error(
                            InviteErrorType.ValidationError(InviteField.PASSWORD),
                        ),
                )
            return
        }

        if (password != confirmPassword) {
            state.value =
                state.value.copy(
                    submissionStatus = InviteSubmissionStatus.Error(InviteErrorType.PasswordMismatch),
                )
            return
        }

        viewModelScope.launch {
            state.value = state.value.copy(submissionStatus = InviteSubmissionStatus.Submitting)

            try {
                val result = inviteRepository.claimInvite(serverUrl, inviteCode, password)

                // Save the server URL first (before tokens, as it's needed for auth state)
                serverConfig.setServerUrl(ServerUrl(serverUrl))

                // Store auth tokens - this triggers AuthState.Authenticated
                authSession.saveAuthTokens(
                    access = result.accessToken,
                    refresh = result.refreshToken,
                    sessionId = result.sessionId,
                    userId = result.userId,
                )

                // Save user data to local database for avatar display
                userRepository.saveUser(result.user)

                state.value = state.value.copy(submissionStatus = InviteSubmissionStatus.Success)
            } catch (e: Exception) {
                val errorType = e.toInviteErrorType()
                state.value =
                    state.value.copy(
                        submissionStatus = InviteSubmissionStatus.Error(errorType),
                    )
            }
        }
    }

    /**
     * Clear the error state to allow retry.
     */
    fun clearError() {
        if (state.value.submissionStatus is InviteSubmissionStatus.Error) {
            state.value = state.value.copy(submissionStatus = InviteSubmissionStatus.Idle)
        }
    }
}

/**
 * Convert exception to semantic error type.
 */
@Suppress("CyclomaticComplexMethod")
private fun Exception.toInviteErrorType(): InviteErrorType {
    val msg = message?.lowercase() ?: ""
    val causeMsg = cause?.message?.lowercase() ?: ""
    val fullMsg = "$msg $causeMsg"

    return when {
        // Invite-specific errors
        msg.contains("already claimed") ||
            msg.contains("claimed") ||
            msg.contains("expired") ||
            (msg.contains("invalid") && msg.contains("invite")) -> {
            InviteErrorType.InviteInvalid
        }

        // Connection errors
        fullMsg.contains("connection refused") ||
            fullMsg.contains("econnrefused") ||
            fullMsg.contains("timeout") ||
            fullMsg.contains("timed out") ||
            fullMsg.contains("unable to resolve host") ||
            fullMsg.contains("unknown host") -> {
            InviteErrorType.NetworkError(cause?.message ?: message)
        }

        // Generic network issues
        fullMsg.contains("network") ||
            fullMsg.contains("connect") ||
            fullMsg.contains("socket") -> {
            InviteErrorType.NetworkError(cause?.message ?: message)
        }

        // Server errors
        fullMsg.contains("500") ||
            fullMsg.contains("502") ||
            fullMsg.contains("503") ||
            fullMsg.contains("504") -> {
            InviteErrorType.ServerError("Server error. Please try again later.")
        }

        // Unknown
        else -> {
            InviteErrorType.ServerError(message ?: "Unknown error")
        }
    }
}

