package com.calypsan.listenup.client.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.data.local.db.UserDao
import com.calypsan.listenup.client.data.remote.AdminApiContract
import com.calypsan.listenup.client.data.remote.AdminCollectionApiContract
import com.calypsan.listenup.client.data.remote.AdminUser
import com.calypsan.listenup.client.data.remote.CollectionBookResponse
import com.calypsan.listenup.client.data.remote.ShareResponse
import com.calypsan.listenup.client.domain.model.Collection
import com.calypsan.listenup.client.domain.repository.CollectionRepository
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
    private val adminCollectionApi: AdminCollectionApiContract,
    private val adminApi: AdminApiContract,
    private val userDao: UserDao,
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
                    // Fallback to API if not in local DB yet
                    val response = adminCollectionApi.getCollection(collectionId)
                    val now = System.currentTimeMillis()
                    state.value =
                        state.value.copy(
                            isLoading = false,
                            collection =
                                Collection(
                                    id = response.id,
                                    name = response.name,
                                    bookCount = response.bookCount,
                                    createdAtMs = now,
                                    updatedAtMs = now,
                                ),
                            editedName = response.name,
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
     * Load shares for the collection from the API.
     */
    private suspend fun loadShares() {
        try {
            val shares = adminCollectionApi.getCollectionShares(collectionId)
            // Load users to get their names
            val users =
                try {
                    adminApi.getUsers()
                } catch (e: Exception) {
                    emptyList()
                }
            val userMap = users.associateBy { it.id }

            state.value =
                state.value.copy(
                    shares =
                        shares.map { share ->
                            val user = userMap[share.sharedWithUserId]
                            share.toShareItem(user)
                        },
                )
        } catch (e: Exception) {
            logger.warn(e) { "Failed to load shares for collection: $collectionId" }
            // Don't fail the whole screen - just show empty shares
        }
    }

    /**
     * Convert API response to UI model.
     *
     * Uses display name > first+last name > email > truncated ID as fallback chain.
     */
    private fun ShareResponse.toShareItem(user: AdminUser?) =
        CollectionShareItem(
            id = id,
            userId = sharedWithUserId,
            userName =
                user?.let {
                    it.displayName?.takeIf { name -> name.isNotBlank() }
                        ?: "${it.firstName ?: ""} ${it.lastName ?: ""}".trim().takeIf { name -> name.isNotBlank() }
                        ?: it.email
                } ?: "User ${sharedWithUserId.take(8)}...",
            // Fallback to truncated ID
            userEmail = user?.email ?: "",
            permission = permission,
        )

    /**
     * Load books in the collection from the API.
     */
    private suspend fun loadBooks() {
        try {
            val books = adminCollectionApi.getCollectionBooks(collectionId)
            state.value =
                state.value.copy(
                    books = books.map { it.toCollectionBookItem() },
                )
        } catch (e: Exception) {
            logger.warn(e) { "Failed to load books for collection: $collectionId" }
            // Don't fail the whole screen - just show empty books
        }
    }

    /**
     * Convert API response to UI model.
     */
    private fun CollectionBookResponse.toCollectionBookItem() =
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

            try {
                val response = adminCollectionApi.updateCollection(collectionId, newName)
                logger.info { "Updated collection name: ${response.name}" }
                // SSE will update the local database
                state.value =
                    state.value.copy(
                        isSaving = false,
                        saveSuccess = true,
                        collection = collection.copy(name = response.name),
                    )
            } catch (e: Exception) {
                logger.error(e) { "Failed to update collection name" }
                state.value =
                    state.value.copy(
                        isSaving = false,
                        error = e.message ?: "Failed to update collection",
                    )
            }
        }
    }

    /**
     * Remove a book from the collection.
     */
    fun removeBook(bookId: String) {
        viewModelScope.launch {
            state.value = state.value.copy(removingBookId = bookId)

            try {
                adminCollectionApi.removeBook(collectionId, bookId)
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
            } catch (e: Exception) {
                logger.error(e) { "Failed to remove book from collection" }
                state.value =
                    state.value.copy(
                        removingBookId = null,
                        error = e.message ?: "Failed to remove book",
                    )
            }
        }
    }

    /**
     * Load users available for sharing.
     */
    fun loadUsersForSharing() {
        viewModelScope.launch {
            state.value = state.value.copy(isLoadingUsers = true)

            try {
                val users = adminApi.getUsers()
                val currentUser = userDao.getCurrentUser()

                // Filter out:
                // 1. Users who already have a share
                // 2. The current user (can't share with yourself)
                val existingShareUserIds =
                    state.value.shares
                        .map { it.userId }
                        .toSet()
                val availableUsers =
                    users.filter { user ->
                        user.id !in existingShareUserIds && user.id != currentUser?.id
                    }
                state.value =
                    state.value.copy(
                        isLoadingUsers = false,
                        availableUsers = availableUsers,
                    )
            } catch (e: Exception) {
                logger.error(e) { "Failed to load users" }
                state.value =
                    state.value.copy(
                        isLoadingUsers = false,
                        error = e.message ?: "Failed to load users",
                    )
            }
        }
    }

    /**
     * Share the collection with a user.
     */
    fun shareWithUser(userId: String) {
        viewModelScope.launch {
            state.value = state.value.copy(isSharing = true)

            try {
                val share = adminCollectionApi.shareCollection(collectionId, userId)
                logger.info { "Shared collection with user: $userId" }

                // Get user details from the available users list
                val user = state.value.availableUsers.find { it.id == userId }

                // Add to shares list
                val newShare = share.toShareItem(user)
                state.value =
                    state.value.copy(
                        isSharing = false,
                        shares = state.value.shares + newShare,
                        showAddMemberSheet = false,
                    )
            } catch (e: Exception) {
                logger.error(e) { "Failed to share collection" }
                state.value =
                    state.value.copy(
                        isSharing = false,
                        error = e.message ?: "Failed to share collection",
                    )
            }
        }
    }

    /**
     * Remove a share (unshare with user).
     */
    fun removeShare(shareId: String) {
        viewModelScope.launch {
            state.value = state.value.copy(removingShareId = shareId)

            try {
                adminCollectionApi.deleteShare(shareId)
                logger.info { "Removed share: $shareId" }

                // Remove from shares list
                state.value =
                    state.value.copy(
                        removingShareId = null,
                        shares = state.value.shares.filterNot { it.id == shareId },
                    )
            } catch (e: Exception) {
                logger.error(e) { "Failed to remove share" }
                state.value =
                    state.value.copy(
                        removingShareId = null,
                        error = e.message ?: "Failed to remove share",
                    )
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
    val availableUsers: List<AdminUser> = emptyList(),
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
