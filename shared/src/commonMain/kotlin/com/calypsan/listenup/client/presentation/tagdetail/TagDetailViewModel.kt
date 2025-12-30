package com.calypsan.listenup.client.presentation.tagdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.data.local.db.TagDao
import com.calypsan.listenup.client.data.repository.BookRepositoryContract
import com.calypsan.listenup.client.domain.model.Book
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * ViewModel for the Tag Detail screen.
 *
 * Loads and manages tag information with its books for display.
 * Uses reactive queries so UI updates when tag data changes via SSE.
 *
 * @property tagDao DAO for tag data
 * @property bookRepository Repository for book data with cover path resolution
 */
class TagDetailViewModel(
    private val tagDao: TagDao,
    private val bookRepository: BookRepositoryContract,
) : ViewModel() {
    val state: StateFlow<TagDetailUiState>
        field = MutableStateFlow(TagDetailUiState())

    /**
     * Load tag details by ID.
     *
     * Observes the tag and its books reactively so UI updates automatically
     * when data changes (e.g., after SSE events or sync).
     *
     * @param tagId The ID of the tag to load
     */
    fun loadTag(tagId: String) {
        state.value = state.value.copy(isLoading = true)

        viewModelScope.launch {
            // Combine tag info and book IDs flows for reactive updates
            combine(
                tagDao.observeById(tagId),
                tagDao.observeBookIdsForTag(tagId),
            ) { tagEntity, bookIds ->
                Pair(tagEntity, bookIds)
            }.collectLatest { (tagEntity, bookIds) ->
                if (tagEntity != null) {
                    // Fetch book domain models from repository
                    val books =
                        bookIds
                            .mapNotNull { bookId ->
                                bookRepository.getBook(bookId.value)
                            }.sortedBy { it.title }

                    state.value =
                        TagDetailUiState(
                            isLoading = false,
                            tagId = tagId,
                            tagName = tagEntity.displayName(),
                            bookCount = books.size,
                            books = books,
                            error = null,
                        )
                } else {
                    state.value =
                        TagDetailUiState(
                            isLoading = false,
                            error = "Tag not found",
                        )
                }
            }
        }
    }
}

/**
 * UI state for the Tag Detail screen.
 */
data class TagDetailUiState(
    val isLoading: Boolean = false,
    val tagId: String = "",
    val tagName: String = "",
    val bookCount: Int = 0,
    val books: List<Book> = emptyList(),
    val error: String? = null,
)
