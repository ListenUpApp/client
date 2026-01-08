package com.calypsan.listenup.client.presentation.lens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.domain.repository.LensRepository
import com.calypsan.listenup.client.domain.usecase.lens.CreateLensUseCase
import com.calypsan.listenup.client.domain.usecase.lens.DeleteLensUseCase
import com.calypsan.listenup.client.domain.usecase.lens.UpdateLensUseCase
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for Create/Edit Lens screen.
 *
 * Thin presentation coordinator that delegates business logic to use cases:
 * - [CreateLensUseCase]: Creates new lenses with validation
 * - [UpdateLensUseCase]: Updates existing lenses with validation
 * - [DeleteLensUseCase]: Deletes lenses
 *
 * Handles both creating new lenses and editing existing ones.
 */
class CreateEditLensViewModel(
    private val createLensUseCase: CreateLensUseCase,
    private val updateLensUseCase: UpdateLensUseCase,
    private val deleteLensUseCase: DeleteLensUseCase,
    private val lensRepository: LensRepository,
) : ViewModel() {
    val state: StateFlow<CreateEditLensUiState>
        field = MutableStateFlow(CreateEditLensUiState())

    private var editingLensId: String? = null

    /**
     * Initialize for creating a new lens.
     */
    fun initCreate() {
        editingLensId = null
        state.update {
            CreateEditLensUiState(isEditing = false)
        }
    }

    /**
     * Initialize for editing an existing lens.
     */
    fun initEdit(lensId: String) {
        editingLensId = lensId
        viewModelScope.launch {
            state.update { it.copy(isLoading = true) }

            try {
                val lens = lensRepository.getById(lensId)
                if (lens != null) {
                    state.update {
                        CreateEditLensUiState(
                            isEditing = true,
                            name = lens.name,
                            description = lens.description ?: "",
                            isLoading = false,
                        )
                    }
                } else {
                    state.update {
                        it.copy(
                            isLoading = false,
                            error = "Lens not found",
                        )
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load lens for edit: $lensId" }
                state.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load lens: ${e.message}",
                    )
                }
            }
        }
    }

    /**
     * Update the lens name.
     */
    fun updateName(name: String) {
        state.update { it.copy(name = name) }
    }

    /**
     * Update the lens description.
     */
    fun updateDescription(description: String) {
        state.update { it.copy(description = description) }
    }

    /**
     * Save the lens (create or update).
     *
     * Delegates to appropriate use case based on mode.
     * Validation is handled by the use cases.
     */
    fun save(onSuccess: () -> Unit) {
        val currentState = state.value

        viewModelScope.launch {
            state.update { it.copy(isSaving = true, error = null) }

            val result =
                if (editingLensId != null) {
                    updateLensUseCase(
                        lensId = editingLensId!!,
                        name = currentState.name,
                        description = currentState.description.takeIf { it.isNotEmpty() },
                    )
                } else {
                    createLensUseCase(
                        name = currentState.name,
                        description = currentState.description.takeIf { it.isNotEmpty() },
                    )
                }

            when (result) {
                is Success -> {
                    state.update { it.copy(isSaving = false) }
                    onSuccess()
                }

                is Failure -> {
                    state.update {
                        it.copy(
                            isSaving = false,
                            error = result.message,
                        )
                    }
                }
            }
        }
    }

    /**
     * Delete the current lens (edit mode only).
     *
     * Delegates to [DeleteLensUseCase].
     */
    fun delete(onSuccess: () -> Unit) {
        val lensId = editingLensId ?: return

        viewModelScope.launch {
            state.update { it.copy(isSaving = true, error = null) }

            when (val result = deleteLensUseCase(lensId)) {
                is Success -> {
                    state.update { it.copy(isSaving = false) }
                    onSuccess()
                }

                is Failure -> {
                    state.update {
                        it.copy(
                            isSaving = false,
                            error = result.message,
                        )
                    }
                }
            }
        }
    }

    /**
     * Clear any error message.
     */
    fun clearError() {
        state.update { it.copy(error = null) }
    }
}

/**
 * UI state for Create/Edit Lens screen.
 */
data class CreateEditLensUiState(
    val isEditing: Boolean = false,
    val name: String = "",
    val description: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
) {
    val canSave: Boolean
        get() = name.isNotBlank() && !isLoading && !isSaving
}
