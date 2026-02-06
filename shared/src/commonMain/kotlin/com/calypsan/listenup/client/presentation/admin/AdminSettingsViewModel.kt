package com.calypsan.listenup.client.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.domain.usecase.admin.LoadServerSettingsUseCase
import com.calypsan.listenup.client.domain.usecase.admin.UpdateServerSettingsUseCase
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
) : ViewModel() {
    val state: StateFlow<AdminSettingsUiState>
        field = MutableStateFlow(AdminSettingsUiState())

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
     */
    fun setServerName(name: String) {
        viewModelScope.launch {
            state.value = state.value.copy(isSaving = true, error = null)

            when (val result = updateServerSettingsUseCase.updateServerName(name)) {
                is Success -> {
                    state.value =
                        state.value.copy(
                            isSaving = false,
                            serverName = result.data.serverName,
                        )
                    logger.info { "Server name updated to: ${result.data.serverName}" }
                }

                is Failure -> {
                    logger.error { "Failed to update server name: ${result.message}" }
                    state.value =
                        state.value.copy(
                            isSaving = false,
                            error = result.message,
                        )
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

    fun clearError() {
        state.value = state.value.copy(error = null)
    }
}

/**
 * UI state for admin settings screen.
 */
data class AdminSettingsUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val serverName: String = "",
    val inboxEnabled: Boolean = false,
    val inboxCount: Int = 0,
    val showDisableConfirmation: Boolean = false,
    val error: String? = null,
) {
    val hasPendingBooks: Boolean get() = inboxCount > 0
}
