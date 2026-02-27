package com.calypsan.listenup.client.domain.usecase.auth

import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.suspendRunCatching
import com.calypsan.listenup.client.domain.repository.AuthRepository
import com.calypsan.listenup.client.domain.repository.AuthSession
import com.calypsan.listenup.client.domain.repository.UserRepository
import com.calypsan.listenup.client.data.sync.sse.PlaybackStateProvider
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Use case for user logout.
 *
 * Encapsulates all business logic for logout:
 * - Invalidates session on server (best-effort, continues on failure)
 * - Clears local auth tokens
 * - Clears local user data
 *
 * The logout operation is designed to always succeed locally, even if
 * the server cannot be reached. This ensures users can always log out,
 * which is important for security and UX.
 *
 * Follows the operator invoke pattern for clean call-site syntax:
 * ```kotlin
 * logoutUseCase()
 * navigateToLogin()
 * ```
 */
open class LogoutUseCase(
    private val authRepository: AuthRepository,
    private val authSession: AuthSession,
    private val userRepository: UserRepository,
    private val playbackStateProvider: PlaybackStateProvider? = null,
) {
    /**
     * Execute logout.
     *
     * Server session invalidation is best-effort - if it fails,
     * we still clear local data and return success.
     *
     * @return Result<Unit> - always Success unless local operations fail
     */
    open suspend operator fun invoke(): Result<Unit> =
        suspendRunCatching {
            // Get session ID for server invalidation
            val sessionId = authSession.getSessionId()

            // Try to invalidate on server (best-effort)
            if (sessionId != null) {
                try {
                    authRepository.logout(sessionId)
                    logger.info { "Server session invalidated" }
                } catch (e: Exception) {
                    // Log but don't fail - local logout should still work
                    logger.warn(e) { "Failed to invalidate server session, continuing with local logout" }
                }
            }

            // Stop any active playback before clearing auth
            playbackStateProvider?.clearPlayback()

            // Clear local auth tokens - this triggers AuthState change
            authSession.clearAuthTokens()

            // Clear local user data
            userRepository.clearUsers()

            logger.info { "Local logout completed" }
        }

    /**
     * Perform local-only logout without server communication.
     *
     * Use this when you know the server is unreachable or when
     * tokens are already invalid.
     *
     * @return Result<Unit> - Success after clearing local state
     */
    open suspend fun logoutLocally(): Result<Unit> =
        suspendRunCatching {
            playbackStateProvider?.clearPlayback()
            authSession.clearAuthTokens()
            userRepository.clearUsers()
            logger.info { "Local-only logout completed" }
        }
}
