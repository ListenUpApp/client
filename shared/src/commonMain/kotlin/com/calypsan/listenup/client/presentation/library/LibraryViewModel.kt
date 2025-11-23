package com.calypsan.listenup.client.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.data.repository.BookRepository
import com.calypsan.listenup.client.data.sync.SyncManager
import com.calypsan.listenup.client.data.sync.SyncStatus
import com.calypsan.listenup.client.domain.model.Book
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Library screen.
 *
 * Manages book list data and sync state for UI consumption.
 * Follows modern Android architecture with StateFlow for reactive UI updates.
 *
 * Data flows:
 * - books: Reactive list from Room database via BookRepository
 * - syncState: Current sync status from SyncManager
 *
 * @property bookRepository Repository for book data operations
 * @property syncManager Manager for sync operations
 */
class LibraryViewModel(
    private val bookRepository: BookRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    /**
     * Observable list of books.
     *
     * Automatically updates when Room database changes (after sync, etc.).
     * WhileSubscribed(5000) keeps Flow active for 5 seconds after last collector
     * to handle configuration changes without restarting collection.
     */
    val books: StateFlow<List<Book>> = bookRepository.observeBooks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Observable sync status.
     *
     * UI can observe this to show:
     * - Loading indicators (Syncing state)
     * - Error messages (Error state)
     * - Success confirmations (Success state)
     */
    val syncState: StateFlow<SyncStatus> = syncManager.syncState

    init {
        // Trigger initial sync when ViewModel is created
        refreshBooks()
    }

    /**
     * Handle UI events from the Library screen.
     *
     * Processes user actions and updates state accordingly.
     *
     * @param event The UI event to handle
     */
    fun onEvent(event: LibraryUiEvent) {
        when (event) {
            is LibraryUiEvent.RefreshRequested -> refreshBooks()
            is LibraryUiEvent.BookClicked -> {
                // TODO: Navigate to book detail screen
                // For now, this is a no-op until navigation is wired up
            }
        }
    }

    /**
     * Trigger a refresh/sync of books from the server.
     *
     * Launches sync in background. UI will update automatically
     * via books StateFlow when sync completes.
     */
    private fun refreshBooks() {
        viewModelScope.launch {
            bookRepository.refreshBooks()
        }
    }
}

/**
 * Events that can be triggered from the Library UI.
 *
 * Sealed interface for type-safe event handling.
 */
sealed interface LibraryUiEvent {
    /**
     * User requested a refresh (e.g., pull-to-refresh).
     */
    data object RefreshRequested : LibraryUiEvent

    /**
     * User clicked on a book in the library.
     *
     * @property bookId ID of the clicked book
     */
    data class BookClicked(val bookId: String) : LibraryUiEvent
}
