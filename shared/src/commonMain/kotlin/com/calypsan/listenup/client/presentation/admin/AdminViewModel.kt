package com.calypsan.listenup.client.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.data.remote.AdminApiContract
import com.calypsan.listenup.client.data.remote.AdminInvite
import com.calypsan.listenup.client.data.remote.AdminUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the combined admin screen.
 *
 * Manages users and pending invites on a single screen.
 */
class AdminViewModel(
    private val adminApi: AdminApiContract,
) : ViewModel() {
    val state: StateFlow<AdminUiState>
        field = MutableStateFlow(AdminUiState())

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            state.value = state.value.copy(isLoading = true, error = null)

            // Load users and invites independently so one failure doesn't block the other
            val users =
                try {
                    adminApi.getUsers()
                } catch (e: Exception) {
                    state.value = state.value.copy(error = "Failed to load users: ${e.message}")
                    emptyList()
                }

            val pendingInvites =
                try {
                    val invites = adminApi.getInvites()
                    // Only show pending (unclaimed) invites
                    invites.filter { it.claimedAt == null }
                } catch (e: Exception) {
                    // Don't overwrite user error if already set
                    if (state.value.error == null) {
                        state.value = state.value.copy(error = "Failed to load invites: ${e.message}")
                    }
                    emptyList()
                }

            state.value =
                state.value.copy(
                    isLoading = false,
                    users = users,
                    pendingInvites = pendingInvites,
                )
        }
    }

    fun deleteUser(userId: String) {
        viewModelScope.launch {
            state.value = state.value.copy(deletingUserId = userId)

            try {
                adminApi.deleteUser(userId)
                val updatedUsers = state.value.users.filter { it.id != userId }
                state.value =
                    state.value.copy(
                        deletingUserId = null,
                        users = updatedUsers,
                    )
            } catch (e: Exception) {
                state.value =
                    state.value.copy(
                        deletingUserId = null,
                        error = e.message ?: "Failed to delete user",
                    )
            }
        }
    }

    fun revokeInvite(inviteId: String) {
        viewModelScope.launch {
            state.value = state.value.copy(revokingInviteId = inviteId)

            try {
                adminApi.deleteInvite(inviteId)
                val updatedInvites = state.value.pendingInvites.filter { it.id != inviteId }
                state.value =
                    state.value.copy(
                        revokingInviteId = null,
                        pendingInvites = updatedInvites,
                    )
            } catch (e: Exception) {
                state.value =
                    state.value.copy(
                        revokingInviteId = null,
                        error = e.message ?: "Failed to revoke invite",
                    )
            }
        }
    }

    fun clearError() {
        state.value = state.value.copy(error = null)
    }
}

data class AdminUiState(
    val isLoading: Boolean = true,
    val users: List<AdminUser> = emptyList(),
    val pendingInvites: List<AdminInvite> = emptyList(),
    val deletingUserId: String? = null,
    val revokingInviteId: String? = null,
    val error: String? = null,
)
