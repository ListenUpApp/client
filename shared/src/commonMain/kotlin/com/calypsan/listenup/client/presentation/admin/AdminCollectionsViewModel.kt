package com.calypsan.listenup.client.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.data.local.db.CollectionDao
import com.calypsan.listenup.client.data.local.db.CollectionEntity
import com.calypsan.listenup.client.data.remote.AdminCollectionApiContract
import com.calypsan.listenup.client.data.remote.model.toTimestamp
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for the admin collections list screen.
 *
 * Manages the list of collections, including create and delete operations.
 * Observes local Room database for real-time updates from SSE events.
 */
class AdminCollectionsViewModel(
    private val collectionDao: CollectionDao,
    private val adminCollectionApi: AdminCollectionApiContract,
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

            collectionDao.observeAll().collect { collections ->
                logger.debug { "Collections updated: ${collections.size}" }
                state.value = state.value.copy(
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
                val serverCollections = adminCollectionApi.getCollections()
                logger.debug { "Fetched ${serverCollections.size} collections from server" }

                // Update local database with server data
                serverCollections.forEach { response ->
                    val entity = CollectionEntity(
                        id = response.id,
                        name = response.name,
                        bookCount = response.bookCount,
                        createdAt = response.createdAt.toTimestamp(),
                        updatedAt = response.updatedAt.toTimestamp(),
                    )
                    collectionDao.upsert(entity)
                }

                // Delete local collections that no longer exist on server
                val serverIds = serverCollections.map { it.id }.toSet()
                val localCollections = collectionDao.getAll()
                localCollections.filter { it.id !in serverIds }.forEach { orphan ->
                    logger.debug { "Removing orphaned collection: ${orphan.name} (${orphan.id})" }
                    collectionDao.deleteById(orphan.id)
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to refresh collections from server" }
                // Don't update error state - local data is still usable
            }
        }
    }

    /**
     * Create a new collection with the given name.
     */
    fun createCollection(name: String) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            state.value = state.value.copy(error = "Collection name cannot be empty")
            return
        }

        viewModelScope.launch {
            state.value = state.value.copy(isCreating = true)

            try {
                val response = adminCollectionApi.createCollection(trimmedName)
                logger.info { "Created collection: ${response.name} (${response.id})" }

                // Insert locally immediately for instant feedback
                // (SSE may also upsert, which is fine - idempotent)
                val entity = CollectionEntity(
                    id = response.id,
                    name = response.name,
                    bookCount = response.bookCount,
                    createdAt = response.createdAt.toTimestamp(),
                    updatedAt = response.updatedAt.toTimestamp(),
                )
                collectionDao.upsert(entity)

                state.value = state.value.copy(
                    isCreating = false,
                    createSuccess = true,
                )
            } catch (e: Exception) {
                logger.error(e) { "Failed to create collection" }
                state.value = state.value.copy(
                    isCreating = false,
                    error = e.message ?: "Failed to create collection",
                )
            }
        }
    }

    /**
     * Delete a collection by ID.
     */
    fun deleteCollection(collectionId: String) {
        viewModelScope.launch {
            state.value = state.value.copy(deletingCollectionId = collectionId)

            try {
                adminCollectionApi.deleteCollection(collectionId)
                logger.info { "Deleted collection: $collectionId" }

                // Remove locally immediately for instant feedback
                collectionDao.deleteById(collectionId)

                state.value = state.value.copy(deletingCollectionId = null)
            } catch (e: Exception) {
                logger.error(e) { "Failed to delete collection: $collectionId" }
                state.value = state.value.copy(
                    deletingCollectionId = null,
                    error = e.message ?: "Failed to delete collection",
                )
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
    val collections: List<CollectionEntity> = emptyList(),
    val isCreating: Boolean = false,
    val createSuccess: Boolean = false,
    val deletingCollectionId: String? = null,
    val error: String? = null,
)
