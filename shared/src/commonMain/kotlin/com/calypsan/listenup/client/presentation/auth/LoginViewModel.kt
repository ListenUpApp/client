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
    private val errorMapper: LoginErrorMapper = DefaultLoginErrorMapper(),
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
        val exception = failure.exception
        val message = failure.message

        // Check for validation errors from use case (exception might be null for validation errors)
        if (exception is IllegalArgumentException || failure.errorCode == com.calypsan.listenup.client.core.ErrorCode.VALIDATION_ERROR) {
            return when {
                message.contains("email", ignoreCase = true) ->
                    LoginErrorType.ValidationError(LoginField.EMAIL)

                message.contains("password", ignoreCase = true) ->
                    LoginErrorType.ValidationError(LoginField.PASSWORD)

                else -> LoginErrorType.ServerError(message)
            }
        }

        // Use error mapper for network/server exceptions
        return exception?.let { errorMapper.map(it) } ?: LoginErrorType.ServerError(message)
    }
}
