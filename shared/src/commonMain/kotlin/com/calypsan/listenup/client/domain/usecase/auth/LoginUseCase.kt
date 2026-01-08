package com.calypsan.listenup.client.domain.usecase.auth

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.suspendRunCatching
import com.calypsan.listenup.client.core.validationError
import com.calypsan.listenup.client.domain.model.User
import com.calypsan.listenup.client.domain.repository.AuthRepository
import com.calypsan.listenup.client.domain.repository.AuthSession
import com.calypsan.listenup.client.domain.repository.UserRepository

/**
 * Use case for user login.
 *
 * Encapsulates all business logic for authentication:
 * - Email validation (format, length)
 * - Password validation (non-empty)
 * - API call orchestration
 * - Token storage
 * - User data persistence
 *
 * The ViewModel becomes a thin coordinator that:
 * - Manages UI state (Loading, Success, Error)
 * - Delegates to this use case for business logic
 *
 * Follows the operator invoke pattern for clean call-site syntax:
 * ```kotlin
 * when (val result = loginUseCase(email, password)) {
 *     is Success -> navigateToHome()
 *     is Failure -> showError(result.message)
 * }
 * ```
 */
open class LoginUseCase(
    private val authRepository: AuthRepository,
    private val authSession: AuthSession,
    private val userRepository: UserRepository,
) {
    /**
     * Execute login with the provided credentials.
     *
     * @param email User's email address (will be trimmed)
     * @param password User's password
     * @return Result containing the logged-in User on success, or an error on failure
     */
    open suspend operator fun invoke(
        email: String,
        password: String,
    ): Result<User> {
        val trimmedEmail = email.trim()

        // Validate email format
        if (!isValidEmail(trimmedEmail)) {
            return validationError("Please enter a valid email address")
        }

        // Validate password is not empty
        if (password.isEmpty()) {
            return validationError("Password is required")
        }

        // Perform login
        return suspendRunCatching {
            val result =
                authRepository.login(
                    email = trimmedEmail,
                    password = password,
                )

            // Store tokens - this triggers AuthState.Authenticated
            authSession.saveAuthTokens(
                access = result.accessToken,
                refresh = result.refreshToken,
                sessionId = result.sessionId,
                userId = result.userId,
            )

            // Save user data to local database
            userRepository.saveUser(result.user)

            // Return domain user
            result.user
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

    private companion object {
        const val MAX_EMAIL_LENGTH = 254
        val EMAIL_REGEX = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")
    }
}
