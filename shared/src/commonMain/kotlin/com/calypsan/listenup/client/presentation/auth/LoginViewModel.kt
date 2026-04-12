package com.calypsan.listenup.client.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.domain.usecase.auth.LoginUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the login screen.
 *
 * Thin coordinator that:
 * - Manages UI state (Loading, Success, Error)
 * - Delegates business logic to LoginUseCase
 * - Maps use case results to UI states
 *
 * On success, auth tokens are stored by the use case which triggers
 * AuthState.Authenticated, causing automatic navigation to Library.
 *
 * Note: Initial sync is handled by LibraryViewModel's intelligent auto-sync.
 */
class LoginViewModel(
    private val loginUseCase: LoginUseCase,
) : ViewModel() {
    val state: StateFlow<LoginUiState>
        field = MutableStateFlow(LoginUiState())

    /**
     * Submit the login form with user credentials.
     *
     * Delegates validation and API calls to LoginUseCase.
     * Maps results to appropriate UI states.
     */
    fun onLoginSubmit(
        email: String,
        password: String,
    ) {
        viewModelScope.launch {
            state.value = LoginUiState(status = LoginStatus.Loading)

            when (val result = loginUseCase(email, password)) {
                is Success -> {
                    state.value = LoginUiState(status = LoginStatus.Success)
                }

                is Failure -> {
                    val errorType = mapFailureToErrorType(result)
                    state.value = LoginUiState(status = LoginStatus.Error(errorType))
                }
            }
        }
    }

    /**
     * Clear the error state to allow retry.
     */
    fun clearError() {
        if (state.value.status is LoginStatus.Error) {
            state.value = LoginUiState(status = LoginStatus.Idle)
        }
    }

    /**
     * Map use case failure to UI error type.
     *
     * Handles both validation errors (from use case) and
     * network/server errors (via error mapper).
     */
    private fun mapFailureToErrorType(failure: Failure): LoginErrorType {
        val message = failure.message

        // Validation errors from the use case surface as DataError (AppError variant).
        if (failure.error is com.calypsan.listenup.client.core.error.DataError) {
            return when {
                message.contains("email", ignoreCase = true) -> {
                    LoginErrorType.ValidationError(LoginField.EMAIL)
                }

                message.contains("password", ignoreCase = true) -> {
                    LoginErrorType.ValidationError(LoginField.PASSWORD)
                }

                else -> {
                    LoginErrorType.ServerError(message)
                }
            }
        }

        // Network/server/auth errors flow through as the already-typed AppError. For
        // legacy Throwable flows that land as UnknownError, fall back to string-matching
        // on the message until callers start producing typed AppError variants directly.
        return when (failure.error) {
            is com.calypsan.listenup.client.core.error.NetworkError -> LoginErrorType.NetworkError(message)
            is com.calypsan.listenup.client.core.error.AuthError -> LoginErrorType.InvalidCredentials
            is com.calypsan.listenup.client.core.error.UnknownError -> classifyByMessage(message)
            else -> LoginErrorType.ServerError(message)
        }
    }

    private fun classifyByMessage(message: String): LoginErrorType {
        val lower = message.lowercase()
        return when {
            "invalid credentials" in lower -> {
                LoginErrorType.InvalidCredentials
            }

            "connection refused" in lower -> {
                LoginErrorType.NetworkError("Connection refused. Is the server running?")
            }

            "timed out" in lower -> {
                LoginErrorType.NetworkError("Connection timed out. Check server address.")
            }

            "unable to resolve host" in lower || "unknown host" in lower -> {
                LoginErrorType.NetworkError("Server not found. Check the address.")
            }

            "500" in lower -> {
                LoginErrorType.ServerError("Server error (500)")
            }

            "server error" in lower -> {
                LoginErrorType.ServerError(message)
            }

            else -> {
                LoginErrorType.ServerError(message)
            }
        }
    }
}
