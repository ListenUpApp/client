package com.calypsan.listenup.client.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.data.remote.AuthApiContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the registration screen.
 *
 * Handles new user registration when open registration is enabled.
 * Users are created with pending status and must wait for admin approval.
 */
class RegisterViewModel(
    private val authApi: AuthApiContract,
) : ViewModel() {
    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun onRegisterSubmit(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
    ) {
        // Validate inputs
        if (email.isBlank()) {
            _state.value = _state.value.copy(status = RegisterStatus.Error("Email is required"))
            return
        }
        if (password.length < 8) {
            _state.value =
                _state.value.copy(status = RegisterStatus.Error("Password must be at least 8 characters"))
            return
        }
        if (firstName.isBlank()) {
            _state.value = _state.value.copy(status = RegisterStatus.Error("First name is required"))
            return
        }
        if (lastName.isBlank()) {
            _state.value = _state.value.copy(status = RegisterStatus.Error("Last name is required"))
            return
        }

        _state.value = _state.value.copy(status = RegisterStatus.Loading)

        viewModelScope.launch {
            try {
                val response = authApi.register(email, password, firstName, lastName)
                _state.value =
                    _state.value.copy(
                        status = RegisterStatus.Success(response.message),
                    )
            } catch (e: Exception) {
                val message = e.message ?: "Registration failed. Please try again."
                _state.value = _state.value.copy(status = RegisterStatus.Error(message))
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(status = RegisterStatus.Idle)
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

    data class Success(
        val message: String,
    ) : RegisterStatus

    data class Error(
        val message: String,
    ) : RegisterStatus
}
