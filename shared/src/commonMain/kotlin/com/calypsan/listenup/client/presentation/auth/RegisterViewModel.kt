package com.calypsan.listenup.client.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.domain.usecase.auth.RegisterUseCase
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for the registration screen.
 *
 * Thin coordinator that:
 * - Manages UI state as a sealed [RegisterUiState] hierarchy
 * - Delegates business logic to [RegisterUseCase]
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
    private val _state = MutableStateFlow<RegisterUiState>(RegisterUiState.Idle)
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

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
            _state.value = RegisterUiState.Loading

            when (val result = registerUseCase(email, password, firstName, lastName)) {
                is Success -> {
                    logger.info { "Registration successful, userId: ${result.data.userId}" }
                    _state.value = RegisterUiState.Success
                }

                is Failure -> {
                    logger.error { "Registration failed" }
                    _state.value = RegisterUiState.Error(result.message)
                }
            }
        }
    }

    /** Clear the error state to allow retry. */
    fun clearError() {
        _state.value = RegisterUiState.Idle
    }
}
