package com.calypsan.listenup.client.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.core.error.ErrorBus
import com.calypsan.listenup.client.domain.model.Collection
import com.calypsan.listenup.client.domain.repository.CollectionRepository
import com.calypsan.listenup.client.domain.usecase.collection.CreateCollectionUseCase
import com.calypsan.listenup.client.domain.usecase.collection.DeleteCollectionUseCase
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for the admin collections list screen.
 *
 * Manages the list of collections, including create and delete operations.
 * Observes local Room database for real-time updates from SSE events.
 *
 * Delegates mutation operations to use cases:
 * - [CreateCollectionUseCase]: Creates new collections with validation
 * - [DeleteCollectionUseCase]: Deletes collections
 */
class AdminCollectionsViewModel(
    private val collectionRepository: CollectionRepository,
    private val createCollectionUseCase: CreateCollectionUseCase,
    private val deleteCollectionUseCase: DeleteCollectionUseCase,
) : ViewModel() {
    val state: StateFlow<AdminCollectionsUiState>
        field = MutableStateFlow(AdminCollectionsUiState())

    init {
        observeCollections()
        refreshCollections()
    }

    /**
     * Observe collections from local database.
     * Updates are pushed via SSE events and processed by SSEEventProcessor.
     */
    private fun observeCollections() {
        viewModelScope.launch {
            state.value = state.value.copy(isLoading = true)

            collectionRepository.observeAll().collect { collections ->
                logger.debug { "Collections updated: ${collections.size}" }
                state.value =
                    state.value.copy(
                        isLoading = false,
                        collections = collections,
                    )
            }
        }
    }

    /**
     * Refresh collections from the server API.
     * This syncs the local database with the latest server state including book counts.
     */
    fun refreshCollections() {
        viewModelScope.launch {
            try {
                collectionRepository.refreshFromServer()
                logger.debug { "Refreshed collections from server" }
            } catch (e: Exception) {
                ErrorBus.emit(e)
                logger.warn(e) { "Failed to refresh collections from server" }
                // Don't update error state - local data is still usable
            }
        }
    }

    /**
     * Create a new collection with the given name.
     *
     * Delegates to [CreateCollectionUseCase] for validation and persistence.
     */
    fun createCollection(name: String) {
        viewModelScope.launch {
            state.value = state.value.copy(isCreating = true)

            when (val result = createCollectionUseCase(name)) {
                is Success -> {
                    state.value =
                        state.value.copy(
                            isCreating = false,
                            createSuccess = true,
                        )
                }

                is Failure -> {
                    state.value =
                        state.value.copy(
                            isCreating = false,
                            error = result.message,
                        )
                }
            }
        }
    }

    /**
     * Delete a collection by ID.
     *
     * Delegates to [DeleteCollectionUseCase].
     */
    fun deleteCollection(collectionId: String) {
        viewModelScope.launch {
            state.value = state.value.copy(deletingCollectionId = collectionId)

            when (val result = deleteCollectionUseCase(collectionId)) {
                is Success -> {
                    state.value = state.value.copy(deletingCollectionId = null)
                }

                is Failure -> {
                    state.value =
                        state.value.copy(
                            deletingCollectionId = null,
                            error = result.message,
                        )
                }
            }
        }
    }

    /**
     * Clear the error state.
     */
    fun clearError() {
        state.value = state.value.copy(error = null)
    }

    /**
     * Clear the create success flag.
     */
    fun clearCreateSuccess() {
        state.value = state.value.copy(createSuccess = false)
    }
}

/**
 * UI state for the admin collections list screen.
 */
data class AdminCollectionsUiState(
    val isLoading: Boolean = true,
    val collections: List<Collection> = emptyList(),
    val isCreating: Boolean = false,
    val createSuccess: Boolean = false,
    val deletingCollectionId: String? = null,
    val error: String? = null,
)
