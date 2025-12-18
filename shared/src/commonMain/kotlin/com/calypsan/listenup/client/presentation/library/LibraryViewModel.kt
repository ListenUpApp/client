package com.calypsan.listenup.client.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.data.local.db.ContributorDao
import com.calypsan.listenup.client.data.local.db.ContributorWithBookCount
import com.calypsan.listenup.client.data.local.db.PlaybackPositionDao
import com.calypsan.listenup.client.data.local.db.SeriesDao
import com.calypsan.listenup.client.data.local.db.SeriesWithBooks
import com.calypsan.listenup.client.data.local.db.SyncDao
import com.calypsan.listenup.client.data.local.db.getLastSyncTime
import com.calypsan.listenup.client.data.repository.BookRepositoryContract
import com.calypsan.listenup.client.data.repository.SettingsRepositoryContract
import com.calypsan.listenup.client.data.sync.SyncManagerContract
import com.calypsan.listenup.client.data.sync.SyncStatus
import com.calypsan.listenup.client.domain.model.Book
import com.calypsan.listenup.client.util.sortableTitle
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

/**
 * ViewModel for the Library screen.
 *
 * Manages book list data, sort state, and sync state for UI consumption.
 * Implements intelligent auto-sync: triggers initial sync automatically
 * if user is authenticated but has never synced before.
 *
 * Sort state is separated into category + direction, allowing users to:
 * - Toggle direction with a single tap
 * - Change category via dropdown menu
 */
class LibraryViewModel(
    private val bookRepository: BookRepositoryContract,
    private val seriesDao: SeriesDao,
    private val contributorDao: ContributorDao,
    private val syncManager: SyncManagerContract,
    private val settingsRepository: SettingsRepositoryContract,
    private val syncDao: SyncDao,
    private val playbackPositionDao: PlaybackPositionDao,
) : ViewModel() {
    // Sort state for each tab (category + direction)
    private val _booksSortState = MutableStateFlow(SortState.booksDefault)
    val booksSortState: StateFlow<SortState> = _booksSortState.asStateFlow()

    private val _seriesSortState = MutableStateFlow(SortState.seriesDefault)
    val seriesSortState: StateFlow<SortState> = _seriesSortState.asStateFlow()

    private val _authorsSortState = MutableStateFlow(SortState.contributorDefault)
    val authorsSortState: StateFlow<SortState> = _authorsSortState.asStateFlow()

    private val _narratorsSortState = MutableStateFlow(SortState.contributorDefault)
    val narratorsSortState: StateFlow<SortState> = _narratorsSortState.asStateFlow()

    // Article handling for title sort (A, An, The)
    private val _ignoreTitleArticles = MutableStateFlow(true)
    val ignoreTitleArticles: StateFlow<Boolean> = _ignoreTitleArticles.asStateFlow()

    // Series display preferences
    private val _hideSingleBookSeries = MutableStateFlow(true)
    val hideSingleBookSeries: StateFlow<Boolean> = _hideSingleBookSeries.asStateFlow()

    /**
     * Observable list of books, sorted by current sort state.
     */
    val books: StateFlow<List<Book>> =
        combine(
            bookRepository.observeBooks(),
            _booksSortState,
            _ignoreTitleArticles,
        ) { books, sortState, ignoreArticles ->
            sortBooks(books, sortState, ignoreArticles)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    /**
     * Observable list of series with their books, sorted and filtered by current state.
     * Filters out single-book series when hideSingleBookSeries is enabled.
     */
    val series: StateFlow<List<SeriesWithBooks>> =
        combine(
            seriesDao.observeAllWithBooks(),
            _seriesSortState,
            _hideSingleBookSeries,
        ) { series, sortState, hideSingle ->
            val filtered = if (hideSingle) {
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
            contributorDao.observeByRoleWithCount("author"),
            _authorsSortState,
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
            contributorDao.observeByRoleWithCount("narrator"),
            _narratorsSortState,
        ) { narrators, sortState ->
            sortContributors(narrators, sortState)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    /**
     * Observable sync status.
     */
    val syncState: StateFlow<SyncStatus> = syncManager.syncState

    /**
     * Observable progress data for all books.
     * Maps bookId -> progress (0.0 to 1.0).
     * Used for showing progress indicators on book cards.
     */
    val bookProgress: StateFlow<Map<String, Float>> =
        combine(
            playbackPositionDao.observeAll(),
            books,
        ) { positions, booksList ->
            // Create a map of book durations for progress calculation
            val bookDurations = booksList.associate { it.id.value to it.duration }

            // Convert positions to progress percentages
            positions
                .mapNotNull { position ->
                    val bookId = position.bookId.value
                    val duration = bookDurations[bookId] ?: return@mapNotNull null
                    if (duration <= 0) return@mapNotNull null

                    val progress = (position.positionMs.toFloat() / duration).coerceIn(0f, 1f)
                    // Only include books with meaningful progress (> 0% and < 99%)
                    if (progress > 0f && progress < 0.99f) {
                        bookId to progress
                    } else {
                        null
                    }
                }.toMap()
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap(),
        )

    private var hasPerformedInitialSync = false

    init {
        // Load persisted sort states and preferences
        viewModelScope.launch {
            settingsRepository.getBooksSortState()?.let { key ->
                SortState.fromPersistenceKey(key)?.let { _booksSortState.value = it }
            }
            settingsRepository.getSeriesSortState()?.let { key ->
                SortState.fromPersistenceKey(key)?.let { _seriesSortState.value = it }
            }
            settingsRepository.getAuthorsSortState()?.let { key ->
                SortState.fromPersistenceKey(key)?.let { _authorsSortState.value = it }
            }
            settingsRepository.getNarratorsSortState()?.let { key ->
                SortState.fromPersistenceKey(key)?.let { _narratorsSortState.value = it }
            }
            // Load article handling preference
            _ignoreTitleArticles.value = settingsRepository.getIgnoreTitleArticles()
            // Load series display preference
            _hideSingleBookSeries.value = settingsRepository.getHideSingleBookSeries()
        }

        logger.debug { "Initialized (auto-sync deferred until screen visible)" }
    }

    /**
     * Called when the Library screen becomes visible.
     * Reloads preferences that may have changed in Settings.
     */
    fun onScreenVisible() {
        // Reload preferences in case user changed them in Settings
        viewModelScope.launch {
            _hideSingleBookSeries.value = settingsRepository.getHideSingleBookSeries()
            _ignoreTitleArticles.value = settingsRepository.getIgnoreTitleArticles()
        }

        if (hasPerformedInitialSync) return
        hasPerformedInitialSync = true

        logger.debug { "Screen became visible, checking if initial sync needed..." }
        viewModelScope.launch {
            val isAuthenticated = settingsRepository.getAccessToken() != null
            val lastSyncTime = syncDao.getLastSyncTime()

            if (isAuthenticated && lastSyncTime == null) {
                logger.info { "User authenticated but never synced, triggering initial sync..." }
                refreshBooks()
            }
        }
    }

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
                updateBooksSortState(
                    _booksSortState.value.withCategory(event.category),
                )
            }

            is LibraryUiEvent.BooksDirectionToggled -> {
                updateBooksSortState(
                    _booksSortState.value.toggleDirection(),
                )
            }

            // Series tab sort events
            is LibraryUiEvent.SeriesCategoryChanged -> {
                updateSeriesSortState(
                    _seriesSortState.value.withCategory(event.category),
                )
            }

            is LibraryUiEvent.SeriesDirectionToggled -> {
                updateSeriesSortState(
                    _seriesSortState.value.toggleDirection(),
                )
            }

            // Authors tab sort events
            is LibraryUiEvent.AuthorsCategoryChanged -> {
                updateAuthorsSortState(
                    _authorsSortState.value.withCategory(event.category),
                )
            }

            is LibraryUiEvent.AuthorsDirectionToggled -> {
                updateAuthorsSortState(
                    _authorsSortState.value.toggleDirection(),
                )
            }

            // Narrators tab sort events
            is LibraryUiEvent.NarratorsCategoryChanged -> {
                updateNarratorsSortState(
                    _narratorsSortState.value.withCategory(event.category),
                )
            }

            is LibraryUiEvent.NarratorsDirectionToggled -> {
                updateNarratorsSortState(
                    _narratorsSortState.value.toggleDirection(),
                )
            }

            // Title sort article handling
            is LibraryUiEvent.ToggleIgnoreTitleArticles -> {
                toggleIgnoreTitleArticles()
            }
        }
    }

    private fun toggleIgnoreTitleArticles() {
        val newValue = !_ignoreTitleArticles.value
        _ignoreTitleArticles.value = newValue
        viewModelScope.launch {
            settingsRepository.setIgnoreTitleArticles(newValue)
        }
    }

    private fun refreshBooks() {
        viewModelScope.launch {
            bookRepository.refreshBooks()
        }
    }

    // Sort state update methods (persist and update state)

    private fun updateBooksSortState(state: SortState) {
        _booksSortState.value = state
        viewModelScope.launch {
            settingsRepository.setBooksSortState(state.persistenceKey)
        }
    }

    private fun updateSeriesSortState(state: SortState) {
        _seriesSortState.value = state
        viewModelScope.launch {
            settingsRepository.setSeriesSortState(state.persistenceKey)
        }
    }

    private fun updateAuthorsSortState(state: SortState) {
        _authorsSortState.value = state
        viewModelScope.launch {
            settingsRepository.setAuthorsSortState(state.persistenceKey)
        }
    }

    private fun updateNarratorsSortState(state: SortState) {
        _narratorsSortState.value = state
        viewModelScope.launch {
            settingsRepository.setNarratorsSortState(state.persistenceKey)
        }
    }

    // Sorting helper functions

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
