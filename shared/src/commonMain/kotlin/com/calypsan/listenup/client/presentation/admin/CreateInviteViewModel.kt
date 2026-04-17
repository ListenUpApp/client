package com.calypsan.listenup.client.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.domain.model.InviteInfo
import com.calypsan.listenup.client.domain.usecase.admin.CreateInviteUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the create invite screen.
 *
 * Handles form validation and invite creation.
 */
class CreateInviteViewModel(
    private val createInviteUseCase: CreateInviteUseCase,
) : ViewModel() {
    val state: StateFlow<CreateInviteUiState>
        field = MutableStateFlow<CreateInviteUiState>(CreateInviteUiState.Ready())

    fun createInvite(
        name: String,
        email: String,
        role: String,
        expiresInDays: Int,
    ) {
        viewModelScope.launch {
            updateReady { it.copy(status = CreateInviteStatus.Submitting) }

            when (val result = createInviteUseCase(name, email, role, expiresInDays)) {
                is Success -> {
                    updateReady { it.copy(status = CreateInviteStatus.Success(result.data)) }
                }

                is Failure -> {
                    val errorType =
                        when {
                            result.message.contains("Name is required", ignoreCase = true) -> {
                                CreateInviteErrorType.ValidationError(CreateInviteField.NAME)
                            }

                            result.message.contains("Invalid email", ignoreCase = true) -> {
                                CreateInviteErrorType.ValidationError(CreateInviteField.EMAIL)
                            }

                            result.message.contains("already exists", ignoreCase = true) ||
                                result.message.contains("conflict", ignoreCase = true) -> {
                                CreateInviteErrorType.EmailInUse
                            }

                            result.message.contains("network", ignoreCase = true) ||
                                result.message.contains("connection", ignoreCase = true) -> {
                                CreateInviteErrorType.NetworkError(result.message)
                            }

                            else -> {
                                CreateInviteErrorType.ServerError(result.message)
                            }
                        }
                    updateReady { it.copy(status = CreateInviteStatus.Error(errorType)) }
                }
            }
        }
    }

    fun clearError() {
        val ready = state.value as? CreateInviteUiState.Ready ?: return
        if (ready.status is CreateInviteStatus.Error) {
            updateReady { it.copy(status = CreateInviteStatus.Idle) }
        }
    }

    fun reset() {
        state.value = CreateInviteUiState.Ready()
    }

    /**
     * Apply [transform] to state only if it is currently [CreateInviteUiState.Ready].
     * No-ops when state is [CreateInviteUiState.Loading] or [CreateInviteUiState.Error].
     */
    private fun updateReady(transform: (CreateInviteUiState.Ready) -> CreateInviteUiState.Ready) {
        state.update { current ->
            if (current is CreateInviteUiState.Ready) transform(current) else current
        }
    }
}

/**
 * UI state for the Create Invite screen.
 *
 * Sealed hierarchy — this VM is a command-driven form with no async initial
 * load, so it enters [Ready] immediately at construction. [Loading] and
 * [Error] are present for sealed-hierarchy symmetry with other VMs; form
 * submission outcomes (including validation errors) flow through
 * [Ready.status].
 */
sealed interface CreateInviteUiState {
    /** Unused by this VM; present for hierarchy symmetry. */
    data object Loading : CreateInviteUiState

    /** Form ready for input; [status] tracks submission lifecycle. */
    data class Ready(
        val status: CreateInviteStatus = CreateInviteStatus.Idle,
    ) : CreateInviteUiState

    /** Unused by this VM; present for hierarchy symmetry. */
    data class Error(
        val message: String,
    ) : CreateInviteUiState
}

sealed interface CreateInviteStatus {
    data object Idle : CreateInviteStatus

    data object Submitting : CreateInviteStatus

    data class Success(
        val invite: InviteInfo,
    ) : CreateInviteStatus

    data class Error(
        val type: CreateInviteErrorType,
    ) : CreateInviteStatus
}

sealed interface CreateInviteErrorType {
    data class ValidationError(
        val field: CreateInviteField,
    ) : CreateInviteErrorType

    data object EmailInUse : CreateInviteErrorType

    data class NetworkError(
        val detail: String?,
    ) : CreateInviteErrorType

    data class ServerError(
        val detail: String?,
    ) : CreateInviteErrorType
}

enum class CreateInviteField {
    NAME,
    EMAIL,
}
