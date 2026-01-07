package com.calypsan.listenup.client.presentation.lens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.core.Success
import com.calypsan.listenup.client.domain.model.LensBook
import com.calypsan.listenup.client.domain.model.LensDetail
import com.calypsan.listenup.client.domain.repository.UserRepository
import com.calypsan.listenup.client.domain.usecase.lens.LoadLensDetailUseCase
import com.calypsan.listenup.client.domain.usecase.lens.RemoveBookFromLensUseCase
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for the Lens Detail screen.
 *
 * Fetches lens detail from the server and displays:
 * - Lens name, description, owner info
 * - Book count and total duration
 * - List of books in the lens
 */
class LensDetailViewModel(
    private val loadLensDetailUseCase: LoadLensDetailUseCase,
    private val removeBookFromLensUseCase: RemoveBookFromLensUseCase,
    private val userRepository: UserRepository,
) : ViewModel() {
    val state: StateFlow<LensDetailUiState>
        field = MutableStateFlow(LensDetailUiState())

    private var currentLensId: String? = null

    /**
     * Load lens detail from the server.
     */
    fun loadLens(lensId: String) {
        if (lensId == currentLensId && !state.value.isLoading && state.value.lensDetail != null) {
            return // Already loaded
        }
        currentLensId = lensId

        viewModelScope.launch {
            state.update { it.copy(isLoading = true, error = null) }

            // Get current user ID for ownership check
            val currentUserId = userRepository.observeCurrentUser().first()?.id

            // Load lens detail via use case (handles API call, cache update, and cover resolution)
            when (val result = loadLensDetailUseCase(lensId)) {
                is Success -> {
                    val lensDetail = result.data
                    state.update {
                        it.copy(
                            isLoading = false,
                            lensDetail = lensDetail,
                            isOwner = currentUserId == lensDetail.owner.id,
                            error = null,
                        )
                    }
                    logger.debug { "Loaded lens detail: ${lensDetail.name}" }
                }
                else -> {
                    val errorMessage = (result as? com.calypsan.listenup.client.core.Failure)?.message
                        ?: "Failed to load lens"
                    logger.error { "Failed to load lens: $lensId - $errorMessage" }
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
     * Remove a book from the lens.
     */
    fun removeBook(bookId: String) {
        val lensId = currentLensId ?: return

        viewModelScope.launch {
            when (val result = removeBookFromLensUseCase(lensId, bookId)) {
                is Success -> {
                    // Reload to get updated book list
                    currentLensId = null // Force reload
                    loadLens(lensId)
                    logger.info { "Removed book $bookId from lens $lensId" }
                }
                else -> {
                    val errorMessage = (result as? com.calypsan.listenup.client.core.Failure)?.message
                        ?: "Failed to remove book"
                    logger.error { "Failed to remove book from lens: $errorMessage" }
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
        val minutes = (seconds % 3600) / 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }
}

/**
 * UI state for the Lens Detail screen.
 */
data class LensDetailUiState(
    val isLoading: Boolean = true,
    val lensDetail: LensDetail? = null,
    val isOwner: Boolean = false,
    val error: String? = null,
) {
    val name: String get() = lensDetail?.name ?: ""
    val description: String get() = lensDetail?.description ?: ""
    val ownerDisplayName: String get() = lensDetail?.owner?.displayName ?: ""
    val ownerAvatarColor: String get() = lensDetail?.owner?.avatarColor ?: "#6B7280"
    val bookCount: Int get() = lensDetail?.bookCount ?: 0
    val totalDurationSeconds: Long get() = lensDetail?.totalDurationSeconds ?: 0
    val books: List<LensBook> get() = lensDetail?.books ?: emptyList()
}
