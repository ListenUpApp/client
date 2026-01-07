package com.calypsan.listenup.client.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.domain.model.InviteInfo
import com.calypsan.listenup.client.domain.usecase.admin.CreateInviteUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
        field = MutableStateFlow(CreateInviteUiState())

    fun createInvite(
        name: String,
        email: String,
        role: String,
        expiresInDays: Int,
    ) {
        viewModelScope.launch {
            state.value = state.value.copy(status = CreateInviteStatus.Submitting)

            when (val result = createInviteUseCase(name, email, role, expiresInDays)) {
                is Success -> {
                    state.value =
                        state.value.copy(
                            status = CreateInviteStatus.Success(result.data),
                        )
                }
                is Failure -> {
                    val errorType = when {
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
                    state.value =
                        state.value.copy(
                            status = CreateInviteStatus.Error(errorType),
                        )
                }
            }
        }
    }

    fun clearError() {
        if (state.value.status is CreateInviteStatus.Error) {
            state.value = state.value.copy(status = CreateInviteStatus.Idle)
        }
    }

    fun reset() {
        state.value = CreateInviteUiState()
    }
}

data class CreateInviteUiState(
    val status: CreateInviteStatus = CreateInviteStatus.Idle,
)

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
