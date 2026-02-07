package com.calypsan.listenup.client.presentation.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.domain.model.ActiveSession
import com.calypsan.listenup.client.domain.model.Shelf
import com.calypsan.listenup.client.domain.repository.ActiveSessionRepository
import com.calypsan.listenup.client.domain.repository.AuthSession
import com.calypsan.listenup.client.domain.repository.AuthState
import com.calypsan.listenup.client.domain.repository.BookRepository
import com.calypsan.listenup.client.domain.repository.DiscoveryBook
import com.calypsan.listenup.client.domain.repository.ShelfRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for the Discover screen.
 *
 * All discovery data comes from local Room database:
 * - What others are listening to: active_sessions table via SSE sync
 * - Discover something new: random unstarted books from books table
 * - Recently added: newest books from books table
 * - Shelves from other users: fetched initially from API, stored in Room, observed from Room
 *
 * This offline-first architecture ensures the Discover screen works
 * instantly on app launch without any API calls (after initial fetch).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModel(
    private val bookRepository: BookRepository,
    private val activeSessionRepository: ActiveSessionRepository,
    private val authSession: AuthSession,
    private val shelfRepository: ShelfRepository,
) : ViewModel() {
    init {
        // Fetch initial discover shelves if Room is empty
        fetchInitialShelvesIfNeeded()
    }

    // === Currently Listening State (from Room) ===

    /**
     * Observe active sessions from Room, filtered to exclude current user.
     * Automatically updates when SSE events modify the active_sessions table.
     */
    private val currentlyListeningFlow =
        authSession.authState.flatMapLatest { authState ->
            if (authState is AuthState.Authenticated) {
                activeSessionRepository.observeActiveSessions(authState.userId)
            } else {
                flowOf(emptyList())
            }
        }

    val currentlyListeningState: StateFlow<CurrentlyListeningUiState> =
        currentlyListeningFlow
            .map { sessions ->
                CurrentlyListeningUiState(
                    isLoading = false,
                    sessions = sessions.map { it.toUiModel() },
                    error = null,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = CurrentlyListeningUiState(isLoading = true),
            )

    /**
     * Convert ActiveSession domain model to UI model.
     */
    private fun ActiveSession.toUiModel(): CurrentlyListeningUiSession =
        CurrentlyListeningUiSession(
            sessionId = sessionId,
            userId = userId,
            bookId = bookId,
            bookTitle = book.title,
            authorName = book.authorName,
            coverPath = book.coverPath,
            coverBlurHash = book.coverBlurHash,
            displayName = user.displayName,
            avatarType = user.avatarType,
            avatarValue = user.avatarValue,
            avatarColor = user.avatarColor,
            startedAt = startedAtMs,
        )

    // === Discover Books State (Random Unstarted from Room) ===

    /**
     * Observe random unstarted books from Room with author info.
     * Uses RANDOM() in SQL so results change when table changes.
     */
    val discoverBooksState: StateFlow<DiscoverBooksUiState> =
        bookRepository
            .observeRandomUnstartedBooks(limit = 10)
            .map { books ->
                DiscoverBooksUiState(
                    isLoading = false,
                    books = books.map { it.toDiscoverUiBook() },
                    error = null,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = DiscoverBooksUiState(isLoading = true),
            )

    /**
     * Convert DiscoveryBook domain model to DiscoverUiBook.
     */
    private fun DiscoveryBook.toDiscoverUiBook(): DiscoverUiBook =
        DiscoverUiBook(
            id = id,
            title = title,
            authorName = authorName,
            coverPath = coverPath,
            coverBlurHash = coverBlurHash,
            seriesName = null, // Series comes from book_series relation
        )

    // === Recently Added State (from Room) ===

    /**
     * Observe recently added books from Room with author info.
     * Sorted by createdAt timestamp descending.
     */
    val recentlyAddedState: StateFlow<RecentlyAddedUiState> =
        bookRepository
            .observeRecentlyAddedBooks(limit = 10)
            .map { books ->
                RecentlyAddedUiState(
                    isLoading = false,
                    books = books.map { it.toRecentlyAddedUiBook() },
                    error = null,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = RecentlyAddedUiState(isLoading = true),
            )

    /**
     * Convert DiscoveryBook domain model to RecentlyAddedUiBook.
     */
    private fun DiscoveryBook.toRecentlyAddedUiBook(): RecentlyAddedUiBook =
        RecentlyAddedUiBook(
            id = id,
            title = title,
            authorName = authorName,
            coverPath = coverPath,
            coverBlurHash = coverBlurHash,
            createdAt = createdAt,
        )

    // === Discover Shelves State (from Room) ===

    /**
     * Observe shelves from other users from Room.
     * Initial data fetched from API if Room is empty.
     * Subsequent updates via SSE events.
     */
    private val discoverShelvesFlow =
        authSession.authState.flatMapLatest { authState ->
            if (authState is AuthState.Authenticated) {
                shelfRepository.observeDiscoverShelves(authState.userId)
            } else {
                flowOf(emptyList())
            }
        }

    val discoverShelvesState: StateFlow<DiscoverShelvesUiState> =
        discoverShelvesFlow
            .map { shelves ->
                // Group shelves by owner for display
                val groupedByOwner = shelves.groupBy { it.ownerId }
                val userShelves =
                    groupedByOwner.map { (ownerId, ownerShelves) ->
                        val firstShelf = ownerShelves.first()
                        DiscoverUserShelves(
                            user =
                                DiscoverShelfOwner(
                                    id = ownerId,
                                    displayName = firstShelf.ownerDisplayName,
                                    avatarColor = firstShelf.ownerAvatarColor,
                                ),
                            shelves = ownerShelves.map { it.toUiModel() },
                        )
                    }
                DiscoverShelvesUiState(
                    isLoading = false,
                    users = userShelves,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = DiscoverShelvesUiState(isLoading = true),
            )

    /**
     * Convert Shelf domain model to UI model.
     */
    private fun Shelf.toUiModel(): DiscoverShelfUi =
        DiscoverShelfUi(
            id = id,
            name = name,
            description = description,
            bookCount = bookCount,
            totalDurationSeconds = totalDurationSeconds,
        )

    /**
     * Fetch initial discover shelves from API if Room is empty.
     * This ensures data is available on first launch before any SSE events arrive.
     */
    private fun fetchInitialShelvesIfNeeded() {
        viewModelScope.launch {
            val authState = authSession.authState.value
            if (authState !is AuthState.Authenticated) {
                logger.debug { "Not authenticated, skipping shelf fetch" }
                return@launch
            }

            val existingCount = shelfRepository.countDiscoverShelves(authState.userId)
            if (existingCount > 0) {
                logger.debug { "Room has $existingCount discover shelves, skipping initial fetch" }
                return@launch
            }

            logger.debug { "Room is empty, fetching discover shelves from API" }
            try {
                val count = shelfRepository.fetchAndCacheDiscoverShelves()
                logger.info { "Fetched and stored $count discover shelves" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to fetch discover shelves" }
                // Not fatal - Room Flow will show empty state, SSE will populate over time
            }
        }
    }

    /**
     * Refresh discover shelves from API.
     */
    private fun refreshDiscoverShelves() {
        viewModelScope.launch {
            try {
                val count = shelfRepository.fetchAndCacheDiscoverShelves()
                logger.debug { "Refreshed $count discover shelves from API" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to refresh discover shelves" }
            }
        }
    }

    /**
     * Refresh all discovery content.
     * - Shelves: fetched from API and stored in Room
     * - Sessions: automatically updated via Room flows (synced via SSE)
     * - Books: automatically updated via Room flows (re-queries with new RANDOM seed)
     */
    fun refresh() {
        refreshDiscoverShelves()
    }
}

/**
 * UI state for the Discover shelves section.
 * Data comes from Room - no network errors possible after initial fetch.
 */
data class DiscoverShelvesUiState(
    val isLoading: Boolean = false,
    val users: List<DiscoverUserShelves> = emptyList(),
) {
    val isEmpty: Boolean
        get() = users.isEmpty() && !isLoading

    val totalShelfCount: Int
        get() = users.sumOf { it.shelves.size }
}

/**
 * User with their shelves for Discover screen.
 */
data class DiscoverUserShelves(
    val user: DiscoverShelfOwner,
    val shelves: List<DiscoverShelfUi>,
)

/**
 * Shelf owner info for display.
 */
data class DiscoverShelfOwner(
    val id: String,
    val displayName: String,
    val avatarColor: String,
)

/**
 * Shelf UI model for Discover screen.
 */
data class DiscoverShelfUi(
    val id: String,
    val name: String,
    val description: String?,
    val bookCount: Int,
    val totalDurationSeconds: Long,
)

/**
 * UI state for the "What Others Are Listening To" section.
 */
data class CurrentlyListeningUiState(
    val isLoading: Boolean = false,
    val sessions: List<CurrentlyListeningUiSession> = emptyList(),
    val error: String? = null,
) {
    val isEmpty: Boolean
        get() = sessions.isEmpty() && !isLoading
}

/**
 * UI state for the "Discover Something New" section.
 */
data class DiscoverBooksUiState(
    val isLoading: Boolean = false,
    val books: List<DiscoverUiBook> = emptyList(),
    val error: String? = null,
) {
    val isEmpty: Boolean
        get() = books.isEmpty() && !isLoading
}

/**
 * UI state for the "Recently Added" section.
 */
data class RecentlyAddedUiState(
    val isLoading: Boolean = false,
    val books: List<RecentlyAddedUiBook> = emptyList(),
    val error: String? = null,
) {
    val isEmpty: Boolean
        get() = books.isEmpty() && !isLoading
}

// === UI Model Types ===

/**
 * Active session for "What Others Are Listening To".
 * Represents a single user listening to a single book.
 */
data class CurrentlyListeningUiSession(
    val sessionId: String,
    val userId: String,
    val bookId: String,
    val bookTitle: String,
    val authorName: String?,
    val coverPath: String?,
    val coverBlurHash: String?,
    val displayName: String,
    val avatarType: String,
    val avatarValue: String?,
    val avatarColor: String,
    val startedAt: Long,
)

/**
 * Book for "Discover Something New" with resolved local cover path.
 */
data class DiscoverUiBook(
    val id: String,
    val title: String,
    val authorName: String?,
    val coverPath: String?,
    val coverBlurHash: String?,
    val seriesName: String?,
)

/**
 * Book for "Recently Added" with resolved local cover path.
 */
data class RecentlyAddedUiBook(
    val id: String,
    val title: String,
    val authorName: String?,
    val coverPath: String?,
    val coverBlurHash: String?,
    val createdAt: Long,
)
