package com.calypsan.listenup.client.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.domain.model.AccessMode
import com.calypsan.listenup.client.domain.model.Library
import com.calypsan.listenup.client.data.remote.DirectoryEntryResponse
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
     * Remove a scan path from the library.
     */
    fun removeScanPath(path: String) {
        viewModelScope.launch {
            state.value = state.value.copy(isSaving = true)

            try {
                val updatedLibrary = adminRepository.removeScanPath(libraryId, path)
                logger.info { "Removed scan path from library $libraryId: $path" }
                state.value =
                    state.value.copy(
                        isSaving = false,
                        library = updatedLibrary,
                    )
            } catch (e: Exception) {
                logger.error(e) { "Failed to remove scan path from library: $libraryId" }
                state.value =
                    state.value.copy(
                        isSaving = false,
                        error = e.message ?: "Failed to remove scan path",
                    )
            }
        }
    }

    /**
     * Add a scan path to the library.
     */
    fun addScanPath(path: String) {
        viewModelScope.launch {
            state.value = state.value.copy(isSaving = true, showFolderBrowser = false)

            try {
                val updatedLibrary = adminRepository.addScanPath(libraryId, path)
                logger.info { "Added scan path to library $libraryId: $path" }
                state.value =
                    state.value.copy(
                        isSaving = false,
                        library = updatedLibrary,
                    )
            } catch (e: Exception) {
                logger.error(e) { "Failed to add scan path to library: $libraryId" }
                state.value =
                    state.value.copy(
                        isSaving = false,
                        error = e.message ?: "Failed to add scan path",
                    )
            }
        }
    }

    /**
     * Trigger a manual library rescan.
     */
    fun triggerScan() {
        viewModelScope.launch {
            state.value = state.value.copy(isScanning = true)

            try {
                adminRepository.triggerScan(libraryId)
                logger.info { "Triggered scan for library $libraryId" }
                state.value = state.value.copy(isScanning = false)
            } catch (e: Exception) {
                logger.error(e) { "Failed to trigger scan for library: $libraryId" }
                state.value =
                    state.value.copy(
                        isScanning = false,
                        error = e.message ?: "Failed to trigger scan",
                    )
            }
        }
    }

    /**
     * Show or hide the folder browser for adding paths.
     */
    fun setShowFolderBrowser(show: Boolean) {
        state.value = state.value.copy(
            showFolderBrowser = show,
            browserPath = "/",
            browserEntries = emptyList(),
            browserParent = null,
        )
        if (show) {
            loadBrowserDirectory("/")
        }
    }

    /**
     * Load directory contents in the folder browser.
     */
    fun loadBrowserDirectory(path: String) {
        viewModelScope.launch {
            state.value = state.value.copy(isBrowserLoading = true)

            try {
                val response = adminRepository.browseFilesystem(path)
                state.value =
                    state.value.copy(
                        isBrowserLoading = false,
                        browserPath = response.path,
                        browserParent = response.parent,
                        browserEntries = response.entries,
                        browserIsRoot = response.isRoot,
                    )
            } catch (e: Exception) {
                logger.error(e) { "Failed to browse directory: $path" }
                state.value =
                    state.value.copy(
                        isBrowserLoading = false,
                        error = e.message ?: "Failed to browse directory",
                    )
            }
        }
    }

    /**
     * Navigate up in the folder browser.
     */
    fun browserNavigateUp() {
        val parent = state.value.browserParent
        if (parent != null) {
            loadBrowserDirectory(parent)
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
    val isScanning: Boolean = false,
    val error: String? = null,
    // Folder browser state
    val showFolderBrowser: Boolean = false,
    val isBrowserLoading: Boolean = false,
    val browserPath: String = "/",
    val browserParent: String? = null,
    val browserEntries: List<DirectoryEntryResponse> = emptyList(),
    val browserIsRoot: Boolean = true,
)
