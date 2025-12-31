package com.calypsan.listenup.client.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calypsan.listenup.client.data.local.db.CollectionDao
import com.calypsan.listenup.client.data.local.db.CollectionEntity
import com.calypsan.listenup.client.data.local.db.ContributorDao
import com.calypsan.listenup.client.data.local.db.ContributorWithBookCount
import com.calypsan.listenup.client.data.local.db.LensDao
import com.calypsan.listenup.client.data.local.db.LensEntity
import com.calypsan.listenup.client.data.local.db.PlaybackPositionDao
import com.calypsan.listenup.client.data.local.db.SeriesDao
import com.calypsan.listenup.client.data.local.db.SeriesWithBooks
import com.calypsan.listenup.client.data.local.db.SyncDao
import com.calypsan.listenup.client.data.local.db.UserDao
import com.calypsan.listenup.client.data.local.db.getLastSyncTime
import com.calypsan.listenup.client.data.remote.AdminCollectionApiContract
import com.calypsan.listenup.client.data.remote.LensApiContract
import com.calypsan.listenup.client.data.remote.model.toTimestamp
import com.calypsan.listenup.client.data.repository.BookRepositoryContract
import com.calypsan.listenup.client.data.repository.SettingsRepositoryContract
import com.calypsan.listenup.client.data.sync.SyncManagerContract
import com.calypsan.listenup.client.data.sync.model.SyncStatus
import com.calypsan.listenup.client.domain.model.Book
import com.calypsan.listenup.client.util.sortableTitle
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
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
    private val userDao: UserDao,
    private val collectionDao: CollectionDao,
    private val adminCollectionApi: AdminCollectionApiContract,
    private val lensDao: LensDao,
    private val lensApi: LensApiContract,
) : ViewModel() {
    // Sort state for each tab (category + direction)
    val booksSortState: StateFlow<SortState>
        field = MutableStateFlow(SortState.booksDefault)

    val seriesSortState: StateFlow<SortState>
        field = MutableStateFlow(SortState.seriesDefault)

    val authorsSortState: StateFlow<SortState>
        field = MutableStateFlow(SortState.contributorDefault)

    val narratorsSortState: StateFlow<SortState>
        field = MutableStateFlow(SortState.contributorDefault)

    // Article handling for title sort (A, An, The)
    val ignoreTitleArticles: StateFlow<Boolean>
        field = MutableStateFlow(true)

    // Series display preferences
    val hideSingleBookSeries: StateFlow<Boolean>
        field = MutableStateFlow(true)

    // Tracks whether initial database load has completed
    // Used to distinguish "loading" from "truly empty" in UI
    val hasLoadedBooks: StateFlow<Boolean>
        field = MutableStateFlow(false)

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
            seriesDao.observeAllWithBooks(),
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
            contributorDao.observeByRoleWithCount("author"),
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
            contributorDao.observeByRoleWithCount("narrator"),
            narratorsSortState,
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
            // Create a map of book durations for O(1) lookup
            val bookDurations = booksList.associate { it.id.value to it.duration }

            // Build progress map with capacity hint to avoid resizing
            buildMap(positions.size) {
                for (position in positions) {
                    val bookId = position.bookId.value
                    val duration = bookDurations[bookId] ?: continue
                    if (duration <= 0) continue

                    val progress = (position.positionMs.toFloat() / duration).coerceIn(0f, 1f)
                    // Only include books with meaningful progress (> 0% and < 99%)
                    if (progress > 0f && progress < PROGRESS_COMPLETE_THRESHOLD) {
                        put(bookId, progress)
                    }
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap(),
        )

    // Selection mode for multi-select (admin only)
    private val _selectionMode = MutableStateFlow<SelectionMode>(SelectionMode.None)
    val selectionMode: StateFlow<SelectionMode> = _selectionMode

    /**
     * Whether the current user is an admin (isRoot).
     * Only admins can use multi-select to add books to collections.
     */
    val isAdmin: StateFlow<Boolean> =
        userDao
            .observeCurrentUser()
            .map { user -> user?.isRoot == true }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = false,
            )

    /**
     * Observable list of collections for the collection picker.
     * Only relevant for admins.
     */
    val collections: StateFlow<List<CollectionEntity>> =
        collectionDao
            .observeAll()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

    // State for collection add operation
    private val _isAddingToCollection = MutableStateFlow(false)
    val isAddingToCollection: StateFlow<Boolean> = _isAddingToCollection

    /**
     * Observable list of the current user's lenses for the lens picker.
     * Available to all users (not just admins).
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val myLenses: StateFlow<List<LensEntity>> =
        userDao
            .observeCurrentUser()
            .flatMapLatest { user ->
                if (user != null) {
                    lensDao.observeMyLenses(user.id)
                } else {
                    flowOf(emptyList())
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

    // State for lens add operation
    private val _isAddingToLens = MutableStateFlow(false)
    val isAddingToLens: StateFlow<Boolean> = _isAddingToLens

    // Events for UI feedback
    private val _events = MutableSharedFlow<LibraryEvent>()
    val events = _events.asSharedFlow()

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
                    booksSortState.value.withCategory(event.category),
                )
            }

            is LibraryUiEvent.BooksDirectionToggled -> {
                updateBooksSortState(
                    booksSortState.value.toggleDirection(),
                )
            }

            // Series tab sort events
            is LibraryUiEvent.SeriesCategoryChanged -> {
                updateSeriesSortState(
                    seriesSortState.value.withCategory(event.category),
                )
            }

            is LibraryUiEvent.SeriesDirectionToggled -> {
                updateSeriesSortState(
                    seriesSortState.value.toggleDirection(),
                )
            }

            // Authors tab sort events
            is LibraryUiEvent.AuthorsCategoryChanged -> {
                updateAuthorsSortState(
                    authorsSortState.value.withCategory(event.category),
                )
            }

            is LibraryUiEvent.AuthorsDirectionToggled -> {
                updateAuthorsSortState(
                    authorsSortState.value.toggleDirection(),
                )
            }

            // Narrators tab sort events
            is LibraryUiEvent.NarratorsCategoryChanged -> {
                updateNarratorsSortState(
                    narratorsSortState.value.withCategory(event.category),
                )
            }

            is LibraryUiEvent.NarratorsDirectionToggled -> {
                updateNarratorsSortState(
                    narratorsSortState.value.toggleDirection(),
                )
            }

            // Title sort article handling
            is LibraryUiEvent.ToggleIgnoreTitleArticles -> {
                toggleIgnoreTitleArticles()
            }
        }
    }

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

    // Selection mode actions

    /**
     * Enter selection mode with the given book as the initial selection.
     * Available to all users for lens actions; collection actions require admin.
     * Refreshes collections (for admins) to ensure picker has up-to-date data.
     *
     * @param initialBookId The ID of the book that was long-pressed
     */
    fun enterSelectionMode(initialBookId: String) {
        _selectionMode.value = SelectionMode.Active(selectedIds = setOf(initialBookId))
        logger.debug { "Entered selection mode with book: $initialBookId" }
        if (isAdmin.value) {
            refreshCollections()
        }
    }

    /**
     * Refresh collections from the server API.
     * This syncs the local database with the latest server state including book counts.
     */
    private fun refreshCollections() {
        viewModelScope.launch {
            try {
                val serverCollections = adminCollectionApi.getCollections()
                logger.debug { "Fetched ${serverCollections.size} collections from server" }

                // Update local database with server data
                serverCollections.forEach { response ->
                    val entity =
                        CollectionEntity(
                            id = response.id,
                            name = response.name,
                            bookCount = response.bookCount,
                            createdAt = response.createdAt.toTimestamp(),
                            updatedAt = response.updatedAt.toTimestamp(),
                        )
                    collectionDao.upsert(entity)
                }

                // Delete local collections that no longer exist on server
                val serverIds = serverCollections.map { it.id }.toSet()
                val localCollections = collectionDao.getAll()
                localCollections.filter { it.id !in serverIds }.forEach { orphan ->
                    logger.debug { "Removing orphaned collection: ${orphan.name} (${orphan.id})" }
                    collectionDao.deleteById(orphan.id)
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to refresh collections from server" }
                // Don't emit error - local data is still usable
            }
        }
    }

    /**
     * Toggle the selection state of a book.
     * If in selection mode, adds/removes the book from selection.
     *
     * @param bookId The ID of the book to toggle
     */
    fun toggleBookSelection(bookId: String) {
        val current = _selectionMode.value
        if (current !is SelectionMode.Active) return

        val newSelectedIds =
            if (bookId in current.selectedIds) {
                current.selectedIds - bookId
            } else {
                current.selectedIds + bookId
            }

        // If no books remain selected, exit selection mode
        if (newSelectedIds.isEmpty()) {
            exitSelectionMode()
        } else {
            _selectionMode.value = SelectionMode.Active(selectedIds = newSelectedIds)
        }
    }

    /**
     * Exit selection mode and clear all selections.
     */
    fun exitSelectionMode() {
        _selectionMode.value = SelectionMode.None
        logger.debug { "Exited selection mode" }
    }

    /**
     * Add all selected books to the specified collection.
     * Calls the AdminCollectionApi and emits success/error events.
     *
     * @param collectionId The ID of the collection to add books to
     */
    fun addSelectedToCollection(collectionId: String) {
        val current = _selectionMode.value
        if (current !is SelectionMode.Active) return
        if (current.selectedIds.isEmpty()) return

        viewModelScope.launch {
            _isAddingToCollection.value = true
            try {
                val bookIds = current.selectedIds.toList()
                adminCollectionApi.addBooks(collectionId, bookIds)
                logger.info { "Added ${bookIds.size} books to collection $collectionId" }
                _events.emit(LibraryEvent.BooksAddedToCollection(bookIds.size))
                exitSelectionMode()
            } catch (e: Exception) {
                logger.error(e) { "Failed to add books to collection" }
                _events.emit(LibraryEvent.AddToCollectionFailed(e.message ?: "Unknown error"))
            } finally {
                _isAddingToCollection.value = false
            }
        }
    }

    /**
     * Add all selected books to the specified lens.
     * Calls the LensApi and emits success/error events.
     *
     * @param lensId The ID of the lens to add books to
     */
    fun addSelectedToLens(lensId: String) {
        val current = _selectionMode.value
        if (current !is SelectionMode.Active) return
        if (current.selectedIds.isEmpty()) return

        viewModelScope.launch {
            _isAddingToLens.value = true
            try {
                val bookIds = current.selectedIds.toList()
                lensApi.addBooks(lensId, bookIds)
                logger.info { "Added ${bookIds.size} books to lens $lensId" }
                _events.emit(LibraryEvent.BooksAddedToLens(bookIds.size))
                exitSelectionMode()
            } catch (e: Exception) {
                logger.error(e) { "Failed to add books to lens" }
                _events.emit(LibraryEvent.AddToLensFailed(e.message ?: "Unknown error"))
            } finally {
                _isAddingToLens.value = false
            }
        }
    }

    /**
     * Create a new lens and add all selected books to it.
     * First creates the lens via API, then adds the books.
     *
     * @param name The name for the new lens
     */
    fun createLensAndAddBooks(name: String) {
        val current = _selectionMode.value
        if (current !is SelectionMode.Active) return
        if (current.selectedIds.isEmpty()) return

        viewModelScope.launch {
            _isAddingToLens.value = true
            try {
                val bookIds = current.selectedIds.toList()
                // Create the lens
                val newLens = lensApi.createLens(name, null)
                logger.info { "Created lens '${newLens.name}' with id ${newLens.id}" }
                // Add books to the new lens
                lensApi.addBooks(newLens.id, bookIds)
                logger.info { "Added ${bookIds.size} books to new lens ${newLens.id}" }
                _events.emit(LibraryEvent.LensCreatedAndBooksAdded(newLens.name, bookIds.size))
                exitSelectionMode()
            } catch (e: Exception) {
                logger.error(e) { "Failed to create lens and add books" }
                _events.emit(LibraryEvent.AddToLensFailed(e.message ?: "Unknown error"))
            } finally {
                _isAddingToLens.value = false
            }
        }
    }

    // Sort state update methods (persist and update state)

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

    companion object {
        /** Progress threshold above which a book is considered complete (99%). */
        private const val PROGRESS_COMPLETE_THRESHOLD = 0.99f
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
 * Selection mode state for multi-select functionality (admin only).
 */
sealed interface SelectionMode {
    /**
     * No selection active - normal library behavior.
     */
    data object None : SelectionMode

    /**
     * Multi-select mode is active with the given selected book IDs.
     */
    data class Active(
        val selectedIds: Set<String>,
    ) : SelectionMode
}

/**
 * One-time events emitted by the Library ViewModel for UI feedback.
 */
sealed interface LibraryEvent {
    /**
     * Books were successfully added to a collection.
     */
    data class BooksAddedToCollection(
        val count: Int,
    ) : LibraryEvent

    /**
     * Failed to add books to a collection.
     */
    data class AddToCollectionFailed(
        val message: String,
    ) : LibraryEvent

    /**
     * Books were successfully added to a lens.
     */
    data class BooksAddedToLens(
        val count: Int,
    ) : LibraryEvent

    /**
     * A new lens was created and books were added to it.
     */
    data class LensCreatedAndBooksAdded(
        val lensName: String,
        val bookCount: Int,
    ) : LibraryEvent

    /**
     * Failed to add books to a lens.
     */
    data class AddToLensFailed(
        val message: String,
    ) : LibraryEvent
}
