package com.calypsan.listenup.client.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.domain.usecase.auth.RegisterUseCase
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for the registration screen.
 *
 * Thin coordinator that:
 * - Manages UI state (Loading, Success, Error)
 * - Delegates business logic to RegisterUseCase
 * - Maps use case results to UI states
 *
 * After successful registration:
 * 1. Use case saves pending registration state to secure storage
 * 2. AuthState transitions to PendingApproval
 * 3. Navigation shows PendingApprovalScreen
 * 4. PendingApprovalScreen handles SSE/polling and auto-login
 */
class RegisterViewModel(
    private val registerUseCase: RegisterUseCase,
) : ViewModel() {
    val state: StateFlow<RegisterUiState>
        field = MutableStateFlow(RegisterUiState())

    /**
     * Submit the registration form.
     *
     * Delegates validation and API calls to RegisterUseCase.
     * Maps results to appropriate UI states.
     */
    fun onRegisterSubmit(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
    ) {
        viewModelScope.launch {
            state.value = state.value.copy(status = RegisterStatus.Loading)

            when (val result = registerUseCase(email, password, firstName, lastName)) {
                is Success -> {
                    logger.info { "Registration successful, userId: ${result.data.userId}" }
                    state.value = state.value.copy(status = RegisterStatus.Success)
                }

                is Failure -> {
                    logger.error(result.exception) { "Registration failed" }
                    state.value = state.value.copy(
                        status = RegisterStatus.Error(result.message),
                    )
                }
            }
        }
    }

    /**
     * Clear the error state to allow retry.
     */
    fun clearError() {
        state.value = state.value.copy(status = RegisterStatus.Idle)
    }
}

/**
 * UI state for the registration screen.
 */
data class RegisterUiState(
    val status: RegisterStatus = RegisterStatus.Idle,
)

/**
 * Registration status.
 */
sealed interface RegisterStatus {
    data object Idle : RegisterStatus

    data object Loading : RegisterStatus

    /**
     * Registration submitted successfully.
     * AuthState has been updated to PendingApproval.
     * Navigation will automatically show PendingApprovalScreen.
     */
    data object Success : RegisterStatus

    data class Error(
        val message: String,
    ) : RegisterStatus
}
