package com.calypsan.listenup.client.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.error.ErrorBus
import com.calypsan.listenup.client.domain.repository.AuthRepository
import com.calypsan.listenup.client.domain.repository.AuthSession
import com.calypsan.listenup.client.domain.repository.RegistrationStatusStream
import com.calypsan.listenup.client.domain.repository.StreamedRegistrationStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

private const val POLL_INTERVAL_MS = 5000L

/**
 * ViewModel for the pending approval screen.
 *
 * Handles real-time monitoring of registration approval status.
 * Uses SSE for instant updates with polling fallback.
 */
class PendingApprovalViewModel(
    private val authRepository: AuthRepository,
    private val authSession: AuthSession,
    private val registrationStatusStream: RegistrationStatusStream,
    val userId: String,
    val email: String,
    private val password: String,
) : ViewModel() {
    private val _state = MutableStateFlow<PendingApprovalUiState>(PendingApprovalUiState.Waiting)
    val state: StateFlow<PendingApprovalUiState> = _state.asStateFlow()

    private var sseJob: Job? = null
    private var pollingJob: Job? = null

    init {
        // Start SSE connection immediately
        connectToSSE()
    }

    override fun onCleared() {
        super.onCleared()
        sseJob?.cancel()
        pollingJob?.cancel()
    }

    /**
     * Connects to the registration status SSE stream.
     * Falls back to polling if SSE fails.
     */
    private fun connectToSSE() {
        sseJob?.cancel()
        sseJob =
            viewModelScope.launch {
                try {
                    registrationStatusStream.streamStatus(userId).collect { status ->
                        handleStatusUpdate(status)
                    }
                } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ErrorBus.emit(e)
                    logger.warn(e) { "SSE connection failed, falling back to polling" }
                    // Fall back to polling
                    startPolling()
                }
            }
    }

    private suspend fun handleStatusUpdate(status: StreamedRegistrationStatus) {
        when (status) {
            is StreamedRegistrationStatus.Approved -> {
                logger.info { "Registration approved via SSE!" }
                attemptAutoLogin()
            }

            is StreamedRegistrationStatus.Denied -> {
                logger.info { "Registration denied via SSE" }
                handleDenied(status.message)
            }

            is StreamedRegistrationStatus.Pending -> {
                // Still pending, update UI
                _state.value = PendingApprovalUiState.Waiting
            }
        }
    }

    /**
     * Falls back to polling when SSE isn't available.
     */
    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob =
            viewModelScope.launch {
                while (isActive) {
                    delay(POLL_INTERVAL_MS)
                    try {
                        val status = authRepository.checkRegistrationStatus(userId)
                        logger.debug { "Poll result: status=${status.status}, approved=${status.approved}" }

                        when {
                            status.approved -> {
                                logger.info { "Registration approved via polling!" }
                                attemptAutoLogin()
                                break
                            }

                            status.status == "denied" -> {
                                logger.info { "Registration denied via polling" }
                                handleDenied()
                                break
                            }
                        }
                    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        ErrorBus.emit(e)
                        logger.warn(e) { "Failed to check registration status" }
                        // Continue polling
                    }
                }
            }
    }

    /**
     * Attempts to log in automatically after approval.
     */
    private suspend fun attemptAutoLogin() {
        _state.value = PendingApprovalUiState.LoggingIn

        try {
            val loginResult = authRepository.login(email, password)

            // Save tokens
            authSession.saveAuthTokens(
                access = loginResult.accessToken,
                refresh = loginResult.refreshToken,
                sessionId = loginResult.sessionId,
                userId = loginResult.userId,
            )

            // Clear pending registration
            authSession.clearPendingRegistration()

            logger.info { "Auto-login successful!" }
            _state.value = PendingApprovalUiState.LoginSuccess
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            ErrorBus.emit(e)
            logger.error(e) { "Auto-login failed" }
            // Clear pending and let user log in manually
            authSession.clearPendingRegistration()
            _state.value =
                PendingApprovalUiState.ApprovedManualLogin(
                    "Your account has been approved! Please log in with your credentials.",
                )
        }
    }

    private suspend fun handleDenied(message: String? = null) {
        authSession.clearPendingRegistration()
        _state.value =
            PendingApprovalUiState.Denied(
                message ?: "Your registration was denied by an administrator.",
            )
    }

    /**
     * Cancel registration and return to login.
     */
    fun cancelRegistration() {
        viewModelScope.launch {
            authSession.clearPendingRegistration()
        }
    }
}
