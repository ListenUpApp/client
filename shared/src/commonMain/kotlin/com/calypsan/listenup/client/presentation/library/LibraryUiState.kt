package com.calypsan.listenup.client.presentation.library

import com.calypsan.listenup.client.domain.model.Book
import com.calypsan.listenup.client.data.sync.sse.ScanProgressState
import com.calypsan.listenup.client.domain.model.ContributorWithBookCount
import com.calypsan.listenup.client.domain.model.SeriesWithBooks
import com.calypsan.listenup.client.domain.model.SyncState

/**
 * Consolidated UI state for the Library screen.
 *
 * This data class replaces the multiple individual StateFlows in LibraryViewModel,
 * providing several benefits:
 *
 * - **Atomic updates**: All state changes are applied together, preventing
 *   inconsistent intermediate states where some fields have updated but others haven't.
 *
 * - **Easier testing**: State can be captured as snapshots for assertions,
 *   making it simple to verify complex state transitions.
 *
 * - **Better debugging**: The entire UI state can be logged or inspected as a
 *   single object, making it easier to understand the current state at any point.
 *
 * - **Simplified composition**: Composables receive a single state object rather
 *   than needing to collect multiple flows individually.
 *
 * @property booksSortState Current sort configuration for the Books tab
 * @property seriesSortState Current sort configuration for the Series tab
 * @property authorsSortState Current sort configuration for the Authors tab
 * @property narratorsSortState Current sort configuration for the Narrators tab
 * @property ignoreTitleArticles Whether to ignore articles (A, An, The) when sorting by title
 * @property hideSingleBookSeries Whether to hide series with only one book in the Series tab
 * @property hasLoadedBooks Whether the initial database load has completed;
 *   used to distinguish "loading" from "truly empty" library
 * @property books Sorted list of all books in the library
 * @property series Sorted and filtered list of series with their books
 * @property authors Sorted list of authors with their book counts
 * @property narrators Sorted list of narrators with their book counts
 * @property bookProgress Map of bookId to progress (0.0 to 1.0) for all books with playback progress
 * @property bookIsFinished Map of bookId to isFinished flag; authoritative completion status from server
 *   (honors ABS imports where books may be marked complete at <99% progress)
 * @property syncState Current synchronization state (idle, syncing, success, error, etc.)
 * @property selectionMode Current selection mode (none or active with selected book IDs)
 */
data class LibraryUiState(
    // Sort states for each tab
    val booksSortState: SortState = SortState.booksDefault,
    val seriesSortState: SortState = SortState.seriesDefault,
    val authorsSortState: SortState = SortState.contributorDefault,
    val narratorsSortState: SortState = SortState.contributorDefault,
    // Display preferences
    val ignoreTitleArticles: Boolean = true,
    val hideSingleBookSeries: Boolean = true,
    // Loading state
    val hasLoadedBooks: Boolean = false,
    // Content lists
    val books: List<Book> = emptyList(),
    val series: List<SeriesWithBooks> = emptyList(),
    val authors: List<ContributorWithBookCount> = emptyList(),
    val narrators: List<ContributorWithBookCount> = emptyList(),
    // Progress tracking (bookId -> progress 0.0 to 1.0)
    val bookProgress: Map<String, Float> = emptyMap(),
    // Completion status (bookId -> isFinished, authoritative from server)
    val bookIsFinished: Map<String, Boolean> = emptyMap(),
    // Sync state
    val syncState: SyncState = SyncState.Idle,
    // Server scanning state (true during library scan on server)
    val isServerScanning: Boolean = false,
    val scanProgress: ScanProgressState? = null,
    // Selection mode
    val selectionMode: SelectionMode = SelectionMode.None,
) {
    /**
     * Whether the library is currently loading (initial load not yet complete).
     */
    val isLoading: Boolean
        get() = !hasLoadedBooks

    /**
     * Whether the library is empty (loaded but contains no books).
     */
    val isEmpty: Boolean
        get() = hasLoadedBooks && books.isEmpty()

    /**
     * Whether a sync operation is currently in progress.
     */
    val isSyncing: Boolean
        get() = syncState is SyncState.Syncing || syncState is SyncState.Progress

    /**
     * Whether selection mode is currently active.
     */
    val isSelectionActive: Boolean
        get() = selectionMode is SelectionMode.Active

    /**
     * The number of currently selected books, or 0 if not in selection mode.
     */
    val selectedCount: Int
        get() =
            when (val mode = selectionMode) {
                is SelectionMode.None -> 0
                is SelectionMode.Active -> mode.selectedIds.size
            }
}
