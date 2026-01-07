package com.calypsan.listenup.client.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.domain.model.AdminEvent
import com.calypsan.listenup.client.domain.model.AdminUserInfo
import com.calypsan.listenup.client.domain.model.InviteInfo
import com.calypsan.listenup.client.domain.repository.EventStreamRepository
import com.calypsan.listenup.client.domain.repository.InstanceRepository
import com.calypsan.listenup.client.domain.usecase.admin.ApproveUserUseCase
import com.calypsan.listenup.client.domain.usecase.admin.DeleteUserUseCase
import com.calypsan.listenup.client.domain.usecase.admin.DenyUserUseCase
import com.calypsan.listenup.client.domain.usecase.admin.LoadInvitesUseCase
import com.calypsan.listenup.client.domain.usecase.admin.LoadPendingUsersUseCase
import com.calypsan.listenup.client.domain.usecase.admin.LoadUsersUseCase
import com.calypsan.listenup.client.domain.usecase.admin.RevokeInviteUseCase
import com.calypsan.listenup.client.domain.usecase.admin.SetOpenRegistrationUseCase
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
    private val instanceRepository: InstanceRepository,
    private val loadUsersUseCase: LoadUsersUseCase,
    private val loadPendingUsersUseCase: LoadPendingUsersUseCase,
    private val loadInvitesUseCase: LoadInvitesUseCase,
    private val deleteUserUseCase: DeleteUserUseCase,
    private val revokeInviteUseCase: RevokeInviteUseCase,
    private val approveUserUseCase: ApproveUserUseCase,
    private val denyUserUseCase: DenyUserUseCase,
    private val setOpenRegistrationUseCase: SetOpenRegistrationUseCase,
    private val eventStreamRepository: EventStreamRepository,
) : ViewModel() {
    val state: StateFlow<AdminUiState>
        field = MutableStateFlow(AdminUiState())

    init {
        loadData()
        observeSSEEvents()
    }

    /**
     * Observe admin events for real-time pending user updates.
     */
    private fun observeSSEEvents() {
        viewModelScope.launch {
            eventStreamRepository.adminEvents.collect { event ->
                when (event) {
                    is AdminEvent.UserPending -> {
                        handleUserPending(event.user)
                    }

                    is AdminEvent.UserApproved -> {
                        handleUserApproved(event.user)
                    }

                    else -> { /* Other admin events handled elsewhere */ }
                }
            }
        }
    }

    private fun handleUserPending(user: AdminUserInfo) {
        logger.debug { "SSE: User pending - ${user.email}" }
        val currentPending = state.value.pendingUsers
        // Only add if not already in list
        if (currentPending.none { it.id == user.id }) {
            state.value =
                state.value.copy(
                    pendingUsers = currentPending + user,
                )
        }
    }

    private fun handleUserApproved(user: AdminUserInfo) {
        logger.debug { "SSE: User approved - ${user.email}" }
        // Remove from pending
        val updatedPending = state.value.pendingUsers.filter { it.id != user.id }
        // Only add to users if not already present (avoid duplicates from button + SSE)
        val updatedUsers =
            if (state.value.users.none { it.id == user.id }) {
                state.value.users + user
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
                when (val result = instanceRepository.getInstance()) {
                    is Success -> result.data.openRegistration
                    is Failure -> false
                }

            // Load users and invites independently so one failure doesn't block the other
            val users =
                when (val result = loadUsersUseCase()) {
                    is Success -> result.data
                    is Failure -> {
                        state.value = state.value.copy(error = "Failed to load users: ${result.message}")
                        emptyList()
                    }
                }

            val pendingUsers =
                when (val result = loadPendingUsersUseCase()) {
                    is Success -> result.data
                    is Failure -> emptyList() // Don't overwrite other errors
                }

            val pendingInvites =
                when (val result = loadInvitesUseCase()) {
                    is Success -> result.data.filter { it.claimedAt == null }
                    is Failure -> {
                        if (state.value.error == null) {
                            state.value = state.value.copy(error = "Failed to load invites: ${result.message}")
                        }
                        emptyList()
                    }
                }

            // Sort users: root user first, then by creation date (oldest first)
            val sortedUsers =
                users.sortedWith(
                    compareByDescending<AdminUserInfo> { it.isRoot }
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

            when (val result = deleteUserUseCase(userId)) {
                is Success -> {
                    val updatedUsers = state.value.users.filter { it.id != userId }
                    state.value =
                        state.value.copy(
                            deletingUserId = null,
                            users = updatedUsers,
                        )
                }
                is Failure -> {
                    state.value =
                        state.value.copy(
                            deletingUserId = null,
                            error = result.message,
                        )
                }
            }
        }
    }

    fun revokeInvite(inviteId: String) {
        viewModelScope.launch {
            state.value = state.value.copy(revokingInviteId = inviteId)

            when (val result = revokeInviteUseCase(inviteId)) {
                is Success -> {
                    val updatedInvites = state.value.pendingInvites.filter { it.id != inviteId }
                    state.value =
                        state.value.copy(
                            revokingInviteId = null,
                            pendingInvites = updatedInvites,
                        )
                }
                is Failure -> {
                    state.value =
                        state.value.copy(
                            revokingInviteId = null,
                            error = result.message,
                        )
                }
            }
        }
    }

    fun clearError() {
        state.value = state.value.copy(error = null)
    }

    fun approveUser(userId: String) {
        viewModelScope.launch {
            state.value = state.value.copy(approvingUserId = userId)

            when (val result = approveUserUseCase(userId)) {
                is Success -> {
                    val approvedUser = result.data
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
                }
                is Failure -> {
                    state.value =
                        state.value.copy(
                            approvingUserId = null,
                            error = result.message,
                        )
                }
            }
        }
    }

    fun denyUser(userId: String) {
        viewModelScope.launch {
            state.value = state.value.copy(denyingUserId = userId)

            when (val result = denyUserUseCase(userId)) {
                is Success -> {
                    val updatedPending = state.value.pendingUsers.filter { it.id != userId }
                    state.value =
                        state.value.copy(
                            denyingUserId = null,
                            pendingUsers = updatedPending,
                        )
                }
                is Failure -> {
                    state.value =
                        state.value.copy(
                            denyingUserId = null,
                            error = result.message,
                        )
                }
            }
        }
    }

    fun setOpenRegistration(enabled: Boolean) {
        viewModelScope.launch {
            state.value = state.value.copy(isTogglingOpenRegistration = true)

            when (val result = setOpenRegistrationUseCase(enabled)) {
                is Success -> {
                    state.value =
                        state.value.copy(
                            isTogglingOpenRegistration = false,
                            openRegistration = enabled,
                        )
                }
                is Failure -> {
                    state.value =
                        state.value.copy(
                            isTogglingOpenRegistration = false,
                            error = result.message,
                        )
                }
            }
        }
    }
}

data class AdminUiState(
    val isLoading: Boolean = true,
    val openRegistration: Boolean = false,
    val users: List<AdminUserInfo> = emptyList(),
    val pendingUsers: List<AdminUserInfo> = emptyList(),
    val pendingInvites: List<InviteInfo> = emptyList(),
    val deletingUserId: String? = null,
    val revokingInviteId: String? = null,
    val approvingUserId: String? = null,
    val denyingUserId: String? = null,
    val isTogglingOpenRegistration: Boolean = false,
    val error: String? = null,
)
