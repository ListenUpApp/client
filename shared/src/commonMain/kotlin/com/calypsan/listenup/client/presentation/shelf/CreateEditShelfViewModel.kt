package com.calypsan.listenup.client.presentation.shelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.domain.repository.ShelfRepository
import com.calypsan.listenup.client.domain.usecase.shelf.CreateShelfUseCase
import com.calypsan.listenup.client.domain.usecase.shelf.DeleteShelfUseCase
import com.calypsan.listenup.client.domain.usecase.shelf.UpdateShelfUseCase
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for Create/Edit Shelf screen.
 *
 * Thin presentation coordinator that delegates business logic to use cases:
 * - [CreateShelfUseCase]: Creates new shelves with validation
 * - [UpdateShelfUseCase]: Updates existing shelves with validation
 * - [DeleteShelfUseCase]: Deletes shelves
 *
 * Handles both creating new shelves and editing existing ones.
 */
class CreateEditShelfViewModel(
    private val createShelfUseCase: CreateShelfUseCase,
    private val updateShelfUseCase: UpdateShelfUseCase,
    private val deleteShelfUseCase: DeleteShelfUseCase,
    private val shelfRepository: ShelfRepository,
) : ViewModel() {
    val state: StateFlow<CreateEditShelfUiState>
        field = MutableStateFlow(CreateEditShelfUiState())

    private var editingShelfId: String? = null

    /**
     * Initialize for creating a new shelf.
     */
    fun initCreate() {
        editingShelfId = null
        state.update {
            CreateEditShelfUiState(isEditing = false)
        }
    }

    /**
     * Initialize for editing an existing shelf.
     */
    fun initEdit(shelfId: String) {
        editingShelfId = shelfId
        viewModelScope.launch {
            state.update { it.copy(isLoading = true) }

            try {
                val shelf = shelfRepository.getById(shelfId)
                if (shelf != null) {
                    state.update {
                        CreateEditShelfUiState(
                            isEditing = true,
                            name = shelf.name,
                            description = shelf.description ?: "",
                            isLoading = false,
                        )
                    }
                } else {
                    state.update {
                        it.copy(
                            isLoading = false,
                            error = "Shelf not found",
                        )
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load shelf for edit: $shelfId" }
                state.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load shelf: ${e.message}",
                    )
                }
            }
        }
    }

    /**
     * Update the shelf name.
     */
    fun updateName(name: String) {
        state.update { it.copy(name = name) }
    }

    /**
     * Update the shelf description.
     */
    fun updateDescription(description: String) {
        state.update { it.copy(description = description) }
    }

    /**
     * Save the shelf (create or update).
     *
     * Delegates to appropriate use case based on mode.
     * Validation is handled by the use cases.
     */
    fun save(onSuccess: () -> Unit) {
        val currentState = state.value

        viewModelScope.launch {
            state.update { it.copy(isSaving = true, error = null) }

            val result =
                if (editingShelfId != null) {
                    updateShelfUseCase(
                        shelfId = editingShelfId!!,
                        name = currentState.name,
                        description = currentState.description.takeIf { it.isNotEmpty() },
                    )
                } else {
                    createShelfUseCase(
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
     * Delete the current shelf (edit mode only).
     *
     * Delegates to [DeleteShelfUseCase].
     */
    fun delete(onSuccess: () -> Unit) {
        val shelfId = editingShelfId ?: return

        viewModelScope.launch {
            state.update { it.copy(isSaving = true, error = null) }

            when (val result = deleteShelfUseCase(shelfId)) {
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
 * UI state for Create/Edit Shelf screen.
 */
data class CreateEditShelfUiState(
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
