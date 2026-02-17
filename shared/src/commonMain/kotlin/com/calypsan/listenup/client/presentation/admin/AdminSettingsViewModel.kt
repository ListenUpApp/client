package com.calypsan.listenup.client.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Result
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.core.error.ErrorBus
import com.calypsan.listenup.client.domain.usecase.admin.LoadServerSettingsUseCase
import com.calypsan.listenup.client.domain.repository.AdminRepository
import com.calypsan.listenup.client.domain.repository.InstanceRepository
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
 * All changes are local-only until the user taps the Save FAB, which persists
 * everything at once via [saveAll].
 *
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

    /** Baseline values from the server, used to compute dirty state. */
    private var savedServerName: String = ""
    private var savedRemoteUrl: String = ""
    private var savedInboxEnabled: Boolean = false

    init {
        loadSettings()
    }

    fun loadSettings() {
        viewModelScope.launch {
            state.value = state.value.copy(isLoading = true, error = null)

            when (val result = loadServerSettingsUseCase()) {
                is Success -> {
                    savedServerName = result.data.serverName
                    savedInboxEnabled = result.data.inboxEnabled
                    state.value =
                        state.value.copy(
                            isLoading = false,
                            serverName = result.data.serverName,
                            inboxEnabled = result.data.inboxEnabled,
                            inboxCount = result.data.inboxCount,
                        )
                    updateDirty()
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
     * Update the server display name (local only).
     */
    fun setServerName(name: String) {
        state.value = state.value.copy(serverName = name)
        updateDirty()
    }

    /**
     * Toggle the inbox workflow on/off (local only).
     *
     * When disabling with pending books, shows confirmation first.
     */
    fun setInboxEnabled(enabled: Boolean) {
        // If disabling and there are books in inbox, show confirmation
        if (!enabled && state.value.inboxCount > 0) {
            state.value = state.value.copy(showDisableConfirmation = true)
            return
        }

        state.value = state.value.copy(inboxEnabled = enabled)
        updateDirty()
    }

    /**
     * Confirm disabling inbox workflow (local only).
     * Called after user confirms they want to release all pending books.
     */
    fun confirmDisableInbox() {
        state.value =
            state.value.copy(
                showDisableConfirmation = false,
                inboxEnabled = false,
            )
        updateDirty()
    }

    /**
     * Cancel disabling inbox workflow.
     */
    fun cancelDisableInbox() {
        state.value = state.value.copy(showDisableConfirmation = false)
    }

    private fun loadRemoteUrl() {
        viewModelScope.launch {
            when (val result = instanceRepository.getInstance(forceRefresh = true)) {
                is Result.Success -> {
                    val url = result.data.remoteUrl ?: ""
                    savedRemoteUrl = url
                    state.value = state.value.copy(remoteUrl = url)
                    updateDirty()
                }

                is Result.Failure -> {
                    // Non-fatal, just leave remote URL empty
                }
            }
        }
    }

    /**
     * Update the remote access URL (local only).
     */
    fun setRemoteUrl(url: String) {
        state.value = state.value.copy(remoteUrl = url)
        updateDirty()
    }

    /**
     * Persist all current settings to the server.
     */
    fun saveAll() {
        viewModelScope.launch {
            state.value = state.value.copy(isSaving = true, error = null)

            try {
                // Save server name if changed
                if (state.value.serverName != savedServerName) {
                    when (val result = updateServerSettingsUseCase.updateServerName(state.value.serverName)) {
                        is Success -> {
                            savedServerName = result.data.serverName
                            logger.info { "Server name saved: ${result.data.serverName}" }
                        }

                        is Failure -> {
                            throw IllegalStateException(result.message)
                        }
                    }
                }

                // Save remote URL if changed
                if (state.value.remoteUrl != savedRemoteUrl) {
                    adminRepository.updateInstanceRemoteUrl(state.value.remoteUrl)
                    savedRemoteUrl = state.value.remoteUrl
                    logger.info { "Remote URL saved: ${state.value.remoteUrl}" }
                }

                // Save inbox enabled if changed
                if (state.value.inboxEnabled != savedInboxEnabled) {
                    when (val result = updateServerSettingsUseCase(state.value.inboxEnabled)) {
                        is Success -> {
                            savedInboxEnabled = result.data.inboxEnabled
                            state.value =
                                state.value.copy(
                                    inboxCount = result.data.inboxCount,
                                )
                            logger.info { "Inbox workflow ${if (state.value.inboxEnabled) "enabled" else "disabled"}" }
                        }

                        is Failure -> {
                            throw IllegalStateException(result.message)
                        }
                    }
                }

                state.value =
                    state.value.copy(
                        isSaving = false,
                    )
                updateDirty()
            } catch (e: Exception) {
                ErrorBus.emit(e)
                logger.error(e) { "Failed to save settings" }
                state.value =
                    state.value.copy(
                        isSaving = false,
                        error = "Failed to save settings: ${e.message}",
                    )
                updateDirty()
            }
        }
    }

    private fun updateDirty() {
        val dirty =
            state.value.serverName != savedServerName ||
                state.value.remoteUrl != savedRemoteUrl ||
                state.value.inboxEnabled != savedInboxEnabled
        state.value = state.value.copy(isDirty = dirty)
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
    val remoteUrl: String = "",
    val inboxEnabled: Boolean = false,
    val inboxCount: Int = 0,
    val showDisableConfirmation: Boolean = false,
    val isDirty: Boolean = false,
    val error: String? = null,
) {
    val hasPendingBooks: Boolean get() = inboxCount > 0
}
