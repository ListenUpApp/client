package com.calypsan.listenup.client.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.domain.model.Book
import com.calypsan.listenup.client.domain.model.SyncState
import com.calypsan.listenup.client.domain.model.ContributorWithBookCount
import com.calypsan.listenup.client.domain.model.SeriesWithBooks
import com.calypsan.listenup.client.domain.repository.BookRepository
import com.calypsan.listenup.client.domain.repository.ContributorRepository
import com.calypsan.listenup.client.domain.repository.PlaybackPositionRepository
import com.calypsan.listenup.client.domain.repository.SeriesRepository
import com.calypsan.listenup.client.domain.repository.SettingsRepository
import com.calypsan.listenup.client.domain.repository.SyncRepository
import com.calypsan.listenup.client.domain.repository.SyncStatusRepository
import com.calypsan.listenup.client.util.sortableTitle
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for the Library screen content.
 *
 * Manages book list data, sort state, and sync state for UI consumption.
 * Implements intelligent auto-sync: triggers initial sync automatically
 * if user is authenticated but has never synced before.
 *
 * Sort state is separated into category + direction, allowing users to:
 * - Toggle direction with a single tap
 * - Change category via dropdown menu
 *
 * Selection state is managed by [LibrarySelectionManager] which is shared
 * with [LibraryActionsViewModel] for coordinated batch operations.
 */
class LibraryViewModel(
    private val bookRepository: BookRepository,
    private val seriesRepository: SeriesRepository,
    private val contributorRepository: ContributorRepository,
    private val playbackPositionRepository: PlaybackPositionRepository,
    private val syncRepository: SyncRepository,
    private val settingsRepository: SettingsRepository,
    private val syncStatusRepository: SyncStatusRepository,
    private val selectionManager: LibrarySelectionManager,
) : ViewModel() {
    // ═══════════════════════════════════════════════════════════════════════
    // SORT STATE
    // ═══════════════════════════════════════════════════════════════════════

    val booksSortState: StateFlow<SortState>
        field = MutableStateFlow(SortState.booksDefault)

    val seriesSortState: StateFlow<SortState>
        field = MutableStateFlow(SortState.seriesDefault)

    val authorsSortState: StateFlow<SortState>
        field = MutableStateFlow(SortState.contributorDefault)

    val narratorsSortState: StateFlow<SortState>
        field = MutableStateFlow(SortState.contributorDefault)

    /** Article handling for title sort (A, An, The). */
    val ignoreTitleArticles: StateFlow<Boolean>
        field = MutableStateFlow(true)

    /** Series display preferences. */
    val hideSingleBookSeries: StateFlow<Boolean>
        field = MutableStateFlow(true)

    // ═══════════════════════════════════════════════════════════════════════
    // LOADING STATE
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Tracks whether initial database load has completed.
     * Used to distinguish "loading" from "truly empty" in UI.
     */
    val hasLoadedBooks: StateFlow<Boolean>
        field = MutableStateFlow(false)

    // ═══════════════════════════════════════════════════════════════════════
    // CONTENT LISTS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Observable list of books, sorted by current sort state.
     *
     * Uses SharingStarted.Eagerly so database loading begins immediately when
     * the ViewModel is created (at AppShell level), not when Library screen
     * is first displayed. This eliminates the "Loading library..." flash.
     */
    val books: StateFlow<List<Book>> =
        combine(
            bookRepository.observeBooks(),
            booksSortState,
            ignoreTitleArticles,
        ) { books, sortState, ignoreArticles ->
            sortBooks(books, sortState, ignoreArticles)
        }.onEach {
            // Mark as loaded after first database emission
            if (!hasLoadedBooks.value) {
                hasLoadedBooks.value = true
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList(),
        )

    /**
     * Observable list of series with their books, sorted and filtered by current state.
     * Filters out single-book series when hideSingleBookSeries is enabled.
     */
    val series: StateFlow<List<SeriesWithBooks>> =
        combine(
            seriesRepository.observeAllWithBooks(),
            seriesSortState,
            hideSingleBookSeries,
        ) { series, sortState, hideSingle ->
            val filtered =
                if (hideSingle) {
                    series.filter { it.books.size > 1 }
                } else {
                    series
                }
            sortSeries(filtered, sortState)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    /**
     * Observable list of authors with book counts, sorted by current sort state.
     */
    val authors: StateFlow<List<ContributorWithBookCount>> =
        combine(
            contributorRepository.observeContributorsByRole("author"),
            authorsSortState,
        ) { authors, sortState ->
            sortContributors(authors, sortState)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    /**
     * Observable list of narrators with book counts, sorted by current sort state.
     */
    val narrators: StateFlow<List<ContributorWithBookCount>> =
        combine(
            contributorRepository.observeContributorsByRole("narrator"),
            narratorsSortState,
        ) { narrators, sortState ->
            sortContributors(narrators, sortState)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    // ═══════════════════════════════════════════════════════════════════════
    // SYNC STATE
    // ═══════════════════════════════════════════════════════════════════════

    /** Observable sync status. */
    val syncState: StateFlow<SyncState> = syncRepository.syncState

    // ═══════════════════════════════════════════════════════════════════════
    // PROGRESS TRACKING
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Observable progress data for all books.
     * Maps bookId -> progress (0.0 to 1.0).
     * Used for showing progress indicators on book cards.
     *
     * Includes both in-progress books (< 99%) and completed books (>= 99%).
     * BookCard uses this to show progress overlay for in-progress,
     * and a completion badge for completed books.
     */
    val bookProgress: StateFlow<Map<String, Float>> =
        combine(
            playbackPositionRepository.observeAll(),
            books,
        ) { positions, booksList ->
            // Create a map of book durations for O(1) lookup
            val bookDurations = booksList.associate { it.id.value to it.duration }

            // Build progress map with capacity hint to avoid resizing
            buildMap(positions.size) {
                for ((bookId, position) in positions) {
                    val duration = bookDurations[bookId] ?: continue
                    if (duration <= 0) continue

                    val progress = (position.positionMs.toFloat() / duration).coerceIn(0f, 1f)
                    // Include all books with any progress (both in-progress and completed)
                    if (progress > 0f) {
                        put(bookId, progress)
                    }
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap(),
        )

    // ═══════════════════════════════════════════════════════════════════════
    // SELECTION STATE (delegated to shared manager)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Current selection mode, observed from the shared [LibrarySelectionManager].
     */
    val selectionMode: StateFlow<SelectionMode> = selectionManager.selectionMode

    // ═══════════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════════════

    private var hasPerformedInitialSync = false

    init {
        // Load persisted sort states and preferences
        viewModelScope.launch {
            settingsRepository.getBooksSortState()?.let { key ->
                SortState.fromPersistenceKey(key)?.let { booksSortState.value = it }
            }
            settingsRepository.getSeriesSortState()?.let { key ->
                SortState.fromPersistenceKey(key)?.let { seriesSortState.value = it }
            }
            settingsRepository.getAuthorsSortState()?.let { key ->
                SortState.fromPersistenceKey(key)?.let { authorsSortState.value = it }
            }
            settingsRepository.getNarratorsSortState()?.let { key ->
                SortState.fromPersistenceKey(key)?.let { narratorsSortState.value = it }
            }
            // Load article handling preference
            ignoreTitleArticles.value = settingsRepository.getIgnoreTitleArticles()
            // Load series display preference
            hideSingleBookSeries.value = settingsRepository.getHideSingleBookSeries()
        }

        logger.debug { "Initialized (auto-sync deferred until screen visible)" }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Called when the Library screen becomes visible.
     * Reloads preferences that may have changed in Settings.
     */
    fun onScreenVisible() {
        // Reload preferences in case user changed them in Settings
        viewModelScope.launch {
            hideSingleBookSeries.value = settingsRepository.getHideSingleBookSeries()
            ignoreTitleArticles.value = settingsRepository.getIgnoreTitleArticles()
        }

        if (hasPerformedInitialSync) return
        hasPerformedInitialSync = true

        logger.debug { "Screen became visible, checking if initial sync needed..." }
        viewModelScope.launch {
            val isAuthenticated = settingsRepository.getAccessToken() != null
            val lastSyncTime = syncStatusRepository.getLastSyncTime()

            if (isAuthenticated && lastSyncTime == null) {
                logger.info { "User authenticated but never synced, triggering initial sync..." }
                refreshBooks()
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // UI EVENTS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Handle UI events from the Library screen.
     */
    fun onEvent(event: LibraryUiEvent) {
        when (event) {
            is LibraryUiEvent.RefreshRequested -> {
                refreshBooks()
            }

            is LibraryUiEvent.BookClicked -> { /* Navigation handled by parent */ }

            // Books tab sort events
            is LibraryUiEvent.BooksCategoryChanged -> {
                updateBooksSortState(booksSortState.value.withCategory(event.category))
            }

            is LibraryUiEvent.BooksDirectionToggled -> {
                updateBooksSortState(booksSortState.value.toggleDirection())
            }

            // Series tab sort events
            is LibraryUiEvent.SeriesCategoryChanged -> {
                updateSeriesSortState(seriesSortState.value.withCategory(event.category))
            }

            is LibraryUiEvent.SeriesDirectionToggled -> {
                updateSeriesSortState(seriesSortState.value.toggleDirection())
            }

            // Authors tab sort events
            is LibraryUiEvent.AuthorsCategoryChanged -> {
                updateAuthorsSortState(authorsSortState.value.withCategory(event.category))
            }

            is LibraryUiEvent.AuthorsDirectionToggled -> {
                updateAuthorsSortState(authorsSortState.value.toggleDirection())
            }

            // Narrators tab sort events
            is LibraryUiEvent.NarratorsCategoryChanged -> {
                updateNarratorsSortState(narratorsSortState.value.withCategory(event.category))
            }

            is LibraryUiEvent.NarratorsDirectionToggled -> {
                updateNarratorsSortState(narratorsSortState.value.toggleDirection())
            }

            // Title sort article handling
            is LibraryUiEvent.ToggleIgnoreTitleArticles -> {
                toggleIgnoreTitleArticles()
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SELECTION ACTIONS (delegated to shared manager)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Enter selection mode with the given book as the initial selection.
     *
     * @param bookId The ID of the book that was long-pressed
     */
    fun enterSelectionMode(bookId: String) = selectionManager.enterSelectionMode(bookId)

    /**
     * Toggle the selection state of a book.
     *
     * @param bookId The ID of the book to toggle
     */
    fun toggleBookSelection(bookId: String) = selectionManager.toggleSelection(bookId)

    /**
     * Exit selection mode and clear all selections.
     */
    fun exitSelectionMode() = selectionManager.exitSelectionMode()

    // ═══════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    private fun toggleIgnoreTitleArticles() {
        val newValue = !ignoreTitleArticles.value
        ignoreTitleArticles.value = newValue
        viewModelScope.launch {
            settingsRepository.setIgnoreTitleArticles(newValue)
        }
    }

    private fun refreshBooks() {
        viewModelScope.launch {
            bookRepository.refreshBooks()
        }
    }

    private fun updateBooksSortState(state: SortState) {
        booksSortState.value = state
        viewModelScope.launch {
            settingsRepository.setBooksSortState(state.persistenceKey)
        }
    }

    private fun updateSeriesSortState(state: SortState) {
        seriesSortState.value = state
        viewModelScope.launch {
            settingsRepository.setSeriesSortState(state.persistenceKey)
        }
    }

    private fun updateAuthorsSortState(state: SortState) {
        authorsSortState.value = state
        viewModelScope.launch {
            settingsRepository.setAuthorsSortState(state.persistenceKey)
        }
    }

    private fun updateNarratorsSortState(state: SortState) {
        narratorsSortState.value = state
        viewModelScope.launch {
            settingsRepository.setNarratorsSortState(state.persistenceKey)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SORTING HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    @Suppress("CyclomaticComplexMethod")
    private fun sortBooks(
        books: List<Book>,
        state: SortState,
        ignoreArticles: Boolean,
    ): List<Book> {
        val isAsc = state.direction == SortDirection.ASCENDING

        return when (state.category) {
            SortCategory.TITLE -> {
                if (isAsc) {
                    books.sortedBy { it.title.sortableTitle(ignoreArticles) }
                } else {
                    books.sortedByDescending { it.title.sortableTitle(ignoreArticles) }
                }
            }

            SortCategory.AUTHOR -> {
                if (isAsc) {
                    books.sortedWith(
                        compareBy<Book> { it.authorNames.lowercase() }
                            .thenBy { it.title.lowercase() },
                    )
                } else {
                    books.sortedWith(
                        compareByDescending<Book> { it.authorNames.lowercase() }
                            .thenBy { it.title.lowercase() },
                    )
                }
            }

            SortCategory.DURATION -> {
                if (isAsc) {
                    books.sortedBy { it.duration }
                } else {
                    books.sortedByDescending { it.duration }
                }
            }

            SortCategory.YEAR -> {
                if (isAsc) {
                    books.sortedWith(
                        compareBy<Book> { it.publishYear ?: Int.MAX_VALUE }
                            .thenBy { it.title.lowercase() },
                    )
                } else {
                    books.sortedWith(
                        compareByDescending<Book> { it.publishYear ?: 0 }
                            .thenBy { it.title.lowercase() },
                    )
                }
            }

            SortCategory.ADDED -> {
                if (isAsc) {
                    books.sortedBy { it.addedAt.epochMillis }
                } else {
                    books.sortedByDescending { it.addedAt.epochMillis }
                }
            }

            SortCategory.SERIES -> {
                if (isAsc) {
                    books.sortedWith(
                        compareBy<Book> { it.seriesName?.lowercase() ?: "\uFFFF" }
                            .thenBy { it.seriesSequence?.toFloatOrNull() ?: Float.MAX_VALUE }
                            .thenBy { it.title.lowercase() },
                    )
                } else {
                    books.sortedWith(
                        compareByDescending<Book> { it.seriesName?.lowercase() ?: "" }
                            .thenByDescending { it.seriesSequence?.toFloatOrNull() ?: 0f }
                            .thenBy { it.title.lowercase() },
                    )
                }
            }

            // Not applicable for books
            SortCategory.NAME, SortCategory.BOOK_COUNT -> {
                books
            }
        }
    }

    private fun sortSeries(
        series: List<SeriesWithBooks>,
        state: SortState,
    ): List<SeriesWithBooks> {
        val isAsc = state.direction == SortDirection.ASCENDING

        return when (state.category) {
            SortCategory.NAME -> {
                if (isAsc) {
                    series.sortedBy { it.series.name.lowercase() }
                } else {
                    series.sortedByDescending { it.series.name.lowercase() }
                }
            }

            SortCategory.BOOK_COUNT -> {
                if (isAsc) {
                    series.sortedBy { it.books.size }
                } else {
                    series.sortedByDescending { it.books.size }
                }
            }

            SortCategory.ADDED -> {
                if (isAsc) {
                    series.sortedBy { it.series.createdAt.epochMillis }
                } else {
                    series.sortedByDescending { it.series.createdAt.epochMillis }
                }
            }

            // Default to name sort for unsupported categories
            else -> {
                series.sortedBy { it.series.name.lowercase() }
            }
        }
    }

    private fun sortContributors(
        contributors: List<ContributorWithBookCount>,
        state: SortState,
    ): List<ContributorWithBookCount> {
        val isAsc = state.direction == SortDirection.ASCENDING

        return when (state.category) {
            SortCategory.NAME -> {
                if (isAsc) {
                    contributors.sortedBy { it.contributor.name.lowercase() }
                } else {
                    contributors.sortedByDescending { it.contributor.name.lowercase() }
                }
            }

            SortCategory.BOOK_COUNT -> {
                if (isAsc) {
                    contributors.sortedBy { it.bookCount }
                } else {
                    contributors.sortedByDescending { it.bookCount }
                }
            }

            // Default to name sort for unsupported categories
            else -> {
                contributors.sortedBy { it.contributor.name.lowercase() }
            }
        }
    }
}

/**
 * Events that can be triggered from the Library UI.
 */
sealed interface LibraryUiEvent {
    data object RefreshRequested : LibraryUiEvent

    data class BookClicked(
        val bookId: String,
    ) : LibraryUiEvent

    // Books tab
    data class BooksCategoryChanged(
        val category: SortCategory,
    ) : LibraryUiEvent

    data object BooksDirectionToggled : LibraryUiEvent

    data object ToggleIgnoreTitleArticles : LibraryUiEvent

    // Series tab
    data class SeriesCategoryChanged(
        val category: SortCategory,
    ) : LibraryUiEvent

    data object SeriesDirectionToggled : LibraryUiEvent

    // Authors tab
    data class AuthorsCategoryChanged(
        val category: SortCategory,
    ) : LibraryUiEvent

    data object AuthorsDirectionToggled : LibraryUiEvent

    // Narrators tab
    data class NarratorsCategoryChanged(
        val category: SortCategory,
    ) : LibraryUiEvent

    data object NarratorsDirectionToggled : LibraryUiEvent
}

/**
 * Selection mode state for multi-select functionality.
 */
sealed interface SelectionMode {
    /** No selection active - normal library behavior. */
    data object None : SelectionMode

    /** Multi-select mode is active with the given selected book IDs. */
    data class Active(
        val selectedIds: Set<String>,
    ) : SelectionMode
}
