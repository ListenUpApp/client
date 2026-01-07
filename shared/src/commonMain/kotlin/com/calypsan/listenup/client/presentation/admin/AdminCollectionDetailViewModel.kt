package com.calypsan.listenup.client.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.Failure
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.domain.model.AdminUserInfo
import com.calypsan.listenup.client.domain.model.Collection
import com.calypsan.listenup.client.domain.repository.CollectionBookSummary
import com.calypsan.listenup.client.domain.repository.CollectionRepository
import com.calypsan.listenup.client.domain.repository.CollectionShareSummary
import com.calypsan.listenup.client.domain.usecase.collection.GetUsersForSharingUseCase
import com.calypsan.listenup.client.domain.usecase.collection.LoadCollectionBooksUseCase
import com.calypsan.listenup.client.domain.usecase.collection.LoadCollectionSharesUseCase
import com.calypsan.listenup.client.domain.usecase.collection.RemoveBookFromCollectionUseCase
import com.calypsan.listenup.client.domain.usecase.collection.RemoveCollectionShareUseCase
import com.calypsan.listenup.client.domain.usecase.collection.ShareCollectionUseCase
import com.calypsan.listenup.client.domain.usecase.collection.UpdateCollectionNameUseCase
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for the admin collection detail screen.
 *
 * Manages viewing and editing a single collection, including:
 * - Loading collection details
 * - Editing the collection name
 * - Displaying and removing books in the collection
 * - Managing collection members (sharing)
 */
class AdminCollectionDetailViewModel(
    private val collectionId: String,
    private val collectionRepository: CollectionRepository,
    private val loadCollectionBooksUseCase: LoadCollectionBooksUseCase,
    private val loadCollectionSharesUseCase: LoadCollectionSharesUseCase,
    private val updateCollectionNameUseCase: UpdateCollectionNameUseCase,
    private val removeBookFromCollectionUseCase: RemoveBookFromCollectionUseCase,
    private val shareCollectionUseCase: ShareCollectionUseCase,
    private val removeCollectionShareUseCase: RemoveCollectionShareUseCase,
    private val getUsersForSharingUseCase: GetUsersForSharingUseCase,
) : ViewModel() {
    val state: StateFlow<AdminCollectionDetailUiState>
        field = MutableStateFlow(AdminCollectionDetailUiState())

    init {
        loadCollection()
    }

    /**
     * Load the collection details from local database.
     * The collection is synced via SSE events, so we observe for changes.
     */
    private fun loadCollection() {
        viewModelScope.launch {
            state.value = state.value.copy(isLoading = true)

            try {
                // First, try to get from local DB via repository
                val localCollection = collectionRepository.getById(collectionId)
                if (localCollection != null) {
                    state.value =
                        state.value.copy(
                            isLoading = false,
                            collection = localCollection,
                            editedName = localCollection.name,
                        )
                } else {
                    // Fallback to server if not in local DB yet
                    val serverCollection = collectionRepository.getCollectionFromServer(collectionId)
                    state.value =
                        state.value.copy(
                            isLoading = false,
                            collection = serverCollection,
                            editedName = serverCollection.name,
                        )
                }

                // Load books and shares in parallel
                loadBooks()
                loadShares()
            } catch (e: Exception) {
                logger.error(e) { "Failed to load collection: $collectionId" }
                state.value =
                    state.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load collection",
                    )
            }
        }
    }

    /**
     * Load shares for the collection via use case.
     * The use case enriches shares with user information.
     */
    private suspend fun loadShares() {
        when (val result = loadCollectionSharesUseCase(collectionId)) {
            is Success -> {
                state.value =
                    state.value.copy(
                        shares = result.data.map { it.toShareItem() },
                    )
            }
            is Failure -> {
                logger.warn { "Failed to load shares for collection: $collectionId - ${result.message}" }
                // Don't fail the whole screen - just show empty shares
            }
        }
    }

    /**
     * Convert domain model to UI model.
     */
    private fun CollectionShareSummary.toShareItem() =
        CollectionShareItem(
            id = id,
            userId = userId,
            userName = userName,
            userEmail = userEmail,
            permission = permission,
        )

    /**
     * Load books in the collection via use case.
     */
    private suspend fun loadBooks() {
        when (val result = loadCollectionBooksUseCase(collectionId)) {
            is Success -> {
                state.value =
                    state.value.copy(
                        books = result.data.map { it.toBookItem() },
                    )
            }
            is Failure -> {
                logger.warn { "Failed to load books for collection: $collectionId - ${result.message}" }
                // Don't fail the whole screen - just show empty books
            }
        }
    }

    /**
     * Convert domain model to UI model.
     */
    private fun CollectionBookSummary.toBookItem() =
        CollectionBookItem(
            id = id,
            title = title,
            authorNames = "", // Not provided by API
            coverPath = coverPath,
        )

    /**
     * Update the edited name field.
     */
    fun updateName(name: String) {
        state.value = state.value.copy(editedName = name)
    }

    /**
     * Save the edited collection name.
     */
    fun saveName() {
        val collection = state.value.collection ?: return
        val newName = state.value.editedName.trim()

        if (newName.isBlank()) {
            state.value = state.value.copy(error = "Collection name cannot be empty")
            return
        }

        if (newName == collection.name) {
            // No change needed
            state.value = state.value.copy(saveSuccess = true)
            return
        }

        viewModelScope.launch {
            state.value = state.value.copy(isSaving = true)

            when (val result = updateCollectionNameUseCase(collectionId, newName)) {
                is Success -> {
                    logger.info { "Updated collection name: ${result.data.name}" }
                    // SSE will update the local database
                    state.value =
                        state.value.copy(
                            isSaving = false,
                            saveSuccess = true,
                            collection = result.data,
                        )
                }
                is Failure -> {
                    logger.error { "Failed to update collection name: ${result.message}" }
                    state.value =
                        state.value.copy(
                            isSaving = false,
                            error = result.message,
                        )
                }
            }
        }
    }

    /**
     * Remove a book from the collection.
     */
    fun removeBook(bookId: String) {
        viewModelScope.launch {
            state.value = state.value.copy(removingBookId = bookId)

            when (val result = removeBookFromCollectionUseCase(collectionId, bookId)) {
                is Success -> {
                    logger.info { "Removed book $bookId from collection $collectionId" }

                    // Optimistically update the book list and count
                    val currentBooks = state.value.books.filterNot { it.id == bookId }
                    val currentCollection = state.value.collection
                    state.value =
                        state.value.copy(
                            removingBookId = null,
                            books = currentBooks,
                            collection =
                                currentCollection?.copy(
                                    bookCount = (currentCollection.bookCount - 1).coerceAtLeast(0),
                                ),
                        )
                }
                is Failure -> {
                    logger.error { "Failed to remove book from collection: ${result.message}" }
                    state.value =
                        state.value.copy(
                            removingBookId = null,
                            error = result.message,
                        )
                }
            }
        }
    }

    /**
     * Load users available for sharing.
     */
    fun loadUsersForSharing() {
        viewModelScope.launch {
            state.value = state.value.copy(isLoadingUsers = true)

            when (val result = getUsersForSharingUseCase(collectionId)) {
                is Success -> {
                    state.value =
                        state.value.copy(
                            isLoadingUsers = false,
                            availableUsers = result.data,
                        )
                }
                is Failure -> {
                    logger.error { "Failed to load users: ${result.message}" }
                    state.value =
                        state.value.copy(
                            isLoadingUsers = false,
                            error = result.message,
                        )
                }
            }
        }
    }

    /**
     * Share the collection with a user.
     */
    fun shareWithUser(userId: String) {
        viewModelScope.launch {
            state.value = state.value.copy(isSharing = true)

            when (val result = shareCollectionUseCase(collectionId, userId)) {
                is Success -> {
                    logger.info { "Shared collection with user: $userId" }

                    // Get user details from the available users list to enrich the share
                    val user = state.value.availableUsers.find { it.id == userId }
                    val enrichedShare = result.data.copy(
                        userName = user?.let {
                            it.displayName?.takeIf { name -> name.isNotBlank() }
                                ?: "${it.firstName ?: ""} ${it.lastName ?: ""}".trim().takeIf { name -> name.isNotBlank() }
                                ?: it.email
                        } ?: result.data.userName,
                        userEmail = user?.email ?: result.data.userEmail,
                    )

                    // Add to shares list
                    state.value =
                        state.value.copy(
                            isSharing = false,
                            shares = state.value.shares + enrichedShare.toShareItem(),
                            showAddMemberSheet = false,
                        )
                }
                is Failure -> {
                    logger.error { "Failed to share collection: ${result.message}" }
                    state.value =
                        state.value.copy(
                            isSharing = false,
                            error = result.message,
                        )
                }
            }
        }
    }

    /**
     * Remove a share (unshare with user).
     */
    fun removeShare(shareId: String) {
        viewModelScope.launch {
            state.value = state.value.copy(removingShareId = shareId)

            when (val result = removeCollectionShareUseCase(shareId)) {
                is Success -> {
                    logger.info { "Removed share: $shareId" }

                    // Remove from shares list
                    state.value =
                        state.value.copy(
                            removingShareId = null,
                            shares = state.value.shares.filterNot { it.id == shareId },
                        )
                }
                is Failure -> {
                    logger.error { "Failed to remove share: ${result.message}" }
                    state.value =
                        state.value.copy(
                            removingShareId = null,
                            error = result.message,
                        )
                }
            }
        }
    }

    /**
     * Show the add member sheet.
     */
    fun showAddMemberSheet() {
        state.value = state.value.copy(showAddMemberSheet = true)
        loadUsersForSharing()
    }

    /**
     * Hide the add member sheet.
     */
    fun hideAddMemberSheet() {
        state.value = state.value.copy(showAddMemberSheet = false)
    }

    /**
     * Clear the error state.
     */
    fun clearError() {
        state.value = state.value.copy(error = null)
    }

    /**
     * Clear the save success flag.
     */
    fun clearSaveSuccess() {
        state.value = state.value.copy(saveSuccess = false)
    }
}

/**
 * UI state for the admin collection detail screen.
 */
data class AdminCollectionDetailUiState(
    val isLoading: Boolean = true,
    val collection: Collection? = null,
    val editedName: String = "",
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val books: List<CollectionBookItem> = emptyList(),
    val removingBookId: String? = null,
    val error: String? = null,
    // Sharing state
    val shares: List<CollectionShareItem> = emptyList(),
    val showAddMemberSheet: Boolean = false,
    val isSharing: Boolean = false,
    val removingShareId: String? = null,
    val isLoadingUsers: Boolean = false,
    val availableUsers: List<AdminUserInfo> = emptyList(),
)

/**
 * A book item in a collection.
 *
 * This is a simplified representation for the collection detail screen.
 * Full book details can be fetched by navigating to the book detail screen.
 */
data class CollectionBookItem(
    val id: String,
    val title: String,
    val authorNames: String,
    val coverPath: String?,
)

/**
 * A share item representing a user who has access to the collection.
 */
data class CollectionShareItem(
    val id: String,
    val userId: String,
    val userName: String,
    val userEmail: String,
    val permission: String,
)
