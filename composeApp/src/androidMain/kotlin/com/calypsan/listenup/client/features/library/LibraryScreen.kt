package com.calypsan.listenup.client.features.library

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calypsan.listenup.client.domain.model.SyncState
import com.calypsan.listenup.client.features.library.components.AuthorsContent
import com.calypsan.listenup.client.features.library.components.BooksContent
import com.calypsan.listenup.client.features.library.components.LibraryTabRow
import com.calypsan.listenup.client.features.library.components.NarratorsContent
import com.calypsan.listenup.client.features.library.components.SelectionToolbar
import com.calypsan.listenup.client.features.library.components.SeriesContent
import com.calypsan.listenup.client.presentation.library.LibraryActionEvent
import com.calypsan.listenup.client.presentation.library.LibraryActionsViewModel
import com.calypsan.listenup.client.presentation.library.LibraryUiEvent
import com.calypsan.listenup.client.presentation.library.LibraryViewModel
import com.calypsan.listenup.client.presentation.library.SelectionMode
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * Library screen displaying the user's audiobook collection.
 *
 * Features:
 * - Four tabs: Books, Series, Authors, Narrators
 * - Swipeable content via HorizontalPager
 * - Pull-to-refresh for manual sync (applies to all tabs)
 * - Intelligent auto-sync on first visibility
 * - Split button sort controls (category + direction)
 *
 * This screen is designed to work within the AppShell scaffold,
 * so it does not include its own Scaffold or TopAppBar.
 *
 * @param onBookClick Callback when a book is clicked
 * @param onSeriesClick Callback when a series is clicked
 * @param onAuthorClick Callback when an author is clicked
 * @param onNarratorClick Callback when a narrator is clicked
 * @param topBarCollapseFraction Fraction of top bar collapse (0 = expanded, 1 = collapsed)
 * @param modifier Modifier from parent (includes scaffold padding)
 * @param viewModel The LibraryViewModel (injected via Koin)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onBookClick: (String) -> Unit,
    onSeriesClick: (String) -> Unit,
    onAuthorClick: (String) -> Unit,
    onNarratorClick: (String) -> Unit,
    topBarCollapseFraction: Float = 0f,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = koinViewModel(),
    actionsViewModel: LibraryActionsViewModel = koinViewModel(),
) {
    val context = LocalContext.current

    // Trigger intelligent auto-sync when screen becomes visible (only once)
    LaunchedEffect(Unit) {
        viewModel.onScreenVisible()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CONTENT STATE (from LibraryViewModel)
    // ═══════════════════════════════════════════════════════════════════════
    val books by viewModel.books.collectAsStateWithLifecycle()
    val hasLoadedBooks by viewModel.hasLoadedBooks.collectAsStateWithLifecycle()
    val series by viewModel.series.collectAsStateWithLifecycle()
    val authors by viewModel.authors.collectAsStateWithLifecycle()
    val narrators by viewModel.narrators.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val bookProgress by viewModel.bookProgress.collectAsStateWithLifecycle()

    // Sort state for each tab
    val booksSortState by viewModel.booksSortState.collectAsStateWithLifecycle()
    val seriesSortState by viewModel.seriesSortState.collectAsStateWithLifecycle()
    val authorsSortState by viewModel.authorsSortState.collectAsStateWithLifecycle()
    val narratorsSortState by viewModel.narratorsSortState.collectAsStateWithLifecycle()
    val ignoreTitleArticles by viewModel.ignoreTitleArticles.collectAsStateWithLifecycle()

    // Selection mode (from LibraryViewModel, which delegates to shared manager)
    val selectionMode by viewModel.selectionMode.collectAsStateWithLifecycle()

    // ═══════════════════════════════════════════════════════════════════════
    // ACTION STATE (from LibraryActionsViewModel)
    // ═══════════════════════════════════════════════════════════════════════
    val isAdmin by actionsViewModel.isAdmin.collectAsStateWithLifecycle()
    val collections by actionsViewModel.collections.collectAsStateWithLifecycle()
    val isAddingToCollection by actionsViewModel.isAddingToCollection.collectAsStateWithLifecycle()
    val myLenses by actionsViewModel.myLenses.collectAsStateWithLifecycle()
    val isAddingToLens by actionsViewModel.isAddingToLens.collectAsStateWithLifecycle()

    // Derive selection state
    val isInSelectionMode = selectionMode is SelectionMode.Active
    val selectedBookIds = (selectionMode as? SelectionMode.Active)?.selectedIds ?: emptySet()

    // Collection and lens picker sheet state
    var showCollectionPicker by remember { mutableStateOf(false) }
    var showLensPicker by remember { mutableStateOf(false) }

    // Handle back press to exit selection mode
    BackHandler(enabled = isInSelectionMode) {
        viewModel.exitSelectionMode()
    }

    // Notify actions VM when selection mode is entered (to refresh collections for admins)
    LaunchedEffect(selectionMode) {
        if (selectionMode is SelectionMode.Active) {
            actionsViewModel.onSelectionModeEntered()
        }
    }

    // Handle action events (toast feedback)
    LaunchedEffect(Unit) {
        actionsViewModel.events.collect { event ->
            when (event) {
                is LibraryActionEvent.BooksAddedToCollection -> {
                    showCollectionPicker = false
                    Toast
                        .makeText(
                            context,
                            if (event.count == 1) {
                                "1 book added to collection"
                            } else {
                                "${event.count} books added to collection"
                            },
                            Toast.LENGTH_SHORT,
                        ).show()
                }

                is LibraryActionEvent.AddToCollectionFailed -> {
                    Toast
                        .makeText(
                            context,
                            "Failed to add: ${event.message}",
                            Toast.LENGTH_LONG,
                        ).show()
                }

                is LibraryActionEvent.BooksAddedToLens -> {
                    showLensPicker = false
                    Toast
                        .makeText(
                            context,
                            if (event.count == 1) "1 book added to lens" else "${event.count} books added to lens",
                            Toast.LENGTH_SHORT,
                        ).show()
                }

                is LibraryActionEvent.LensCreatedAndBooksAdded -> {
                    showLensPicker = false
                    val bookText = if (event.bookCount == 1) "1 book" else "${event.bookCount} books"
                    Toast
                        .makeText(
                            context,
                            "Created \"${event.lensName}\" with $bookText",
                            Toast.LENGTH_SHORT,
                        ).show()
                }

                is LibraryActionEvent.AddToLensFailed -> {
                    Toast
                        .makeText(
                            context,
                            "Failed to add: ${event.message}",
                            Toast.LENGTH_LONG,
                        ).show()
                }
            }
        }
    }

    // Pager state for tab switching
    val pagerState = rememberPagerState(pageCount = { LibraryTab.entries.size })
    val scope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Tab row - collapses icons when top bar collapses
            LibraryTabRow(
                selectedTabIndex = pagerState.currentPage,
                onTabSelected = { index ->
                    scope.launch {
                        pagerState.animateScrollToPage(index)
                    }
                },
                collapseFraction = topBarCollapseFraction,
            )

            // Pull-to-refresh wraps entire pager (syncs all data)
            PullToRefreshBox(
                isRefreshing = syncState is SyncState.Syncing,
                onRefresh = { viewModel.onEvent(LibraryUiEvent.RefreshRequested) },
                modifier = Modifier.fillMaxSize(),
            ) {
                // Swipeable content
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    when (LibraryTab.entries[page]) {
                        LibraryTab.Books -> {
                            BooksContent(
                                books = books,
                                hasLoadedBooks = hasLoadedBooks,
                                syncState = syncState,
                                sortState = booksSortState,
                                ignoreTitleArticles = ignoreTitleArticles,
                                bookProgress = bookProgress,
                                isInSelectionMode = isInSelectionMode,
                                selectedBookIds = selectedBookIds,
                                onCategorySelected = { category ->
                                    viewModel.onEvent(LibraryUiEvent.BooksCategoryChanged(category))
                                },
                                onDirectionToggle = {
                                    viewModel.onEvent(LibraryUiEvent.BooksDirectionToggled)
                                },
                                onToggleIgnoreArticles = {
                                    viewModel.onEvent(LibraryUiEvent.ToggleIgnoreTitleArticles)
                                },
                                onBookClick = { bookId ->
                                    if (isInSelectionMode) {
                                        viewModel.toggleBookSelection(bookId)
                                    } else {
                                        onBookClick(bookId)
                                    }
                                },
                                onBookLongPress = { bookId ->
                                    viewModel.enterSelectionMode(bookId)
                                },
                                onRetry = { viewModel.onEvent(LibraryUiEvent.RefreshRequested) },
                            )
                        }

                        LibraryTab.Series -> {
                            SeriesContent(
                                series = series,
                                sortState = seriesSortState,
                                onCategorySelected = { category ->
                                    viewModel.onEvent(LibraryUiEvent.SeriesCategoryChanged(category))
                                },
                                onDirectionToggle = {
                                    viewModel.onEvent(LibraryUiEvent.SeriesDirectionToggled)
                                },
                                onSeriesClick = onSeriesClick,
                            )
                        }

                        LibraryTab.Authors -> {
                            AuthorsContent(
                                authors = authors,
                                sortState = authorsSortState,
                                onCategorySelected = { category ->
                                    viewModel.onEvent(LibraryUiEvent.AuthorsCategoryChanged(category))
                                },
                                onDirectionToggle = {
                                    viewModel.onEvent(LibraryUiEvent.AuthorsDirectionToggled)
                                },
                                onAuthorClick = onAuthorClick,
                            )
                        }

                        LibraryTab.Narrators -> {
                            NarratorsContent(
                                narrators = narrators,
                                sortState = narratorsSortState,
                                onCategorySelected = { category ->
                                    viewModel.onEvent(LibraryUiEvent.NarratorsCategoryChanged(category))
                                },
                                onDirectionToggle = {
                                    viewModel.onEvent(LibraryUiEvent.NarratorsDirectionToggled)
                                },
                                onNarratorClick = onNarratorClick,
                            )
                        }
                    }
                }
            }
        }

        // Selection toolbar (overlays at top)
        AnimatedVisibility(
            visible = isInSelectionMode,
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            SelectionToolbar(
                selectedCount = selectedBookIds.size,
                onAddToLens = { showLensPicker = true },
                onAddToCollection =
                    if (isAdmin) {
                        { showCollectionPicker = true }
                    } else {
                        null
                    },
                onClose = { viewModel.exitSelectionMode() },
            )
        }
    }

    // Collection picker sheet
    if (showCollectionPicker) {
        CollectionPickerSheet(
            collections = collections,
            selectedBookCount = selectedBookIds.size,
            onCollectionSelected = { collectionId ->
                actionsViewModel.addSelectedToCollection(collectionId)
            },
            onDismiss = { showCollectionPicker = false },
            isLoading = isAddingToCollection,
        )
    }

    // Lens picker sheet
    if (showLensPicker) {
        LensPickerSheet(
            lenses = myLenses,
            selectedBookCount = selectedBookIds.size,
            onLensSelected = { lensId ->
                actionsViewModel.addSelectedToLens(lensId)
            },
            onCreateAndAddToLens = { name ->
                actionsViewModel.createLensAndAddBooks(name)
            },
            onDismiss = { showLensPicker = false },
            isLoading = isAddingToLens,
        )
    }
}
