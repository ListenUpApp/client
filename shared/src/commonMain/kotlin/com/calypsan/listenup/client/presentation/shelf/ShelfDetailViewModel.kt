package com.calypsan.listenup.client.presentation.shelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.domain.model.ShelfBook
import com.calypsan.listenup.client.domain.model.ShelfDetail
import com.calypsan.listenup.client.domain.repository.UserRepository
import com.calypsan.listenup.client.domain.usecase.shelf.LoadShelfDetailUseCase
import com.calypsan.listenup.client.domain.usecase.shelf.RemoveBookFromShelfUseCase
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for the Shelf Detail screen.
 *
 * Fetches shelf detail from the server and displays:
 * - Shelf name, description, owner info
 * - Book count and total duration
 * - List of books in the shelf
 */
class ShelfDetailViewModel(
    private val loadShelfDetailUseCase: LoadShelfDetailUseCase,
    private val removeBookFromShelfUseCase: RemoveBookFromShelfUseCase,
    private val userRepository: UserRepository,
) : ViewModel() {
    val state: StateFlow<ShelfDetailUiState>
        field = MutableStateFlow(ShelfDetailUiState())

    private var currentShelfId: String? = null

    /**
     * Load shelf detail from the server.
     */
    fun loadShelf(shelfId: String) {
        // Always re-fetch from server to ensure fresh data after book additions
        // (previously cached and skipped re-fetch, but that caused stale data)
        currentShelfId = shelfId

        viewModelScope.launch {
            state.update { it.copy(isLoading = true, error = null) }

            // Get current user ID for ownership check
            val currentUserId =
                userRepository
                    .observeCurrentUser()
                    .first()
                    ?.id
                    ?.value

            // Load shelf detail via use case (handles API call, cache update, and cover resolution)
            when (val result = loadShelfDetailUseCase(shelfId)) {
                is Success -> {
                    val shelfDetail = result.data
                    state.update {
                        it.copy(
                            isLoading = false,
                            shelfDetail = shelfDetail,
                            isOwner = currentUserId == shelfDetail.owner.id,
                            error = null,
                        )
                    }
                    logger.debug { "Loaded shelf detail: ${shelfDetail.name}" }
                }

                else -> {
                    val errorMessage =
                        (result as? com.calypsan.listenup.client.core.Failure)?.message
                            ?: "Failed to load shelf"
                    logger.error { "Failed to load shelf: $shelfId - $errorMessage" }
                    state.update {
                        it.copy(
                            isLoading = false,
                            error = errorMessage,
                        )
                    }
                }
            }
        }
    }

    /**
     * Remove a book from the shelf.
     */
    fun removeBook(bookId: String) {
        val shelfId = currentShelfId ?: return

        viewModelScope.launch {
            when (val result = removeBookFromShelfUseCase(shelfId, bookId)) {
                is Success -> {
                    // Reload to get updated book list
                    currentShelfId = null // Force reload
                    loadShelf(shelfId)
                    logger.info { "Removed book $bookId from shelf $shelfId" }
                }

                else -> {
                    val errorMessage =
                        (result as? com.calypsan.listenup.client.core.Failure)?.message
                            ?: "Failed to remove book"
                    logger.error { "Failed to remove book from shelf: $errorMessage" }
                    state.update {
                        it.copy(error = errorMessage)
                    }
                }
            }
        }
    }

    /**
     * Format duration in seconds to human-readable string.
     */
    fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = seconds % 3600 / 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
}

/**
 * UI state for the Shelf Detail screen.
 */
data class ShelfDetailUiState(
    val isLoading: Boolean = true,
    val shelfDetail: ShelfDetail? = null,
    val isOwner: Boolean = false,
    val error: String? = null,
) {
    val name: String get() = shelfDetail?.name ?: ""
    val description: String get() = shelfDetail?.description ?: ""
    val ownerDisplayName: String get() = shelfDetail?.owner?.displayName ?: ""
    val ownerAvatarColor: String get() = shelfDetail?.owner?.avatarColor ?: "#6B7280"
    val bookCount: Int get() = shelfDetail?.bookCount ?: 0
    val totalDurationSeconds: Long get() = shelfDetail?.totalDurationSeconds ?: 0
    val books: List<ShelfBook> get() = shelfDetail?.books ?: emptyList()
}
