package com.calypsan.listenup.client.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.domain.model.Book
import com.calypsan.listenup.client.domain.model.ContributorRole
import com.calypsan.listenup.client.domain.model.ContributorWithBookCount
import com.calypsan.listenup.client.domain.model.SeriesWithBooks
import com.calypsan.listenup.client.domain.repository.AuthSession
import com.calypsan.listenup.client.domain.repository.BookRepository
import com.calypsan.listenup.client.domain.repository.ContributorRepository
import com.calypsan.listenup.client.domain.repository.LibraryPreferences
import com.calypsan.listenup.client.domain.repository.PlaybackPositionRepository
import com.calypsan.listenup.client.domain.repository.SeriesRepository
import com.calypsan.listenup.client.domain.repository.SyncRepository
import com.calypsan.listenup.client.domain.repository.SyncStatusRepository
import com.calypsan.listenup.client.util.sortableTitle
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
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
    private val authSession: AuthSession,
    private val libraryPreferences: LibraryPreferences,
    private val syncStatusRepository: SyncStatusRepository,
    private val selectionManager: LibrarySelectionManager,
) : ViewModel() {
    // ═══════════════════════════════════════════════════════════════════════
    // CONSOLIDATED UI STATE
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Single source of truth for all Library UI state.
     * All state mutations go through `_uiState.update { it.copy(...) }`.
     */
    private val _uiState = MutableStateFlow(LibraryUiState())

    /**
     * Consolidated UI state for the Library screen.
     * Prefer using this over individual StateFlow properties for new code.
     */
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    // ═══════════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════════════

    private var hasPerformedInitialSync = false

    init {
        // Load persisted sort states and preferences
        viewModelScope.launch {
            libraryPreferences.getBooksSortState()?.let { key ->
                SortState.fromPersistenceKey(key)?.let { state ->
                    _uiState.update { it.copy(booksSortState = state) }
                }
            }
            libraryPreferences.getSeriesSortState()?.let { key ->
                SortState.fromPersistenceKey(key)?.let { state ->
                    _uiState.update { it.copy(seriesSortState = state) }
                }
            }
            libraryPreferences.getAuthorsSortState()?.let { key ->
                SortState.fromPersistenceKey(key)?.let { state ->
                    _uiState.update { it.copy(authorsSortState = state) }
                }
            }
            libraryPreferences.getNarratorsSortState()?.let { key ->
                SortState.fromPersistenceKey(key)?.let { state ->
                    _uiState.update { it.copy(narratorsSortState = state) }
                }
            }
            // Load article handling preference
            _uiState.update { it.copy(ignoreTitleArticles = libraryPreferences.getIgnoreTitleArticles()) }
            // Load series display preference
            _uiState.update { it.copy(hideSingleBookSeries = libraryPreferences.getHideSingleBookSeries()) }
        }

        // Set up flow collectors that update uiState

        // Books: combine repository data with sort state
        combine(
            bookRepository.observeBooks(),
            uiState.map { it.booksSortState },
            uiState.map { it.ignoreTitleArticles },
        ) { rawBooks, sortState, ignoreArticles ->
            sortBooks(rawBooks, sortState, ignoreArticles)
        }.onEach { sortedBooks ->
            _uiState.update { current ->
                current.copy(
                    books = sortedBooks,
                    hasLoadedBooks = true,
                )
            }
        }.launchIn(viewModelScope)

        // Series: combine repository data with sort state and filter preference
        combine(
            seriesRepository.observeAllWithBooks(),
            uiState.map { it.seriesSortState },
            uiState.map { it.hideSingleBookSeries },
        ) { rawSeries, sortState, hideSingle ->
            val filtered = if (hideSingle) rawSeries.filter { it.books.size > 1 } else rawSeries
            sortSeries(filtered, sortState)
        }.onEach { sortedSeries ->
            _uiState.update { it.copy(series = sortedSeries) }
        }.launchIn(viewModelScope)

        // Authors: combine repository data with sort state
        combine(
            contributorRepository.observeContributorsByRole(ContributorRole.AUTHOR.apiValue),
            uiState.map { it.authorsSortState },
        ) { rawAuthors, sortState ->
            sortContributors(rawAuthors, sortState)
        }.onEach { sortedAuthors ->
            _uiState.update { it.copy(authors = sortedAuthors) }
        }.launchIn(viewModelScope)

        // Narrators: combine repository data with sort state
        combine(
            contributorRepository.observeContributorsByRole(ContributorRole.NARRATOR.apiValue),
            uiState.map { it.narratorsSortState },
        ) { rawNarrators, sortState ->
            sortContributors(rawNarrators, sortState)
        }.onEach { sortedNarrators ->
            _uiState.update { it.copy(narrators = sortedNarrators) }
        }.launchIn(viewModelScope)

        // Sync state: observe from repository
        syncRepository.syncState
            .onEach { newSyncState ->
                _uiState.update { it.copy(syncState = newSyncState) }
            }.launchIn(viewModelScope)

        // Server scanning state: observe from repository
        syncRepository.isServerScanning
            .onEach { isScanning ->
                _uiState.update { it.copy(isServerScanning = isScanning) }
            }.launchIn(viewModelScope)

        // Book progress: combine playback positions with book durations
        combine(
            playbackPositionRepository.observeAll(),
            uiState.map { it.books },
        ) { positions, booksList ->
            val bookDurations = booksList.associate { it.id.value to it.duration }
            buildMap(positions.size) {
                for ((bookId, position) in positions) {
                    val duration = bookDurations[bookId] ?: continue
                    if (duration <= 0) continue
                    val progress = (position.positionMs.toFloat() / duration).coerceIn(0f, 1f)
                    if (progress > 0f) {
                        put(bookId, progress)
                    }
                }
            }
        }.onEach { progressMap ->
            _uiState.update { it.copy(bookProgress = progressMap) }
        }.launchIn(viewModelScope)

        // Selection mode: observe from shared manager
        selectionManager.selectionMode
            .onEach { mode ->
                _uiState.update { it.copy(selectionMode = mode) }
            }.launchIn(viewModelScope)

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
            _uiState.update {
                it.copy(
                    hideSingleBookSeries = libraryPreferences.getHideSingleBookSeries(),
                    ignoreTitleArticles = libraryPreferences.getIgnoreTitleArticles(),
                )
            }
        }

        if (hasPerformedInitialSync) return
        hasPerformedInitialSync = true

        logger.debug { "Screen became visible, checking if initial sync needed..." }
        viewModelScope.launch {
            val isAuthenticated = authSession.getAccessToken() != null
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
                updateBooksSortState(_uiState.value.booksSortState.withCategory(event.category))
            }

            is LibraryUiEvent.BooksDirectionToggled -> {
                updateBooksSortState(_uiState.value.booksSortState.toggleDirection())
            }

            // Series tab sort events
            is LibraryUiEvent.SeriesCategoryChanged -> {
                updateSeriesSortState(_uiState.value.seriesSortState.withCategory(event.category))
            }

            is LibraryUiEvent.SeriesDirectionToggled -> {
                updateSeriesSortState(_uiState.value.seriesSortState.toggleDirection())
            }

            // Authors tab sort events
            is LibraryUiEvent.AuthorsCategoryChanged -> {
                updateAuthorsSortState(_uiState.value.authorsSortState.withCategory(event.category))
            }

            is LibraryUiEvent.AuthorsDirectionToggled -> {
                updateAuthorsSortState(_uiState.value.authorsSortState.toggleDirection())
            }

            // Narrators tab sort events
            is LibraryUiEvent.NarratorsCategoryChanged -> {
                updateNarratorsSortState(_uiState.value.narratorsSortState.withCategory(event.category))
            }

            is LibraryUiEvent.NarratorsDirectionToggled -> {
                updateNarratorsSortState(_uiState.value.narratorsSortState.toggleDirection())
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
        val newValue = !_uiState.value.ignoreTitleArticles
        _uiState.update { it.copy(ignoreTitleArticles = newValue) }
        viewModelScope.launch {
            libraryPreferences.setIgnoreTitleArticles(newValue)
        }
    }

    private fun refreshBooks() {
        viewModelScope.launch {
            bookRepository.refreshBooks()
        }
    }

    private fun updateBooksSortState(state: SortState) {
        _uiState.update { it.copy(booksSortState = state) }
        viewModelScope.launch {
            libraryPreferences.setBooksSortState(state.persistenceKey)
        }
    }

    private fun updateSeriesSortState(state: SortState) {
        _uiState.update { it.copy(seriesSortState = state) }
        viewModelScope.launch {
            libraryPreferences.setSeriesSortState(state.persistenceKey)
        }
    }

    private fun updateAuthorsSortState(state: SortState) {
        _uiState.update { it.copy(authorsSortState = state) }
        viewModelScope.launch {
            libraryPreferences.setAuthorsSortState(state.persistenceKey)
        }
    }

    private fun updateNarratorsSortState(state: SortState) {
        _uiState.update { it.copy(narratorsSortState = state) }
        viewModelScope.launch {
            libraryPreferences.setNarratorsSortState(state.persistenceKey)
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
