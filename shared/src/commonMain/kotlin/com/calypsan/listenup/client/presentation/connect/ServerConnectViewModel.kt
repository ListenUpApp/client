package com.calypsan.listenup.client.presentation.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.ApiResponse
import com.calypsan.listenup.client.core.PlatformUtils
import com.calypsan.listenup.client.core.ServerUrl
import com.calypsan.listenup.client.core.error.ErrorMapper
import com.calypsan.listenup.client.core.error.ServerConnectError
import com.calypsan.listenup.client.data.repository.SettingsRepositoryContract
import com.calypsan.listenup.client.domain.model.Instance
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.URLParserException
import io.ktor.http.Url
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for server connection screen.
 *
 * Responsibilities:
 * - Manage server URL input state
 * - Validate URL format and accessibility
 * - Verify server is a ListenUp instance
 * - Save verified URL to SettingsRepository
 * - Provide error feedback for user
 *
 * Event-driven architecture:
 * - UI dispatches events via onEvent()
 * - ViewModel updates state via StateFlow
 * - UI observes state and reacts to changes
 *
 * Two-phase verification:
 * 1. Local validation (format, localhost on device)
 * 2. Network verification (fetch /api/v1/instance)
 */
class ServerConnectViewModel(
    private val settingsRepository: SettingsRepositoryContract,
) : ViewModel() {
    private val _state = MutableStateFlow(ServerConnectUiState())
    val state: StateFlow<ServerConnectUiState> = _state.asStateFlow()

    // Simple HTTP client for unauthenticated server verification
    // (separate from authenticated client used elsewhere in app)
    private val httpClient =
        HttpClient {
            install(ContentNegotiation) {
                json(
                    Json {
                        prettyPrint = false
                        isLenient = false
                        ignoreUnknownKeys = true
                    },
                )
            }
        }

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
        _state.update {
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
        val url = _state.value.serverUrl.trim()

        // Phase 1: Local validation (instant feedback)
        val validationError = validateUrl(url)
        if (validationError != null) {
            _state.update { it.copy(error = validationError) }
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
     * Normalize URL by adding protocol if missing.
     * Returns list of URLs to try (HTTPS first, then HTTP fallback).
     */
    private fun normalizeUrl(url: String): List<String> =
        if (url.startsWith("https://") || url.startsWith("http://")) {
            listOf(url)
        } else {
            listOf("https://$url", "http://$url")
        }

    /**
     * Verify server by fetching /api/v1/instance endpoint.
     *
     * Smart protocol detection:
     * - If user enters URL without protocol, tries HTTPS first, then HTTP
     * - If user specifies protocol, uses that protocol only
     *
     * Success criteria:
     * - HTTP request succeeds (200 OK)
     * - Response deserializes as Instance model
     *
     * On success:
     * - Saves URL to SettingsRepository (with successful protocol)
     * - UI navigates to next screen (handled by UI layer observing success)
     *
     * On failure:
     * - Maps exception to user-friendly error
     * - Shows error in UI
     */
    private fun verifyServer(url: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            val urlsToTry = normalizeUrl(url)
            var lastException: Exception? = null

            for ((index, currentUrl) in urlsToTry.withIndex()) {
                try {
                    // Fetch instance info from server (wrapped in ApiResponse envelope)
                    val instanceUrl = currentUrl.trimEnd('/') + "/api/v1/instance"
                    val httpResponse = httpClient.get(instanceUrl)

                    // Try to deserialize as ApiResponse<Instance>
                    val response =
                        try {
                            httpResponse.body<ApiResponse<Instance>>()
                        } catch (e: SerializationException) {
                            // Get raw response body for debugging
                            val rawBody =
                                try {
                                    httpResponse.body<String>()
                                } catch (_: Exception) {
                                    "Unable to read response body"
                                }

                            _state.update {
                                it.copy(
                                    isLoading = false,
                                    error =
                                        ServerConnectError.NotListenUpServer(
                                            debugInfo =
                                                """
                                                Failed to parse server response.

                                                Error: ${e.message}

                                                Raw response:
                                                $rawBody
                                                """.trimIndent(),
                                        ),
                                )
                            }
                            return@launch
                        } catch (e: IllegalArgumentException) {
                            // Field validation errors (e.g., blank InstanceId)
                            val rawBody =
                                try {
                                    httpResponse.body<String>()
                                } catch (_: Exception) {
                                    "Unable to read response body"
                                }

                            _state.update {
                                it.copy(
                                    isLoading = false,
                                    error =
                                        ServerConnectError.NotListenUpServer(
                                            debugInfo =
                                                """
                                                Invalid data in server response.

                                                Error: ${e.message}

                                                Raw response:
                                                $rawBody
                                                """.trimIndent(),
                                        ),
                                )
                            }
                            return@launch
                        }

                    // Verify response has data
                    val instance = response.data
                    if (instance == null) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                error =
                                    ServerConnectError.NotListenUpServer(
                                        debugInfo =
                                            """
                                            Server returned success but no instance data.

                                            Response: success=${response.success}, error=${response.error}, message=${response.message}
                                            """.trimIndent(),
                                    ),
                            )
                        }
                        return@launch
                    }

                    // Success! Save URL to repository
                    settingsRepository.setServerUrl(ServerUrl(currentUrl))

                    // Set verified flag (UI will observe and navigate)
                    _state.update { it.copy(isLoading = false, isVerified = true) }
                    return@launch
                } catch (e: Exception) {
                    val errorMessage = e.message?.lowercase() ?: ""

                    // Check if this is an SSL/TLS error and we have more URLs to try
                    val isSslError =
                        errorMessage.contains("ssl") ||
                            errorMessage.contains("tls") ||
                            errorMessage.contains("handshake")

                    if (isSslError && index < urlsToTry.size - 1) {
                        // SSL error and we have HTTP fallback to try - continue to next URL
                        lastException = e
                        continue
                    }

                    // No more URLs to try or different error type
                    lastException = e
                    break
                }
            }

            // All URLs failed - show error from last attempt
            lastException?.let { e ->
                val errorMessage = e.message?.lowercase() ?: ""
                val error =
                    if (errorMessage.contains("connection refused") ||
                        errorMessage.contains("failed to connect")
                    ) {
                        // Connection refused - server not running
                        ServerConnectError.ServerNotReachable(
                            debugInfo = "Server not reachable at ${urlsToTry.last()}",
                        )
                    } else {
                        // Generic network/server errors
                        val appError = ErrorMapper.map(e)
                        ServerConnectError.VerificationFailed(appError)
                    }

                _state.update { it.copy(isLoading = false, error = error) }
            }
        }
    }

    /**
     * Clean up resources when ViewModel is destroyed.
     */
    override fun onCleared() {
        super.onCleared()
        httpClient.close()
    }
}
