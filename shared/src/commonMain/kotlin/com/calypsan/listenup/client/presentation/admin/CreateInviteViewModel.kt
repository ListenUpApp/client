package com.calypsan.listenup.client.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.data.remote.AdminApiContract
import com.calypsan.listenup.client.data.remote.AdminInvite
import com.calypsan.listenup.client.data.remote.CreateInviteRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the create invite screen.
 *
 * Handles form validation and invite creation.
 */
class CreateInviteViewModel(
    private val adminApi: AdminApiContract,
) : ViewModel() {
    val state: StateFlow<CreateInviteUiState>
        field = MutableStateFlow(CreateInviteUiState())

    fun createInvite(
        name: String,
        email: String,
        role: String,
        expiresInDays: Int,
    ) {
        // Validate
        val trimmedName = name.trim()
        val trimmedEmail = email.trim()

        if (trimmedName.isBlank()) {
            state.value =
                state.value.copy(
                    status = CreateInviteStatus.Error(CreateInviteErrorType.ValidationError(CreateInviteField.NAME)),
                )
            return
        }

        if (!isValidEmail(trimmedEmail)) {
            state.value =
                state.value.copy(
                    status = CreateInviteStatus.Error(CreateInviteErrorType.ValidationError(CreateInviteField.EMAIL)),
                )
            return
        }

        viewModelScope.launch {
            state.value = state.value.copy(status = CreateInviteStatus.Submitting)

            try {
                val invite =
                    adminApi.createInvite(
                        CreateInviteRequest(
                            name = trimmedName,
                            email = trimmedEmail,
                            role = role,
                            expiresInDays = expiresInDays,
                        ),
                    )
                state.value =
                    state.value.copy(
                        status = CreateInviteStatus.Success(invite),
                    )
            } catch (e: Exception) {
                val errorType =
                    when {
                        e.message?.contains("already exists", ignoreCase = true) == true ||
                            e.message?.contains("conflict", ignoreCase = true) == true -> {
                            CreateInviteErrorType.EmailInUse
                        }

                        e.message?.contains("network", ignoreCase = true) == true ||
                            e.message?.contains("connection", ignoreCase = true) == true -> {
                            CreateInviteErrorType.NetworkError(e.message)
                        }

                        else -> {
                            CreateInviteErrorType.ServerError(e.message)
                        }
                    }
                state.value =
                    state.value.copy(
                        status = CreateInviteStatus.Error(errorType),
                    )
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

    private fun isValidEmail(email: String): Boolean = email.contains("@") && email.contains(".")
}

data class CreateInviteUiState(
    val status: CreateInviteStatus = CreateInviteStatus.Idle,
)

sealed interface CreateInviteStatus {
    data object Idle : CreateInviteStatus

    data object Submitting : CreateInviteStatus

    data class Success(
        val invite: AdminInvite,
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
