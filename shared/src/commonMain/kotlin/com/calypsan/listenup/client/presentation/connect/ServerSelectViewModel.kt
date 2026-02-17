package com.calypsan.listenup.client.presentation.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.ServerUrl
import com.calypsan.listenup.client.core.error.ErrorBus
import com.calypsan.listenup.client.domain.model.DiscoveredServer
import com.calypsan.listenup.client.domain.model.ServerWithStatus
import com.calypsan.listenup.client.domain.repository.InstanceRepository
import com.calypsan.listenup.client.domain.repository.ServerConfig
import com.calypsan.listenup.client.domain.repository.ServerRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * UI state for server selection screen.
 */
data class ServerSelectUiState(
    val servers: List<ServerWithStatus> = emptyList(),
    val isDiscovering: Boolean = true,
    val selectedServerId: String? = null,
    val isConnecting: Boolean = false,
    val error: String? = null,
)

/**
 * Events from the server selection UI.
 */
sealed interface ServerSelectUiEvent {
    /** User selected a server from the list. */
    data class ServerSelected(
        val server: ServerWithStatus,
    ) : ServerSelectUiEvent

    /** User selected a discovered server not yet persisted. */
    data class DiscoveredServerSelected(
        val server: DiscoveredServer,
    ) : ServerSelectUiEvent

    /** User wants to enter server URL manually. */
    data object ManualEntryClicked : ServerSelectUiEvent

    /** User wants to refresh discovery. */
    data object RefreshClicked : ServerSelectUiEvent

    /** Error was dismissed. */
    data object ErrorDismissed : ServerSelectUiEvent
}

/**
 * ViewModel for server selection screen.
 *
 * Responsibilities:
 * - Start/stop mDNS discovery
 * - Observe discovered and persisted servers
 * - Handle server selection
 * - Activate selected server
 *
 * The screen shows servers discovered via mDNS on the local network,
 * merged with any previously connected servers (which may be offline).
 */
class ServerSelectViewModel(
    private val serverRepository: ServerRepository,
    private val serverConfig: ServerConfig,
    private val instanceRepository: InstanceRepository,
) : ViewModel() {
    val state: StateFlow<ServerSelectUiState>
        field = MutableStateFlow(ServerSelectUiState())

    val navigationEvents: StateFlow<NavigationEvent?>
        field = MutableStateFlow<NavigationEvent?>(null)

    /**
     * Navigation events that the UI should handle.
     */
    sealed interface NavigationEvent {
        /** Navigate to manual server entry screen. */
        data object GoToManualEntry : NavigationEvent

        /** Server was selected and activated - proceed to auth. */
        data object ServerActivated : NavigationEvent
    }

    init {
        startDiscovery()
        observeServers()
    }

    /**
     * Process user events from UI.
     */
    fun onEvent(event: ServerSelectUiEvent) {
        when (event) {
            is ServerSelectUiEvent.ServerSelected -> handleServerSelected(event.server)
            is ServerSelectUiEvent.DiscoveredServerSelected -> handleDiscoveredServerSelected(event.server)
            ServerSelectUiEvent.ManualEntryClicked -> handleManualEntryClicked()
            ServerSelectUiEvent.RefreshClicked -> handleRefreshClicked()
            ServerSelectUiEvent.ErrorDismissed -> state.update { it.copy(error = null) }
        }
    }

    /**
     * Clear navigation event after UI has handled it.
     */
    fun onNavigationHandled() {
        navigationEvents.value = null
    }

    private fun startDiscovery() {
        logger.info { "Starting server discovery" }
        serverRepository.startDiscovery()
        state.update { it.copy(isDiscovering = true) }
    }

    private fun observeServers() {
        viewModelScope.launch {
            serverRepository.observeServers().collect { servers ->
                logger.debug { "Received ${servers.size} servers from repository" }
                state.update {
                    it.copy(
                        servers = servers,
                        isDiscovering = false,
                    )
                }
            }
        }
    }

    private fun handleServerSelected(serverWithStatus: ServerWithStatus) {
        val server = serverWithStatus.server
        logger.info { "Server selected: ${server.name} (${server.id})" }

        // Get the best URL for this server
        val serverUrl = server.getBestUrl()
        if (serverUrl == null) {
            logger.error { "Server has no URL configured" }
            state.update { it.copy(error = "Server has no URL configured") }
            return
        }

        state.update {
            it.copy(
                selectedServerId = server.id,
                isConnecting = true,
                error = null,
            )
        }

        viewModelScope.launch {
            try {
                serverRepository.setActiveServer(server.id)
                // Also update ServerConfig to trigger AuthState change
                serverConfig.setServerUrl(ServerUrl(serverUrl))
                logger.info { "Server activated: ${server.id} at $serverUrl" }
                state.update { it.copy(isConnecting = false) }
                navigationEvents.value = NavigationEvent.ServerActivated
            } catch (e: Exception) {
                ErrorBus.emit(e)
                logger.error(e) { "Failed to activate server" }
                state.update {
                    it.copy(
                        isConnecting = false,
                        error = "Failed to connect: ${e.message}",
                    )
                }
            }
        }
    }

    private fun handleDiscoveredServerSelected(discovered: DiscoveredServer) {
        logger.info { "Discovered server selected: ${discovered.name} (${discovered.id})" }

        state.update {
            it.copy(
                selectedServerId = discovered.id,
                isConnecting = true,
                error = null,
            )
        }

        viewModelScope.launch {
            try {
                // Build list of URLs to try:
                // 1. Discovered LAN IP (from mDNS)
                // 2. Remote URL if advertised in TXT record
                val urlsToTry =
                    buildList {
                        add(discovered.localUrl)
                        discovered.remoteUrl?.let { add(it) }
                    }

                // Quick-check which URL is actually reachable (3s timeout each)
                val reachableUrl = instanceRepository.findReachableUrl(urlsToTry)

                if (reachableUrl != null) {
                    serverRepository.setActiveServer(discovered)
                    serverConfig.setServerUrl(ServerUrl(reachableUrl))
                    logger.info { "Server activated: ${discovered.id} at $reachableUrl" }
                    state.update { it.copy(isConnecting = false) }
                    navigationEvents.value = NavigationEvent.ServerActivated
                } else {
                    logger.warn { "Server discovered but not reachable at any URL: $urlsToTry" }
                    state.update {
                        it.copy(
                            isConnecting = false,
                            error =
                                "Server found on network but not reachable. " +
                                    "Try adding it manually with the server's IP address.",
                        )
                    }
                }
            } catch (e: Exception) {
                ErrorBus.emit(e)
                logger.error(e) { "Failed to activate discovered server" }
                state.update {
                    it.copy(
                        isConnecting = false,
                        error = "Failed to connect: ${e.message}",
                    )
                }
            }
        }
    }

    private fun handleManualEntryClicked() {
        logger.info { "Manual entry requested" }
        navigationEvents.value = NavigationEvent.GoToManualEntry
    }

    private fun handleRefreshClicked() {
        logger.info { "Refresh discovery requested" }
        serverRepository.stopDiscovery()
        state.update { it.copy(isDiscovering = true, servers = emptyList()) }
        serverRepository.startDiscovery()
    }

    override fun onCleared() {
        super.onCleared()
        logger.info { "Stopping server discovery" }
        serverRepository.stopDiscovery()
    }
}
