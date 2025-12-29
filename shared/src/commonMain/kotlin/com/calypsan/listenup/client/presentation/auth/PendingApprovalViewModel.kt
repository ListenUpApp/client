package com.calypsan.listenup.client.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.AccessToken
import com.calypsan.listenup.client.core.RefreshToken
import com.calypsan.listenup.client.data.remote.ApiClientFactory
import com.calypsan.listenup.client.data.remote.AuthApiContract
import com.calypsan.listenup.client.data.repository.SettingsRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

private const val POLL_INTERVAL_MS = 5000L
private const val SSE_RECONNECT_DELAY_MS = 3000L

/**
 * ViewModel for the pending approval screen.
 *
 * Handles real-time monitoring of registration approval status.
 * Uses SSE for instant updates with polling fallback.
 */
class PendingApprovalViewModel(
    private val authApi: AuthApiContract,
    private val settingsRepository: SettingsRepository,
    private val apiClientFactory: ApiClientFactory,
    val userId: String,
    val email: String,
    private val password: String,
) : ViewModel() {
    private val _state = MutableStateFlow(PendingApprovalUiState())
    val state: StateFlow<PendingApprovalUiState> = _state.asStateFlow()

    private var sseJob: Job? = null
    private var pollingJob: Job? = null

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

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
        sseJob = viewModelScope.launch {
            try {
                streamRegistrationStatus()
            } catch (e: Exception) {
                logger.warn(e) { "SSE connection failed, falling back to polling" }
                // Fall back to polling
                startPolling()
            }
        }
    }

    private suspend fun streamRegistrationStatus() {
        val serverUrl = settingsRepository.getServerUrl()
            ?: error("Server URL not configured")

        // Use unauthenticated client for this endpoint
        val httpClient = apiClientFactory.getUnauthenticatedStreamingClient()
        val url = "$serverUrl/api/v1/auth/registration-status/$userId/stream"

        logger.info { "Connecting to registration status SSE: $url" }

        httpClient.prepareGet(url).execute { response ->
            logger.debug { "SSE connection established: ${response.status}" }

            val channel = response.bodyAsChannel()
            var eventData = StringBuilder()

            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break

                when {
                    line.isEmpty() -> {
                        // End of event
                        if (eventData.isNotEmpty()) {
                            processSSEEvent(eventData.toString())
                            eventData = StringBuilder()
                        }
                    }
                    line.startsWith("data: ") -> {
                        eventData.append(line.removePrefix("data: "))
                    }
                    line.startsWith("event: ") -> {
                        // Event type line, handled via data parsing
                    }
                }
            }
        }
    }

    private suspend fun processSSEEvent(eventJson: String) {
        try {
            logger.debug { "Processing SSE event: $eventJson" }

            val event = json.decodeFromString<RegistrationStatusEvent>(eventJson)

            when (event.status) {
                "approved" -> {
                    logger.info { "Registration approved via SSE!" }
                    attemptAutoLogin()
                }
                "denied" -> {
                    logger.info { "Registration denied via SSE" }
                    handleDenied()
                }
                "pending" -> {
                    // Still pending, update UI
                    _state.value = _state.value.copy(status = PendingApprovalStatus.Waiting)
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to parse SSE event" }
        }
    }

    /**
     * Falls back to polling when SSE isn't available.
     */
    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                try {
                    val status = authApi.checkRegistrationStatus(userId)
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
                } catch (e: Exception) {
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
        _state.value = _state.value.copy(status = PendingApprovalStatus.LoggingIn)

        try {
            val authResponse = authApi.login(email, password)

            // Save tokens
            settingsRepository.saveAuthTokens(
                access = AccessToken(authResponse.accessToken),
                refresh = RefreshToken(authResponse.refreshToken),
                sessionId = authResponse.sessionId,
                userId = authResponse.userId,
            )

            // Clear pending registration
            settingsRepository.clearPendingRegistration()

            logger.info { "Auto-login successful!" }
            _state.value = _state.value.copy(status = PendingApprovalStatus.LoginSuccess)
        } catch (e: Exception) {
            logger.error(e) { "Auto-login failed" }
            // Clear pending and let user log in manually
            settingsRepository.clearPendingRegistration()
            _state.value = _state.value.copy(
                status = PendingApprovalStatus.ApprovedManualLogin(
                    "Your account has been approved! Please log in with your credentials.",
                ),
            )
        }
    }

    private suspend fun handleDenied() {
        settingsRepository.clearPendingRegistration()
        _state.value = _state.value.copy(
            status = PendingApprovalStatus.Denied(
                "Your registration was denied by an administrator.",
            ),
        )
    }

    /**
     * Cancel registration and return to login.
     */
    fun cancelRegistration() {
        viewModelScope.launch {
            settingsRepository.clearPendingRegistration()
        }
    }
}

/**
 * UI state for the pending approval screen.
 */
data class PendingApprovalUiState(
    val status: PendingApprovalStatus = PendingApprovalStatus.Waiting,
)

/**
 * Status of the pending approval.
 */
sealed interface PendingApprovalStatus {
    /** Waiting for admin approval. */
    data object Waiting : PendingApprovalStatus

    /** Approved, logging in automatically. */
    data object LoggingIn : PendingApprovalStatus

    /** Login successful, will navigate to home. */
    data object LoginSuccess : PendingApprovalStatus

    /** Approved but auto-login failed. User should log in manually. */
    data class ApprovedManualLogin(val message: String) : PendingApprovalStatus

    /** Registration was denied. */
    data class Denied(val message: String) : PendingApprovalStatus
}

/**
 * SSE event for registration status.
 */
@Serializable
private data class RegistrationStatusEvent(
    val status: String,
    val timestamp: String? = null,
    val message: String? = null,
)
