package com.calypsan.listenup.client.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.domain.usecase.admin.LoadServerSettingsUseCase
import com.calypsan.listenup.client.domain.repository.AdminRepository
import com.calypsan.listenup.client.domain.repository.InstanceRepository
import com.calypsan.listenup.client.domain.usecase.admin.UpdateServerSettingsUseCase
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for admin server settings screen.
 *
 * Manages server-wide settings like the server name and inbox workflow toggle.
 * When inbox is disabled with pending books, they are automatically
 * released with their staged collections.
 */
class AdminSettingsViewModel(
    private val loadServerSettingsUseCase: LoadServerSettingsUseCase,
    private val updateServerSettingsUseCase: UpdateServerSettingsUseCase,
    private val instanceRepository: InstanceRepository,
    private val adminRepository: AdminRepository,
) : ViewModel() {
    val state: StateFlow<AdminSettingsUiState>
        field = MutableStateFlow(AdminSettingsUiState())

    private var serverNameSaveJob: Job? = null
    private var remoteUrlSaveJob: Job? = null

    init {
        loadSettings()
    }

    fun loadSettings() {
        viewModelScope.launch {
            state.value = state.value.copy(isLoading = true, error = null)

            when (val result = loadServerSettingsUseCase()) {
                is Success -> {
                    state.value =
                        state.value.copy(
                            isLoading = false,
                            serverName = result.data.serverName,
                            inboxEnabled = result.data.inboxEnabled,
                            inboxCount = result.data.inboxCount,
                        )
                    // Also load remote URL from instance
                    loadRemoteUrl()
                }

                is Failure -> {
                    logger.error { "Failed to load server settings: ${result.message}" }
                    state.value =
                        state.value.copy(
                            isLoading = false,
                            error = result.message,
                        )
                }
            }
        }
    }

    /**
     * Update the server display name.
     * Updates local state immediately, debounces the API save.
     */
    fun setServerName(name: String) {
        state.value = state.value.copy(serverName = name)
        serverNameSaveJob?.cancel()
        serverNameSaveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            when (val result = updateServerSettingsUseCase.updateServerName(name)) {
                is Success -> {
                    logger.info { "Server name saved: ${result.data.serverName}" }
                }
                is Failure -> {
                    logger.error { "Failed to save server name: ${result.message}" }
                    state.value = state.value.copy(error = result.message)
                }
            }
        }
    }

    /**
     * Toggle the inbox workflow on/off.
     *
     * When disabling with pending books, shows confirmation first.
     * The server will auto-release books with their staged collections.
     */
    fun setInboxEnabled(enabled: Boolean) {
        // If disabling and there are books in inbox, show confirmation
        if (!enabled && state.value.inboxCount > 0) {
            state.value = state.value.copy(showDisableConfirmation = true)
            return
        }

        updateInboxEnabled(enabled)
    }

    /**
     * Confirm disabling inbox workflow.
     * Called after user confirms they want to release all pending books.
     */
    fun confirmDisableInbox() {
        state.value = state.value.copy(showDisableConfirmation = false)
        updateInboxEnabled(false)
    }

    /**
     * Cancel disabling inbox workflow.
     */
    fun cancelDisableInbox() {
        state.value = state.value.copy(showDisableConfirmation = false)
    }

    private fun updateInboxEnabled(enabled: Boolean) {
        viewModelScope.launch {
            state.value = state.value.copy(isSaving = true, error = null)

            when (val result = updateServerSettingsUseCase(enabled)) {
                is Success -> {
                    state.value =
                        state.value.copy(
                            isSaving = false,
                            inboxEnabled = result.data.inboxEnabled,
                            inboxCount = result.data.inboxCount,
                        )
                    logger.info { "Inbox workflow ${if (enabled) "enabled" else "disabled"}" }
                }

                is Failure -> {
                    logger.error { "Failed to update server settings: ${result.message}" }
                    state.value =
                        state.value.copy(
                            isSaving = false,
                            error = result.message,
                        )
                }
            }
        }
    }

    private fun loadRemoteUrl() {
        viewModelScope.launch {
            when (val result = instanceRepository.getInstance(forceRefresh = true)) {
                is Result.Success -> {
                    state.value = state.value.copy(remoteUrl = result.data.remoteUrl ?: "")
                }
                is Result.Failure -> {
                    // Non-fatal, just leave remote URL empty
                }
            }
        }
    }

    /**
     * Update the remote access URL.
     * Updates local state immediately, debounces the API save.
     */
    fun setRemoteUrl(url: String) {
        state.value = state.value.copy(remoteUrl = url)
        remoteUrlSaveJob?.cancel()
        remoteUrlSaveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            try {
                adminRepository.updateInstanceRemoteUrl(url)
                logger.info { "Remote URL saved: $url" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to save remote URL" }
                state.value = state.value.copy(error = "Failed to save remote URL: ${e.message}")
            }
        }
    }

    fun clearError() {
        state.value = state.value.copy(error = null)
    }

    companion object {
        private const val SAVE_DEBOUNCE_MS = 800L
    }
}

/**
 * UI state for admin settings screen.
 */
data class AdminSettingsUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val serverName: String = "",
    val remoteUrl: String = "",
    val inboxEnabled: Boolean = false,
    val inboxCount: Int = 0,
    val showDisableConfirmation: Boolean = false,
    val error: String? = null,
) {
    val hasPendingBooks: Boolean get() = inboxCount > 0
}
