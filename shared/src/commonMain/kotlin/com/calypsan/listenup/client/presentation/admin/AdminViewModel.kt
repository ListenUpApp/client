package com.calypsan.listenup.client.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.data.remote.AdminApiContract
import com.calypsan.listenup.client.data.remote.AdminInvite
import com.calypsan.listenup.client.data.remote.AdminUser
import com.calypsan.listenup.client.data.remote.InstanceApiContract
import com.calypsan.listenup.client.data.remote.model.SSEUserData
import com.calypsan.listenup.client.data.sync.SSEEventType
import com.calypsan.listenup.client.data.sync.SSEManagerContract
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for the combined admin screen.
 *
 * Manages users, pending invites, and pending users on a single screen.
 * Subscribes to SSE events for real-time updates of pending users.
 */
class AdminViewModel(
    private val adminApi: AdminApiContract,
    private val instanceApi: InstanceApiContract,
    private val sseManager: SSEManagerContract,
) : ViewModel() {
    val state: StateFlow<AdminUiState>
        field = MutableStateFlow(AdminUiState())

    init {
        loadData()
        observeSSEEvents()
    }

    /**
     * Observe SSE events for real-time pending user updates.
     */
    private fun observeSSEEvents() {
        viewModelScope.launch {
            sseManager.eventFlow.collect { event ->
                when (event) {
                    is SSEEventType.UserPending -> {
                        handleUserPending(event.user)
                    }

                    is SSEEventType.UserApproved -> {
                        handleUserApproved(event.user)
                    }

                    else -> { /* Other events handled elsewhere */ }
                }
            }
        }
    }

    private fun handleUserPending(userData: SSEUserData) {
        logger.debug { "SSE: User pending - ${userData.email}" }
        val newPendingUser = userData.toAdminUser()
        val currentPending = state.value.pendingUsers
        // Only add if not already in list
        if (currentPending.none { it.id == newPendingUser.id }) {
            state.value =
                state.value.copy(
                    pendingUsers = currentPending + newPendingUser,
                )
        }
    }

    private fun handleUserApproved(userData: SSEUserData) {
        logger.debug { "SSE: User approved - ${userData.email}" }
        val approvedUser = userData.toAdminUser()
        // Remove from pending
        val updatedPending = state.value.pendingUsers.filter { it.id != userData.id }
        // Only add to users if not already present (avoid duplicates from button + SSE)
        val updatedUsers =
            if (state.value.users.none { it.id == userData.id }) {
                state.value.users + approvedUser
            } else {
                state.value.users
            }
        state.value =
            state.value.copy(
                pendingUsers = updatedPending,
                users = updatedUsers,
            )
    }

    fun loadData() {
        viewModelScope.launch {
            state.value = state.value.copy(isLoading = true, error = null)

            // Load instance info first for open registration status
            val openRegistration =
                try {
                    when (val result = instanceApi.getInstance()) {
                        is Success -> result.data.openRegistration
                        else -> false
                    }
                } catch (e: Exception) {
                    false
                }

            // Load users and invites independently so one failure doesn't block the other
            val users =
                try {
                    adminApi.getUsers()
                } catch (e: Exception) {
                    state.value = state.value.copy(error = "Failed to load users: ${e.message}")
                    emptyList()
                }

            val pendingUsers =
                try {
                    adminApi.getPendingUsers()
                } catch (e: Exception) {
                    // Don't overwrite other errors
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

            // Sort users: root user first, then by creation date (oldest first)
            val sortedUsers =
                users.sortedWith(
                    compareByDescending<AdminUser> { it.isRoot }
                        .thenBy { it.createdAt },
                )

            state.value =
                state.value.copy(
                    isLoading = false,
                    openRegistration = openRegistration,
                    users = sortedUsers,
                    pendingUsers = pendingUsers,
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

    fun approveUser(userId: String) {
        viewModelScope.launch {
            state.value = state.value.copy(approvingUserId = userId)

            try {
                val approvedUser = adminApi.approveUser(userId)
                // Move from pending to active users
                val updatedPending = state.value.pendingUsers.filter { it.id != userId }
                // Only add to users if not already present (avoid duplicates from button + SSE)
                val updatedUsers =
                    if (state.value.users.none { it.id == userId }) {
                        state.value.users + approvedUser
                    } else {
                        state.value.users
                    }
                state.value =
                    state.value.copy(
                        approvingUserId = null,
                        pendingUsers = updatedPending,
                        users = updatedUsers,
                    )
            } catch (e: Exception) {
                state.value =
                    state.value.copy(
                        approvingUserId = null,
                        error = e.message ?: "Failed to approve user",
                    )
            }
        }
    }

    fun denyUser(userId: String) {
        viewModelScope.launch {
            state.value = state.value.copy(denyingUserId = userId)

            try {
                adminApi.denyUser(userId)
                val updatedPending = state.value.pendingUsers.filter { it.id != userId }
                state.value =
                    state.value.copy(
                        denyingUserId = null,
                        pendingUsers = updatedPending,
                    )
            } catch (e: Exception) {
                state.value =
                    state.value.copy(
                        denyingUserId = null,
                        error = e.message ?: "Failed to deny user",
                    )
            }
        }
    }

    fun setOpenRegistration(enabled: Boolean) {
        viewModelScope.launch {
            state.value = state.value.copy(isTogglingOpenRegistration = true)

            try {
                adminApi.setOpenRegistration(enabled)
                state.value =
                    state.value.copy(
                        isTogglingOpenRegistration = false,
                        openRegistration = enabled,
                    )
            } catch (e: Exception) {
                state.value =
                    state.value.copy(
                        isTogglingOpenRegistration = false,
                        error = e.message ?: "Failed to update registration setting",
                    )
            }
        }
    }
}

data class AdminUiState(
    val isLoading: Boolean = true,
    val openRegistration: Boolean = false,
    val users: List<AdminUser> = emptyList(),
    val pendingUsers: List<AdminUser> = emptyList(),
    val pendingInvites: List<AdminInvite> = emptyList(),
    val deletingUserId: String? = null,
    val revokingInviteId: String? = null,
    val approvingUserId: String? = null,
    val denyingUserId: String? = null,
    val isTogglingOpenRegistration: Boolean = false,
    val error: String? = null,
)

/**
 * Convert SSE user data to AdminUser model.
 */
private fun SSEUserData.toAdminUser(): AdminUser =
    AdminUser(
        id = id,
        email = email,
        displayName = displayName,
        firstName = firstName,
        lastName = lastName,
        isRoot = isRoot,
        role = role,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
