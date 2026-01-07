package com.calypsan.listenup.client.presentation.tagdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.domain.repository.BookRepository
import com.calypsan.listenup.client.domain.model.Book
import com.calypsan.listenup.client.domain.repository.TagRepository
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
 * @property tagRepository Repository for tag data
 * @property bookRepository Repository for book data with cover path resolution
 */
class TagDetailViewModel(
    private val tagRepository: TagRepository,
    private val bookRepository: BookRepository,
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
                tagRepository.observeById(tagId),
                tagRepository.observeBookIdsForTag(tagId),
            ) { tag, bookIds ->
                Pair(tag, bookIds)
            }.collectLatest { (tag, bookIds) ->
                if (tag != null) {
                    // Fetch book domain models from repository
                    val books =
                        bookIds
                            .mapNotNull { bookId ->
                                bookRepository.getBook(bookId)
                            }.sortedBy { it.title }

                    state.value =
                        TagDetailUiState(
                            isLoading = false,
                            tagId = tagId,
                            tagName = tag.displayName(),
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
