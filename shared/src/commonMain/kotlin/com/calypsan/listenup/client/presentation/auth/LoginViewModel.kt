package com.calypsan.listenup.client.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.domain.usecase.auth.LoginUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the login screen.
 *
 * Thin coordinator that:
 * - Manages UI state as a sealed [LoginUiState] hierarchy
 * - Delegates business logic to [LoginUseCase]
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
    private val _state = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

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
            _state.value = LoginUiState.Loading

            when (val result = loginUseCase(email, password)) {
                is Success -> _state.value = LoginUiState.Success
                is Failure -> _state.value = LoginUiState.Error(mapFailureToErrorType(result))
            }
        }
    }

    /** Clear the error state to allow retry. */
    fun clearError() {
        if (_state.value is LoginUiState.Error) {
            _state.value = LoginUiState.Idle
        }
    }

    /**
     * Map use case failure to UI error type.
     *
     * Handles both validation errors (from use case) and network/server errors.
     */
    private fun mapFailureToErrorType(failure: Failure): LoginErrorType {
        val message = failure.message

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
