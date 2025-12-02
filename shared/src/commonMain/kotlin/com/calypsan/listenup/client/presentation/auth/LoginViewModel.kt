@file:OptIn(ExperimentalTime::class)

package com.calypsan.listenup.client.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.AccessToken
import com.calypsan.listenup.client.core.RefreshToken
import com.calypsan.listenup.client.data.local.db.UserDao
import com.calypsan.listenup.client.data.local.db.UserEntity
import com.calypsan.listenup.client.data.remote.AuthApi
import com.calypsan.listenup.client.data.remote.AuthUser
import com.calypsan.listenup.client.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * ViewModel for the login screen.
 *
 * Handles validation and submission of user credentials.
 * On success, stores auth tokens which triggers AuthState.Authenticated,
 * causing automatic navigation to the Library screen.
 *
 * Note: Initial sync is handled by LibraryViewModel's intelligent auto-sync.
 */
class LoginViewModel(
    private val authApi: AuthApi,
    private val settingsRepository: SettingsRepository,
    private val userDao: UserDao
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    /**
     * Submit the login form with user credentials.
     *
     * Performs client-side validation before making the network request.
     * On success, stores tokens and navigation happens automatically via AuthState.
     */
    fun onLoginSubmit(email: String, password: String) {
        // Client-side validation
        val trimmedEmail = email.trim()

        if (!isValidEmail(trimmedEmail)) {
            _state.value = LoginUiState(
                status = LoginStatus.Error(
                    LoginErrorType.ValidationError(LoginField.EMAIL)
                )
            )
            return
        }

        if (password.isEmpty()) {
            _state.value = LoginUiState(
                status = LoginStatus.Error(
                    LoginErrorType.ValidationError(LoginField.PASSWORD)
                )
            )
            return
        }

        // Submit to server
        viewModelScope.launch {
            _state.value = LoginUiState(status = LoginStatus.Loading)

            try {
                val response = authApi.login(
                    email = trimmedEmail,
                    password = password
                )

                // Store tokens - this triggers AuthState.Authenticated
                // LibraryViewModel will detect authenticated state and trigger initial sync
                settingsRepository.saveAuthTokens(
                    access = AccessToken(response.accessToken),
                    refresh = RefreshToken(response.refreshToken),
                    sessionId = response.sessionId,
                    userId = response.userId
                )

                // Save user data to local database for avatar display
                userDao.upsert(response.user.toEntity())

                _state.value = LoginUiState(status = LoginStatus.Success)
            } catch (e: Exception) {
                _state.value = LoginUiState(
                    status = LoginStatus.Error(e.toLoginErrorType())
                )
            }
        }
    }

    /**
     * Clear the error state to allow retry.
     */
    fun clearError() {
        if (_state.value.status is LoginStatus.Error) {
            _state.value = LoginUiState(status = LoginStatus.Idle)
        }
    }

    /**
     * Basic email validation.
     */
    private fun isValidEmail(email: String): Boolean {
        return email.contains("@") && email.contains(".")
    }
}

/**
 * Convert exception to semantic error type.
 */
private fun Exception.toLoginErrorType(): LoginErrorType {
    return when {
        message?.contains("invalid credentials", ignoreCase = true) == true ||
                message?.contains("unauthorized", ignoreCase = true) == true ||
                message?.contains("401", ignoreCase = true) == true ->
            LoginErrorType.InvalidCredentials

        message?.contains("network", ignoreCase = true) == true ||
                message?.contains("connection", ignoreCase = true) == true ->
            LoginErrorType.NetworkError

        else -> LoginErrorType.ServerError
    }
}

/**
 * Convert AuthUser from API response to UserEntity for local storage.
 */
@OptIn(ExperimentalTime::class)
private fun AuthUser.toEntity(): UserEntity {
    return UserEntity(
        id = id,
        email = email,
        displayName = displayName,
        isRoot = isRoot,
        createdAt = Instant.parse(createdAt).toEpochMilliseconds(),
        updatedAt = Instant.parse(updatedAt).toEpochMilliseconds()
    )
}
