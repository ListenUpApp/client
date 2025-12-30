package com.calypsan.listenup.client.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.data.remote.AdminApiContract
import com.calypsan.listenup.client.data.remote.ServerSettingsRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for admin server settings screen.
 *
 * Manages server-wide settings like the inbox workflow toggle.
 * When inbox is disabled with pending books, they are automatically
 * released with their staged collections.
 */
class AdminSettingsViewModel(
    private val adminApi: AdminApiContract,
) : ViewModel() {
    val state: StateFlow<AdminSettingsUiState>
        field = MutableStateFlow(AdminSettingsUiState())

    init {
        loadSettings()
    }

    fun loadSettings() {
        viewModelScope.launch {
            state.value = state.value.copy(isLoading = true, error = null)

            try {
                val settings = adminApi.getServerSettings()
                state.value = state.value.copy(
                    isLoading = false,
                    inboxEnabled = settings.inboxEnabled,
                    inboxCount = settings.inboxCount,
                )
            } catch (e: Exception) {
                logger.error(e) { "Failed to load server settings" }
                state.value = state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load settings",
                )
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

            try {
                val settings = adminApi.updateServerSettings(
                    ServerSettingsRequest(inboxEnabled = enabled),
                )
                state.value = state.value.copy(
                    isSaving = false,
                    inboxEnabled = settings.inboxEnabled,
                    inboxCount = settings.inboxCount,
                )
                logger.info { "Inbox workflow ${if (enabled) "enabled" else "disabled"}" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to update server settings" }
                state.value = state.value.copy(
                    isSaving = false,
                    error = e.message ?: "Failed to update settings",
                )
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
    val inboxEnabled: Boolean = false,
    val inboxCount: Int = 0,
    val showDisableConfirmation: Boolean = false,
    val error: String? = null,
) {
    val hasPendingBooks: Boolean get() = inboxCount > 0
}
