package com.calypsan.listenup.client.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.domain.model.AdminUserInfo
import com.calypsan.listenup.client.domain.repository.AdminRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for the user detail screen.
 *
 * Manages viewing and editing a single user's details and permissions.
 * Allows toggling canDownload and canShare permissions for non-protected users.
 */
class UserDetailViewModel(
    private val userId: String,
    private val adminRepository: AdminRepository,
) : ViewModel() {
    val state: StateFlow<UserDetailUiState>
        field = MutableStateFlow(UserDetailUiState())

    init {
        loadUser()
    }

    /**
     * Load the user details from the server.
     */
    private fun loadUser() {
        viewModelScope.launch {
            state.value = state.value.copy(isLoading = true)

            try {
                val user = adminRepository.getUser(userId)
                state.value =
                    state.value.copy(
                        isLoading = false,
                        user = user,
                        canDownload = user.permissions.canDownload,
                        canShare = user.permissions.canShare,
                        isProtected = user.isProtected,
                    )
            } catch (e: Exception) {
                logger.error(e) { "Failed to load user: $userId" }
                state.value =
                    state.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load user",
                    )
            }
        }
    }

    /**
     * Toggle the canDownload permission.
     *
     * Optimistically updates the UI state, then saves to server.
     * Reverts on failure.
     */
    fun toggleCanDownload() {
        if (state.value.isProtected) return

        val previousValue = state.value.canDownload
        val newValue = !previousValue

        // Optimistic update
        state.value = state.value.copy(canDownload = newValue, isSaving = true)

        viewModelScope.launch {
            try {
                val updatedUser =
                    adminRepository.updateUser(
                        userId = userId,
                        canDownload = newValue,
                    )
                logger.info { "Updated canDownload for user $userId to $newValue" }
                state.value =
                    state.value.copy(
                        isSaving = false,
                        user = updatedUser,
                        canDownload = updatedUser.permissions.canDownload,
                    )
            } catch (e: Exception) {
                logger.error(e) { "Failed to update canDownload for user: $userId" }
                // Revert to previous value
                state.value =
                    state.value.copy(
                        isSaving = false,
                        canDownload = previousValue,
                        error = e.message ?: "Failed to update permission",
                    )
            }
        }
    }

    /**
     * Toggle the canShare permission.
     *
     * Optimistically updates the UI state, then saves to server.
     * Reverts on failure.
     */
    fun toggleCanShare() {
        if (state.value.isProtected) return

        val previousValue = state.value.canShare
        val newValue = !previousValue

        // Optimistic update
        state.value = state.value.copy(canShare = newValue, isSaving = true)

        viewModelScope.launch {
            try {
                val updatedUser =
                    adminRepository.updateUser(
                        userId = userId,
                        canShare = newValue,
                    )
                logger.info { "Updated canShare for user $userId to $newValue" }
                state.value =
                    state.value.copy(
                        isSaving = false,
                        user = updatedUser,
                        canShare = updatedUser.permissions.canShare,
                    )
            } catch (e: Exception) {
                logger.error(e) { "Failed to update canShare for user: $userId" }
                // Revert to previous value
                state.value =
                    state.value.copy(
                        isSaving = false,
                        canShare = previousValue,
                        error = e.message ?: "Failed to update permission",
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
 * UI state for the user detail screen.
 */
data class UserDetailUiState(
    val isLoading: Boolean = true,
    val user: AdminUserInfo? = null,
    val canDownload: Boolean = true,
    val canShare: Boolean = true,
    val isProtected: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
)
