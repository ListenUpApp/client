package com.calypsan.listenup.client.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.domain.model.AccessMode
import com.calypsan.listenup.client.domain.model.Library
import com.calypsan.listenup.client.domain.repository.AdminRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for the library settings screen.
 *
 * Manages viewing and editing a single library's settings:
 * - Access mode (open vs restricted)
 * - Skip inbox setting
 */
class LibrarySettingsViewModel(
    private val libraryId: String,
    private val adminRepository: AdminRepository,
) : ViewModel() {
    val state: StateFlow<LibrarySettingsUiState>
        field = MutableStateFlow(LibrarySettingsUiState())

    init {
        loadLibrary()
    }

    /**
     * Load the library details from the server.
     */
    private fun loadLibrary() {
        viewModelScope.launch {
            state.value = state.value.copy(isLoading = true)

            try {
                val library = adminRepository.getLibrary(libraryId)
                state.value =
                    state.value.copy(
                        isLoading = false,
                        library = library,
                        accessMode = library.accessMode,
                        skipInbox = library.skipInbox,
                    )
            } catch (e: Exception) {
                logger.error(e) { "Failed to load library: $libraryId" }
                state.value =
                    state.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load library",
                    )
            }
        }
    }

    /**
     * Set the access mode for the library.
     *
     * Optimistically updates the UI state, then saves to server.
     * Reverts on failure.
     */
    fun setAccessMode(accessMode: AccessMode) {
        val previousValue = state.value.accessMode

        if (accessMode == previousValue) return

        // Optimistic update
        state.value = state.value.copy(accessMode = accessMode, isSaving = true)

        viewModelScope.launch {
            try {
                val updatedLibrary =
                    adminRepository.updateLibrary(
                        libraryId = libraryId,
                        accessMode = accessMode,
                    )
                logger.info { "Updated access mode for library $libraryId to ${accessMode.toApiString()}" }
                state.value =
                    state.value.copy(
                        isSaving = false,
                        library = updatedLibrary,
                        accessMode = updatedLibrary.accessMode,
                    )
            } catch (e: Exception) {
                logger.error(e) { "Failed to update access mode for library: $libraryId" }
                // Revert to previous value
                state.value =
                    state.value.copy(
                        isSaving = false,
                        accessMode = previousValue,
                        error = e.message ?: "Failed to update access mode",
                    )
            }
        }
    }

    /**
     * Toggle the skip inbox setting.
     *
     * Optimistically updates the UI state, then saves to server.
     * Reverts on failure.
     */
    fun toggleSkipInbox() {
        val previousValue = state.value.skipInbox
        val newValue = !previousValue

        // Optimistic update
        state.value = state.value.copy(skipInbox = newValue, isSaving = true)

        viewModelScope.launch {
            try {
                val updatedLibrary =
                    adminRepository.updateLibrary(
                        libraryId = libraryId,
                        skipInbox = newValue,
                    )
                logger.info { "Updated skip inbox for library $libraryId to $newValue" }
                state.value =
                    state.value.copy(
                        isSaving = false,
                        library = updatedLibrary,
                        skipInbox = updatedLibrary.skipInbox,
                    )
            } catch (e: Exception) {
                logger.error(e) { "Failed to update skip inbox for library: $libraryId" }
                // Revert to previous value
                state.value =
                    state.value.copy(
                        isSaving = false,
                        skipInbox = previousValue,
                        error = e.message ?: "Failed to update skip inbox",
                    )
            }
        }
    }

    /**
     * Clear the error state.
     */
    fun clearError() {
        state.value = state.value.copy(error = null)
    }
}

/**
 * UI state for the library settings screen.
 */
data class LibrarySettingsUiState(
    val isLoading: Boolean = true,
    val library: Library? = null,
    val accessMode: AccessMode = AccessMode.OPEN,
    val skipInbox: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
)
