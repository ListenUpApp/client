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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * ViewModel for the root user setup screen.
 *
 * Handles validation and submission of the initial admin account creation.
 * On success, stores auth tokens which triggers AuthState.Authenticated,
 * causing automatic navigation to the Library screen.
 */
class SetupViewModel(
    private val authApi: AuthApiContract,
    private val settingsRepository: SettingsRepositoryContract,
    private val userDao: UserDao,
) : ViewModel() {
    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    /**
     * Submit the setup form to create the root user.
     *
     * Performs client-side validation before making the network request.
     * On success, stores tokens and navigation happens automatically via AuthState.
     */
    fun onSetupSubmit(
        firstName: String,
        lastName: String,
        email: String,
        password: String,
        passwordConfirm: String,
    ) {
        // Client-side validation
        val trimmedFirstName = firstName.trim()
        val trimmedLastName = lastName.trim()
        val trimmedEmail = email.trim()

        if (trimmedFirstName.isBlank()) {
            _state.value =
                SetupUiState(
                    status =
                        SetupStatus.Error(
                            SetupErrorType.ValidationError(SetupField.FIRST_NAME),
                        ),
                )
            return
        }

        if (trimmedLastName.isBlank()) {
            _state.value =
                SetupUiState(
                    status =
                        SetupStatus.Error(
                            SetupErrorType.ValidationError(SetupField.LAST_NAME),
                        ),
                )
            return
        }

        if (!isValidEmail(trimmedEmail)) {
            _state.value =
                SetupUiState(
                    status =
                        SetupStatus.Error(
                            SetupErrorType.ValidationError(SetupField.EMAIL),
                        ),
                )
            return
        }

        if (password.length < 8) {
            _state.value =
                SetupUiState(
                    status =
                        SetupStatus.Error(
                            SetupErrorType.ValidationError(SetupField.PASSWORD),
                        ),
                )
            return
        }

        if (password != passwordConfirm) {
            _state.value =
                SetupUiState(
                    status =
                        SetupStatus.Error(
                            SetupErrorType.ValidationError(SetupField.PASSWORD_CONFIRM),
                        ),
                )
            return
        }

        // Submit to server
        viewModelScope.launch {
            _state.value = SetupUiState(status = SetupStatus.Loading)

            try {
                val response =
                    authApi.setup(
                        email = trimmedEmail,
                        password = password,
                        firstName = trimmedFirstName,
                        lastName = trimmedLastName,
                    )

                // Store tokens - this triggers AuthState.Authenticated
                settingsRepository.saveAuthTokens(
                    access = AccessToken(response.accessToken),
                    refresh = RefreshToken(response.refreshToken),
                    sessionId = response.sessionId,
                    userId = response.userId,
                )

                // Save user data to local database for avatar display
                userDao.upsert(response.user.toEntity())

                _state.value = SetupUiState(status = SetupStatus.Success)
            } catch (e: Exception) {
                _state.value =
                    SetupUiState(
                        status = SetupStatus.Error(e.toSetupErrorType()),
                    )
            }
        }
    }

    /**
     * Clear the error state to allow retry.
     */
    fun clearError() {
        if (_state.value.status is SetupStatus.Error) {
            _state.value = SetupUiState(status = SetupStatus.Idle)
        }
    }

    /**
     * Basic email validation.
     */
    private fun isValidEmail(email: String): Boolean = email.contains("@") && email.contains(".")
}

/**
 * Convert exception to semantic error type.
 */
private fun Exception.toSetupErrorType(): SetupErrorType =
    when {
        message?.contains("already configured", ignoreCase = true) == true -> {
            SetupErrorType.AlreadyConfigured
        }

        message?.contains("network", ignoreCase = true) == true ||
            message?.contains("connection", ignoreCase = true) == true -> {
            SetupErrorType.NetworkError
        }

        else -> {
            SetupErrorType.ServerError
        }
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
