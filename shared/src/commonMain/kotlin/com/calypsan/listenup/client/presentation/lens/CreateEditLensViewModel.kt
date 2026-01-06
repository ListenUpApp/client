package com.calypsan.listenup.client.presentation.lens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.data.remote.LensApiContract
import com.calypsan.listenup.client.domain.repository.LensRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for Create/Edit Lens screen.
 *
 * Handles both creating new lenses and editing existing ones.
 */
class CreateEditLensViewModel(
    private val lensApi: LensApiContract,
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
     * @return true if successful and should navigate back
     */
    fun save(onSuccess: () -> Unit) {
        val currentState = state.value
        if (currentState.name.isBlank()) {
            state.update { it.copy(error = "Name is required") }
            return
        }

        viewModelScope.launch {
            state.update { it.copy(isSaving = true, error = null) }

            try {
                if (editingLensId != null) {
                    // Update existing lens
                    lensApi.updateLens(
                        lensId = editingLensId!!,
                        name = currentState.name.trim(),
                        description = currentState.description.trim().takeIf { it.isNotEmpty() },
                    )
                    logger.info { "Updated lens: $editingLensId" }
                } else {
                    // Create new lens
                    lensApi.createLens(
                        name = currentState.name.trim(),
                        description = currentState.description.trim().takeIf { it.isNotEmpty() },
                    )
                    logger.info { "Created new lens: ${currentState.name}" }
                }

                state.update { it.copy(isSaving = false) }
                onSuccess()
            } catch (e: Exception) {
                logger.error(e) { "Failed to save lens" }
                state.update {
                    it.copy(
                        isSaving = false,
                        error = "Failed to save: ${e.message}",
                    )
                }
            }
        }
    }

    /**
     * Delete the current lens (edit mode only).
     */
    fun delete(onSuccess: () -> Unit) {
        val lensId = editingLensId ?: return

        viewModelScope.launch {
            state.update { it.copy(isSaving = true, error = null) }

            try {
                lensApi.deleteLens(lensId)
                logger.info { "Deleted lens: $lensId" }
                state.update { it.copy(isSaving = false) }
                onSuccess()
            } catch (e: Exception) {
                logger.error(e) { "Failed to delete lens" }
                state.update {
                    it.copy(
                        isSaving = false,
                        error = "Failed to delete: ${e.message}",
                    )
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
