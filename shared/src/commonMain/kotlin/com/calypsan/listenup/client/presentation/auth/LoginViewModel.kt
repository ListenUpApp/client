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
    private val errorMapper: LoginErrorMapper = DefaultLoginErrorMapper(),
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
                        status = LoginStatus.Error(errorMapper.map(e)),
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
     * Email validation using a practical regex pattern.
     *
     * Validates:
     * - Has local part before @
     * - Has domain part after @
     * - Domain has at least one dot with TLD
     * - Reasonable length limit (RFC 5321)
     */
    private fun isValidEmail(email: String): Boolean {
        if (email.length > MAX_EMAIL_LENGTH) return false
        return EMAIL_REGEX.matches(email)
    }

    companion object {
        private const val MAX_EMAIL_LENGTH = 254
        private val EMAIL_REGEX = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")
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
        firstName = firstName.ifEmpty { null },
        lastName = lastName.ifEmpty { null },
        isRoot = isRoot,
        createdAt = Instant.parse(createdAt).toEpochMilliseconds(),
        updatedAt = Instant.parse(updatedAt).toEpochMilliseconds(),
        avatarType = avatarType,
        avatarValue = avatarValue,
        avatarColor = avatarColor,
    )
