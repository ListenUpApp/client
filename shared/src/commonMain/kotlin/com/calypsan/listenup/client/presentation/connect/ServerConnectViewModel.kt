package com.calypsan.listenup.client.presentation.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.PlatformUtils
import com.calypsan.listenup.client.core.ServerUrl
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.core.error.ErrorBus
import com.calypsan.listenup.client.core.error.ErrorMapper
import com.calypsan.listenup.client.core.error.ServerConnectError
import com.calypsan.listenup.client.core.error.UnknownError
import com.calypsan.listenup.client.domain.repository.InstanceRepository
import com.calypsan.listenup.client.domain.repository.ServerConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.URLParserException
import io.ktor.http.Url
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for server connection screen.
 *
 * Responsibilities:
 * - Manage server URL input state
 * - Validate URL format and accessibility
 * - Verify server is a ListenUp instance
 * - Save verified URL to ServerConfig
 * - Provide error feedback for user
 *
 * Event-driven architecture:
 * - UI dispatches events via onEvent()
 * - ViewModel updates state via StateFlow
 * - UI observes state and reacts to changes
 *
 * Two-phase verification:
 * 1. Local validation (format, localhost on device)
 * 2. Network verification via InstanceRepository.verifyServer()
 */
class ServerConnectViewModel(
    private val serverConfig: ServerConfig,
    private val instanceRepository: InstanceRepository,
) : ViewModel() {
    val state: StateFlow<ServerConnectUiState>
        field = MutableStateFlow(ServerConnectUiState())

    /**
     * Process user events from UI.
     *
     * All user interactions flow through this single entry point,
     * making the ViewModel's behavior predictable and testable.
     */
    fun onEvent(event: ServerConnectUiEvent) {
        when (event) {
            is ServerConnectUiEvent.UrlChanged -> handleUrlChanged(event.newUrl)
            is ServerConnectUiEvent.ConnectClicked -> handleConnectClicked()
        }
    }

    /**
     * Handle URL text field changes.
     * Clears error when user starts typing (fresh start).
     */
    private fun handleUrlChanged(newUrl: String) {
        state.update {
            it.copy(
                serverUrl = newUrl,
                error = null, // Clear error on new input
            )
        }
    }

    /**
     * Handle Connect button click.
     * Validates URL locally, then verifies server if valid.
     */
    private fun handleConnectClicked() {
        val url = state.value.serverUrl.trim()

        // Phase 1: Local validation (instant feedback)
        val validationError = validateUrl(url)
        if (validationError != null) {
            state.update { it.copy(error = validationError) }
            return
        }

        // Phase 2: Network verification (requires loading state)
        verifyServer(url)
    }

    /**
     * Validate URL format and accessibility.
     *
     * Checks performed (in order):
     * 1. URL not blank
     * 2. Valid URL syntax (protocol added automatically if missing)
     * 3. Not localhost on physical device
     *
     * @return ServerConnectError if invalid, null if valid
     */
    private fun validateUrl(url: String): ServerConnectError? {
        // Check 1: Not blank
        if (url.isBlank()) {
            return ServerConnectError.InvalidUrl("blank")
        }

        // Add protocol if missing for validation (will try https first in verifyServer)
        val urlWithProtocol =
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "https://$url"
            } else {
                url
            }

        // Check 2: Valid URL syntax
        try {
            Url(urlWithProtocol)
        } catch (e: URLParserException) {
            logger.debug(e) { "URL validation failed for: $url" }
            return ServerConnectError.InvalidUrl("malformed")
        }

        // Check 3: Localhost on physical device
        val isLocalhost = url.contains("localhost") || url.contains("127.0.0.1") || url.contains("0.0.0.0")
        if (isLocalhost && !PlatformUtils.isEmulator()) {
            return ServerConnectError.InvalidUrl("localhost_physical")
        }

        return null
    }

    /**
     * Verify server using the InstanceRepository.
     *
     * Delegates to repository which handles:
     * - Protocol detection (HTTPS first, HTTP fallback)
     * - Response parsing and validation
     *
     * On success:
     * - Saves verified URL to ServerConfig
     * - UI navigates to next screen (handled by UI layer observing success)
     *
     * On failure:
     * - Maps exception to user-friendly error
     * - Shows error in UI
     */
    private fun verifyServer(url: String) {
        viewModelScope.launch {
            state.update { it.copy(isLoading = true, error = null) }

            when (val result = instanceRepository.verifyServer(url)) {
                is Success -> {
                    // Save the verified URL (with successful protocol)
                    serverConfig.setServerUrl(ServerUrl(result.data.verifiedUrl))

                    // Set verified flag (UI will observe and navigate)
                    state.update { it.copy(isLoading = false, isVerified = true) }
                }

                is Failure -> {
                    val errorMessage = result.message.lowercase()

                    val error =
                        when {
                            errorMessage.contains("connection refused") ||
                                errorMessage.contains("failed to connect") -> {
                                ServerConnectError.ServerNotReachable(
                                    debugInfo = "Server not reachable at $url",
                                )
                            }

                            errorMessage.contains("serialization") ||
                                errorMessage.contains("parse") -> {
                                ServerConnectError.NotListenUpServer(
                                    debugInfo = "Failed to parse server response: ${result.message}",
                                )
                            }

                            else -> {
                                val appError =
                                    result.exception?.let { ErrorMapper.map(it) }
                                        ?: UnknownError(message = result.message, debugInfo = null)
                                ServerConnectError.VerificationFailed(appError)
                            }
                        }

                    state.update { it.copy(isLoading = false, error = error) }
                }
            }
        }
    }
}
