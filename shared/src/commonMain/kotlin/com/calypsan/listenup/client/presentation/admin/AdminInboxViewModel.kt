package com.calypsan.listenup.client.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.data.remote.AdminApiContract
import com.calypsan.listenup.client.data.remote.InboxBookResponse
import com.calypsan.listenup.client.data.sync.SSEEventType
import com.calypsan.listenup.client.data.sync.SSEManagerContract
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for the admin inbox screen.
 *
 * Manages the inbox staging workflow where newly scanned books
 * land for admin review before becoming visible to users.
 *
 * Subscribes to SSE events for real-time updates when books are
 * added to or released from the inbox.
 */
class AdminInboxViewModel(
    private val adminApi: AdminApiContract,
    private val sseManager: SSEManagerContract,
) : ViewModel() {
    val state: StateFlow<AdminInboxUiState>
        field = MutableStateFlow(AdminInboxUiState())

    init {
        loadInboxBooks()
        observeSSEEvents()
    }

    /**
     * Observe SSE events for real-time inbox updates.
     */
    private fun observeSSEEvents() {
        viewModelScope.launch {
            sseManager.eventFlow.collect { event ->
                when (event) {
                    is SSEEventType.InboxBookAdded -> {
                        handleInboxBookAdded(event)
                    }

                    is SSEEventType.InboxBookReleased -> {
                        handleInboxBookReleased(event.bookId)
                    }

                    else -> { /* Other events handled elsewhere */ }
                }
            }
        }
    }

    private fun handleInboxBookAdded(event: SSEEventType.InboxBookAdded) {
        logger.debug { "SSE: Inbox book added - ${event.bookId}" }
        // Reload to get full book details
        loadInboxBooks()
    }

    private fun handleInboxBookReleased(bookId: String) {
        logger.debug { "SSE: Inbox book released - $bookId" }
        // Remove the book from local state
        val currentBooks = state.value.books
        if (currentBooks.any { it.id == bookId }) {
            state.value =
                state.value.copy(
                    books = currentBooks.filter { it.id != bookId },
                )
        }
    }

    fun loadInboxBooks() {
        viewModelScope.launch {
            state.value = state.value.copy(isLoading = true, error = null)

            try {
                val response = adminApi.listInboxBooks()
                state.value =
                    state.value.copy(
                        isLoading = false,
                        books = response.books,
                    )
            } catch (e: Exception) {
                logger.error(e) { "Failed to load inbox books" }
                state.value =
                    state.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load inbox",
                    )
            }
        }
    }

    /**
     * Release selected books from inbox.
     *
     * If any books have no staged collections, shows a confirmation
     * warning that they will become publicly visible.
     */
    fun releaseBooks(bookIds: List<String>) {
        if (bookIds.isEmpty()) return

        viewModelScope.launch {
            state.value =
                state.value.copy(
                    isReleasing = true,
                    releasingBookIds = bookIds.toSet(),
                )

            try {
                val result = adminApi.releaseBooks(bookIds)
                logger.info {
                    "Released ${result.released} books (${result.public} public, ${result.toCollections} to collections)"
                }

                // Remove released books from local state and clear selection
                val releasedSet = bookIds.toSet()
                state.value =
                    state.value.copy(
                        isReleasing = false,
                        releasingBookIds = emptySet(),
                        selectedBookIds = emptySet(), // Clear selection after successful release
                        books = state.value.books.filter { it.id !in releasedSet },
                        lastReleaseResult =
                            ReleaseResult(
                                released = result.released,
                                public = result.public,
                                toCollections = result.toCollections,
                            ),
                    )
            } catch (e: Exception) {
                logger.error(e) { "Failed to release books" }
                state.value =
                    state.value.copy(
                        isReleasing = false,
                        releasingBookIds = emptySet(),
                        error = e.message ?: "Failed to release books",
                    )
            }
        }
    }

    /**
     * Stage a collection assignment for an inbox book.
     */
    fun stageCollection(
        bookId: String,
        collectionId: String,
    ) {
        viewModelScope.launch {
            state.value = state.value.copy(stagingBookId = bookId)
            try {
                adminApi.stageCollection(bookId, collectionId)
                // Reload to get updated staged collections
                loadInboxBooks()
            } catch (e: Exception) {
                logger.error(e) { "Failed to stage collection" }
                state.value =
                    state.value.copy(
                        error = e.message ?: "Failed to stage collection",
                    )
            } finally {
                state.value = state.value.copy(stagingBookId = null)
            }
        }
    }

    /**
     * Remove a staged collection from an inbox book.
     */
    fun unstageCollection(
        bookId: String,
        collectionId: String,
    ) {
        viewModelScope.launch {
            state.value = state.value.copy(stagingBookId = bookId)
            try {
                adminApi.unstageCollection(bookId, collectionId)
                // Reload to get updated staged collections
                loadInboxBooks()
            } catch (e: Exception) {
                logger.error(e) { "Failed to unstage collection" }
                state.value =
                    state.value.copy(
                        error = e.message ?: "Failed to unstage collection",
                    )
            } finally {
                state.value = state.value.copy(stagingBookId = null)
            }
        }
    }

    /**
     * Toggle selection of a book for batch operations.
     */
    fun toggleBookSelection(bookId: String) {
        val currentSelection = state.value.selectedBookIds
        val newSelection =
            if (bookId in currentSelection) {
                currentSelection - bookId
            } else {
                currentSelection + bookId
            }
        state.value = state.value.copy(selectedBookIds = newSelection)
    }

    /**
     * Select all books.
     */
    fun selectAll() {
        state.value =
            state.value.copy(
                selectedBookIds =
                    state.value.books
                        .map { it.id }
                        .toSet(),
            )
    }

    /**
     * Clear all selections.
     */
    fun clearSelection() {
        state.value = state.value.copy(selectedBookIds = emptySet())
    }

    /**
     * Check if any selected books have no staged collections.
     * Used to show public visibility warning before release.
     */
    fun hasSelectedBooksWithoutCollections(): Boolean {
        val selectedIds = state.value.selectedBookIds
        return state.value.books
            .filter { it.id in selectedIds }
            .any { it.stagedCollectionIds.isEmpty() }
    }

    fun clearError() {
        state.value = state.value.copy(error = null)
    }

    fun clearReleaseResult() {
        state.value = state.value.copy(lastReleaseResult = null)
    }
}

/**
 * UI state for the admin inbox screen.
 */
data class AdminInboxUiState(
    val isLoading: Boolean = true,
    val books: List<InboxBookResponse> = emptyList(),
    val selectedBookIds: Set<String> = emptySet(),
    val isReleasing: Boolean = false,
    val releasingBookIds: Set<String> = emptySet(),
    val stagingBookId: String? = null,
    val lastReleaseResult: ReleaseResult? = null,
    val error: String? = null,
) {
    val hasBooks: Boolean get() = books.isNotEmpty()
    val hasSelection: Boolean get() = selectedBookIds.isNotEmpty()
    val selectedCount: Int get() = selectedBookIds.size
    val allSelected: Boolean get() = selectedBookIds.size == books.size && books.isNotEmpty()

    /** Check if a specific book is currently being staged/unstaged */
    fun isBookStaging(bookId: String): Boolean = stagingBookId == bookId
}

/**
 * Result of releasing books from inbox.
 */
data class ReleaseResult(
    val released: Int,
    val public: Int,
    val toCollections: Int,
)
