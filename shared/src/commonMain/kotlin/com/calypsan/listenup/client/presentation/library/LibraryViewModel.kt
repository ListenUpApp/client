package com.calypsan.listenup.client.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.data.local.db.SyncDao
import com.calypsan.listenup.client.data.local.db.getLastSyncTime
import com.calypsan.listenup.client.data.repository.BookRepository
import com.calypsan.listenup.client.data.repository.SettingsRepository
import com.calypsan.listenup.client.data.sync.SyncManager
import com.calypsan.listenup.client.data.sync.SyncStatus
import com.calypsan.listenup.client.domain.model.Book
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for the Library screen.
 *
 * Manages book list data and sync state for UI consumption.
 * Implements intelligent auto-sync: triggers initial sync automatically
 * if user is authenticated but has never synced before.
 *
 * This self-healing approach works for all entry points:
 * - First time after registration
 * - First time after login
 * - App restart with valid session
 *
 * @property bookRepository Repository for book data operations
 * @property syncManager Manager for sync operations
 * @property settingsRepository For checking authentication state
 * @property syncDao For checking if initial sync has occurred
 */
class LibraryViewModel(
    private val bookRepository: BookRepository,
    private val syncManager: SyncManager,
    private val settingsRepository: SettingsRepository,
    private val syncDao: SyncDao
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

    private var hasPerformedInitialSync = false

    init {
        // Auto-sync is deferred to onScreenVisible() to avoid syncing
        // when ViewModel is created but screen isn't actually shown yet
        logger.debug { "Initialized (auto-sync deferred until screen visible)" }
    }

    /**
     * Called when the Library screen becomes visible.
     *
     * Performs intelligent auto-sync on first visibility:
     * - Triggers initial sync if authenticated but never synced
     * - Handles all entry points: registration, login, app restart
     * - Only runs once per ViewModel instance
     */
    fun onScreenVisible() {
        if (hasPerformedInitialSync) {
            return
        }
        hasPerformedInitialSync = true

        logger.debug { "Screen became visible, checking if initial sync needed..." }
        viewModelScope.launch {
            val isAuthenticated = settingsRepository.getAccessToken() != null
            val lastSyncTime = syncDao.getLastSyncTime()

            logger.debug { "isAuthenticated=$isAuthenticated, lastSyncTime=$lastSyncTime" }

            if (isAuthenticated && lastSyncTime == null) {
                logger.info { "User authenticated but never synced, triggering initial sync..." }
                refreshBooks()
            } else if (!isAuthenticated) {
                logger.debug { "User not authenticated, skipping sync" }
            } else {
                logger.debug { "Already synced (last sync: $lastSyncTime), skipping auto-sync" }
            }
        }
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
