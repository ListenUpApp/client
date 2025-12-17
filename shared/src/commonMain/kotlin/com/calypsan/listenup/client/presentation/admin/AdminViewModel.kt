package com.calypsan.listenup.client.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.data.remote.AdminApiContract
import com.calypsan.listenup.client.data.remote.AdminInvite
import com.calypsan.listenup.client.data.remote.AdminUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the combined admin screen.
 *
 * Manages users and pending invites on a single screen.
 */
class AdminViewModel(
    private val adminApi: AdminApiContract,
) : ViewModel() {
    private val _state = MutableStateFlow(AdminUiState())
    val state: StateFlow<AdminUiState> = _state.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            // Load users and invites independently so one failure doesn't block the other
            val users =
                try {
                    adminApi.getUsers()
                } catch (e: Exception) {
                    _state.value = _state.value.copy(error = "Failed to load users: ${e.message}")
                    emptyList()
                }

            val pendingInvites =
                try {
                    val invites = adminApi.getInvites()
                    // Only show pending (unclaimed) invites
                    invites.filter { it.claimedAt == null }
                } catch (e: Exception) {
                    // Don't overwrite user error if already set
                    if (_state.value.error == null) {
                        _state.value = _state.value.copy(error = "Failed to load invites: ${e.message}")
                    }
                    emptyList()
                }

            _state.value =
                _state.value.copy(
                    isLoading = false,
                    users = users,
                    pendingInvites = pendingInvites,
                )
        }
    }

    fun deleteUser(userId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(deletingUserId = userId)

            try {
                adminApi.deleteUser(userId)
                val updatedUsers = _state.value.users.filter { it.id != userId }
                _state.value =
                    _state.value.copy(
                        deletingUserId = null,
                        users = updatedUsers,
                    )
            } catch (e: Exception) {
                _state.value =
                    _state.value.copy(
                        deletingUserId = null,
                        error = e.message ?: "Failed to delete user",
                    )
            }
        }
    }

    fun revokeInvite(inviteId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(revokingInviteId = inviteId)

            try {
                adminApi.deleteInvite(inviteId)
                val updatedInvites = _state.value.pendingInvites.filter { it.id != inviteId }
                _state.value =
                    _state.value.copy(
                        revokingInviteId = null,
                        pendingInvites = updatedInvites,
                    )
            } catch (e: Exception) {
                _state.value =
                    _state.value.copy(
                        revokingInviteId = null,
                        error = e.message ?: "Failed to revoke invite",
                    )
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
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
