package com.calypsan.listenup.client.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.data.remote.AuthApiContract
import com.calypsan.listenup.client.data.repository.SettingsRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for the registration screen.
 *
 * Handles new user registration when open registration is enabled.
 * After successful registration:
 * 1. Saves pending registration state to secure storage
 * 2. AuthState transitions to PendingApproval
 * 3. Navigation shows PendingApprovalScreen
 * 4. PendingApprovalScreen handles SSE/polling and auto-login
 */
class RegisterViewModel(
    private val authApi: AuthApiContract,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val state: StateFlow<RegisterUiState>
        field = MutableStateFlow(RegisterUiState())

    fun onRegisterSubmit(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
    ) {
        // Validate inputs
        if (email.isBlank()) {
            state.value = state.value.copy(status = RegisterStatus.Error("Email is required"))
            return
        }
        if (password.length < 8) {
            state.value =
                state.value.copy(status = RegisterStatus.Error("Password must be at least 8 characters"))
            return
        }
        if (firstName.isBlank()) {
            state.value = state.value.copy(status = RegisterStatus.Error("First name is required"))
            return
        }
        if (lastName.isBlank()) {
            state.value = state.value.copy(status = RegisterStatus.Error("Last name is required"))
            return
        }

        state.value = state.value.copy(status = RegisterStatus.Loading)

        viewModelScope.launch {
            try {
                val response = authApi.register(email, password, firstName, lastName)
                logger.info { "Registration successful, userId: ${response.userId}" }

                // Save pending registration state - this will:
                // 1. Persist credentials securely for auto-login after approval
                // 2. Update AuthState to PendingApproval
                // 3. Trigger navigation to PendingApprovalScreen
                settingsRepository.savePendingRegistration(
                    userId = response.userId,
                    email = email,
                    password = password,
                )

                // Mark as success - navigation will happen automatically via AuthState
                state.value = state.value.copy(status = RegisterStatus.Success)
            } catch (e: Exception) {
                val message = e.message ?: "Registration failed. Please try again."
                logger.error(e) { "Registration failed" }
                state.value = state.value.copy(status = RegisterStatus.Error(message))
            }
        }
    }

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
