package com.calypsan.listenup.client.presentation.lens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.data.local.db.BookId
import com.calypsan.listenup.client.data.local.db.LensDao
import com.calypsan.listenup.client.data.local.db.UserDao
import com.calypsan.listenup.client.data.local.images.ImageStorage
import com.calypsan.listenup.client.data.remote.LensApiContract
import com.calypsan.listenup.client.data.remote.LensBookResponse
import com.calypsan.listenup.client.data.remote.LensDetailResponse
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
    private val lensApi: LensApiContract,
    private val lensDao: LensDao,
    private val userDao: UserDao,
    private val imageStorage: ImageStorage,
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

            try {
                // Get current user ID for ownership check
                val currentUserId = userDao.observeCurrentUser().first()?.id

                // Fetch lens detail from server
                val lensDetail = lensApi.getLens(lensId)

                // Update local cache
                lensDao.getById(lensId)?.let { cached ->
                    lensDao.upsert(
                        cached.copy(
                            bookCount = lensDetail.bookCount,
                            totalDurationSeconds = lensDetail.totalDuration,
                        ),
                    )
                }

                // Map books to use local cover paths instead of server paths
                val booksWithLocalCovers = lensDetail.books.map { book ->
                    val bookId = BookId(book.id)
                    val localCoverPath = if (imageStorage.exists(bookId)) {
                        imageStorage.getCoverPath(bookId)
                    } else {
                        null
                    }
                    book.copy(coverPath = localCoverPath)
                }

                state.update {
                    it.copy(
                        isLoading = false,
                        lensDetail = lensDetail.copy(books = booksWithLocalCovers),
                        isOwner = currentUserId == lensDetail.owner.id,
                        error = null,
                    )
                }
                logger.debug { "Loaded lens detail: ${lensDetail.name}" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load lens: $lensId" }
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
     * Remove a book from the lens.
     */
    fun removeBook(bookId: String) {
        val lensId = currentLensId ?: return

        viewModelScope.launch {
            try {
                lensApi.removeBook(lensId, bookId)
                // Reload to get updated book list
                currentLensId = null // Force reload
                loadLens(lensId)
                logger.info { "Removed book $bookId from lens $lensId" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to remove book from lens" }
                state.update {
                    it.copy(error = "Failed to remove book: ${e.message}")
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
    val lensDetail: LensDetailResponse? = null,
    val isOwner: Boolean = false,
    val error: String? = null,
) {
    val name: String get() = lensDetail?.name ?: ""
    val description: String get() = lensDetail?.description ?: ""
    val ownerDisplayName: String get() = lensDetail?.owner?.displayName ?: ""
    val ownerAvatarColor: String get() = lensDetail?.owner?.avatarColor ?: "#6B7280"
    val bookCount: Int get() = lensDetail?.bookCount ?: 0
    val totalDurationSeconds: Long get() = lensDetail?.totalDuration ?: 0
    val books: List<LensBookResponse> get() = lensDetail?.books ?: emptyList()
}
