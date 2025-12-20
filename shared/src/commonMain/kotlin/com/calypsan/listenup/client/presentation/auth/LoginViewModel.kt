@file:OptIn(ExperimentalTime::class)

package com.calypsan.listenup.client.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.AccessToken
import com.calypsan.listenup.client.core.RefreshToken
import com.calypsan.listenup.client.data.local.db.UserDao
import com.calypsan.listenup.client.data.local.db.UserEntity
import com.calypsan.listenup.client.data.remote.AuthApiContract
import com.calypsan.listenup.client.data.remote.AuthUser
import com.calypsan.listenup.client.data.repository.SettingsRepositoryContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    private val authApi: AuthApiContract,
    private val settingsRepository: SettingsRepositoryContract,
    private val userDao: UserDao,
) : ViewModel() {
    val state: StateFlow<LoginUiState>
        field = MutableStateFlow(LoginUiState())

    /**
     * Submit the login form with user credentials.
     *
     * Performs client-side validation before making the network request.
     * On success, stores tokens and navigation happens automatically via AuthState.
     */
    fun onLoginSubmit(
        email: String,
        password: String,
    ) {
        // Client-side validation
        val trimmedEmail = email.trim()

        if (!isValidEmail(trimmedEmail)) {
            state.value =
                LoginUiState(
                    status =
                        LoginStatus.Error(
                            LoginErrorType.ValidationError(LoginField.EMAIL),
                        ),
                )
            return
        }

        if (password.isEmpty()) {
            state.value =
                LoginUiState(
                    status =
                        LoginStatus.Error(
                            LoginErrorType.ValidationError(LoginField.PASSWORD),
                        ),
                )
            return
        }

        // Submit to server
        viewModelScope.launch {
            state.value = LoginUiState(status = LoginStatus.Loading)

            try {
                val response =
                    authApi.login(
                        email = trimmedEmail,
                        password = password,
                    )

                // Store tokens - this triggers AuthState.Authenticated
                // LibraryViewModel will detect authenticated state and trigger initial sync
                settingsRepository.saveAuthTokens(
                    access = AccessToken(response.accessToken),
                    refresh = RefreshToken(response.refreshToken),
                    sessionId = response.sessionId,
                    userId = response.userId,
                )

                // Save user data to local database for avatar display
                userDao.upsert(response.user.toEntity())

                state.value = LoginUiState(status = LoginStatus.Success)
            } catch (e: Exception) {
                state.value =
                    LoginUiState(
                        status = LoginStatus.Error(e.toLoginErrorType()),
                    )
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
     * Basic email validation.
     */
    private fun isValidEmail(email: String): Boolean = email.contains("@") && email.contains(".")
}

/**
 * Convert exception to semantic error type with helpful details.
 */
@Suppress("CyclomaticComplexMethod")
private fun Exception.toLoginErrorType(): LoginErrorType {
    val msg = message?.lowercase() ?: ""
    val causeMsg = cause?.message?.lowercase() ?: ""
    val fullMsg = "$msg $causeMsg"

    return when {
        // Authentication errors
        msg.contains("invalid credentials") ||
            msg.contains("unauthorized") ||
            msg.contains("401") -> {
            LoginErrorType.InvalidCredentials
        }

        // Connection refused - server not running or wrong port
        fullMsg.contains("connection refused") ||
            fullMsg.contains("econnrefused") -> {
            LoginErrorType.NetworkError("Connection refused. Is the server running?")
        }

        // Connection timeout
        fullMsg.contains("timeout") ||
            fullMsg.contains("timed out") -> {
            LoginErrorType.NetworkError("Connection timed out. Check server address.")
        }

        // Host not found
        fullMsg.contains("unable to resolve host") ||
            fullMsg.contains("unknown host") ||
            fullMsg.contains("no address associated") -> {
            LoginErrorType.NetworkError("Server not found. Check the address.")
        }

        // Generic network/connection issues
        fullMsg.contains("network") ||
            fullMsg.contains("connect") ||
            fullMsg.contains("socket") ||
            fullMsg.contains("ioexception") -> {
            LoginErrorType.NetworkError(cause?.message ?: message)
        }

        // HTTP errors
        fullMsg.contains("500") ||
            fullMsg.contains("502") ||
            fullMsg.contains("503") ||
            fullMsg.contains("504") -> {
            LoginErrorType.ServerError("Server error (${extractStatusCode(fullMsg)})")
        }

        // Unknown - include the actual error message
        else -> {
            LoginErrorType.ServerError(message ?: "Unknown error")
        }
    }
}

/**
 * Extract HTTP status code from error message if present.
 */
private fun extractStatusCode(msg: String): String {
    val codes = listOf("500", "502", "503", "504", "400", "403", "404")
    return codes.find { msg.contains(it) } ?: "unknown"
}

/**
 * Convert AuthUser from API response to UserEntity for local storage.
 */
@OptIn(ExperimentalTime::class)
private fun AuthUser.toEntity(): UserEntity =
    UserEntity(
        id = id,
        email = email,
        displayName = displayName,
        isRoot = isRoot,
        createdAt = Instant.parse(createdAt).toEpochMilliseconds(),
        updatedAt = Instant.parse(updatedAt).toEpochMilliseconds(),
    )
