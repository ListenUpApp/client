package com.calypsan.listenup.client.presentation.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.domain.model.ActiveSession
import com.calypsan.listenup.client.domain.model.Lens
import com.calypsan.listenup.client.domain.repository.ActiveSessionRepository
import com.calypsan.listenup.client.domain.repository.AuthSession
import com.calypsan.listenup.client.domain.repository.AuthState
import com.calypsan.listenup.client.domain.repository.BookRepository
import com.calypsan.listenup.client.domain.repository.DiscoveryBook
import com.calypsan.listenup.client.domain.repository.LensRepository
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
 * - Lenses from other users: fetched initially from API, stored in Room, observed from Room
 *
 * This offline-first architecture ensures the Discover screen works
 * instantly on app launch without any API calls (after initial fetch).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModel(
    private val bookRepository: BookRepository,
    private val activeSessionRepository: ActiveSessionRepository,
    private val authSession: AuthSession,
    private val lensRepository: LensRepository,
) : ViewModel() {
    init {
        // Fetch initial discover lenses if Room is empty
        fetchInitialLensesIfNeeded()
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

    // === Discover Lenses State (from Room) ===

    /**
     * Observe lenses from other users from Room.
     * Initial data fetched from API if Room is empty.
     * Subsequent updates via SSE events.
     */
    private val discoverLensesFlow =
        authSession.authState.flatMapLatest { authState ->
            if (authState is AuthState.Authenticated) {
                lensRepository.observeDiscoverLenses(authState.userId)
            } else {
                flowOf(emptyList())
            }
        }

    val discoverLensesState: StateFlow<DiscoverLensesUiState> =
        discoverLensesFlow
            .map { lenses ->
                // Group lenses by owner for display
                val groupedByOwner = lenses.groupBy { it.ownerId }
                val userLenses =
                    groupedByOwner.map { (ownerId, ownerLenses) ->
                        val firstLens = ownerLenses.first()
                        DiscoverUserLenses(
                            user =
                                DiscoverLensOwner(
                                    id = ownerId,
                                    displayName = firstLens.ownerDisplayName,
                                    avatarColor = firstLens.ownerAvatarColor,
                                ),
                            lenses = ownerLenses.map { it.toUiModel() },
                        )
                    }
                DiscoverLensesUiState(
                    isLoading = false,
                    users = userLenses,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = DiscoverLensesUiState(isLoading = true),
            )

    /**
     * Convert Lens domain model to UI model.
     */
    private fun Lens.toUiModel(): DiscoverLensUi =
        DiscoverLensUi(
            id = id,
            name = name,
            description = description,
            bookCount = bookCount,
            totalDurationSeconds = totalDurationSeconds,
        )

    /**
     * Fetch initial discover lenses from API if Room is empty.
     * This ensures data is available on first launch before any SSE events arrive.
     */
    private fun fetchInitialLensesIfNeeded() {
        viewModelScope.launch {
            val authState = authSession.authState.value
            if (authState !is AuthState.Authenticated) {
                logger.debug { "Not authenticated, skipping lens fetch" }
                return@launch
            }

            val existingCount = lensRepository.countDiscoverLenses(authState.userId)
            if (existingCount > 0) {
                logger.debug { "Room has $existingCount discover lenses, skipping initial fetch" }
                return@launch
            }

            logger.debug { "Room is empty, fetching discover lenses from API" }
            try {
                val count = lensRepository.fetchAndCacheDiscoverLenses()
                logger.info { "Fetched and stored $count discover lenses" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to fetch discover lenses" }
                // Not fatal - Room Flow will show empty state, SSE will populate over time
            }
        }
    }

    /**
     * Refresh discover lenses from API.
     */
    private fun refreshDiscoverLenses() {
        viewModelScope.launch {
            try {
                val count = lensRepository.fetchAndCacheDiscoverLenses()
                logger.debug { "Refreshed $count discover lenses from API" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to refresh discover lenses" }
            }
        }
    }

    /**
     * Refresh all discovery content.
     * - Lenses: fetched from API and stored in Room
     * - Sessions: automatically updated via Room flows (synced via SSE)
     * - Books: automatically updated via Room flows (re-queries with new RANDOM seed)
     */
    fun refresh() {
        refreshDiscoverLenses()
    }
}

/**
 * UI state for the Discover lenses section.
 * Data comes from Room - no network errors possible after initial fetch.
 */
data class DiscoverLensesUiState(
    val isLoading: Boolean = false,
    val users: List<DiscoverUserLenses> = emptyList(),
) {
    val isEmpty: Boolean
        get() = users.isEmpty() && !isLoading

    val totalLensCount: Int
        get() = users.sumOf { it.lenses.size }
}

/**
 * User with their lenses for Discover screen.
 */
data class DiscoverUserLenses(
    val user: DiscoverLensOwner,
    val lenses: List<DiscoverLensUi>,
)

/**
 * Lens owner info for display.
 */
data class DiscoverLensOwner(
    val id: String,
    val displayName: String,
    val avatarColor: String,
)

/**
 * Lens UI model for Discover screen.
 */
data class DiscoverLensUi(
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
